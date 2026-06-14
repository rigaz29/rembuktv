package com.rembuk.rembuktv.data.repository

import com.rembuk.rembuktv.core.Constants
import com.rembuk.rembuktv.data.local.dao.ChannelDao
import com.rembuk.rembuktv.data.local.dao.FavoriteDao
import com.rembuk.rembuktv.data.local.dao.HistoryDao
import com.rembuk.rembuktv.data.local.dao.PlaylistSourceDao
import com.rembuk.rembuktv.data.local.entity.FavoriteEntity
import com.rembuk.rembuktv.data.local.entity.HistoryEntity
import com.rembuk.rembuktv.data.local.entity.PlaylistSourceEntity
import com.rembuk.rembuktv.data.local.toDomain
import com.rembuk.rembuktv.data.local.toEntity
import com.rembuk.rembuktv.data.parser.JsonPlaylistParser
import com.rembuk.rembuktv.data.parser.M3uPlaylistParser
import com.rembuk.rembuktv.data.remote.RemotePlaylistDataSource
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.PlaylistSource
import com.rembuk.rembuktv.domain.model.PlaylistType
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistSourceDao,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val remote: RemotePlaylistDataSource,
    private val jsonParser: JsonPlaylistParser,
    private val m3uParser: M3uPlaylistParser,
) : PlaylistRepository {

    // --- Playlist sources ---

    override fun observePlaylists(): Flow<List<PlaylistSource>> =
        playlistDao.observeAllWithCount().map { list -> list.map { it.toDomain() } }

    override suspend fun addPlaylist(name: String, url: String, type: PlaylistType): Result<Long> =
        runCatching {
            val id = playlistDao.insert(
                PlaylistSourceEntity(name = name.trim(), url = url.trim(), type = type.name),
            )
            // Best-effort initial sync; the playlist is still added even if offline.
            refreshPlaylist(id).onFailure { Timber.w(it, "Initial sync failed for %s", url) }
            id
        }

    override suspend fun removePlaylist(id: Long) {
        // Channels cascade-delete via the foreign key.
        playlistDao.delete(id)
    }

    override suspend fun setPlaylistEnabled(id: Long, enabled: Boolean) =
        playlistDao.setEnabled(id, enabled)

    override suspend fun refreshPlaylist(id: Long): Result<Int> = runCatching {
        val source = playlistDao.getById(id)
            ?: error("Playlist $id not found")
        val raw = remote.fetch(source.url)
        val type = runCatching { PlaylistType.valueOf(source.type) }.getOrDefault(PlaylistType.M3U)
        val channels = when (type) {
            PlaylistType.JSON -> jsonParser.parse(raw, id)
            PlaylistType.M3U -> m3uParser.parse(raw, id)
        }
        val entities = channels.mapIndexed { index, channel -> channel.toEntity(index) }
        channelDao.replacePlaylistChannels(id, entities)
        playlistDao.updateLastSynced(id, System.currentTimeMillis())
        Timber.d("Refreshed playlist %s: %d channels", source.name, channels.size)
        channels.size
    }

    override suspend fun refreshStale(ttlMillis: Long, force: Boolean): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        playlistDao.getEnabled().forEach { source ->
            val stale = force || source.lastSyncedAt == null ||
                now - source.lastSyncedAt > ttlMillis
            if (stale) {
                refreshPlaylist(source.id).onFailure {
                    Timber.w(it, "Refresh failed for %s (using cache)", source.name)
                }
            }
        }
    }

    // --- Channels ---

    override fun observeChannels(): Flow<List<Channel>> =
        channelDao.observeEnabledChannels().map { list -> list.map { it.toDomain() } }

    override fun observeGroups(): Flow<List<String>> = channelDao.observeGroups()

    override suspend fun getChannel(id: String): Channel? = channelDao.getById(id)?.toDomain()

    // --- Favorites ---

    override fun observeFavorites(): Flow<List<Channel>> =
        channelDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeIds().map { it.toSet() }

    override suspend fun setFavorite(channelId: String, favorite: Boolean) {
        if (favorite) {
            favoriteDao.add(FavoriteEntity(channelId, System.currentTimeMillis()))
        } else {
            favoriteDao.remove(channelId)
        }
    }

    // --- History ---

    override fun observeHistory(limit: Int): Flow<List<Channel>> =
        channelDao.observeHistory(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun recordHistory(channelId: String) {
        historyDao.upsert(HistoryEntity(channelId, System.currentTimeMillis()))
        historyDao.trim(Constants.HISTORY_LIMIT)
    }

    override suspend fun clearHistory() = historyDao.clear()

    // --- Maintenance ---

    override suspend fun clearCache() = channelDao.clearAll()
}
