package com.rembuk.rembuktv.domain.model

/** A user-configured playlist source (JSON or M3U). */
data class PlaylistSource(
    val id: Long = 0,
    val name: String,
    val url: String,
    val type: PlaylistType,
    val enabled: Boolean = true,
    val lastSyncedAt: Long? = null,
    val channelCount: Int = 0,
)

enum class PlaylistType { JSON, M3U }
