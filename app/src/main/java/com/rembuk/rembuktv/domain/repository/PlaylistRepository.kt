package com.rembuk.rembuktv.domain.repository

import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.Entitlement
import com.rembuk.rembuktv.domain.model.PlaylistSource
import com.rembuk.rembuktv.domain.model.PlaylistType
import com.rembuk.rembuktv.domain.model.RemoteConfig
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for playlists and channels. Implementations cache parsed
 * channels in Room so the app opens fast/offline and avoids repeated network fetches.
 */
interface PlaylistRepository {

    // --- Playlist sources ---
    fun observePlaylists(): Flow<List<PlaylistSource>>
    suspend fun addPlaylist(name: String, url: String, type: PlaylistType): Result<Long>
    suspend fun removePlaylist(id: Long)
    suspend fun setPlaylistEnabled(id: Long, enabled: Boolean)

    /** Fetch + parse + cache a single playlist. */
    suspend fun refreshPlaylist(id: Long): Result<Int>

    /** Refresh every enabled playlist whose cache is stale (older than [ttlMillis]) or forced. */
    suspend fun refreshStale(ttlMillis: Long, force: Boolean): Result<Unit>

    // --- Subscription backend (Fase 2) ---
    /** Register/refresh the device with the backend: updates entitlement, config and the
     *  cached channel catalog. */
    suspend fun sync(appVersion: String): Result<Unit>
    fun observeEntitlement(): Flow<Entitlement>
    fun observeRemoteConfig(): Flow<RemoteConfig>

    // --- Channels ---
    fun observeChannels(): Flow<List<Channel>>
    fun observeGroups(): Flow<List<String>>
    suspend fun getChannel(id: String): Channel?

    // --- Favorites ---
    fun observeFavorites(): Flow<List<Channel>>
    fun observeFavoriteIds(): Flow<Set<String>>
    suspend fun setFavorite(channelId: String, favorite: Boolean)

    // --- History ---
    fun observeHistory(limit: Int): Flow<List<Channel>>
    suspend fun recordHistory(channelId: String)
    suspend fun clearHistory()

    // --- Maintenance ---
    suspend fun clearCache()
}
