package com.rembuk.rembuktv.data.local

import com.rembuk.rembuktv.data.local.entity.ChannelEntity
import com.rembuk.rembuktv.data.local.entity.PlaylistSourceWithCount
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
)

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
