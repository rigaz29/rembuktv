package com.rembuk.rembuktv.domain.model

/** A playable TV channel from a playlist source. Immutable domain model. */
data class Channel(
    /** Stable identifier, unique within the app (prefixed with playlist id). */
    val id: String,
    /** Id of the [PlaylistSource] this channel came from. */
    val playlistId: Long,
    val name: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val streamUrl: String,
    val streamType: StreamType = StreamType.OTHER,
    val drm: DrmInfo? = null,
    /** XMLTV id used to match EPG data, when available. */
    val tvgId: String? = null,
    /** Custom HTTP headers required to play the stream (e.g. User-Agent, Referer). */
    val headers: Map<String, String> = emptyMap(),
    /** From the subscription backend: this channel is free to watch. */
    val isFree: Boolean = false,
    /** Locked for the current device (paid channel + not entitled → no playable URL). */
    val locked: Boolean = false,
)

enum class StreamType { DASH, HLS, OTHER }

/** DRM configuration for a protected stream. */
data class DrmInfo(
    val scheme: String,
    val licenseUrl: String? = null,
    /** Static ClearKey pairs (KID -> KEY, hex or base64url), used when there is no license server. */
    val clearKeys: Map<String, String> = emptyMap(),
)
