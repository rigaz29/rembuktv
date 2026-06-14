package com.rembuk.rembuktv.di

import android.content.Context
import androidx.room.Room
import com.rembuk.rembuktv.data.local.RembukTvDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): RembukTvDatabase =
        Room.databaseBuilder(context, RembukTvDatabase::class.java, RembukTvDatabase.NAME)
            .fallbackToDestructiveMigration() // cache only; safe to rebuild on schema change
            .build()

    @Provides fun providePlaylistDao(db: RembukTvDatabase): PlaylistSourceDao = db.playlistSourceDao()
    @Provides fun provideChannelDao(db: RembukTvDatabase): ChannelDao = db.channelDao()
    @Provides fun provideFavoriteDao(db: RembukTvDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideHistoryDao(db: RembukTvDatabase): HistoryDao = db.historyDao()
}
