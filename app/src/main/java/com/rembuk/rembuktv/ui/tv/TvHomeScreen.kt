package com.rembuk.rembuktv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.ui.ChannelsViewModel
import com.rembuk.rembuktv.ui.common.ChannelLogo
import com.rembuk.rembuktv.ui.common.LoadingState
import com.rembuk.rembuktv.ui.common.MessageState
import com.rembuk.rembuktv.ui.common.badgeColor
import com.rembuk.rembuktv.ui.common.label
import com.rembuk.rembuktv.ui.common.showSubscribeCta

private data class TvCategory(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val channels: List<Channel>,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    onChannelClick: (Channel, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onSubscribe: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var selectedKey by remember { mutableStateOf("all") }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.channels.isEmpty() && state.favorites.isEmpty() && state.query.isBlank() ->
                    MessageState(
                        title = "Belum ada channel",
                        subtitle = "Berdonasi untuk membuka semua channel.",
                        actionLabel = "Donasi",
                        onAction = onSubscribe,
                    )
                else -> {
                    val grouped = state.channels.groupBy { it.group?.takeIf { g -> g.isNotBlank() } ?: "Lainnya" }
                    val cats = buildList {
                        add(TvCategory("all", "Semua", Icons.Filled.GridView, state.channels))
                        if (state.favorites.isNotEmpty()) add(TvCategory("fav", "Favorit", Icons.Filled.Favorite, state.favorites))
                        if (state.history.isNotEmpty()) add(TvCategory("hist", "Baru ditonton", Icons.Filled.History, state.history))
                        grouped.keys.sorted().forEach { g -> add(TvCategory("g:$g", g, Icons.Filled.Label, grouped.getValue(g))) }
                    }
                    val current = cats.firstOrNull { it.key == selectedKey } ?: cats.first()
                    val searching = state.query.isNotBlank()
                    val shown = if (searching) state.channels else current.channels

                    Row(Modifier.fillMaxSize()) {
                        // ---- Left category rail ----
                        Column(
                            Modifier
                                .width(248.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 20.dp),
                        ) {
                            Text("Rembuk TV", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                cats.forEach { cat ->
                                    RailItem(cat, selected = cat.key == selectedKey && !searching) {
                                        viewModel.setQuery("")
                                        searchVisible = false
                                        selectedKey = cat.key
                                    }
                                }
                            }
                        }

                        // ---- Right content ----
                        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 28.dp, vertical = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (searching) "Pencarian" else current.label,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    "  ${shown.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(state.entitlement.badgeColor())
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    Text(state.entitlement.label(), style = MaterialTheme.typography.labelLarge, color = Color.White)
                                }
                                Spacer(Modifier.width(12.dp))
                                if (state.entitlement.showSubscribeCta()) {
                                    Button(onClick = onSubscribe) { Text("Donasi") }
                                    Spacer(Modifier.width(12.dp))
                                }
                                Button(onClick = {
                                    searchVisible = !searchVisible
                                    if (!searchVisible) viewModel.setQuery("")
                                }) { Text(if (searchVisible) "Tutup" else "Cari") }
                                Spacer(Modifier.width(12.dp))
                                Button(onClick = onOpenSettings) { Text("Pengaturan") }
                            }

                            if (searchVisible) {
                                Spacer(Modifier.height(12.dp))
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

                            if (shown.isEmpty()) {
                                MessageState(
                                    title = "Tidak ada channel",
                                    subtitle = if (searching) "Coba kata kunci lain." else "Kategori ini kosong.",
                                    actionLabel = "",
                                    onAction = {},
                                )
                            } else {
                                LazyVerticalGrid(
                                    columns = if (state.gridColumns > 0) GridCells.Fixed(state.gridColumns) else GridCells.Adaptive(184.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(shown, key = { it.id }) { ch ->
                                        TvChannelCard(ch) {
                                            if (ch.locked) onSubscribe() else onChannelClick(ch, ch.group)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RailItem(cat: TvCategory, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(cat.icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(cat.label, color = fg, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(cat.channels.size.toString(), color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "cardScale")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                ChannelLogo(
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    modifier = Modifier.fillMaxSize(),
                )
                if (channel.locked) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(4.dp),
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Terkunci (khusus donatur)", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
