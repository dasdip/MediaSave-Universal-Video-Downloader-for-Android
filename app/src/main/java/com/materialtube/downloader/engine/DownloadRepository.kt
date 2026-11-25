package com.materialtube.downloader.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.materialtube.downloader.data.DownloadMode
import com.materialtube.downloader.service.DownloadService
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class JobStatus {
    QUEUED,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    ERROR,
}

data class DownloadJob(
    val id: String = UUID.randomUUID().toString().take(10),
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val mode: DownloadMode,
    val quality: String,
    val container: String,
    val embedThumbnail: Boolean,
    val embedMetadata: Boolean,
    val status: JobStatus = JobStatus.QUEUED,
    val phase: String = "Queued",
    val progress: Float = 0f,
    val etaSeconds: Long = -1L,
    val statusLine: String = "",
    val outputUri: Uri? = null,
    val outputFileName: String? = null,
    val errorMessage: String? = null,
)

data class CompletedDownload(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val mode: DownloadMode,
    val quality: String,
    val container: String,
    val uriString: String,
    val legacyFilePath: String?,
    val fileName: String,
    val fileSizeBytes: Long,
    val completedAtMillis: Long,
)

class DownloadRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _history = MutableStateFlow<List<CompletedDownload>>(emptyList())
    val history: StateFlow<List<CompletedDownload>> = _history.asStateFlow()

    private val historyFile = File(context.filesDir, "download_history.json")
    private val historyLock = Any()
    private val cancelledIds = mutableSetOf<String>()

    @Volatile
    private var isProcessingQueue = false

    init {
        scope.launch {
            loadAndPruneHistory()
        }
    }

    fun refreshHistoryAvailability() {
        scope.launch {
            val current = _history.value
            val valid = current.filter { isFileAvailableInStorage(it) }
            if (valid.size != current.size) {
                _history.value = valid
                persistHistory(valid)
            }
        }
    }

    fun enqueue(job: DownloadJob) {
        _jobs.update { listOf(job) + it }
        startServiceAndDispatch()
    }

    fun enqueueAll(newJobs: List<DownloadJob>) {
        if (newJobs.isEmpty()) return
        _jobs.update { newJobs.reversed() + it }
        startServiceAndDispatch()
    }

    fun cancel(jobId: String) {
        synchronized(cancelledIds) { cancelledIds.add(jobId) }
        YoutubeDL.getInstance().destroyProcessById(jobId)
        updateJob(jobId) {
            if (it.status == JobStatus.QUEUED || it.status == JobStatus.DOWNLOADING || it.status == JobStatus.PROCESSING) {
                it.copy(status = JobStatus.CANCELLED, phase = "Cancelled")
            } else {
                it
            }
        }
    }

    fun retry(jobId: String) {
        val old = _jobs.value.firstOrNull { it.id == jobId } ?: return
        _jobs.update { list -> list.filterNot { it.id == jobId } }
        val fresh = old.copy(
            id = UUID.randomUUID().toString().take(10),
            status = JobStatus.QUEUED,
            phase = "Queued",
            progress = 0f,
            etaSeconds = -1L,
            statusLine = "",
            errorMessage = null,
        )
        enqueue(fresh)
    }

    fun removeJob(jobId: String) {
        _jobs.update { list ->
            list.filterNot {
                it.id == jobId && it.status in setOf(JobStatus.COMPLETED, JobStatus.CANCELLED, JobStatus.ERROR)
            }
        }
    }

    fun clearFinishedJobs() {
        _jobs.update { list ->
            list.filter { it.status in setOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.PROCESSING) }
        }
    }

    fun clearHistory() {
        scope.launch {
            _history.value = emptyList()
            persistHistory(emptyList())
        }
    }

    fun deleteHistoryItemFromStorage(id: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val target = _history.value.firstOrNull { it.id == id }
            if (target == null) {
                onResult(false)
                return@launch
            }
            val deleted = deleteFileFromStorage(target)
            val updated = _history.value.filterNot { it.id == id }
            _history.value = updated
            persistHistory(updated)
            onResult(deleted)
        }
    }

    private fun startServiceAndDispatch() {
        val intent = Intent(context, DownloadService::class.java)
        ContextCompat.startForegroundService(context, intent)
        dispatchNext()
    }

    @Synchronized
    private fun dispatchNext() {
        if (isProcessingQueue) return
        val next = _jobs.value.lastOrNull { it.status == JobStatus.QUEUED } ?: return
        isProcessingQueue = true
        scope.launch {
            try {
                runJob(next)
            } finally {
                isProcessingQueue = false
                dispatchNext()
            }
        }
    }

    private fun runJob(job: DownloadJob) {
        if (isCancelled(job.id)) {
            updateJob(job.id) { it.copy(status = JobStatus.CANCELLED, phase = "Cancelled") }
            return
        }

        val stagingDir = File(context.filesDir, "dl_staging/${job.id}").apply {
            deleteRecursively()
            mkdirs()
        }

        updateJob(job.id) { it.copy(status = JobStatus.DOWNLOADING, phase = "Fetching streams...") }

        try {
            val request = buildRequest(job, stagingDir)
            YoutubeDL.getInstance().execute(request, job.id) { progress, etaInSeconds, line ->
                if (isCancelled(job.id)) return@execute
                val cleanLine = line.orEmpty().trim()
                val isFfmpegPhase = cleanLine.contains("[Merger]", ignoreCase = true) ||
                    cleanLine.contains("[ExtractAudio]", ignoreCase = true) ||
                    cleanLine.contains("[EmbedThumbnail]", ignoreCase = true) ||
                    cleanLine.contains("[Metadata]", ignoreCase = true)

                val pct = if (progress in 0f..100f) progress else -1f
                updateJob(job.id) { current ->
                    val nextProgress = when {
                        isFfmpegPhase -> maxOf(current.progress, 96f)
                        pct >= 0f -> maxOf(current.progress, pct.coerceAtMost(95f))
                        else -> current.progress
                    }
                    val phaseLabel = when {
                        cleanLine.contains("[Merger]", ignoreCase = true) -> "Merging streams (FFmpeg)"
                        cleanLine.contains("[ExtractAudio]", ignoreCase = true) -> "Converting audio (FFmpeg)"
                        cleanLine.contains("[EmbedThumbnail]", ignoreCase = true) -> "Embedding thumbnail"
                        cleanLine.contains("[Metadata]", ignoreCase = true) -> "Writing metadata"
                        isFfmpegPhase -> "FFmpeg processing"
                        else -> "Downloading"
                    }
                    current.copy(
                        status = if (isFfmpegPhase) JobStatus.PROCESSING else JobStatus.DOWNLOADING,
                        phase = phaseLabel,
                        progress = nextProgress,
                        etaSeconds = etaInSeconds,
                        statusLine = cleanLine.takeLast(80),
                    )
                }
            }

            if (isCancelled(job.id)) {
                updateJob(job.id) { it.copy(status = JobStatus.CANCELLED, phase = "Cancelled") }
                return
            }

            val producedFile = stagingDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: error("No output file produced by yt-dlp")

            val fileSize = producedFile.length()
            updateJob(job.id) { it.copy(status = JobStatus.PROCESSING, phase = "Saving to Downloads...", progress = 99f) }
            val (savedUri, legacyPath) = publishToDownloads(producedFile)

            val completedItem = CompletedDownload(
                id = job.id,
                url = job.url,
                title = job.title,
                uploader = job.uploader,
                thumbnailUrl = job.thumbnailUrl,
                mode = job.mode,
                quality = job.quality,
                container = job.container,
                uriString = savedUri.toString(),
                legacyFilePath = legacyPath,
                fileName = producedFile.name,
                fileSizeBytes = fileSize,
                completedAtMillis = System.currentTimeMillis(),
            )

            // Move completed item from active jobs into persistent history
            _jobs.update { list -> list.filterNot { it.id == job.id } }
            val updatedHistory = listOf(completedItem) + _history.value.filterNot { it.id == completedItem.id }
            _history.value = updatedHistory
            persistHistory(updatedHistory)
        } catch (e: Exception) {
            if (isCancelled(job.id)) {
                updateJob(job.id) { it.copy(status = JobStatus.CANCELLED, phase = "Cancelled") }
            } else {
                updateJob(job.id) {
                    it.copy(
                        status = JobStatus.ERROR,
                        phase = "Failed",
                        errorMessage = e.localizedMessage ?: "Download failed",
                    )
                }
            }
        } finally {
            stagingDir.deleteRecursively()
            synchronized(cancelledIds) { cancelledIds.remove(job.id) }
        }
    }

    internal fun buildRequest(job: DownloadJob, outputDir: File): YoutubeDLRequest {
        val outTemplate = File(outputDir, "%(title).120B.%(ext)s").absolutePath
        val cacheDir = File(context.cacheDir, "ytdlp_cache").apply { mkdirs() }
        return YoutubeDLRequest(job.url).apply {
            addOption("--newline")
            addOption("--no-playlist")
            addOption("--cache-dir", cacheDir.absolutePath)
            addOption("-o", outTemplate)

            if (job.mode == DownloadMode.AUDIO) {
                val audioFmt = job.container.lowercase().takeIf {
                    it in setOf("mp3", "m4a", "opus", "flac")
                } ?: "mp3"
                val audioQ = if (audioFmt == "flac") {
                    "0"
                } else {
                    job.quality.takeIf { it in setOf("0", "320K", "256K", "192K", "128K", "96K") } ?: "0"
                }
                addOption("-f", "ba/b")
                addOption("-x")
                addOption("--audio-format", audioFmt)
                addOption("--audio-quality", audioQ)
            } else {
                val container = job.container.lowercase().takeIf {
                    it in setOf("mp4", "mkv", "webm")
                } ?: "mp4"
                val formatExpr = if (job.quality == "best") {
                    if (container == "mp4") "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b" else "bv*+ba/b"
                } else {
                    val h = job.quality.filter { it.isDigit() }.ifBlank { "1080" }
                    if (container == "mp4") {
                        "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b"
                    } else {
                        "bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b"
                    }
                }
                addOption("-f", formatExpr)
                addOption("--merge-output-format", container)
            }

            if (job.embedMetadata) {
                addOption("--embed-metadata")
            }
            if (job.embedThumbnail) {
                addOption("--embed-thumbnail")
            }
        }
    }

    private fun publishToDownloads(file: File): Pair<Uri, String?> {
        val mime = guessMimeType(file.extension)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MediaSave")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore download entry")
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Failed to open MediaStore output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri to null
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "MediaSave",
            ).apply { mkdirs() }
            val dest = uniqueFile(downloadsDir, file.name)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
            return uri to dest.absolutePath
        }
    }

    private fun loadAndPruneHistory() {
        val loaded = readHistoryFromDisk()
        val valid = loaded.filter { isFileAvailableInStorage(it) }
        _history.value = valid
        if (valid.size != loaded.size) {
            persistHistory(valid)
        }
    }

    private fun isFileAvailableInStorage(item: CompletedDownload): Boolean {
        if (!item.legacyFilePath.isNullOrBlank()) {
            val file = File(item.legacyFilePath)
            return file.exists() && file.length() > 0L
        }
        return runCatching {
            val uri = Uri.parse(item.uriString)
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@runCatching false
            } ?: return@runCatching false
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length != 0L
            } ?: false
        }.getOrDefault(false)
    }

    private fun deleteFileFromStorage(item: CompletedDownload): Boolean {
        if (!item.legacyFilePath.isNullOrBlank()) {
            return runCatching {
                val f = File(item.legacyFilePath)
                !f.exists() || f.delete()
            }.getOrDefault(false)
        }
        return runCatching {
            val uri = Uri.parse(item.uriString)
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    private fun readHistoryFromDisk(): List<CompletedDownload> = synchronized(historyLock) {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(historyFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val mode = runCatching {
                        DownloadMode.valueOf(obj.optString("mode", DownloadMode.VIDEO.name))
                    }.getOrDefault(DownloadMode.VIDEO)
                    add(
                        CompletedDownload(
                            id = obj.optString("id"),
                            url = obj.optString("url"),
                            title = obj.optString("title"),
                            uploader = obj.optString("uploader"),
                            thumbnailUrl = obj.optString("thumbnailUrl"),
                            mode = mode,
                            quality = obj.optString("quality", "1080"),
                            container = obj.optString("container", "mp4"),
                            uriString = obj.optString("uriString"),
                            legacyFilePath = obj.optString("legacyFilePath").takeIf { it.isNotBlank() },
                            fileName = obj.optString("fileName"),
                            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                            completedAtMillis = obj.optLong("completedAtMillis", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistHistory(items: List<CompletedDownload>) = synchronized(historyLock) {
        runCatching {
            val arr = JSONArray()
            items.take(200).forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("url", item.url)
                        put("title", item.title)
                        put("uploader", item.uploader)
                        put("thumbnailUrl", item.thumbnailUrl)
                        put("mode", item.mode.name)
                        put("quality", item.quality)
                        put("container", item.container)
                        put("uriString", item.uriString)
                        put("legacyFilePath", item.legacyFilePath.orEmpty())
                        put("fileName", item.fileName)
                        put("fileSizeBytes", item.fileSizeBytes)
                        put("completedAtMillis", item.completedAtMillis)
                    }
                )
            }
            historyFile.writeText(arr.toString())
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension
        var idx = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "$base ($idx).$ext" else "$base ($idx)"
            candidate = File(dir, nextName)
            idx++
        }
        return candidate
    }

    private fun guessMimeType(ext: String): String = when (ext.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    private fun isCancelled(jobId: String): Boolean = synchronized(cancelledIds) {
        cancelledIds.contains(jobId)
    }

    private fun updateJob(jobId: String, transform: (DownloadJob) -> DownloadJob) {
        _jobs.update { list ->
            list.map { if (it.id == jobId) transform(it) else it }
        }
    }
}
