package com.rembuk.rembuktv.player

import android.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the ClearKey EME license JSON for channels that use **static** `kid:key` pairs
 * (no license server), keyed by media id. Mirrors [PlaybackHeaders]: the player's
 * DrmSessionManagerProvider looks the license up by media id when building a
 * static-ClearKey session. Streams with a real license URL don't use this — they go
 * through the standard MediaItem DRM configuration instead.
 */
object ClearKeyRegistry {

    private val licenses = ConcurrentHashMap<String, ByteArray>()

    /** @param keys map of KID -> KEY, each hex (32 chars) or base64url. Empty clears the entry. */
    fun set(mediaId: String, keys: Map<String, String>) {
        if (keys.isEmpty()) licenses.remove(mediaId) else licenses[mediaId] = buildClearKeyJson(keys)
    }

    /** Raw ClearKey license response (JSON) for [mediaId], or null if not a static-key channel. */
    fun licenseJson(mediaId: String): ByteArray? = licenses[mediaId]

    fun clear() = licenses.clear()

    /** ClearKey EME response: {"keys":[{"kty":"oct","kid":..,"k":..}],"type":"temporary"}. */
    private fun buildClearKeyJson(keys: Map<String, String>): ByteArray {
        val entries = keys.entries.joinToString(",") { (kid, key) ->
            """{"kty":"oct","kid":"${toBase64Url(kid)}","k":"${toBase64Url(key)}"}"""
        }
        return """{"keys":[$entries],"type":"temporary"}""".toByteArray(Charsets.UTF_8)
    }

    /** Normalise a key/kid (hex preferred, else assume base64) to unpadded base64url. */
    private fun toBase64Url(raw: String): String {
        val s = raw.trim()
        val bytes = if (s.length % 2 == 0 && s.matches(HEX)) {
            ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } else {
            runCatching {
                Base64.decode(s.replace('-', '+').replace('_', '/'), Base64.DEFAULT)
            }.getOrDefault(s.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private val HEX = Regex("[0-9a-fA-F]+")
}
