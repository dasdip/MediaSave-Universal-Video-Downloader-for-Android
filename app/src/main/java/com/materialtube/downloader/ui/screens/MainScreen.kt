package com.materialtube.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.materialtube.downloader.data.DownloadMode
import com.materialtube.downloader.engine.DownloadJob
import com.materialtube.downloader.engine.EnginePhase
import com.materialtube.downloader.engine.EnginesState
import com.materialtube.downloader.engine.InspectedMedia
import com.materialtube.downloader.engine.JobStatus
import com.materialtube.downloader.ui.MainUiState
import com.materialtube.downloader.ui.MainViewModel
import com.materialtube.downloader.ui.components.DownloadJobCard
import com.materialtube.downloader.ui.components.DownloadsSheet
import com.materialtube.downloader.ui.components.EngineManagerDialog
import com.materialtube.downloader.ui.components.SettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsState()
    val engines by viewModel.engines.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val history by viewModel.history.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showEnginesDialog by remember { mutableStateOf(false) }
    var showDownloadsSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val activeDownloadCount = jobs.count {
        it.status in setOf(JobStatus.QUEUED, JobStatus.DOWNLOADING, JobStatus.PROCESSING)
    }

    LaunchedEffect(ui.snackbarMessage) {
        ui.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Column {
                        Text(
                            text = "MediaSave",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "yt-dlp & FFmpeg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    EngineStatusPill(
                        engines = engines,
                        onClick = { showEnginesDialog = true },
                    )
                    IconButton(
                        onClick = {
                            viewModel.refreshHistory()
                            showDownloadsSheet = true
                        },
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeDownloadCount > 0) {
                                    Badge { Text(activeDownloadCount.toString()) }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Downloads")
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live Startup Auto-Update Banner
            item {
                AnimatedVisibility(visible = engines.checking) {
                    StartupUpdateBanner(
                        engines = engines,
                        onOpenDetails = { showEnginesDialog = true },
                    )
                }
            }

            // URL Input Card
            item {
                UrlInputCard(
                    url = ui.urlInput,
                    isInspecting = ui.isInspecting,
                    errorText = ui.inspectError,
                    onUrlChange = viewModel::onUrlChange,
                    onPaste = {
                        val text = clipboard.getText()?.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            viewModel.onUrlChange(text)
                        }
                    },
                    onFetch = { viewModel.inspectUrl() },
                )
            }

            // Media Inspector & Format Options
            ui.inspected?.let { media ->
                item {
                    InspectorCard(
                        media = media,
                        ui = ui,
                        onModeChange = viewModel::setMode,
                        onQualityChange = viewModel::setQuality,
                        onAudioQualityChange = viewModel::setAudioQuality,
                        onContainerChange = viewModel::setContainer,
                        onEmbedThumbChange = viewModel::setEmbedThumbnail,
                        onEmbedMetaChange = viewModel::setEmbedMetadata,
                        onTogglePlaylistItem = viewModel::togglePlaylistItem,
                        onSelectAllPlaylist = viewModel::selectAllPlaylist,
                        onDownload = viewModel::startDownload,
                    )
                }
            }

            // Active Queue Snapshot on Home
            if (jobs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Currently Downloading (${jobs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                viewModel.refreshHistory()
                                showDownloadsSheet = true
                            },
                        ) {
                            Text("View All")
                        }
                    }
                }
                items(jobs, key = { it.id }) { job ->
                    DownloadJobCard(
                        job = job,
                        onCancel = { viewModel.cancelJob(job.id) },
                        onRetry = { viewModel.retryJob(job.id) },
                        onRemove = { viewModel.removeJob(job.id) },
                    )
                }
            } else if (ui.inspected == null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                "Ready to save media",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Paste a video or playlist link above, or share from another app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEnginesDialog) {
        EngineManagerDialog(
            engines = engines,
            onCheckNow = viewModel::checkUpdatesNow,
            onDismiss = { showEnginesDialog = false },
        )
    }

    if (showDownloadsSheet) {
        DownloadsSheet(
            jobs = jobs,
            history = history,
            onCancelJob = viewModel::cancelJob,
            onRetryJob = viewModel::retryJob,
            onRemoveJob = viewModel::removeJob,
            onClearFinishedJobs = viewModel::clearFinished,
            onOpenHistoryItem = { item ->
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(item.uriString),
                        if (item.mode == DownloadMode.AUDIO) "audio/*" else "video/*",
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(openIntent) }
                    .onFailure { viewModel.showMessage("Saved in Downloads/MediaSave") }
            },
            onDeleteHistoryItem = viewModel::deleteHistoryItem,
            onClearHistory = viewModel::clearHistory,
            onDismiss = { showDownloadsSheet = false },
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            onUpdateSettings = { autoUpdate, channel ->
                viewModel.updateSettings(
                    autoUpdateOnStart = autoUpdate,
                    updateChannel = channel,
                )
            },
            onUpdateTheme = { mode, dynamic, seed ->
                viewModel.updateSettings(
                    themeMode = mode,
                    dynamicColor = dynamic,
                    seedPresetIndex = seed,
                )
            },
            onDismiss = { showSettingsDialog = false },
        )
    }
}

