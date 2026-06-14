package com.rembuk.rembuktv.di

import com.rembuk.rembuktv.data.repository.PlaylistRepositoryImpl
import com.rembuk.rembuktv.data.repository.SettingsRepositoryImpl
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import com.rembuk.rembuktv.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
