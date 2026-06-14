package com.rembuk.rembuktv.domain.repository

import com.rembuk.rembuktv.domain.model.AppSettings
import com.rembuk.rembuktv.domain.model.BufferProfile
import com.rembuk.rembuktv.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAutoResume(enabled: Boolean)
    suspend fun setLastChannelId(channelId: String?)
    suspend fun setBufferProfile(profile: BufferProfile)
    suspend fun setCapBitrateOnCellular(enabled: Boolean)
    suspend fun setMaxCellularBitrate(bps: Long)
    suspend fun setDefaultResizeMode(mode: Int)
    suspend fun setDefaultPlaylistSeeded(seeded: Boolean)
}
