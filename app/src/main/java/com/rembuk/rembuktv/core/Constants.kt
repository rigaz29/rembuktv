package com.rembuk.rembuktv.core

object Constants {
    /** Subscription backend base URL (Fase 2). No trailing slash. */
    const val API_BASE_URL = "https://api.enyak.my.id"

    /** Default JSON playlist URL placeholder shown in Settings. Replace with your repo. */
    const val DEFAULT_JSON_PLAYLIST_URL =
        "https://raw.githubusercontent.com/USERNAME/REPO/main/playlist.json"

    /** Playlist seeded automatically on first launch so the app is usable out of the box. */
    const val DEFAULT_PLAYLIST_NAME = "IPTV-Org"
    const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/index.category.m3u"

    /** Playlist cache time-to-live: refresh automatically when older than this. */
    const val PLAYLIST_TTL_MILLIS = 3 * 60 * 60 * 1000L // 3 hours

    /** How often the app re-syncs with the backend (entitlement + catalog). */
    const val SYNC_INTERVAL_MILLIS = 15 * 60 * 1000L // 15 minutes

    const val HISTORY_LIMIT = 30

    /** Auto-retry tuning for playback errors. */
    const val MAX_PLAYBACK_RETRIES = 6
    const val RETRY_BASE_DELAY_MS = 1_000L
    const val RETRY_MAX_DELAY_MS = 30_000L
}
