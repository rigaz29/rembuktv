package com.rembuk.rembuktv.ui.navigation

import android.net.Uri

/** Centralised navigation routes, shared by the mobile and TV navigation graphs. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PLAYLISTS = "playlists"
    const val SUBSCRIBE = "subscribe"

    const val PLAYER_ARG_CHANNEL = "channelId"
    const val PLAYER_ARG_GROUP = "group"
    const val PLAYER_ROUTE = "player/{$PLAYER_ARG_CHANNEL}?$PLAYER_ARG_GROUP={$PLAYER_ARG_GROUP}"

    /**
     * @param group the category the user was browsing, so prev/next zapping stays within it.
     *   Pass null to zap through all channels.
     */
    fun player(channelId: String, group: String? = null): String {
        val base = "player/${Uri.encode(channelId)}"
        return if (group.isNullOrBlank()) base else "$base?$PLAYER_ARG_GROUP=${Uri.encode(group)}"
    }
}
