package com.rembuk.rembuktv.data.remote

import com.rembuk.rembuktv.core.Constants
import com.rembuk.rembuktv.data.remote.dto.ConfigDto
import com.rembuk.rembuktv.data.remote.dto.SyncRequestDto
import com.rembuk.rembuktv.data.remote.dto.SyncResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

/** Talks to the Enyak subscription backend (/v1/sync, /v1/config). */
class BackendApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val base = Constants.API_BASE_URL.trimEnd('/')
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    suspend fun sync(deviceId: String, appVersion: String, catalogVersion: String?): SyncResponseDto =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(
                SyncRequestDto(deviceId = deviceId, appVersion = appVersion, catalogVersion = catalogVersion),
            )
            val request = Request.Builder()
                .url("$base/v1/sync")
                .post(payload.toRequestBody(mediaJson))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} on /v1/sync")
                json.decodeFromString<SyncResponseDto>(text)
            }
        }

    suspend fun config(): ConfigDto = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/v1/config").build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} on /v1/config")
            json.decodeFromString<ConfigDto>(text)
        }
    }
}
