package com.rembuk.rembuktv.data.local

import com.rembuk.rembuktv.data.local.entity.ChannelEntity
import com.rembuk.rembuktv.data.local.entity.PlaylistSourceWithCount
import com.rembuk.rembuktv.data.remote.dto.ChannelDto
import com.rembuk.rembuktv.data.remote.dto.DrmDto
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.DrmInfo
import com.rembuk.rembuktv.domain.model.PlaylistSource
import com.rembuk.rembuktv.domain.model.PlaylistType
import com.rembuk.rembuktv.domain.model.StreamType

fun ChannelEntity.toDomain(): Channel = Channel(
    id = id,
    playlistId = playlistId,
    name = name,
    logoUrl = logoUrl,
    group = group,
    streamUrl = streamUrl,
    streamType = runCatching { StreamType.valueOf(streamType) }.getOrDefault(StreamType.OTHER),
    drm = drmScheme?.let {
        DrmInfo(scheme = it, licenseUrl = drmLicenseUrl, clearKeys = decodeClearKeys(drmClearKeys))
    },
    tvgId = tvgId,
    headers = decodeHeaders(headers),
    isFree = isFree,
    locked = locked,
)

fun Channel.toEntity(sortIndex: Int): ChannelEntity = ChannelEntity(
    id = id,
    playlistId = playlistId,
    name = name,
    logoUrl = logoUrl,
    group = group,
    streamUrl = streamUrl,
    streamType = streamType.name,
    drmScheme = drm?.scheme,
    drmLicenseUrl = drm?.licenseUrl,
    drmClearKeys = drm?.clearKeys?.takeIf { it.isNotEmpty() }?.let { encodeClearKeys(it) },
    tvgId = tvgId,
    sortIndex = sortIndex,
    headers = encodeHeaders(headers),
    isFree = isFree,
    locked = locked,
)

/** Map a backend channel DTO (from /v1/sync) into the domain model. */
fun ChannelDto.toDomain(playlistId: Long): Channel = Channel(
    id = id.toString(),
    playlistId = playlistId,
    name = name,
    logoUrl = logoUrl,
    group = group,
    streamUrl = url.orEmpty(),          // "" when locked (no playable URL)
    streamType = when (streamType?.lowercase()) {
        "hls" -> StreamType.HLS
        "dash" -> StreamType.DASH
        else -> StreamType.OTHER
    },
    drm = drm?.toDomain(),
    tvgId = null,
    headers = headers ?: emptyMap(),
    isFree = isFree,
    locked = locked,
)

private fun DrmDto.toDomain(): DrmInfo? {
    val keys = decodeClearKeys(clearkeys)
    if (scheme.isNullOrBlank() && licenseUrl.isNullOrBlank() && keys.isEmpty()) return null
    return DrmInfo(
        scheme = scheme ?: if (keys.isNotEmpty()) "clearkey" else "",
        licenseUrl = licenseUrl,
        clearKeys = keys,
    )
}

fun PlaylistSourceWithCount.toDomain(): PlaylistSource = PlaylistSource(
    id = id,
    name = name,
    url = url,
    type = runCatching { PlaylistType.valueOf(type) }.getOrDefault(PlaylistType.M3U),
    enabled = enabled,
    lastSyncedAt = lastSyncedAt,
    channelCount = channelCount,
)

private fun encodeHeaders(headers: Map<String, String>): String? {
    if (headers.isEmpty()) return null
    return headers.entries.joinToString("|") { "${it.key}==${it.value}" }
}

private fun decodeHeaders(encoded: String?): Map<String, String> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return encoded.split("|").mapNotNull {
        val parts = it.split("==", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()
}

private fun encodeClearKeys(keys: Map<String, String>): String =
    keys.entries.joinToString(",") { "${it.key}:${it.value}" }

private fun decodeClearKeys(encoded: String?): Map<String, String> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return encoded.split(",").mapNotNull {
        val parts = it.split(":", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
    }.toMap()
}
