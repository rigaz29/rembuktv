package com.rembuk.rembuktv.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkStatus(
    val isOnline: Boolean,
    /** True when the active transport is cellular (used to cap ABR / save data). */
    val isMetered: Boolean,
)

/**
 * Emits connectivity changes so the player can react to WiFi<->cellular transitions
 * and trigger reconnects. Uses [ConnectivityManager] callbacks (available since API 21).
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val status: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = trySend(currentStatus()).let {}
            override fun onLost(network: Network) = trySend(currentStatus()).let {}
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                trySend(currentStatus()).let {}
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        trySend(currentStatus())
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged().flowOn(Dispatchers.Default)

    fun currentStatus(): NetworkStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // API 21–22: capabilities-from-active-network isn't available; use legacy info.
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            val metered = info?.type == ConnectivityManager.TYPE_MOBILE
            @Suppress("DEPRECATION")
            return NetworkStatus(isOnline = info?.isConnected == true, isMetered = metered)
        }
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        return NetworkStatus(isOnline = online, isMetered = metered)
    }
}
