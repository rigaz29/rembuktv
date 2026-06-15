package com.rembuk.rembuktv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rembuk.rembuktv.BuildConfig
import com.rembuk.rembuktv.core.Constants
import com.rembuk.rembuktv.domain.model.Channel
import com.rembuk.rembuktv.domain.model.Entitlement
import com.rembuk.rembuktv.domain.model.RemoteConfig
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val history: List<Channel> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val query: String = "",
    val selectedGroup: String? = null,
    val message: String? = null,
    val entitlement: Entitlement = Entitlement.FREE,
    val remoteConfig: RemoteConfig = RemoteConfig(),
)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedGroup = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private data class Repos(
        val channels: List<Channel>,
        val groups: List<String>,
        val favorites: List<Channel>,
        val history: List<Channel>,
        val favoriteIds: Set<String>,
    )

    private val repos = combine(
        repository.observeChannels(),
        repository.observeGroups(),
        repository.observeFavorites(),
        repository.observeHistory(Constants.HISTORY_LIMIT),
        repository.observeFavoriteIds(),
    ) { channels, groups, favorites, history, favoriteIds ->
        Repos(channels, groups, favorites, history, favoriteIds)
    }

    private data class Filters(
        val query: String,
        val group: String?,
        val refreshing: Boolean,
        val message: String?,
    )

    private val filters = combine(query, selectedGroup, refreshing, message) { q, g, r, m ->
        Filters(q, g, r, m)
    }

    private val subscription = combine(
        repository.observeEntitlement(),
        repository.observeRemoteConfig(),
    ) { entitlement, config -> entitlement to config }

    val uiState: StateFlow<ChannelsUiState> = combine(repos, filters, subscription) { r, f, sub ->
        val filtered = r.channels.filter { ch ->
            (f.group == null || ch.group == f.group) && matchesQuery(ch, f.query)
        }
        ChannelsUiState(
            loading = false,
            refreshing = f.refreshing,
            channels = filtered,
            groups = r.groups,
            favorites = r.favorites,
            history = r.history,
            favoriteIds = r.favoriteIds,
            query = f.query,
            selectedGroup = f.group,
            message = f.message,
            entitlement = sub.first,
            remoteConfig = sub.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelsUiState())

    private fun matchesQuery(channel: Channel, query: String): Boolean {
        if (query.isBlank()) return true
        return channel.name.contains(query, ignoreCase = true) ||
            channel.group?.contains(query, ignoreCase = true) == true
    }

    fun setQuery(value: String) { query.value = value }

    fun setGroup(group: String?) { selectedGroup.value = group }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val isFav = uiState.value.favoriteIds.contains(channel.id)
            repository.setFavorite(channel.id, !isFav)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            repository.sync(BuildConfig.VERSION_NAME)
                .onFailure { message.value = "Gagal memuat. Menampilkan data tersimpan." }
            refreshing.value = false
        }
    }

    fun clearMessage() { message.value = null }
}
