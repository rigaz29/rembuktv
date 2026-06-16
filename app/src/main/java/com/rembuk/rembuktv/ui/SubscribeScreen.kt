package com.rembuk.rembuktv.ui

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rembuk.rembuktv.ui.common.LoadingState

/** In-app WebView for the donation website (how to donate + chat admin). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribeScreen(
    onBack: () -> Unit,
    viewModel: SubscribeViewModel = hiltViewModel(),
) {
    val baseUrl by viewModel.websiteUrl.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        if (baseUrl.isBlank()) {
            LoadingState()
            return@Scaffold
        }
        // Pass the device id so the admin can identify/activate the user.
        val url = remember(baseUrl, viewModel.deviceId) {
            runCatching {
                Uri.parse(baseUrl).buildUpon().appendQueryParameter("device", viewModel.deviceId).build().toString()
            }.getOrDefault(baseUrl)
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(url)
                }
            },
            update = { it.loadUrl(url) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}
