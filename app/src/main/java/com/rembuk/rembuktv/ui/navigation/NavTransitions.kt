package com.rembuk.rembuktv.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

/**
 * Shared navigation transitions for the mobile and TV graphs.
 *
 * General screen changes use a quick cross-fade (snappier than the NavHost default
 * 700ms fade). Entering/leaving full-screen playback adds a slight scale so the
 * player feels like it expands into view and shrinks away, rather than abruptly
 * swapping with the home screen.
 */
private const val FADE_MS = 220
private const val PLAYER_MS = 280
private const val PLAYER_SCALE = 0.94f

val NavFadeIn: EnterTransition = fadeIn(tween(FADE_MS))
val NavFadeOut: ExitTransition = fadeOut(tween(FADE_MS))

val PlayerEnter: EnterTransition =
    fadeIn(tween(PLAYER_MS)) + scaleIn(animationSpec = tween(PLAYER_MS), initialScale = PLAYER_SCALE)
val PlayerExit: ExitTransition = fadeOut(tween(FADE_MS))
val PlayerPopEnter: EnterTransition = fadeIn(tween(FADE_MS))
val PlayerPopExit: ExitTransition =
    fadeOut(tween(PLAYER_MS)) + scaleOut(animationSpec = tween(PLAYER_MS), targetScale = PLAYER_SCALE)
