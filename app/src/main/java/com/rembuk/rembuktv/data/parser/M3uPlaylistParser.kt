package com.rembuk.rembuktv.data.parser

import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.DrmInfo
import javax.inject.Inject

/**
 * Parses M3U/M3U8 playlists (e.g. iptv-org) into [Channel]s.
 *
 * Recognises:
 *  - `#EXTINF:` attributes `tvg-id`, `tvg-logo`, `tvg-name`, `group-title` and the
 *    trailing display name after the comma.
 *  - `#KODIPROP:` / `#EXTVLCOPT:` DRM hints and HTTP headers (Referer, User-Agent).
 */
class M3uPlaylistParser @Inject constructor() {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(raw: String, playlistId: Long): List<Channel> {
        if (raw.isBlank()) return emptyList()
        val channels = ArrayList<Channel>()
        var pending: Pending? = null

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                line.startsWith("#KODIPROP", ignoreCase = true) ||
                    line.startsWith("#EXTVLCOPT", ignoreCase = true) ->
                    pending?.applyProp(line)
                line.startsWith("#") -> Unit // ignore unknown directives
                else -> {
                    // A URL line completes the current entry.
                    pending?.let { channels += it.toChannel(playlistId, line) }
                    pending = null
                }
            }
        }
        return channels.distinctBy { it.id }
    }

    private fun parseExtInf(line: String): Pending {
        // The display name is the text after the comma that terminates the attribute
        // list. Attribute values may themselves contain commas (e.g.
        // group-title="News, Sport"), so we split on the first comma that is *outside*
        // quotes rather than the first comma overall. Attributes are then scanned only
        // over the attribute section so a name containing key="value" is not mistaken
        // for an attribute.
        val nameSeparator = indexOfUnquotedComma(line)
        val attrsPart = if (nameSeparator >= 0) line.substring(0, nameSeparator) else line
        val name = if (nameSeparator >= 0) line.substring(nameSeparator + 1).trim() else ""
        val attrs = attrRegex.findAll(attrsPart).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return Pending(
            name = name.ifEmpty { attrs["tvg-name"].orEmpty() },
            tvgId = attrs["tvg-id"]?.ifEmpty { null },
            logo = attrs["tvg-logo"]?.ifEmpty { null },
            group = attrs["group-title"]?.ifEmpty { null },
            initialHeaders = inlineHeaders(attrs),
        )
    }

    /** HTTP headers some playlists embed directly as #EXTINF attributes (not just #EXTVLCOPT). */
    private fun inlineHeaders(attrs: Map<String, String>): Map<String, String> = buildMap {
        attrs["http-user-agent"]?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        (attrs["http-referrer"] ?: attrs["http-referer"])?.takeIf { it.isNotBlank() }?.let {
            put("Referer", it)
        }
    }

    private inner class Pending(
        var name: String,
        val tvgId: String?,
        val logo: String?,
        val group: String?,
        initialHeaders: Map<String, String> = emptyMap(),
    ) {
        private var drmScheme: String? = null
        private var drmLicenseUrl: String? = null
        private val headers = HashMap(initialHeaders)

        fun applyProp(line: String) {
            val value = line.substringAfter('=', missingDelimiterValue = "").trim()
            val key = line.substringAfter(':', missingDelimiterValue = "")
                .substringBefore('=').trim().lowercase()
            when {
                key.endsWith("license_type") -> drmScheme = normalizeScheme(value)
                key.endsWith("license_key") || key.endsWith("license_url") ->
                    if (value.startsWith("http", ignoreCase = true)) drmLicenseUrl = value
                key == "http-referrer" || key == "http-referer" -> headers["Referer"] = value
                key == "http-user-agent" -> headers["User-Agent"] = value
            }
        }

        fun toChannel(playlistId: Long, url: String): Channel {
            val streamUrl = url.trim()
            val displayName = name.ifEmpty { streamUrl }
            val drm = drmScheme?.let { DrmInfo(scheme = it, licenseUrl = drmLicenseUrl) }
            return Channel(
                id = stableChannelId(playlistId, tvgId ?: streamUrl, streamUrl),
                playlistId = playlistId,
                name = displayName,
                logoUrl = logo,
                group = group,
                streamUrl = streamUrl,
                streamType = resolveStreamType(null, streamUrl),
                drm = drm,
                tvgId = tvgId,
                headers = headers.toMap()
            )
        }
    }

    /** Index of the first comma not enclosed in double quotes, or -1 if none. */
    private fun indexOfUnquotedComma(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        return -1
    }

    private fun normalizeScheme(raw: String): String = when {
        raw.contains("widevine", ignoreCase = true) -> "widevine"
        raw.contains("playready", ignoreCase = true) -> "playready"
        raw.contains("clearkey", ignoreCase = true) -> "clearkey"
        else -> raw.lowercase()
    }
}
