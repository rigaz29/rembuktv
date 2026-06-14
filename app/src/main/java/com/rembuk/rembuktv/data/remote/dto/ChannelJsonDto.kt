package com.rembuk.rembuktv.data.remote.dto

import kotlinx.serialization.Serializable

/** Wire model for the JSON playlist. All fields optional so parsing tolerates gaps. */
@Serializable
data class ChannelJsonDto(
    val id: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val url: String? = null,
    val type: String? = null,
    val drm: DrmJsonDto? = null,
    val headers: Map<String, String>? = null,
)

@Serializable
data class DrmJsonDto(
    val scheme: String? = null,
    val licenseUrl: String? = null,
)
