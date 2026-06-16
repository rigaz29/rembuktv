package com.rembuk.rembuktv.ui.common

import androidx.compose.ui.graphics.Color
import com.rembuk.rembuktv.domain.model.Entitlement
import com.rembuk.rembuktv.domain.model.EntitlementStatus

/** Short label for the status chip, e.g. "Donatur", "Trial 57m", "Gratis". */
fun Entitlement.label(): String = when (status) {
    EntitlementStatus.PREMIUM -> "Donatur"
    EntitlementStatus.TRIAL -> "Trial" + (remainingMinutes()?.let { " ${it}m" } ?: "")
    EntitlementStatus.BANNED -> "Diblokir"
    EntitlementStatus.FREE -> "Gratis"
}

fun Entitlement.badgeColor(): Color = when (status) {
    EntitlementStatus.PREMIUM -> Color(0xFF2E7D32) // green
    EntitlementStatus.TRIAL -> Color(0xFFF9A825)   // amber
    EntitlementStatus.BANNED -> Color(0xFFC62828)  // red
    EntitlementStatus.FREE -> Color(0xFF616161)    // grey
}

/** Whether to show a prominent "Donasi" CTA (anything other than active donatur). */
fun Entitlement.showSubscribeCta(): Boolean = status != EntitlementStatus.PREMIUM

/** Minutes left on the trial/subscription, or null when there is no expiry. */
fun Entitlement.remainingMinutes(): Long? {
    val exp = expiresAtEpochMs ?: return null
    val left = exp - System.currentTimeMillis()
    return if (left > 0) left / 60_000L else 0L
}