@Composable
private fun EngineStatusPill(
    engines: EnginesState,
    onClick: () -> Unit,
) {
    val hasError = engines.ytdlp.phase == EnginePhase.ERROR || engines.ffmpeg.phase == EnginePhase.ERROR
    val containerColor = when {
        engines.checking -> MaterialTheme.colorScheme.secondaryContainer
        hasError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        engines.checking -> MaterialTheme.colorScheme.onSecondaryContainer
        hasError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (engines.checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Icon(
                    imageVector = if (hasError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = when {
                    engines.checking -> "Updating..."
                    hasError -> "Engine issue"
                    else -> "Engines OK"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StartupUpdateBanner(
    engines: EnginesState,
    onOpenDetails: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        "Checking yt-dlp & FFmpeg updates...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = onOpenDetails) {
                    Text("Details")
                }
            }
            val activeMsg = if (engines.ytdlp.phase == EnginePhase.UPDATING || engines.ytdlp.phase == EnginePhase.INITIALIZING) {
                engines.ytdlp.message
            } else {
                engines.ffmpeg.message
            }
            Text(activeMsg, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun UrlInputCard(
    url: String,
    isInspecting: Boolean,
    errorText: String?,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onFetch: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CircleShape,
                placeholder = { Text("Paste Video or playlist URL") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        } else {
                            IconButton(onClick = onPaste) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFetch() }),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Saves to Downloads/MediaSave",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onFetch,
                    enabled = !isInspecting,
                ) {
                    Text(if (isInspecting) "Fetching..." else "Fetch Info")
                }
            }

            if (isInspecting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                )
            }

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorCard(
    media: InspectedMedia,
    ui: MainUiState,
    onModeChange: (DownloadMode) -> Unit,
    onQualityChange: (String) -> Unit,
    onAudioQualityChange: (String) -> Unit,
    onContainerChange: (String) -> Unit,
    onEmbedThumbChange: (Boolean) -> Unit,
    onEmbedMetaChange: (Boolean) -> Unit,
    onTogglePlaylistItem: (String) -> Unit,
    onSelectAllPlaylist: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    val videoContainers = listOf("mp4", "mkv", "webm")
    val audioContainers = listOf("mp3", "m4a", "opus", "flac")
    val audioQualityOptions = listOf(
        "0" to "Best",
        "320K" to "320 kbps",
        "256K" to "256 kbps",
        "192K" to "192 kbps",
        "128K" to "128 kbps",
        "96K" to "96 kbps",
    )
    val containers = if (ui.selectedMode == DownloadMode.AUDIO) audioContainers else videoContainers

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = media.thumbnailUrl,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (media.durationText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = media.durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = media.uploader,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (media.isPlaylist) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "Playlist · ${media.entries.size} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (ui.isRefiningFormats) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.8.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "Refining formats...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            if (media.isPlaylist && media.entries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${ui.selectedPlaylistIds.size} of ${media.entries.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row {
                        TextButton(onClick = { onSelectAllPlaylist(true) }) { Text("All") }
                        TextButton(onClick = { onSelectAllPlaylist(false) }) { Text("None") }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    media.entries.take(15).forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePlaylistItem(entry.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = entry.id in ui.selectedPlaylistIds,
                                    onCheckedChange = { onTogglePlaylistItem(entry.id) },
                                )
                                Text(
                                    text = "${entry.index}. ${entry.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = DownloadMode.entries
                modes.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = ui.selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                    ) {
                        Text(if (mode == DownloadMode.VIDEO) "Video + Audio" else "Audio Only")
                    }
                }
            }

            if (ui.selectedMode == DownloadMode.VIDEO) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Quality",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        media.qualities.forEach { q ->
                            FilterChip(
                                selected = ui.selectedQuality == q.id,
                                onClick = { onQualityChange(q.id) },
                                label = { Text(q.label) },
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Audio Quality",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ui.selectedContainer.equals("flac", ignoreCase = true)) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Lossless") },
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            audioQualityOptions.forEach { (id, label) ->
                                FilterChip(
                                    selected = ui.selectedAudioQuality == id,
                                    onClick = { onAudioQualityChange(id) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    containers.forEach { ext ->
                        FilterChip(
                            selected = ui.selectedContainer == ext,
                            onClick = { onContainerChange(ext) },
                            label = { Text(ext.uppercase()) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = ui.embedThumbnail, onCheckedChange = onEmbedThumbChange)
                    Text("Thumbnail", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = ui.embedMetadata, onCheckedChange = onEmbedMetaChange)
                    Text("Metadata", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = onDownload,
                enabled = !media.isPlaylist || ui.selectedPlaylistIds.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (media.isPlaylist) {
                        "Download ${ui.selectedPlaylistIds.size} Selected"
                    } else if (ui.selectedMode == DownloadMode.AUDIO) {
                        val aqLabel = if (ui.selectedContainer.equals("flac", ignoreCase = true)) {
                            "Lossless"
                        } else {
                            audioQualityOptions.firstOrNull { it.first == ui.selectedAudioQuality }?.second ?: "Best"
                        }
                        "Extract ${ui.selectedContainer.uppercase()} · $aqLabel"
                    } else {
                        val qLabel = if (ui.selectedQuality == "best") "Best" else "${ui.selectedQuality}p"
                        "Download $qLabel ${ui.selectedContainer.uppercase()}"
                    },
                )
            }
        }
    }
}
