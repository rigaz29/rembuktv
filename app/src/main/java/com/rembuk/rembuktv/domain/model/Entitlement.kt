package com.rembuk.rembuktv.domain.model

/** Subscription state for the current device, from the backend /v1/sync. */
data class Entitlement(
    val status: EntitlementStatus,
    val entitled: Boolean,
    /** Epoch millis when the trial/subscription ends, or null (free/banned). */
    val expiresAtEpochMs: Long?,
    /** Server clock (epoch millis) at the last sync, for an accurate countdown. */
    val serverNowEpochMs: Long?,
) {
    companion object {
        val FREE = Entitlement(EntitlementStatus.FREE, false, null, null)
    }
}

enum class EntitlementStatus { TRIAL, PREMIUM, FREE, BANNED }

/** App-facing remote config from the backend. */
data class RemoteConfig(
    val websiteUrl: String = "",
    val promoVideoUrl: String = "",
    val minAppVersion: String = "1.0.0",
)
