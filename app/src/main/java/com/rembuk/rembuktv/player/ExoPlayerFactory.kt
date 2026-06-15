package com.rembuk.rembuktv.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
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
import com.rembuk.rembuktv.domain.model.BufferProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds a local ExoPlayer (no MediaSessionService, so no media notification). Carries the
 * same wiring the old service had: custom HTTP headers ([PlaybackHeaders]), static ClearKey
 * DRM ([ClearKeyRegistry]), buffer profile, audio focus and network wake mode.
 */
@UnstableApi
class ExoPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(bufferProfile: BufferProfile): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferProfile.minBufferMs,
                bufferProfile.maxBufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(createDataSourceFactory())
            .setDrmSessionManagerProvider(clearKeyDrmProvider())

        return ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
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
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RembukTV/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
        val customHttpFactory = DataSource.Factory { HeaderDataSource(httpFactory.createDataSource()) }
        return DefaultDataSource.Factory(context, customHttpFactory)
    }

    /** Injects per-stream custom headers (User-Agent/Referer) from the registry. */
    private class HeaderDataSource(private val base: HttpDataSource) : HttpDataSource by base {
        override fun open(dataSpec: DataSpec): Long {
            val headers = PlaybackHeaders.getHeadersForUri(dataSpec.uri)
            val spec = if (!headers.isNullOrEmpty()) {
                dataSpec.withRequestHeaders(dataSpec.httpRequestHeaders.toMutableMap().apply { putAll(headers) })
            } else {
                dataSpec
            }
            return base.open(spec)
        }
    }

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
}
