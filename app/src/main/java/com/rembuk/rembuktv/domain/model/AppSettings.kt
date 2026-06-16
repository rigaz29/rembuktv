package com.rembuk.rembuktv.domain.model

/** User preferences, persisted in DataStore. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Auto-resume the last watched channel on launch. Default off. */
    val autoResume: Boolean = false,
    /** Id of the last channel watched, for auto-resume. */
    val lastChannelId: String? = null,
    val bufferProfile: BufferProfile = BufferProfile.NORMAL,
    /** Cap adaptive bitrate on cellular to save data. Default off. */
    val capBitrateOnCellular: Boolean = false,
    /** Max bitrate (bps) when [capBitrateOnCellular] is on. */
    val maxCellularBitrate: Long = 2_500_000,
    /** Default ExoPlayer resize mode (AspectRatioFrameLayout.RESIZE_MODE_*). */
    val defaultResizeMode: Int = 0,
    /** Channel grid columns on home; 0 = otomatis (adaptive). */
    val gridColumns: Int = 0,
    /** Whether the bundled default playlist has been seeded (first-launch only). */
    val defaultPlaylistSeeded: Boolean = false,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Buffer sizing presets. Live streams use shorter buffers than VOD. */
enum class BufferProfile(val minBufferMs: Int, val maxBufferMs: Int) {
    LOW(5_000, 15_000),
    NORMAL(15_000, 30_000),
    HIGH(30_000, 60_000),
}
