package com.materialtube.downloader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "materialtube_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class EngineChannel { STABLE, NIGHTLY }
enum class DownloadMode { VIDEO, AUDIO }

data class AppSettings(
    val autoUpdateOnStart: Boolean = true,
    val updateChannel: EngineChannel = EngineChannel.STABLE,
    val defaultMode: DownloadMode = DownloadMode.VIDEO,
    val defaultQuality: String = "1080",
    val defaultVideoContainer: String = "mp4",
    val defaultAudioFormat: String = "mp3",
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val seedPresetIndex: Int = 0,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val AUTO_UPDATE = booleanPreferencesKey("auto_update_on_start")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DEFAULT_VIDEO_CONTAINER = stringPreferencesKey("default_video_container")
        val DEFAULT_AUDIO_FORMAT = stringPreferencesKey("default_audio_format")
        val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
        val EMBED_METADATA = booleanPreferencesKey("embed_metadata")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SEED_PRESET = intPreferencesKey("seed_preset_index")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            autoUpdateOnStart = prefs[Keys.AUTO_UPDATE] ?: true,
            updateChannel = runCatching {
                EngineChannel.valueOf(prefs[Keys.UPDATE_CHANNEL] ?: EngineChannel.STABLE.name)
            }.getOrDefault(EngineChannel.STABLE),
            defaultMode = runCatching {
                DownloadMode.valueOf(prefs[Keys.DEFAULT_MODE] ?: DownloadMode.VIDEO.name)
            }.getOrDefault(DownloadMode.VIDEO),
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "1080",
            defaultVideoContainer = prefs[Keys.DEFAULT_VIDEO_CONTAINER] ?: "mp4",
            defaultAudioFormat = prefs[Keys.DEFAULT_AUDIO_FORMAT] ?: "mp3",
            embedThumbnail = prefs[Keys.EMBED_THUMBNAIL] ?: true,
            embedMetadata = prefs[Keys.EMBED_METADATA] ?: true,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            seedPresetIndex = prefs[Keys.SEED_PRESET] ?: 0,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                autoUpdateOnStart = prefs[Keys.AUTO_UPDATE] ?: true,
                updateChannel = runCatching {
                    EngineChannel.valueOf(prefs[Keys.UPDATE_CHANNEL] ?: EngineChannel.STABLE.name)
                }.getOrDefault(EngineChannel.STABLE),
                defaultMode = runCatching {
                    DownloadMode.valueOf(prefs[Keys.DEFAULT_MODE] ?: DownloadMode.VIDEO.name)
                }.getOrDefault(DownloadMode.VIDEO),
                defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "1080",
                defaultVideoContainer = prefs[Keys.DEFAULT_VIDEO_CONTAINER] ?: "mp4",
                defaultAudioFormat = prefs[Keys.DEFAULT_AUDIO_FORMAT] ?: "mp3",
                embedThumbnail = prefs[Keys.EMBED_THUMBNAIL] ?: true,
                embedMetadata = prefs[Keys.EMBED_METADATA] ?: true,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                themeMode = runCatching {
                    ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
                }.getOrDefault(ThemeMode.SYSTEM),
                seedPresetIndex = prefs[Keys.SEED_PRESET] ?: 0,
            )
            val updated = transform(current)
            prefs[Keys.AUTO_UPDATE] = updated.autoUpdateOnStart
            prefs[Keys.UPDATE_CHANNEL] = updated.updateChannel.name
            prefs[Keys.DEFAULT_MODE] = updated.defaultMode.name
            prefs[Keys.DEFAULT_QUALITY] = updated.defaultQuality
            prefs[Keys.DEFAULT_VIDEO_CONTAINER] = updated.defaultVideoContainer
            prefs[Keys.DEFAULT_AUDIO_FORMAT] = updated.defaultAudioFormat
            prefs[Keys.EMBED_THUMBNAIL] = updated.embedThumbnail
            prefs[Keys.EMBED_METADATA] = updated.embedMetadata
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.SEED_PRESET] = updated.seedPresetIndex
        }
    }
}
