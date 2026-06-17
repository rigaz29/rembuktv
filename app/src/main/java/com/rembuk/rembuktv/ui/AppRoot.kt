package com.rembuk.rembuktv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.rembuk.rembuktv.ui.mobile.MobileApp
import com.rembuk.rembuktv.ui.navigation.Routes
import com.rembuk.rembuktv.ui.tv.TvApp

/**
 * Top-level UI entry point. Branches into the mobile or TV navigation graph based on
 * the device form, and triggers auto-resume to the last watched channel on startup.
 */
@Composable
fun AppRoot(
    isTv: Boolean,
    isInPip: Boolean,
    rootViewModel: RootViewModel,
) {
    val navController = rememberNavController()
    val resumeChannelId by rootViewModel.resumeChannelId.collectAsStateWithLifecycle()

    // Auto-refresh the playlist every time the app is entered (cold start + each return to
    // the foreground). LocalLifecycleOwner here is the host Activity, so ON_START fires once
    // per foreground transition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) rootViewModel.onEnterApp()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeChannelId) {
        val id = resumeChannelId ?: return@LaunchedEffect
        navController.navigate(Routes.player(id))
        rootViewModel.consumeResume()
    }

    if (isTv) {
        TvApp(navController = navController, isInPip = isInPip)
    } else {
        MobileApp(navController = navController, isInPip = isInPip)
    }
}
