package com.rembuk.rembuktv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rembuk.rembuktv.domain.model.AppSettings
import com.rembuk.rembuktv.domain.model.BufferProfile
import com.rembuk.rembuktv.domain.model.ThemeMode
import com.rembuk.rembuktv.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val store = context.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val LAST_CHANNEL = stringPreferencesKey("last_channel_id")
        val BUFFER = stringPreferencesKey("buffer_profile")
        val CAP_CELLULAR = booleanPreferencesKey("cap_cellular")
        val MAX_CELLULAR_BPS = longPreferencesKey("max_cellular_bps")
        val RESIZE_MODE = intPreferencesKey("resize_mode")
        val DEFAULT_PLAYLIST_SEEDED = booleanPreferencesKey("default_playlist_seeded")
    }

    override val settings: Flow<AppSettings> = store.data
        // A corrupted prefs file surfaces as IOException; fall back to defaults instead
        // of crashing every settings collector (theme, ABR cap, buffer profile, …).
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = p[Keys.THEME]?.let { enumOrNull<ThemeMode>(it) } ?: defaults.themeMode,
            autoResume = p[Keys.AUTO_RESUME] ?: defaults.autoResume,
            lastChannelId = p[Keys.LAST_CHANNEL],
            bufferProfile = p[Keys.BUFFER]?.let { enumOrNull<BufferProfile>(it) } ?: defaults.bufferProfile,
            capBitrateOnCellular = p[Keys.CAP_CELLULAR] ?: defaults.capBitrateOnCellular,
            maxCellularBitrate = p[Keys.MAX_CELLULAR_BPS] ?: defaults.maxCellularBitrate,
            defaultResizeMode = p[Keys.RESIZE_MODE] ?: defaults.defaultResizeMode,
            defaultPlaylistSeeded = p[Keys.DEFAULT_PLAYLIST_SEEDED] ?: defaults.defaultPlaylistSeeded,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    override suspend fun setAutoResume(enabled: Boolean) = edit { it[Keys.AUTO_RESUME] = enabled }
    override suspend fun setLastChannelId(channelId: String?) = edit {
        if (channelId == null) it.remove(Keys.LAST_CHANNEL) else it[Keys.LAST_CHANNEL] = channelId
    }
    override suspend fun setBufferProfile(profile: BufferProfile) = edit { it[Keys.BUFFER] = profile.name }
    override suspend fun setCapBitrateOnCellular(enabled: Boolean) = edit { it[Keys.CAP_CELLULAR] = enabled }
    override suspend fun setMaxCellularBitrate(bps: Long) = edit { it[Keys.MAX_CELLULAR_BPS] = bps }
    override suspend fun setDefaultResizeMode(mode: Int) = edit { it[Keys.RESIZE_MODE] = mode }
    override suspend fun setDefaultPlaylistSeeded(seeded: Boolean) = edit { it[Keys.DEFAULT_PLAYLIST_SEEDED] = seeded }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()
