package com.rembuk.rembuktv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rembuk.rembuktv.data.local.entity.FavoriteEntity
import com.rembuk.rembuktv.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT channelId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelId = :channelId)")
    fun isFavorite(channelId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE channelId = :channelId")
    suspend fun remove(channelId: String)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Keep only the [keep] most-recently watched entries. */
    @Query(
        """
        DELETE FROM history WHERE channelId NOT IN (
            SELECT channelId FROM history ORDER BY watchedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(keep: Int)
}
