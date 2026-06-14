package com.rembuk.rembuktv.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rembuk.rembuktv.core.NetworkMonitor
import com.rembuk.rembuktv.domain.model.BufferProfile
import com.rembuk.rembuktv.domain.repository.SettingsRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Hosts ExoPlayer + a [MediaSession] so playback survives rotation, PiP and
 * backgrounding. Manages custom HTTP headers via [PlaybackHeaders].
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var networkMonitor: NetworkMonitor

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val bufferProfile = readBufferProfile()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferProfile.minBufferMs,
                bufferProfile.maxBufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        trackSelector = DefaultTrackSelector(this)

        // Create a DataSource factory that supports custom HTTP headers while maintaining defaults
        val dataSourceFactory = createDataSourceFactory()

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(dataSourceFactory)
                    .setDrmSessionManagerProvider(clearKeyDrmProvider()),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .apply { sessionActivityIntent()?.let { setSessionActivity(it) } }
            .setCallback(CustomSessionCallback())
            .build()

        observeAbrCap()
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        // Factory for HTTP requests with a standard User-Agent to prevent server rejection
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RembukTV/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)

        // Functional interface implementation for DataSource.Factory that injects headers
        val customHttpFactory = DataSource.Factory {
            HeaderDataSource(httpFactory.createDataSource())
        }

        // Use DefaultDataSource as base to support all protocols (file, asset, http, etc.)
        return DefaultDataSource.Factory(this, customHttpFactory)
    }

    /**
     * DRM provider: channels with static ClearKey (kid:key) build a local-key session from
     * [ClearKeyRegistry]; everything else falls back to the default provider (handles
     * license-URL DRM from the MediaItem, and clear content).
     */
    private fun clearKeyDrmProvider(): DrmSessionManagerProvider {
        val default = DefaultDrmSessionManagerProvider()
        return DrmSessionManagerProvider { mediaItem ->
            val json = ClearKeyRegistry.licenseJson(mediaItem.mediaId)
            if (json != null) {
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .build(LocalMediaDrmCallback(json))
            } else {
                default.get(mediaItem)
            }
        }
    }

    /**
     * Injects custom headers into HTTP requests by looking them up in the
     * [PlaybackHeaders] registry. This is thread-safe and doesn't rely on player state.
     */
    private inner class HeaderDataSource(private val base: HttpDataSource) : HttpDataSource by base {
        override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
            // Find headers for this specific URI (manifest or segment)
            val headers = PlaybackHeaders.getHeadersForUri(dataSpec.uri)
            
            val newSpec = if (!headers.isNullOrEmpty()) {
                val mergedHeaders = dataSpec.httpRequestHeaders.toMutableMap()
                mergedHeaders.putAll(headers)
                dataSpec.withRequestHeaders(mergedHeaders)
            } else {
                dataSpec
            }
            return base.open(newSpec)
        }
    }

    private inner class CustomSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return Futures.immediateFuture(mediaItems)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlaybackHeaders.clear()
        ClearKeyRegistry.clear()
        super.onDestroy()
    }

    private fun observeAbrCap() {
        serviceScope.launch {
            combine(networkMonitor.status, settingsRepository.settings) { net, settings ->
                if (settings.capBitrateOnCellular && net.isMetered) {
                    settings.maxCellularBitrate.toInt()
                } else {
                    Int.MAX_VALUE
                }
            }.collect { maxBitrate ->
                trackSelector.setParameters(
                    trackSelector.buildUponParameters().setMaxVideoBitrate(maxBitrate),
                )
            }
        }
    }

    private fun readBufferProfile(): BufferProfile =
        runCatching { runBlocking { settingsRepository.settings.first().bufferProfile } }
            .getOrDefault(BufferProfile.NORMAL)

    private fun sessionActivityIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, launch, flags)
    }
}
