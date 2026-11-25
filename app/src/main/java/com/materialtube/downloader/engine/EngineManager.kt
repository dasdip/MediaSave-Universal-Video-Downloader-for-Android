package com.materialtube.downloader.engine

import android.content.Context
import android.util.Log
import com.materialtube.downloader.data.EngineChannel
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class EnginePhase {
    IDLE,
    INITIALIZING,
    CHECKING,
    UPDATING,
    UP_TO_DATE,
    UPDATED,
    OFFLINE,
    ERROR,
}

data class SingleEngineInfo(
    val name: String,
    val phase: EnginePhase = EnginePhase.IDLE,
    val version: String? = null,
    val latestRelease: String? = null,
    val message: String = "Waiting to initialize...",
    val releaseUrl: String? = null,
)

data class EnginesState(
    val initialized: Boolean = false,
    val checking: Boolean = false,
    val ytdlp: SingleEngineInfo = SingleEngineInfo(name = "yt-dlp"),
    val ffmpeg: SingleEngineInfo = SingleEngineInfo(name = "FFmpeg"),
)

class EngineManager(private val context: Context) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(EnginesState())
    val state: StateFlow<EnginesState> = _state.asStateFlow()

    private val initReady = CompletableDeferred<Boolean>()
    private val ytdlpReady = CompletableDeferred<Boolean>()

    companion object {
        private const val TAG = "EngineManager"
        private const val BUNDLED_YTDLP_ANDROID_TAG = "0.18.1"
        private const val FFMPEG_RELEASE_API =
            "https://api.github.com/repos/junkfood02/youtubedl-android/releases/latest"
    }

    suspend fun awaitInitialized(): Boolean = initReady.await()

    suspend fun awaitYtdlpReady(): Boolean = ytdlpReady.await()

    suspend fun initializeAndAutoUpdate(
        autoUpdate: Boolean,
        channel: EngineChannel,
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!_state.value.initialized) {
                    _state.update {
                        it.copy(
                            checking = true,
                            ytdlp = it.ytdlp.copy(phase = EnginePhase.INITIALIZING, message = "Initializing yt-dlp..."),
                            ffmpeg = it.ffmpeg.copy(phase = EnginePhase.INITIALIZING, message = "Initializing FFmpeg..."),
                        )
                    }
                    try {
                        YoutubeDL.getInstance().init(context)
                        FFmpeg.getInstance().init(context)
                        val ytVer = readYoutubeDlVersion()
                        val ffVer = readFfmpegVersion()
                        _state.update {
                            it.copy(
                                initialized = true,
                                ytdlp = it.ytdlp.copy(
                                    phase = EnginePhase.UP_TO_DATE,
                                    version = ytVer,
                                    message = ytVer?.let { v -> "Ready ($v)" } ?: "Ready",
                                ),
                                ffmpeg = it.ffmpeg.copy(
                                    phase = EnginePhase.UP_TO_DATE,
                                    version = ffVer ?: BUNDLED_YTDLP_ANDROID_TAG,
                                    message = ffVer?.let { v -> "Ready ($v)" } ?: "Ready (module v$BUNDLED_YTDLP_ANDROID_TAG)",
                                ),
                            )
                        }
                        initReady.complete(true)
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        Log.e(TAG, "Failed to initialize engines", t)
                        _state.update {
                            it.copy(
                                checking = false,
                                ytdlp = it.ytdlp.copy(
                                    phase = EnginePhase.ERROR,
                                    message = t.localizedMessage ?: t.javaClass.simpleName,
                                ),
                                ffmpeg = it.ffmpeg.copy(
                                    phase = EnginePhase.ERROR,
                                    message = t.localizedMessage ?: t.javaClass.simpleName,
                                ),
                            )
                        }
                        initReady.complete(false)
                        ytdlpReady.complete(false)
                        return@withContext
                    }
                } else if (!initReady.isCompleted) {
                    initReady.complete(true)
                }

                if (autoUpdate) {
                    _state.update { it.copy(checking = true) }
                    updateYtDlp(channel)
                    if (!ytdlpReady.isCompleted) ytdlpReady.complete(true)
                    checkFfmpegUpdate()
                    _state.update { it.copy(checking = false) }
                } else {
                    if (!ytdlpReady.isCompleted) ytdlpReady.complete(true)
                    _state.update { it.copy(checking = false) }
                }
            } finally {
                if (!initReady.isCompleted) initReady.complete(false)
                if (!ytdlpReady.isCompleted) ytdlpReady.complete(false)
            }
        }
    }

    suspend fun checkAndUpdateNow(channel: EngineChannel) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!_state.value.initialized) {
                initializeAndAutoUpdate(autoUpdate = true, channel = channel)
                return@withContext
            }
            checkAndUpdateEnginesInternal(channel)
        }
    }

    private fun checkAndUpdateEnginesInternal(channel: EngineChannel) {
        _state.update { it.copy(checking = true) }
        updateYtDlp(channel)
        checkFfmpegUpdate()
        _state.update { it.copy(checking = false) }
    }

    private fun updateYtDlp(channel: EngineChannel) {
        val currentVersion = readYoutubeDlVersion()
        _state.update {
            it.copy(
                ytdlp = it.ytdlp.copy(
                    phase = EnginePhase.UPDATING,
                    version = currentVersion,
                    message = "Checking & updating yt-dlp (${channel.name.lowercase()})...",
                )
            )
        }

        val ytdlChannel = when (channel) {
            EngineChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
            EngineChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
        }

        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, ytdlChannel)
            val newVersion = readYoutubeDlVersion()
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> {
                    _state.update {
                        it.copy(
                            ytdlp = it.ytdlp.copy(
                                phase = EnginePhase.UPDATED,
                                version = newVersion,
                                message = newVersion?.let { v -> "Updated to $v" } ?: "Updated yt-dlp",
                            )
                        )
                    }
                }
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                    _state.update {
                        it.copy(
                            ytdlp = it.ytdlp.copy(
                                phase = EnginePhase.UP_TO_DATE,
                                version = newVersion,
                                message = newVersion?.let { v -> "Up to date ($v)" } ?: "Up to date",
                            )
                        )
                    }
                }
                else -> {
                    _state.update {
                        it.copy(
                            ytdlp = it.ytdlp.copy(
                                phase = EnginePhase.UP_TO_DATE,
                                version = newVersion,
                                message = newVersion?.let { v -> "Ready ($v)" } ?: "Ready",
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed", e)
            val fallbackVer = readYoutubeDlVersion()
            _state.update {
                it.copy(
                    ytdlp = it.ytdlp.copy(
                        phase = if (fallbackVer != null) EnginePhase.OFFLINE else EnginePhase.ERROR,
                        version = fallbackVer,
                        message = if (fallbackVer != null) {
                            "Offline (using $fallbackVer)"
                        } else {
                            "Update failed: ${e.localizedMessage ?: "Network error"}"
                        },
                    )
                )
            }
        }
    }

    /**
     * Android 10+ (W^X) restricts executing dynamically downloaded native binaries outside
     * nativeLibraryDir, so FFmpeg ships as libffmpeg.so inside the app's native libs.
     * We verify the active libffmpeg.so version with LD_LIBRARY_PATH and check upstream releases.
     */
    private fun checkFfmpegUpdate() {
        val localVer = readFfmpegVersion()
        val displayVer = localVer ?: "module v$BUNDLED_YTDLP_ANDROID_TAG"
        _state.update {
            it.copy(
                ffmpeg = it.ffmpeg.copy(
                    phase = EnginePhase.CHECKING,
                    version = displayVer,
                    message = "Checking FFmpeg module release...",
                )
            )
        }

        try {
            val conn = (URL(FFMPEG_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MediaSave-Android")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val latestTag = json.optString("tag_name").trim().removePrefix("v")
            val htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() }

            val isNewer = latestTag.isNotBlank() && compareVersions(latestTag, BUNDLED_YTDLP_ANDROID_TAG) > 0
            _state.update {
                it.copy(
                    ffmpeg = it.ffmpeg.copy(
                        phase = if (isNewer) EnginePhase.UPDATED else EnginePhase.UP_TO_DATE,
                        version = displayVer,
                        latestRelease = latestTag.ifBlank { null },
                        releaseUrl = htmlUrl,
                        message = if (isNewer) {
                            "FFmpeg $displayVer active (module v$latestTag available)"
                        } else {
                            "Up to date ($displayVer)"
                        },
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "FFmpeg release check failed", e)
            _state.update {
                it.copy(
                    ffmpeg = it.ffmpeg.copy(
                        phase = EnginePhase.OFFLINE,
                        version = displayVer,
                        message = "Offline (using $displayVer)",
                    )
                )
            }
        }
    }

    private fun readYoutubeDlVersion(): String? = runCatching {
        YoutubeDL.getInstance().version(context)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun readFfmpegVersion(): String? = runCatching {
        val libFfmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!libFfmpeg.exists()) return@runCatching null

        val packagesDir = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val libPaths = listOf(
            File(packagesDir, "ffmpeg/usr/lib").absolutePath,
            File(packagesDir, "python/usr/lib").absolutePath,
            File(packagesDir, "aria2c/usr/lib").absolutePath,
            context.applicationInfo.nativeLibraryDir,
        ).filter { File(it).exists() }

        val pb = ProcessBuilder(libFfmpeg.absolutePath, "-version").apply {
            redirectErrorStream(true)
            val existingLd = environment()["LD_LIBRARY_PATH"].orEmpty()
            val combined = (libPaths + existingLd).filter { it.isNotBlank() }.joinToString(":")
            environment()["LD_LIBRARY_PATH"] = combined
        }

        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().use { it.readText() }
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            Log.w(TAG, "libffmpeg.so -version exited $exitCode: ${output.lines().firstOrNull()}")
            return@runCatching null
        }

        val firstLine = output.lineSequence().firstOrNull {
            it.contains("ffmpeg version", ignoreCase = true)
        }?.trim() ?: return@runCatching null

        Regex("ffmpeg version\\s+([^\\s]+)", RegexOption.IGNORE_CASE)
            .find(firstLine)
            ?.groupValues
            ?.getOrNull(1)
    }.getOrNull()

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
