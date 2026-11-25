package com.materialtube.downloader

import com.materialtube.downloader.data.DownloadMode
import com.materialtube.downloader.engine.DownloadJob
import com.materialtube.downloader.engine.VideoInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestTest {

    @Test
    fun videoJobDefaultsAreValid() {
        val job = DownloadJob(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Sample Video",
            uploader = "Channel",
            thumbnailUrl = "",
            mode = DownloadMode.VIDEO,
            quality = "1080",
            container = "mp4",
            embedThumbnail = true,
            embedMetadata = true,
        )
        assertEquals(DownloadMode.VIDEO, job.mode)
        assertEquals("1080", job.quality)
        assertTrue(job.embedMetadata)
    }

    @Test
    fun playlistDetectionTreatsWatchWithMixAsSingleVideo() {
        assertFalse(VideoInspector.isExplicitPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"))
        assertFalse(VideoInspector.isExplicitPlaylistUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(VideoInspector.isExplicitPlaylistUrl("https://www.youtube.com/playlist?list=PL123456789"))
    }

    @Test
    fun extractYouTubeVideoIdHandlesStandardShortAndShortsUrls() {
        assertEquals("dQw4w9WgXcQ", VideoInspector.extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", VideoInspector.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", VideoInspector.extractYouTubeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun audioJobStoresBitrateQuality() {
        val job = DownloadJob(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Sample Audio",
            uploader = "Artist",
            thumbnailUrl = "",
            mode = DownloadMode.AUDIO,
            quality = "320K",
            container = "mp3",
            embedThumbnail = true,
            embedMetadata = true,
        )
        assertEquals(DownloadMode.AUDIO, job.mode)
        assertEquals("320K", job.quality)
        assertEquals("mp3", job.container)
    }
}
