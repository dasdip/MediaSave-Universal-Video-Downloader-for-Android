package com.materialtube.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.materialtube.downloader.MaterialTubeApp
import com.materialtube.downloader.data.AppSettings
import com.materialtube.downloader.data.DownloadMode
import com.materialtube.downloader.data.EngineChannel
import com.materialtube.downloader.data.ThemeMode
import com.materialtube.downloader.engine.CompletedDownload
import com.materialtube.downloader.engine.DownloadJob
import com.materialtube.downloader.engine.EnginesState
import com.materialtube.downloader.engine.InspectedMedia
import com.materialtube.downloader.engine.VideoInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class MainUiState(
    val urlInput: String = "",
    val isInspecting: Boolean = false,
    val isRefiningFormats: Boolean = false,
    val inspectError: String? = null,
    val inspected: InspectedMedia? = null,
    val selectedMode: DownloadMode = DownloadMode.VIDEO,
    val selectedQuality: String = "1080",
    val selectedAudioQuality: String = "320K",
    val selectedContainer: String = "mp4",
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val selectedPlaylistIds: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MaterialTubeApp
    private val settingsStore = app.settingsStore
    private val engineManager = app.engineManager
    private val downloadRepo = app.downloadRepository

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    val engines: StateFlow<EnginesState> = engineManager.state
    val jobs: StateFlow<List<DownloadJob>> = downloadRepo.jobs
    val history: StateFlow<List<CompletedDownload>> = downloadRepo.history

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private var inspectJob: Job? = null

    init {
        viewModelScope.launch {
            val currentSettings = settingsStore.settings.first()
            _ui.update {
                it.copy(
                    selectedMode = currentSettings.defaultMode,
                    selectedQuality = currentSettings.defaultQuality,
                    selectedContainer = if (currentSettings.defaultMode == DownloadMode.AUDIO) {
                        currentSettings.defaultAudioFormat
                    } else {
                        currentSettings.defaultVideoContainer
                    },
                    embedThumbnail = currentSettings.embedThumbnail,
                    embedMetadata = currentSettings.embedMetadata,
                )
            }
            engineManager.initializeAndAutoUpdate(
                autoUpdate = currentSettings.autoUpdateOnStart,
                channel = currentSettings.updateChannel,
            )
        }
    }

    fun onUrlChange(newUrl: String) {
        val prev = _ui.value.urlInput
        _ui.update { it.copy(urlInput = newUrl, inspectError = null) }
        val trimmed = newUrl.trim()
        val looksLikePaste = (newUrl.length - prev.length) > 8 &&
            (trimmed.startsWith("https://") || trimmed.startsWith("http://"))
        if (looksLikePaste) {
            inspectUrl(trimmed)
        }
    }

    fun handleSharedUrl(rawText: String) {
        val extracted = Regex("https?://\\S+").find(rawText)?.value ?: return
        _ui.update { it.copy(urlInput = extracted, inspectError = null) }
        inspectUrl(extracted)
    }

    fun inspectUrl(explicitUrl: String? = null) {
        val target = (explicitUrl ?: _ui.value.urlInput).trim()
        if (target.isBlank()) {
            showMessage("Paste a video or playlist URL first")
            return
        }
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    isInspecting = true,
                    isRefiningFormats = false,
                    inspectError = null,
                )
            }

            supervisorScope {
                // Stage 1: ultra-fast oEmbed preview (~200-350ms)
                val quickDeferred = async {
                    runCatching { VideoInspector.quickInspect(target) }.getOrNull()
                }
                // Stage 2: full yt-dlp format & duration extraction (safe against uncaught child exceptions)
                val fullDeferred = async {
                    runCatching {
                        val ready = engineManager.awaitYtdlpReady()
                        if (!ready) error("yt-dlp engine failed to initialize")
                        VideoInspector.inspect(app, target)
                    }
                }

                val quickMedia = quickDeferred.await()
                if (quickMedia != null) {
                    val currentSettings = settings.value
                    _ui.update {
                        it.copy(
                            isInspecting = false,
                            isRefiningFormats = true,
                            inspected = quickMedia,
                            selectedQuality = currentSettings.defaultQuality,
                            selectedPlaylistIds = emptySet(),
                        )
                    }
                }

                val fullResult = fullDeferred.await()
                fullResult.onSuccess { media ->
                    val currentSettings = settings.value
                    val availableQualities = media.qualities.map { it.id }
                    val chosenQ = _ui.value.selectedQuality
                    val resolvedQ = when {
                        quickMedia != null && chosenQ in availableQualities -> chosenQ
                        currentSettings.defaultQuality in availableQualities -> currentSettings.defaultQuality
                        else -> availableQualities.getOrElse(1) { "best" }
                    }
                    _ui.update {
                        it.copy(
                            isInspecting = false,
                            isRefiningFormats = false,
                            inspected = media,
                            selectedQuality = resolvedQ,
                            selectedPlaylistIds = media.entries.map { e -> e.id }.toSet(),
                        )
                    }
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    _ui.update {
                        it.copy(
                            isInspecting = false,
                            isRefiningFormats = false,
                            inspectError = if (quickMedia == null) {
                                err.localizedMessage ?: "Failed to fetch video info"
                            } else null,
                        )
                    }
                }
            }
        }
    }

    fun setMode(mode: DownloadMode) {
        val currentSettings = settings.value
        val nextContainer = if (mode == DownloadMode.AUDIO) {
            currentSettings.defaultAudioFormat
        } else {
            currentSettings.defaultVideoContainer
        }
        _ui.update { it.copy(selectedMode = mode, selectedContainer = nextContainer) }
    }

    fun setQuality(quality: String) {
        _ui.update { it.copy(selectedQuality = quality) }
    }

    fun setAudioQuality(audioQuality: String) {
        _ui.update { it.copy(selectedAudioQuality = audioQuality) }
    }

    fun setContainer(container: String) {
        _ui.update { it.copy(selectedContainer = container) }
    }

    fun setEmbedThumbnail(enabled: Boolean) {
        _ui.update { it.copy(embedThumbnail = enabled) }
    }

    fun setEmbedMetadata(enabled: Boolean) {
        _ui.update { it.copy(embedMetadata = enabled) }
    }

    fun togglePlaylistItem(id: String) {
        _ui.update { current ->
            val next = current.selectedPlaylistIds.toMutableSet()
            if (!next.add(id)) next.remove(id)
            current.copy(selectedPlaylistIds = next)
        }
    }

    fun selectAllPlaylist(selectAll: Boolean) {
        _ui.update { current ->
            val next = if (selectAll) {
                current.inspected?.entries?.map { it.id }?.toSet().orEmpty()
            } else {
                emptySet()
            }
            current.copy(selectedPlaylistIds = next)
        }
    }

    fun startDownload() {
        val state = _ui.value
        val media = state.inspected ?: return
        val effectiveQuality = if (state.selectedMode == DownloadMode.AUDIO) {
            if (state.selectedContainer.equals("flac", ignoreCase = true)) "0" else state.selectedAudioQuality
        } else {
            state.selectedQuality
        }
        if (media.isPlaylist) {
            val chosen = media.entries.filter { it.id in state.selectedPlaylistIds }
            if (chosen.isEmpty()) return
            val jobs = chosen.map { entry ->
                DownloadJob(
                    url = entry.url,
                    title = entry.title,
                    uploader = entry.uploader.ifBlank { media.uploader },
                    thumbnailUrl = entry.thumbnailUrl.ifBlank { media.thumbnailUrl },
                    mode = state.selectedMode,
                    quality = effectiveQuality,
                    container = state.selectedContainer,
                    embedThumbnail = state.embedThumbnail,
                    embedMetadata = state.embedMetadata,
                )
            }
            downloadRepo.enqueueAll(jobs)
            showMessage("Queued ${jobs.size} videos")
        } else {
            downloadRepo.enqueue(
                DownloadJob(
                    url = media.webpageUrl,
                    title = media.title,
                    uploader = media.uploader,
                    thumbnailUrl = media.thumbnailUrl,
                    mode = state.selectedMode,
                    quality = effectiveQuality,
                    container = state.selectedContainer,
                    embedThumbnail = state.embedThumbnail,
                    embedMetadata = state.embedMetadata,
                )
            )
            showMessage("Added to download queue")
        }
    }

    fun cancelJob(id: String) = downloadRepo.cancel(id)
    fun retryJob(id: String) = downloadRepo.retry(id)
    fun removeJob(id: String) = downloadRepo.removeJob(id)
    fun clearFinished() = downloadRepo.clearFinishedJobs()
    fun refreshHistory() = downloadRepo.refreshHistoryAvailability()
    fun clearHistory() {
        downloadRepo.clearHistory()
        showMessage("Download history cleared")
    }
    fun deleteHistoryItem(id: String) {
        downloadRepo.deleteHistoryItemFromStorage(id) { deleted ->
            showMessage(if (deleted) "Deleted file from storage" else "Removed from history")
        }
    }

    fun checkUpdatesNow() {
        viewModelScope.launch {
            engineManager.checkAndUpdateNow(settings.value.updateChannel)
        }
    }

    fun updateSettings(
        autoUpdateOnStart: Boolean = settings.value.autoUpdateOnStart,
        updateChannel: EngineChannel = settings.value.updateChannel,
        dynamicColor: Boolean = settings.value.dynamicColor,
        themeMode: ThemeMode = settings.value.themeMode,
        seedPresetIndex: Int = settings.value.seedPresetIndex,
    ) {
        viewModelScope.launch {
            settingsStore.update {
                it.copy(
                    autoUpdateOnStart = autoUpdateOnStart,
                    updateChannel = updateChannel,
                    dynamicColor = dynamicColor,
                    themeMode = themeMode,
                    seedPresetIndex = seedPresetIndex,
                )
            }
        }
    }

    fun showMessage(msg: String) {
        _ui.update { it.copy(snackbarMessage = msg) }
    }

    fun clearMessage() {
        _ui.update { it.copy(snackbarMessage = null) }
    }
}
