package com.materialtube.downloader.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.materialtube.downloader.BuildConfig
import com.materialtube.downloader.data.AppSettings
import com.materialtube.downloader.data.DownloadMode
import com.materialtube.downloader.data.EngineChannel
import com.materialtube.downloader.data.ThemeMode
import com.materialtube.downloader.engine.CompletedDownload
import com.materialtube.downloader.engine.DownloadJob
import com.materialtube.downloader.engine.EnginePhase
import com.materialtube.downloader.engine.EnginesState
import com.materialtube.downloader.engine.JobStatus
import com.materialtube.downloader.engine.SingleEngineInfo
import com.materialtube.downloader.ui.theme.MaterialYouPresets

@Composable
fun EngineManagerDialog(
    engines: EnginesState,
    onCheckNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Engines") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EngineDetailCard(
                    info = engines.ytdlp,
                    onOpenRelease = null,
                )
                EngineDetailCard(
                    info = engines.ffmpeg,
                    onOpenRelease = engines.ffmpeg.releaseUrl?.let { url ->
                        {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCheckNow,
                enabled = !engines.checking,
            ) {
                Text(if (engines.checking) "Updating..." else "Check & Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun EngineDetailCard(
    info: SingleEngineInfo,
    onOpenRelease: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = info.version ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = info.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.phase in setOf(EnginePhase.INITIALIZING, EnginePhase.CHECKING, EnginePhase.UPDATING)) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                )
            }
            if (onOpenRelease != null && info.latestRelease != null) {
                TextButton(
                    onClick = onOpenRelease,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("View Release v${info.latestRelease}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onUpdateSettings: (Boolean, EngineChannel) -> Unit,
    onUpdateTheme: (ThemeMode, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            // Engine Section
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Engine & Updates",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-update engines on start", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Check yt-dlp & FFmpeg when app opens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.autoUpdateOnStart,
                            onCheckedChange = { enabled ->
                                onUpdateSettings(enabled, settings.updateChannel)
                            },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "yt-dlp Update Channel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val channels = EngineChannel.entries
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            channels.forEachIndexed { index, ch ->
                                SegmentedButton(
                                    selected = settings.updateChannel == ch,
                                    onClick = { onUpdateSettings(settings.autoUpdateOnStart, ch) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = channels.size),
                                ) {
                                    Text(ch.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            }
                        }
                    }
                }
            }

            // Theme Section (moved into Settings)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Theme & Material You",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    val modes = ThemeMode.entries
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    onUpdateTheme(mode, settings.dynamicColor, settings.seedPresetIndex)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            ) {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wallpaper Dynamic Color", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Match colors from your device wallpaper",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { enabled ->
                                    onUpdateTheme(settings.themeMode, enabled, settings.seedPresetIndex)
                                },
                            )
                        }
                    }

                    if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Text(
                            text = "Seed Palette",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MaterialYouPresets.forEachIndexed { index, preset ->
                                val selected = settings.seedPresetIndex == index
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(preset.primaryLight)
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.onSurface,
                                                    CircleShape,
                                                )
                                            } else Modifier
                                        )
                                        .clickable {
                                            onUpdateTheme(settings.themeMode, false, index)
                                        },
                                )
                            }
                        }
                    }
                }
            }

            // About Section
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "About MediaSave",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "MediaSave v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Developed by Dipendu Das",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "MediaSave lets you download videos and audio from YouTube, Facebook, and 1000+ supported sites using yt-dlp and FFmpeg. Choose video resolutions up to 4K, remux containers (MP4, MKV, WebM), extract and convert audio (MP3, M4A, Opus, FLAC), download playlists, and embed thumbnails and metadata tags.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    jobs: List<DownloadJob>,
    history: List<CompletedDownload>,
    onCancelJob: (String) -> Unit,
    onRetryJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onClearFinishedJobs: () -> Unit,
    onOpenHistoryItem: (CompletedDownload) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = onClearHistory) {
                            Text("Clear History")
                        }
                    }
                    if (jobs.any { it.status in setOf(JobStatus.CANCELLED, JobStatus.ERROR) }) {
                        TextButton(onClick = onClearFinishedJobs) {
                            Text("Clear Failed")
                        }
                    }
                }
            }

            if (jobs.isEmpty() && history.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "No downloads yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Active downloads and saved files will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (jobs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Currently Downloading (${jobs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(jobs, key = { "job_${it.id}" }) { job ->
                            DownloadJobCard(
                                job = job,
                                onCancel = { onCancelJob(job.id) },
                                onRetry = { onRetryJob(job.id) },
                                onRemove = { onRemoveJob(job.id) },
                            )
                        }
                    }

                    if (jobs.isNotEmpty() && history.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    if (history.isNotEmpty()) {
                        item {
                            Text(
                                text = "Completed History (${history.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(history, key = { "hist_${it.id}" }) { item ->
                            CompletedHistoryRow(
                                item = item,
                                onOpen = { onOpenHistoryItem(item) },
                                onDeleteFromStorage = { onDeleteHistoryItem(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadJobCard(
    job: DownloadJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val isActive = job.status in setOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.PROCESSING)
    val pctInt = job.progress.toInt().coerceIn(0, 100)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = job.thumbnailUrl,
                    contentDescription = job.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val tag = formatJobBadge(job.mode, job.quality, job.container)
                    val detail = buildString {
                        append(tag)
                        append(" · ")
                        append(job.errorMessage ?: job.phase)
                        if (job.status == JobStatus.DOWNLOADING || job.status == JobStatus.PROCESSING) {
                            append(" · $pctInt%")
                        }
                        if (job.etaSeconds > 0 && job.status == JobStatus.DOWNLOADING) {
                            append(" · ETA ${job.etaSeconds}s")
                        }
                    }
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (job.status == JobStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                } else {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Dismiss")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                )
                Text(
                    text = "$pctInt%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (job.status == JobStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun CompletedHistoryRow(
    item: CompletedDownload,
    onOpen: () -> Unit,
    onDeleteFromStorage: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = formatJobBadge(item.mode, item.quality, item.container)
                val sizeStr = formatSize(item.fileSizeBytes)
                Text(
                    text = if (sizeStr.isNotEmpty()) "$tag · $sizeStr" else tag,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = "Open file",
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(onClick = onDeleteFromStorage, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete from storage",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

private fun formatJobBadge(mode: DownloadMode, quality: String, container: String): String {
    val fmt = container.uppercase()
    return if (mode == DownloadMode.AUDIO) {
        val qLabel = when {
            fmt == "FLAC" -> "Lossless"
            quality == "0" -> "Best"
            quality.endsWith("K", ignoreCase = true) -> "${quality.dropLast(1)} kbps"
            else -> quality
        }
        "Audio · $fmt · $qLabel"
    } else {
        "${if (quality == "best") "Best" else quality + "p"} · $fmt"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f MB".format(mb) else "${(bytes / 1024L).coerceAtLeast(1)} KB"
}
