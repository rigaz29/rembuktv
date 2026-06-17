package com.rembuk.rembuktv.ui.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.Entitlement
import com.rembuk.rembuktv.ui.ChannelsViewModel
import com.rembuk.rembuktv.ui.common.ChannelLogo
import com.rembuk.rembuktv.ui.common.DoubleBackToExit
import com.rembuk.rembuktv.ui.common.LoadingState
import com.rembuk.rembuktv.ui.common.MessageState
import com.rembuk.rembuktv.ui.common.badgeColor
import com.rembuk.rembuktv.ui.common.label
import com.rembuk.rembuktv.ui.common.showSubscribeCta
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    onChannelClick: (Channel, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onSubscribe: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Categories the user can swipe between: "Semua" (null) first, then the backend groups.
    val tabs = remember(state.groups) { listOf<String?>(null) + state.groups }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val searching = state.query.isNotBlank()

    // Home is the navigation root: require a double back press to exit the app.
    DoubleBackToExit()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rembuk TV")
                        Spacer(Modifier.width(8.dp))
                        EntitlementBadge(state.entitlement, onClick = onSubscribe)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.setQuery("")
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Cari")
                    }
                    if (state.entitlement.showSubscribeCta()) {
                        TextButton(onClick = onSubscribe) { Text("Donasi") }
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

            when {
                state.loading -> LoadingState()
                state.channels.isEmpty() && state.favorites.isEmpty() && state.query.isBlank() ->
                    MessageState(
                        title = "Belum ada channel",
                        subtitle = "Tarik untuk menyegarkan, atau berdonasi untuk membuka semua channel.",
                        actionLabel = "Donasi",
                        onAction = onSubscribe,
                    )
                // While searching, show a flat result list across all categories.
                searching -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    ChannelGridPage(
                        channels = state.channels,
                        showSections = false,
                        favorites = state.favorites,
                        history = state.history,
                        favoriteIds = state.favoriteIds,
                        gridColumns = state.gridColumns,
                        onChannelClick = { if (it.locked) onSubscribe() else onChannelClick(it, null) },
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }
                else -> {
                    if (tabs.size > 1) {
                        CategoryChips(
                            tabs = tabs,
                            selectedIndex = pagerState.currentPage,
                            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                    // Swipe left/right to move between categories.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { page ->
                        val group = tabs.getOrNull(page)
                        val pageChannels = if (group == null) {
                            state.channels
                        } else {
                            state.channels.filter { it.group == group }
                        }
                        PullToRefreshBox(
                            isRefreshing = state.refreshing,
                            onRefresh = viewModel::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            ChannelGridPage(
                                channels = pageChannels,
                                showSections = group == null,
                                favorites = state.favorites,
                                history = state.history,
                                favoriteIds = state.favoriteIds,
                                gridColumns = state.gridColumns,
                                onChannelClick = { if (it.locked) onSubscribe() else onChannelClick(it, group) },
                                onToggleFavorite = viewModel::toggleFavorite,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    tabs: List<String?>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // Keep the active category chip in view as the pager moves.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(tabs) { index, group ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(group ?: "Semua") },
            )
        }
    }
}

@Composable
private fun ChannelGridPage(
    channels: List<Channel>,
    showSections: Boolean,
    favorites: List<Channel>,
    history: List<Channel>,
    favoriteIds: Set<String>,
    gridColumns: Int,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    LazyVerticalGrid(
        columns = if (gridColumns > 0) GridCells.Fixed(gridColumns) else GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (showSections && favorites.isNotEmpty()) {
            fullSpan { SectionTitle("Favorit") }
            fullSpan { HorizontalChannelRow(favorites, favoriteIds, onChannelClick, onToggleFavorite) }
        }
        if (showSections && history.isNotEmpty()) {
            fullSpan { SectionTitle("Baru ditonton") }
            fullSpan { HorizontalChannelRow(history, favoriteIds, onChannelClick, onToggleFavorite) }
        }
        if (showSections) {
            fullSpan { SectionTitle("Semua channel") }
        }
        items(channels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                isFavorite = favoriteIds.contains(channel.id),
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel) },
            )
        }
        if (channels.isEmpty()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntitlementBadge(entitlement: Entitlement, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = entitlement.badgeColor(),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            entitlement.label(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
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
            if (channel.locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Terkunci (khusus donatur)",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
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
