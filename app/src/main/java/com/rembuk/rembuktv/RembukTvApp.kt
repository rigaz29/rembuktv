package com.rembuk.rembuktv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.rembuk.rembuktv.data.local.dao.PlaylistSourceDao
import com.rembuk.rembuktv.domain.model.PlaylistType
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class RembukTvApp : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var playlistDao: PlaylistSourceDao
    @Inject lateinit var repository: PlaylistRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        appScope.launch { seedDefaultPlaylistIfEmpty() }
    }

    /** Coil 3 needs a network fetcher explicitly registered for remote logos. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()

    /** Seed default playlist on first launch so the app isn't empty. */
    private suspend fun seedDefaultPlaylistIfEmpty() {
        runCatching {
            if (playlistDao.count() == 0) {
                repository.addPlaylist(
                    name = "BBC UK (Custom)",
                    url = "https://raw.githubusercontent.com/rigaz29/rembuk-tv/refs/heads/main/bbc_uk.json",
                    type = PlaylistType.JSON,
                )
            }
        }.onFailure { Timber.w(it, "Seeding default playlist failed") }
    }
}
