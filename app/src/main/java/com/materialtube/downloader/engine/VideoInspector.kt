package com.materialtube.downloader.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class QualityOption(
    val id: String,
    val label: String,
    val estimatedBytes: Long? = null,
)

data class PlaylistEntry(
    val index: Int,
    val id: String,
    val title: String,
    val url: String,
    val durationText: String,
    val uploader: String,
    val thumbnailUrl: String,
)

data class InspectedMedia(
    val isPlaylist: Boolean,
    val id: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationText: String,
    val webpageUrl: String,
    val viewCount: Long?,
    val qualities: List<QualityOption>,
    val entries: List<PlaylistEntry>,
    val isPreliminary: Boolean = false,
)

object VideoInspector {

    private val standardHeights = listOf(2160, 1440, 1080, 720, 480, 360)

    internal val defaultQualities = listOf(
        QualityOption("best", "Best"),
        QualityOption("2160", "4K (2160p)"),
        QualityOption("1440", "2K (1440p)"),
        QualityOption("1080", "1080p HD"),
        QualityOption("720", "720p HD"),
        QualityOption("480", "480p"),
        QualityOption("360", "360p"),
    )

    internal fun isExplicitPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase()
        if ("/playlist?" in lower) return true
        return "list=" in lower && "v=" !in lower && "youtu.be/" !in lower && "/shorts/" !in lower
    }

    internal fun extractYouTubeVideoId(url: String): String? {
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/shorts/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/live/([A-Za-z0-9_-]{11})"),
        )
        return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.getOrNull(1) }
    }

    internal fun parseMetadataJson(rawOutput: String, fallbackUrl: String): InspectedMedia {
        val jsonLine = rawOutput.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: error("Could not parse video metadata")

        val json = JSONObject(jsonLine)
        val isPlaylist = json.optString("_type") == "playlist" || json.has("entries")

        if (isPlaylist) {
            val entriesArray = json.optJSONArray("entries")
            val entries = buildList {
                if (entriesArray != null) {
                    for (i in 0 until entriesArray.length()) {
                        val item = entriesArray.optJSONObject(i) ?: continue
                        var entryUrl = item.optString("url").ifBlank { item.optString("webpage_url") }
                        val entryId = item.optString("id").ifBlank { (i + 1).toString() }
                        if (entryUrl.isNotBlank() && !entryUrl.startsWith("http")) {
                            entryUrl = "https://www.youtube.com/watch?v=$entryId"
                        }
                        val thumb = extractThumbnail(item)
                        add(
                            PlaylistEntry(
                                index = i + 1,
                                id = entryId,
                                title = item.optString("title").ifBlank { "Video ${i + 1}" },
                                url = entryUrl,
                                durationText = item.optString("duration_string").ifBlank {
                                    formatDuration(item.optDouble("duration", 0.0).toLong())
                                },
                                uploader = item.optString("uploader").ifBlank { item.optString("channel") },
                                thumbnailUrl = thumb,
                            )
                        )
                    }
                }
            }

            return InspectedMedia(
                isPlaylist = true,
                id = json.optString("id"),
                title = json.optString("title").ifBlank { "Media Playlist" },
                uploader = json.optString("uploader").ifBlank { json.optString("channel", "Playlist") },
                thumbnailUrl = extractThumbnail(json).ifBlank { entries.firstOrNull()?.thumbnailUrl.orEmpty() },
                durationText = "",
                webpageUrl = json.optString("webpage_url").ifBlank { fallbackUrl },
                viewCount = null,
                qualities = defaultQualities,
                entries = entries,
                isPreliminary = false,
            )
        }

        val formats = json.optJSONArray("formats")
        val heightsSeen = mutableSetOf<Int>()
        val bestSizeByHeight = mutableMapOf<Int, Long>()
        var bestAudioSize = 0L

        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val vcodec = f.optString("vcodec", "none")
                val acodec = f.optString("acodec", "none")
                val size = f.optLong("filesize", 0L).takeIf { it > 0L }
                    ?: f.optLong("filesize_approx", 0L)
                val height = f.optInt("height", 0)

                if (vcodec == "none" && acodec != "none" && size > bestAudioSize) {
                    bestAudioSize = size
                }
                if (vcodec != "none" && height > 0) {
                    val snapped = standardHeights.firstOrNull { kotlin.math.abs(height - it) <= 24 } ?: height
                    heightsSeen.add(snapped)
                    if (size > (bestSizeByHeight[snapped] ?: 0L)) {
                        bestSizeByHeight[snapped] = size
                    }
                }
            }
        }

        val qualities = buildList {
            add(QualityOption("best", "Best"))
            heightsSeen.sortedDescending().forEach { h ->
                val label = when {
                    h >= 2160 -> "4K (${h}p)"
                    h >= 1440 -> "2K (${h}p)"
                    h == 1080 -> "1080p HD"
                    h == 720 -> "720p HD"
                    else -> "${h}p"
                }
                val est = bestSizeByHeight[h]?.let { if (it > 0 && bestAudioSize > 0) it + bestAudioSize else it }
                add(QualityOption(h.toString(), label, est?.takeIf { it > 0L }))
            }
        }

        val durationSec = json.optDouble("duration", 0.0).toLong()
        val views = if (json.has("view_count") && !json.isNull("view_count")) {
            json.optLong("view_count")
        } else null

        return InspectedMedia(
            isPlaylist = false,
            id = json.optString("id"),
            title = json.optString("title").ifBlank { "Untitled Video" },
            uploader = json.optString("uploader").ifBlank { json.optString("channel", "Media Source") },
            thumbnailUrl = extractThumbnail(json),
            durationText = json.optString("duration_string").ifBlank { formatDuration(durationSec) },
            webpageUrl = json.optString("webpage_url").ifBlank { fallbackUrl },
            viewCount = views,
            qualities = qualities,
            entries = emptyList(),
            isPreliminary = false,
        )
    }

    /**
     * Ultra-fast (~150-350ms) preview via YouTube oEmbed so the UI renders immediately
     * while yt-dlp extracts full format/size details in the background.
     */
    suspend fun quickInspect(rawUrl: String): InspectedMedia? = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        val lower = url.lowercase()
        if ("youtube.com" !in lower && "youtu.be" !in lower) return@withContext null
        if (isExplicitPlaylistUrl(url)) return@withContext null

        val videoId = extractYouTubeVideoId(url)
        val encoded = URLEncoder.encode(url, "UTF-8")
        val oembedUrl = "https://www.youtube.com/oembed?url=$encoded&format=json"

        runCatching {
            val conn = (URL(oembedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 3_000
                setRequestProperty("User-Agent", "MediaSave-Android")
            }
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val title = json.optString("title").takeIf { it.isNotBlank() } ?: return@runCatching null
            val author = json.optString("author_name").ifBlank { "Media Source" }
            val thumb = json.optString("thumbnail_url").ifBlank {
                videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
            }
            InspectedMedia(
                isPlaylist = false,
                id = videoId.orEmpty(),
                title = title,
                uploader = author,
                thumbnailUrl = thumb,
                durationText = "",
                webpageUrl = url,
                viewCount = null,
                qualities = defaultQualities,
                entries = emptyList(),
                isPreliminary = true,
            )
        }.getOrNull()
    }

    suspend fun inspect(context: Context, rawUrl: String): InspectedMedia = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Enter a valid HTTP or HTTPS video URL"
        }

        val cacheDir = File(context.cacheDir, "ytdlp_cache").apply { mkdirs() }
        val explicitPlaylist = isExplicitPlaylistUrl(url)

        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--no-check-formats")
            addOption("--skip-download")
            addOption("--no-write-subs")
            addOption("--no-write-auto-subs")
            addOption("--cache-dir", cacheDir.absolutePath)
            if (explicitPlaylist) {
                addOption("--flat-playlist")
                addOption("--playlist-end", 30)
            } else {
                addOption("--no-playlist")
            }
        }

        val response = YoutubeDL.getInstance().execute(request)
        parseMetadataJson(response.out, url)
    }

    private fun extractThumbnail(json: JSONObject): String {
        val direct = json.optString("thumbnail")
        if (direct.isNotBlank()) return direct
        val arr = json.optJSONArray("thumbnails") ?: return ""
        if (arr.length() == 0) return ""
        return arr.optJSONObject(arr.length() - 1)?.optString("url").orEmpty()
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
