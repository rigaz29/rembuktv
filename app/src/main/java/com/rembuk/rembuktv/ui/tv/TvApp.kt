package com.rembuk.rembuktv.ui.tv

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rembuk.rembuktv.ui.SubscribeScreen
import com.rembuk.rembuktv.ui.mobile.settings.SettingsScreen
import com.rembuk.rembuktv.ui.navigation.NavFadeIn
import com.rembuk.rembuktv.ui.navigation.NavFadeOut
import com.rembuk.rembuktv.ui.navigation.PlayerEnter
import com.rembuk.rembuktv.ui.navigation.PlayerExit
import com.rembuk.rembuktv.ui.navigation.PlayerPopEnter
import com.rembuk.rembuktv.ui.navigation.PlayerPopExit
import com.rembuk.rembuktv.ui.navigation.Routes
import com.rembuk.rembuktv.ui.player.PlayerScreen

/**
 * TV navigation graph. Home and player are TV-specific (10-foot, D-pad). Settings and
 * playlist management reuse the mobile screens — their Material 3 controls are
 * focusable and operable with a remote.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvApp(navController: NavHostController, isInPip: Boolean) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { NavFadeIn },
        exitTransition = { NavFadeOut },
        popEnterTransition = { NavFadeIn },
        popExitTransition = { NavFadeOut },
    ) {
        composable(Routes.HOME) {
            TvHomeScreen(
                onChannelClick = { channel, group -> navController.navigate(Routes.player(channel.id, group)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onSubscribe = { navController.navigate(Routes.SUBSCRIBE) },
            )
        }
        composable(
            route = Routes.PLAYER_ROUTE,
            arguments = listOf(
                navArgument(Routes.PLAYER_ARG_CHANNEL) { type = NavType.StringType },
                navArgument(Routes.PLAYER_ARG_GROUP) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = { PlayerEnter },
            exitTransition = { PlayerExit },
            popEnterTransition = { PlayerPopEnter },
            popExitTransition = { PlayerPopExit },
        ) {
            PlayerScreen(
                isTv = true,
                isInPip = isInPip,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SUBSCRIBE) {
            SubscribeScreen(onBack = { navController.popBackStack() })
        }
    }
}
