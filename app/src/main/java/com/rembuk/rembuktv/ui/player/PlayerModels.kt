@file:OptIn(UnstableApi::class)

package com.rembuk.rembuktv.ui.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.rembuk.rembuktv.domain.model.Channel

/** A selectable track (quality / audio language / subtitle) shown in the player menu. */
data class TrackOption(
    val id: String,
    val label: String,
    val selected: Boolean,
)

enum class TrackKind { VIDEO, AUDIO, TEXT }

/** A user-facing playback failure (DRM / geo-block / unsupported / exhausted retries). */
data class PlaybackErrorInfo(
    val message: String,
    val manualRetryable: Boolean,
)

/** Resize modes exposed in the player. Maps to ExoPlayer [AspectRatioFrameLayout] modes. */
enum class ResizeOption(val label: String, val mode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom (Crop)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL);

    companion object {
        fun fromMode(mode: Int): ResizeOption = entries.firstOrNull { it.mode == mode } ?: FIT
    }
}

data class PlayerUiState(
    val channel: Channel? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val reconnecting: Boolean = false,
    val error: PlaybackErrorInfo? = null,
    val videoTracks: List<TrackOption> = emptyList(),
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val subtitlesEnabled: Boolean = false,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val sleepTimerMinutes: Int? = null,
    val isFavorite: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** True for non-live, seekable (VOD) content; drives the seekbar. */
    val isSeekable: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
)
