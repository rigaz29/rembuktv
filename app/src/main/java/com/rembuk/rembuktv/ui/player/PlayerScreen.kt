package com.rembuk.rembuktv.ui.player

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.rembuk.rembuktv.asMainActivity
import com.rembuk.rembuktv.ui.common.MessageState

@UnstableApi
@Composable
fun PlayerScreen(
    isTv: Boolean,
    isInPip: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // TV focus: the root captures D-pad keys while controls are hidden; when controls
    // show, focus jumps to play/pause so the remote drives the on-screen buttons.
    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val dialogOpen = showTracks || showSleep
    LaunchedEffect(controlsVisible, isTv, state.error, dialogOpen) {
        if (isTv && state.error == null && !dialogOpen) {
            runCatching {
                if (controlsVisible) playPauseFocus.requestFocus() else rootFocus.requestFocus()
            }
        }
    }

    // Keep the screen awake while the player is on-screen.
    DisposableEffect(Unit) {
        val window = context.asMainActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Drive Picture-in-Picture: enabled while playing without a fatal error.
    LaunchedEffect(state.channel, state.error, state.isPlaying, state.videoWidth, state.videoHeight) {
        context.asMainActivity()?.updatePipState(
            enabled = state.channel != null && state.error == null,
            width = state.videoWidth,
            height = state.videoHeight,
            isPlaying = state.isPlaying,
            onToggle = viewModel::togglePlayPause,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            context.asMainActivity()?.clearPipState()
            context.asMainActivity()?.setFullscreen(false)
        }
    }

    // Auto-hide controls.
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            kotlinx.coroutines.delay(4_000)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isTv) {
                    Modifier
                        .focusRequester(rootFocus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type != KeyEventType.KeyDown -> false
                                // Back hides the controls first, then exits on a second press.
                                event.key == Key.Back ->
                                    if (controlsVisible) { controlsVisible = false; true } else false
                                // Any other key wakes the controls when they're hidden.
                                !controlsVisible -> { controlsVisible = true; true }
                                else -> false
                            }
                        }
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    // Swipe up = next channel, swipe down = previous (mobile zapping).
                                    if (total < -120f) viewModel.next()
                                    else if (total > 120f) viewModel.previous()
                                },
                                onVerticalDrag = { _, dragAmount -> total += dragAmount },
                            )
                        }
                        .clickable { controlsVisible = !controlsVisible }
                }
            ),
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        keepScreenOn = true
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.resizeMode = state.resizeMode
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Connecting / buffering indicator.
        if (!isInPip && (state.isBuffering || state.reconnecting) && state.error == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color.White)
                if (state.reconnecting) {
                    Text(
                        "Menyambungkan ulang…",
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        // Fatal error overlay.
        state.error?.let { error ->
            if (!isInPip) {
                Surface(color = Color.Black.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize()) {
                    MessageState(
                        title = "Tidak dapat memutar",
                        subtitle = error.message,
                        actionLabel = if (error.manualRetryable) "Coba lagi" else "Kembali",
                        onAction = if (error.manualRetryable) viewModel::retry else onBack,
                    )
                }
            }
        }

        if (controlsVisible && !isInPip && state.error == null) {
            PlayerControls(
                isTv = isTv,
                state = state,
                playPauseFocus = playPauseFocus,
                onBack = onBack,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onToggleFavorite = viewModel::toggleFavorite,
                onResize = viewModel::cycleResizeMode,
                onTracks = { showTracks = true },
                onSleep = { showSleep = true },
                showFullscreen = !isTv,
                isFullscreen = isFullscreen,
                onToggleFullscreen = {
                    isFullscreen = !isFullscreen
                    context.asMainActivity()?.setFullscreen(isFullscreen)
                },
            )
        }
    }

    if (showTracks) {
        TrackSelectionDialog(
            state = state,
            onSelectVideo = viewModel::selectVideoTrack,
            onSelectAudio = viewModel::selectAudioTrack,
            onSelectText = viewModel::selectTextTrack,
            onDismiss = { showTracks = false },
        )
    }
    if (showSleep) {
        SleepTimerDialog(
            current = state.sleepTimerMinutes,
            onSelect = { viewModel.setSleepTimer(it); showSleep = false },
            onDismiss = { showSleep = false },
        )
    }
}

@Composable
private fun PlayerControls(
    isTv: Boolean,
    state: PlayerUiState,
    playPauseFocus: FocusRequester,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleFavorite: () -> Unit,
    onResize: () -> Unit,
    onTracks: () -> Unit,
    onSleep: () -> Unit,
    showFullscreen: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    val scrim = Color.Black.copy(alpha = 0.45f)
    Box(Modifier.fillMaxSize().background(scrim)) {
        // Top bar.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    state.channel?.name.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                val res = if (state.videoHeight > 0) "${state.videoHeight}p" else "Live"
                val sleep = state.sleepTimerMinutes?.let { " • Sleep ${it}m" } ?: ""
                Text(
                    listOfNotNull(state.channel?.group, res).joinToString(" • ") + sleep,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = Color.White,
                )
            }
        }

        // Center transport (prev / play-pause / next).
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.hasPrevious || isTv) {
                ControlIcon(Icons.Filled.SkipPrevious, "Sebelumnya", onPrevious, enabled = state.hasPrevious)
            }
            ControlIcon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                "Putar/Jeda",
                onPlayPause,
                large = true,
                focusRequester = playPauseFocus,
            )
            if (state.hasNext || isTv) {
                ControlIcon(Icons.Filled.SkipNext, "Berikutnya", onNext, enabled = state.hasNext)
            }
        }

        // Bottom bar.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIcon(Icons.Filled.Subtitles, "Track & subtitle", onTracks)
            ControlIcon(Icons.Filled.AspectRatio, "Mode layar", onResize)
            ControlIcon(Icons.Filled.Bedtime, "Sleep timer", onSleep)
            if (showFullscreen) {
                ControlIcon(
                    if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    "Layar penuh",
                    onToggleFullscreen,
                )
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    large: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(if (focused) 1.2f else 1f)
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.25f) else Color.Transparent),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(if (large) 56.dp else 32.dp),
        )
    }
}
