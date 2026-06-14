package com.rembuk.rembuktv.data.parser

import com.rembuk.rembuktv.data.remote.dto.ChannelJsonDto
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.DrmInfo
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Parses the user's JSON playlist into [Channel]s. Tolerant of missing/unknown
 * fields; entries without a usable stream URL are dropped. Pure JVM logic (no
 * Android deps) so it can be unit-tested directly.
 */
class JsonPlaylistParser @Inject constructor(
    private val json: Json,
) {
    /** @throws kotlinx.serialization.SerializationException on malformed JSON. */
    fun parse(raw: String, playlistId: Long): List<Channel> {
        if (raw.isBlank()) return emptyList()
        val dtos = json.decodeFromString<List<ChannelJsonDto>>(raw)
        return dtos.mapNotNull { it.toChannel(playlistId) }
            .distinctBy { it.id }
    }

    private fun ChannelJsonDto.toChannel(playlistId: Long): Channel? {
        val streamUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val displayName = name?.trim()?.takeIf { it.isNotEmpty() } ?: streamUrl
        val drmInfo = drm?.scheme?.trim()?.takeIf { it.isNotEmpty() }?.let {
            DrmInfo(scheme = it, licenseUrl = drm.licenseUrl?.trim()?.ifEmpty { null })
        }
        return Channel(
            id = stableChannelId(playlistId, id, streamUrl),
            playlistId = playlistId,
            name = displayName,
            logoUrl = logo?.trim()?.ifEmpty { null },
            group = group?.trim()?.ifEmpty { null },
            streamUrl = streamUrl,
            streamType = resolveStreamType(type, streamUrl),
            drm = drmInfo,
            tvgId = id?.trim()?.ifEmpty { null },
            headers = headers ?: emptyMap(),
        )
    }
}
