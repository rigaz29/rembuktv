package com.rembuk.rembuktv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.rembuk.rembuktv.data.local.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN playlist_sources p ON c.playlistId = p.id
        WHERE p.enabled = 1
        ORDER BY c.playlistId, c.sortIndex
        """,
    )
    fun observeEnabledChannels(): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT DISTINCT c.group_title FROM channels c
        INNER JOIN playlist_sources p ON c.playlistId = p.id
        WHERE p.enabled = 1 AND c.group_title IS NOT NULL AND c.group_title != ''
        ORDER BY c.group_title COLLATE NOCASE
        """,
    )
    fun observeGroups(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChannelEntity?

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN favorites f ON c.id = f.channelId
        INNER JOIN playlist_sources p ON c.playlistId = p.id
        WHERE p.enabled = 1
        ORDER BY f.addedAt DESC
        """,
    )
    fun observeFavorites(): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN history h ON c.id = h.channelId
        INNER JOIN playlist_sources p ON c.playlistId = p.id
        WHERE p.enabled = 1
        ORDER BY h.watchedAt DESC
        LIMIT :limit
        """,
    )
    fun observeHistory(limit: Int): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    /** Replace all channels of one playlist atomically. */
    @Transaction
    suspend fun replacePlaylistChannels(playlistId: Long, channels: List<ChannelEntity>) {
        deleteByPlaylist(playlistId)
        if (channels.isNotEmpty()) insertAll(channels)
    }
}
