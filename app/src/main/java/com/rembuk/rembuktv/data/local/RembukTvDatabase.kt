package com.rembuk.rembuktv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rembuk.rembuktv.data.local.dao.ChannelDao
import com.rembuk.rembuktv.data.local.dao.FavoriteDao
import com.rembuk.rembuktv.data.local.dao.HistoryDao
import com.rembuk.rembuktv.data.local.dao.PlaylistSourceDao
import com.rembuk.rembuktv.data.local.entity.ChannelEntity
import com.rembuk.rembuktv.data.local.entity.FavoriteEntity
import com.rembuk.rembuktv.data.local.entity.HistoryEntity
import com.rembuk.rembuktv.data.local.entity.PlaylistSourceEntity

@Database(
    entities = [
        PlaylistSourceEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class RembukTvDatabase : RoomDatabase() {
    abstract fun playlistSourceDao(): PlaylistSourceDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "rembuktv.db"
    }
}
