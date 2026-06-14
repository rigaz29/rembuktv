package com.rembuk.rembuktv.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.ui.ChannelsViewModel
import com.rembuk.rembuktv.ui.common.ChannelLogo
import com.rembuk.rembuktv.ui.common.LoadingState
import com.rembuk.rembuktv.ui.common.MessageState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    onChannelClick: (Channel, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylists: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Zap within the category the user is currently browsing.
    val onChannelClickInGroup: (Channel) -> Unit = { onChannelClick(it, state.selectedGroup) }
    var searchVisible by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rembuk TV") },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.setQuery("")
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Cari")
                    }
                    IconButton(onClick = onOpenPlaylists) {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = "Playlist")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Pengaturan")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searchVisible) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchVisible = false
                            viewModel.setQuery("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup pencarian")
                        }
                    },
                    placeholder = { Text("Cari channel…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .focusRequester(searchFocus),
                )
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            }

            if (state.groups.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedGroup == null,
                            onClick = { viewModel.setGroup(null) },
                            label = { Text("Semua") },
                        )
                    }
                    items(state.groups) { group ->
                        FilterChip(
                            selected = state.selectedGroup == group,
                            onClick = { viewModel.setGroup(group) },
                            label = { Text(group) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }

            when {
                state.loading -> LoadingState()
                state.channels.isEmpty() && state.favorites.isEmpty() && state.query.isBlank() ->
                    MessageState(
                        title = "Belum ada channel",
                        subtitle = "Tarik untuk menyegarkan atau tambah playlist di menu.",
                        actionLabel = "Tambah playlist",
                        onAction = onOpenPlaylists,
                    )
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ChannelGrid(
                        state = state,
                        onChannelClick = onChannelClickInGroup,
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGrid(
    state: com.rembuk.rembuktv.ui.ChannelsUiState,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    val showRows = state.query.isBlank() && state.selectedGroup == null
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showRows && state.favorites.isNotEmpty()) {
            fullSpan { SectionTitle("Favorit") }
            fullSpan { HorizontalChannelRow(state.favorites, state.favoriteIds, onChannelClick, onToggleFavorite) }
        }
        if (showRows && state.history.isNotEmpty()) {
            fullSpan { SectionTitle("Baru ditonton") }
            fullSpan { HorizontalChannelRow(state.history, state.favoriteIds, onChannelClick, onToggleFavorite) }
        }
        if (showRows) {
            fullSpan { SectionTitle("Semua channel") }
        }
        items(state.channels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                isFavorite = state.favoriteIds.contains(channel.id),
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel) },
            )
        }
        if (state.channels.isEmpty() && !showRows) {
            fullSpan {
                Text(
                    "Tidak ada channel cocok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullSpan(
    content: @Composable () -> Unit,
) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun HorizontalChannelRow(
    channels: List<Channel>,
    favoriteIds: Set<String>,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(channels, key = { it.id }) { channel ->
            Box(Modifier.width(112.dp)) {
                ChannelCard(
                    channel = channel,
                    isFavorite = favoriteIds.contains(channel.id),
                    onClick = { onChannelClick(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                )
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ChannelLogo(name = channel.name, logoUrl = channel.logoUrl, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            channel.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
