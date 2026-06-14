package com.rembuk.rembuktv.player

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.StreamType

/** Builds an ExoPlayer [MediaItem] from a [Channel], wiring MIME type, DRM and live config. */
@UnstableApi
object MediaItemFactory {

    fun fromChannel(channel: Channel): MediaItem {
        // Register headers in the global registry so the background DataSource can find them.
        // We use the stream URL as the key.
        PlaybackHeaders.set(channel.streamUrl, channel.headers)

        val builder = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .setMediaId(channel.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(channel.name)
                    .setStation(channel.group)
                    .apply { channel.logoUrl?.let { setArtworkUri(it.toUri()) } }
                    .build(),
            )

        when (channel.streamType) {
            StreamType.DASH -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            StreamType.HLS -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamType.OTHER -> Unit // let the source factory infer from the manifest
        }

        builder.setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.04f).build(),
        )

        channel.drm?.let { drm ->
            val uuid = when (drm.scheme.lowercase()) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                "clearkey" -> C.CLEARKEY_UUID
                else -> null
            }
            if (uuid != null && !drm.licenseUrl.isNullOrBlank()) {
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(uuid)
                        .setLicenseUri(drm.licenseUrl)
                        .setMultiSession(true)
                        .build(),
                )
            }
        }

        return builder.build()
    }
}
