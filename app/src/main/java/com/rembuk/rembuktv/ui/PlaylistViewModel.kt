package com.rembuk.rembuktv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rembuk.rembuktv.domain.model.PlaylistSource
import com.rembuk.rembuktv.domain.model.PlaylistType
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistSource>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun addPlaylist(name: String, url: String, type: PlaylistType) {
        if (name.isBlank() || url.isBlank()) {
            _message.value = "Nama dan URL tidak boleh kosong."
            return
        }
        viewModelScope.launch {
            _working.value = true
            repository.addPlaylist(name, url, type)
                .onSuccess { _message.value = "Playlist ditambahkan." }
                .onFailure { _message.value = "Gagal menambahkan playlist." }
            _working.value = false
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.removePlaylist(id) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setPlaylistEnabled(id, enabled) }
    }

    fun refresh(id: Long) {
        viewModelScope.launch {
            _working.value = true
            repository.refreshPlaylist(id)
                .onSuccess { count -> _message.value = "$count channel dimuat." }
                .onFailure { _message.value = "Gagal memuat playlist." }
            _working.value = false
        }
    }

    fun clearMessage() { _message.value = null }
}
