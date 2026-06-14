package com.rembuk.rembuktv.di

import android.content.Context
import androidx.room.Room
import com.rembuk.rembuktv.data.local.LiveTvDatabase
import com.rembuk.rembuktv.data.local.dao.ChannelDao
import com.rembuk.rembuktv.data.local.dao.FavoriteDao
import com.rembuk.rembuktv.data.local.dao.HistoryDao
import com.rembuk.rembuktv.data.local.dao.PlaylistSourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LiveTvDatabase =
        Room.databaseBuilder(context, LiveTvDatabase::class.java, LiveTvDatabase.NAME)
            .fallbackToDestructiveMigration() // cache only; safe to rebuild on schema change
            .build()

    @Provides fun providePlaylistDao(db: LiveTvDatabase): PlaylistSourceDao = db.playlistSourceDao()
    @Provides fun provideChannelDao(db: LiveTvDatabase): ChannelDao = db.channelDao()
    @Provides fun provideFavoriteDao(db: LiveTvDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideHistoryDao(db: LiveTvDatabase): HistoryDao = db.historyDao()
}
