package com.rembuk.rembuktv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rembuk.rembuktv.data.local.entity.PlaylistSourceEntity
import com.rembuk.rembuktv.data.local.entity.PlaylistSourceWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSourceDao {

    @Query(
        """
        SELECT p.id, p.name, p.url, p.type, p.enabled, p.lastSyncedAt,
            (SELECT COUNT(*) FROM channels c WHERE c.playlistId = p.id) AS channelCount
        FROM playlist_sources p
        ORDER BY p.id
        """,
    )
    fun observeAllWithCount(): Flow<List<PlaylistSourceWithCount>>

    @Query("SELECT * FROM playlist_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<PlaylistSourceEntity>

    @Query("SELECT * FROM playlist_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlaylistSourceEntity?

    @Query("SELECT COUNT(*) FROM playlist_sources")
    suspend fun count(): Int

    @Insert
    suspend fun insert(source: PlaylistSourceEntity): Long

    @Query("DELETE FROM playlist_sources WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlist_sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE playlist_sources SET lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateLastSynced(id: Long, timestamp: Long)
}
