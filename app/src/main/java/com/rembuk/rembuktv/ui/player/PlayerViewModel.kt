package com.rembuk.rembuktv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.rembuk.rembuktv.core.Constants
import com.rembuk.rembuktv.core.NetworkMonitor
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import com.rembuk.rembuktv.domain.repository.SettingsRepository
import com.rembuk.rembuktv.player.ClearKeyRegistry
import com.rembuk.rembuktv.player.ExoPlayerFactory
import com.rembuk.rembuktv.player.MediaItemFactory
import com.rembuk.rembuktv.player.PlaybackHeaders
import com.rembuk.rembuktv.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.HttpURLConnection
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val playerFactory: ExoPlayerFactory,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val initialChannelId: String =
        checkNotNull(savedStateHandle[Routes.PLAYER_ARG_CHANNEL])

    /** Category the user was browsing when opening the player; null means all channels. */
    private val groupFilter: String? = savedStateHandle[Routes.PLAYER_ARG_GROUP]

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var controller: ExoPlayer? = null

    /** Channels used for prev/next zapping, scoped to the browsed category when set. */
    private var channelList: List<Channel> = emptyList()

    // Retry state.
    private var retryCount = 0
    private var retryJob: Job? = null
    private var sleepJob: Job? = null

    /** Maps a UI track-option id to the override needed to apply it. */
    private val videoOverrides = HashMap<String, TrackSelectionOverride>()
    private val audioOverrides = HashMap<String, TrackSelectionOverride>()
    private val textOverrides = HashMap<String, TrackSelectionOverride>()
    private var videoOverrideActive = false

    init {
        viewModelScope.launch {
            // Only playable (unlocked) channels participate in prev/next zapping.
            val all = repository.observeChannels().first().filter { !it.locked }
            val scoped = if (groupFilter.isNullOrBlank()) all else all.filter { it.group == groupFilter }
            channelList = scoped.ifEmpty { all }
            val resizeMode = settingsRepository.settings.first().defaultResizeMode
            _uiState.update { it.copy(resizeMode = resizeMode) }
            connect()
        }
        observeNetwork()
        observeAbrCap()
    }

    private suspend fun connect() {
        val bufferProfile = settingsRepository.settings.first().bufferProfile
        val exo = runCatching { playerFactory.create(bufferProfile) }.getOrElse {
            Timber.e(it, "Failed to create player")
            _uiState.update {
                it.copy(error = PlaybackErrorInfo("Gagal memulai pemutar.", manualRetryable = true))
            }
            return
        }
        controller = exo
        exo.addListener(listener)
        _player.value = exo
        val channel = repository.getChannel(initialChannelId)
        if (channel != null) {
            play(channel)
        } else {
            _uiState.update {
                it.copy(error = PlaybackErrorInfo("Channel tidak ditemukan.", manualRetryable = false))
            }
        }
    }

    private fun play(channel: Channel) {
        val ctrl = controller ?: return
        retryJob?.cancel()
        retryCount = 0
        videoOverrideActive = false
        _uiState.update {
            it.copy(channel = channel, error = null, reconnecting = false, isFavorite = false)
        }
        ctrl.setMediaItem(MediaItemFactory.fromChannel(channel))
        ctrl.prepare()
        ctrl.playWhenReady = true
        updateZapState(channel)

        viewModelScope.launch {
            repository.recordHistory(channel.id)
            settingsRepository.setLastChannelId(channel.id)
        }
        viewModelScope.launch {
            repository.observeFavoriteIds().first().let { ids ->
                _uiState.update { it.copy(isFavorite = ids.contains(channel.id)) }
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_READY) onPlaybackRecovered()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) onPlaybackRecovered()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.update { it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            rebuildTrackOptions(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    private fun onPlaybackRecovered() {
        retryCount = 0
        retryJob?.cancel()
        if (_uiState.value.reconnecting || _uiState.value.error != null) {
            _uiState.update { it.copy(reconnecting = false, error = null) }
        }
    }

    // --- Error handling & auto-retry ---

    private fun handleError(error: PlaybackException) {
        Timber.w(error, "Playback error code=%d", error.errorCode)
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                // Fell behind the live window: jump back to the live edge and continue.
                controller?.seekToDefaultPosition()
                controller?.prepare()
            }
            isDrmError(error) -> setFatal(
                "Channel ini dilindungi DRM dan tidak dapat diputar tanpa lisensi yang valid.",
                retryable = false,
            )
            isForbidden(error) -> setFatal(
                "Channel tidak dapat diakses (mungkin diblokir wilayah/geo-restricted).",
                retryable = false,
            )
            isUnsupported(error) -> setFatal(
                "Format stream tidak didukung.",
                retryable = false,
            )
            else -> scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (retryCount >= Constants.MAX_PLAYBACK_RETRIES) {
            setFatal("Tidak dapat menyambung ke stream. Periksa koneksi lalu coba lagi.", retryable = true)
            return
        }
        val delayMs = min(
            Constants.RETRY_BASE_DELAY_MS * 2.0.pow(retryCount).toLong(),
            Constants.RETRY_MAX_DELAY_MS,
        )
        retryCount++
        _uiState.update { it.copy(reconnecting = true, error = null) }
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(delayMs)
            controller?.prepare()
            controller?.playWhenReady = true
        }
    }

    private fun setFatal(message: String, retryable: Boolean) {
        retryJob?.cancel()
        _uiState.update {
            it.copy(reconnecting = false, error = PlaybackErrorInfo(message, retryable))
        }
    }

    /** Manual retry from the UI, or auto-retry when connectivity returns. */
    fun retry() {
        retryCount = 0
        _uiState.update { it.copy(reconnecting = true, error = null) }
        controller?.prepare()
        controller?.playWhenReady = true
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.status.collect { status ->
                val state = _uiState.value
                val shouldRetry = status.isOnline &&
                    (state.reconnecting || state.error?.manualRetryable == true)
                if (shouldRetry) retry()
            }
        }
    }

    /** Cap adaptive bitrate on metered networks (moved from the old playback service). */
    private fun observeAbrCap() {
        viewModelScope.launch {
            combine(networkMonitor.status, settingsRepository.settings) { net, settings ->
                if (settings.capBitrateOnCellular && net.isMetered) settings.maxCellularBitrate.toInt() else Int.MAX_VALUE
            }.collect { maxBitrate ->
                controller?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setMaxVideoBitrate(maxBitrate).build()
                }
            }
        }
    }

    // --- Track selection ---

    private fun rebuildTrackOptions(tracks: Tracks) {
        videoOverrides.clear(); audioOverrides.clear(); textOverrides.clear()
        val video = ArrayList<TrackOption>()
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        var anyTextSelected = false

        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val id = "${group.type}-${group.mediaTrackGroup.id}-$i"
                val override = TrackSelectionOverride(group.mediaTrackGroup, i)
                val selected = group.isTrackSelected(i)
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> {
                        videoOverrides[id] = override
                        val label = if (format.height > 0) "${format.height}p" else "Track ${i + 1}"
                        video += TrackOption(id, label, selected && videoOverrideActive)
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        audioOverrides[id] = override
                        audio += TrackOption(id, audioLabel(format.language, format.label, i), selected)
                    }
                    C.TRACK_TYPE_TEXT -> {
                        textOverrides[id] = override
                        if (selected) anyTextSelected = true
                        text += TrackOption(id, audioLabel(format.language, format.label, i), selected)
                    }
                }
            }
        }
        // Video "Auto" (adaptive) option.
        if (video.isNotEmpty()) {
            video.add(0, TrackOption(AUTO_ID, "Auto", !videoOverrideActive))
        }
        _uiState.update {
            it.copy(
                videoTracks = video,
                audioTracks = audio,
                textTracks = text,
                subtitlesEnabled = anyTextSelected,
            )
        }
    }

    fun selectVideoTrack(optionId: String) {
        val ctrl = controller ?: return
        val params = ctrl.trackSelectionParameters.buildUpon()
        if (optionId == AUTO_ID) {
            videoOverrideActive = false
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            videoOverrides[optionId]?.let {
                videoOverrideActive = true
                params.setOverrideForType(it)
            }
        }
        ctrl.trackSelectionParameters = params.build()
    }

    fun selectAudioTrack(optionId: String) {
        val ctrl = controller ?: return
        audioOverrides[optionId]?.let {
            ctrl.trackSelectionParameters = ctrl.trackSelectionParameters.buildUpon()
                .setOverrideForType(it).build()
        }
    }

    fun selectTextTrack(optionId: String?) {
        val ctrl = controller ?: return
        val builder = ctrl.trackSelectionParameters.buildUpon()
        if (optionId == null) {
            // Subtitles off.
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            textOverrides[optionId]?.let {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(it)
            }
        }
        ctrl.trackSelectionParameters = builder.build()
    }

    // --- Resize / playback controls ---

    fun cycleResizeMode() {
        val next = ResizeOption.fromMode(_uiState.value.resizeMode).let { current ->
            val values = ResizeOption.entries
            values[(current.ordinal + 1) % values.size]
        }
        setResizeMode(next.mode)
    }

    fun setResizeMode(mode: Int) {
        _uiState.update { it.copy(resizeMode = mode) }
        viewModelScope.launch { settingsRepository.setDefaultResizeMode(mode) }
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else { ctrl.prepare(); ctrl.play() }
    }

    fun toggleFavorite() {
        val channel = _uiState.value.channel ?: return
        val newValue = !_uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = newValue) }
        viewModelScope.launch { repository.setFavorite(channel.id, newValue) }
    }

    // --- Zapping ---

    private fun updateZapState(channel: Channel) {
        val index = channelList.indexOfFirst { it.id == channel.id }
        _uiState.update {
            it.copy(
                hasPrevious = index > 0,
                hasNext = index in 0 until channelList.lastIndex,
            )
        }
    }

    fun next() = moveBy(1)
    fun previous() = moveBy(-1)

    private fun moveBy(delta: Int) {
        val current = _uiState.value.channel ?: return
        val index = channelList.indexOfFirst { it.id == current.id }
        val target = channelList.getOrNull(index + delta) ?: return
        play(target)
    }

    // --- Sleep timer ---

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerMinutes = null) }
            return
        }
        sleepJob = viewModelScope.launch {
            var remaining = minutes
            while (remaining > 0) {
                _uiState.update { it.copy(sleepTimerMinutes = remaining) }
                delay(60_000)
                remaining--
            }
            controller?.pause()
            _uiState.update { it.copy(sleepTimerMinutes = null) }
        }
    }

    override fun onCleared() {
        retryJob?.cancel()
        sleepJob?.cancel()
        controller?.run {
            stop()
            removeListener(listener)
            release()
        }
        controller = null
        PlaybackHeaders.clear()
        ClearKeyRegistry.clear()
        super.onCleared()
    }

    // --- Error classification helpers ---

    private fun isDrmError(e: PlaybackException): Boolean =
        e.errorCode in PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED

    private fun isForbidden(e: PlaybackException): Boolean {
        if (e.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return false
        val cause = e.cause
        return cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException &&
            (cause.responseCode == HttpURLConnection.HTTP_FORBIDDEN || cause.responseCode == 451)
    }

    private fun isUnsupported(e: PlaybackException): Boolean = e.errorCode in setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    )

    private fun audioLabel(language: String?, label: String?, index: Int): String =
        label?.takeIf { it.isNotBlank() }
            ?: language?.takeIf { it.isNotBlank() }
            ?: "Track ${index + 1}"

    companion object {
        private const val AUTO_ID = "auto"
    }
}
