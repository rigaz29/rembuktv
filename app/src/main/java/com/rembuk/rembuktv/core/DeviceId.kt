package com.rembuk.rembuktv.core

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/**
 * Stable per-device identifier for subscription auth: Settings.Secure.ANDROID_ID.
 * Survives app reinstall (per device + signing key since Android 8), so it can't be
 * farmed for new trials by reinstalling. Same on phones and Android TV.
 */
object DeviceId {
    @SuppressLint("HardwareIds")
    fun get(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "unknown-device"
}
