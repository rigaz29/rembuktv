package com.rembuk.rembuktv.core

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Runtime device-form detection. The data/player layers are shared; only the UI
 * layer branches on this. We treat a device as a TV if the UI mode is TELEVISION
 * or it advertises the Leanback/Television feature (more reliable on some boxes).
 */
fun Context.isTelevision(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}
