package com.rembuk.rembuktv.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body for POST /v1/sync. */
@Serializable
data class SyncRequestDto(
    val deviceId: String,
    val appVersion: String,
    val catalogVersion: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
)

/** Response of POST /v1/sync. */
@Serializable
data class SyncResponseDto(
    val status: String = "free",                 // trial | premium | free | banned
    val entitled: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,     // ISO-8601 or null
    @SerialName("server_time") val serverTime: String? = null,   // ISO-8601
    @SerialName("catalog_version") val catalogVersion: String? = null,
    val channels: List<ChannelDto>? = null,      // null = unchanged, keep cache
    val config: ConfigDto = ConfigDto(),
)

@Serializable
data class ChannelDto(
    val id: Long,
    val name: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    val group: String? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    val locked: Boolean = false,
    @SerialName("stream_type") val streamType: String? = null,
    val url: String? = null,                      // proxy URL with token; null when locked
    val drm: DrmDto? = null,
    val headers: Map<String, String>? = null,
)

@Serializable
data class DrmDto(
    val scheme: String? = null,
    @SerialName("license_url") val licenseUrl: String? = null,
    val clearkeys: String? = null,                // "kid:key,kid:key"
    @SerialName("license_headers") val licenseHeaders: Map<String, String>? = null,
)

@Serializable
data class ConfigDto(
    @SerialName("website_url") val websiteUrl: String = "",
    @SerialName("promo_video_url") val promoVideoUrl: String = "",
    @SerialName("min_app_version") val minAppVersion: String = "1.0.0",
)
