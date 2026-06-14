package com.rembuk.rembuktv

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rembuk.rembuktv.core.isTelevision
import com.rembuk.rembuktv.ui.AppRoot
import com.rembuk.rembuktv.ui.RootViewModel
import com.rembuk.rembuktv.ui.theme.RembukTvTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val rootViewModel: RootViewModel by viewModels()

    // PiP state, set by the player screen.
    private var pipEnabled = false
    private var pipAspect: Rational? = null
    private var pipIsPlaying = true
    private var onPipToggle: (() -> Unit)? = null

    private val isInPip = mutableStateOf(false)

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) onPipToggle?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isTv = isTelevision()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, IntentFilter(ACTION_PIP_TOGGLE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipReceiver, IntentFilter(ACTION_PIP_TOGGLE))
        }

        setContent {
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
            val inPip by isInPip
            RembukTvTheme(themeMode) {
                AppRoot(
                    isTv = isTv,
                    isInPip = inPip,
                    rootViewModel = rootViewModel,
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    /** Called by the player screen to control PiP behaviour. */
    fun updatePipState(enabled: Boolean, width: Int, height: Int, isPlaying: Boolean, onToggle: () -> Unit) {
        pipEnabled = enabled
        pipIsPlaying = isPlaying
        onPipToggle = onToggle
        pipAspect = if (enabled && width > 0 && height > 0) Rational(width, height) else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && supportsPip()) {
            runCatching { setPictureInPictureParams(buildPipParams()) }
        }
    }

    fun clearPipState() {
        pipEnabled = false
        onPipToggle = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && supportsPip()) {
            runCatching { enterPictureInPictureMode(buildPipParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
    }

    private fun supportsPip(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        pipAspect?.let { ratio ->
            // Guard against out-of-range ratios that throw on some OEMs.
            if (ratio.toFloat() in 0.42f..2.39f) builder.setAspectRatio(ratio)
        }
        builder.setActions(listOf(buildToggleAction()))
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildToggleAction(): RemoteAction {
        val iconRes = if (pipIsPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_PIP_TOGGLE).setPackage(packageName),
            flags,
        )
        val label = if (pipIsPlaying) "Pause" else "Play"
        return RemoteAction(Icon.createWithResource(this, iconRes), label, label, pendingIntent)
    }

    companion object {
        private const val ACTION_PIP_TOGGLE = "com.rembuk.rembuktv.PIP_TOGGLE"
    }
}

/** Resolve the current [MainActivity] from a Composable context, if hosted by one. */
fun Context.asMainActivity(): MainActivity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is MainActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
