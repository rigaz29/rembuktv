package com.rembuk.rembuktv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/** Fetches raw playlist text (JSON or M3U) from an arbitrary URL. */
class RemotePlaylistDataSource @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} fetching playlist")
            }
            response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IOException("Empty playlist response")
        }
    }
}
