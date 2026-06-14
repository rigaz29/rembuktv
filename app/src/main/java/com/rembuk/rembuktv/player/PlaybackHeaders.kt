package com.rembuk.rembuktv.player

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe registry to pass per-stream HTTP headers to the player's DataSource.
 */
object PlaybackHeaders {
    private val headersMap = ConcurrentHashMap<String, Map<String, String>>()

    /** Registers headers for a stream URL. */
    fun set(url: String, headers: Map<String, String>) {
        if (headers.isEmpty()) {
            headersMap.remove(url)
        } else {
            headersMap[url] = headers
        }
    }

    /** 
     * Finds headers for a given request URI. 
     * Matches exact URL (manifest) or directory prefix (segments).
     */
    fun getHeadersForUri(uri: Uri): Map<String, String>? {
        val url = uri.toString()
        
        // 1. Exact match (highest priority)
        headersMap[url]?.let { return it }
        
        // 2. Prefix match (for DASH/HLS segments)
        return headersMap.entries
            .filter { (baseUrl, _) ->
                val lastSlash = baseUrl.lastIndexOf('/')
                if (lastSlash <= 8) return@filter false // skip "https://"
                val baseDir = baseUrl.substring(0, lastSlash + 1)
                url.startsWith(baseDir)
            }
            .maxByOrNull { it.key.length }
            ?.value
    }

    fun clear() = headersMap.clear()
}
