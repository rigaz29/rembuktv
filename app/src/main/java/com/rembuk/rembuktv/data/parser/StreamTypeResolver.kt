package com.rembuk.rembuktv.data.parser

import com.rembuk.rembuktv.domain.model.StreamType

/**
 * Resolves the stream container type. Prefers an explicit declaration, then falls
 * back to sniffing the URL extension. Query strings are stripped before sniffing.
 */
fun resolveStreamType(declared: String?, url: String): StreamType {
    when (declared?.trim()?.lowercase()) {
        "dash", "mpd" -> return StreamType.DASH
        "hls", "m3u8" -> return StreamType.HLS
    }
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".mpd") -> StreamType.DASH
        path.endsWith(".m3u8") || path.endsWith(".m3u") -> StreamType.HLS
        else -> StreamType.OTHER
    }
}

/** Build a stable, content-derived channel id so favorites/history survive refreshes. */
fun stableChannelId(playlistId: Long, preferredKey: String?, streamUrl: String): String {
    val key = preferredKey?.trim()?.takeIf { it.isNotEmpty() } ?: streamUrl
    return "$playlistId::$key"
}
