package com.rembuk.rembuktv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.ui.ChannelsViewModel
import com.rembuk.rembuktv.ui.common.ChannelLogo
import com.rembuk.rembuktv.ui.common.LoadingState
import com.rembuk.rembuktv.ui.common.MessageState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    onChannelClick: (Channel, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylists: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rembuk TV", style = MaterialTheme.typography.headlineMedium)
                    state.selectedGroup?.let { group ->
                        Text(
                            "  •  $group",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.setQuery("")
                    }) { Text(if (searchVisible) "Tutup" else "Cari") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onOpenPlaylists) { Text("Playlist") }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onOpenSettings) { Text("Pengaturan") }
                }

                if (searchVisible) {
                    Spacer(Modifier.height(12.dp))
                    // Use a Material3 (non-TV) text field, dark-themed so it stays legible.
                    M3Theme(colorScheme = m3DarkColorScheme()) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            singleLine = true,
                            label = { M3Text("Cari channel…") },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                    }
                    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
                }

                Spacer(Modifier.height(16.dp))

                when {
                    state.loading -> LoadingState()
                    state.channels.isEmpty() && state.favorites.isEmpty() && state.query.isBlank() ->
                        MessageState(
                            title = "Belum ada channel",
                            subtitle = "Tambahkan playlist dari menu Playlist.",
                            actionLabel = "Buka Playlist",
                            onAction = onOpenPlaylists,
                        )
                    else -> ChannelRows(state, onChannelClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRows(
    state: com.rembuk.rembuktv.ui.ChannelsUiState,
    onChannelClick: (Channel, String?) -> Unit,
) {
    val grouped = state.channels.groupBy { it.group?.takeIf { g -> g.isNotBlank() } ?: "Lainnya" }
    val searching = state.query.isNotBlank()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Favorit/Baru ditonton span categories, so zap through all channels. Hidden while
        // searching so the results stay focused on matches.
        if (!searching && state.favorites.isNotEmpty()) {
            item { ChannelRow("Favorit", state.favorites) { onChannelClick(it, null) } }
        }
        if (!searching && state.history.isNotEmpty()) {
            item { ChannelRow("Baru ditonton", state.history) { onChannelClick(it, null) } }
        }
        // Each group row is a category: zap within the channel's own group.
        grouped.forEach { (group, channels) ->
            item { ChannelRow(group, channels) { onChannelClick(it, it.group) } }
        }
        if (grouped.isEmpty()) {
            item {
                Text("Tidak ada channel cocok.", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "  ${channels.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(channels, key = { it.id }) { channel ->
                TvChannelCard(channel, onClick = { onChannelClick(channel) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "cardScale")
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(168.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Column(Modifier.width(168.dp)) {
            ChannelLogo(
                name = channel.name,
                logoUrl = channel.logoUrl,
                modifier = Modifier.fillMaxWidth().height(104.dp),
            )
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
