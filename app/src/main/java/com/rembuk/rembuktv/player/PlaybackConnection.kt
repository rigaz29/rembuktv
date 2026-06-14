package com.rembuk.rembuktv.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Connects the UI to [PlaybackService] by building a [MediaController]. */
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sessionToken =
        SessionToken(context, ComponentName(context, PlaybackService::class.java))

    /** Builds and returns a connected [MediaController]. Caller must release it. */
    suspend fun connect(): MediaController = suspendCancellableCoroutine { cont ->
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        cont.invokeOnCancellation {
            if (future.isDone && !future.isCancelled) {
                runCatching { MediaController.releaseFuture(future) }
            } else {
                future.cancel(true)
            }
        }
    }
}
