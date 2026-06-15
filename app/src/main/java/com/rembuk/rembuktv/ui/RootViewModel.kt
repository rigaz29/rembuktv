package com.rembuk.rembuktv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rembuk.rembuktv.BuildConfig
import com.rembuk.rembuktv.core.Constants
import com.rembuk.rembuktv.domain.model.ThemeMode
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import com.rembuk.rembuktv.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** One-shot auto-resume target (last watched channel), or null. */
    private val _resumeChannelId = MutableStateFlow<String?>(null)
    val resumeChannelId: StateFlow<String?> = _resumeChannelId.asStateFlow()

    init {
        // Sync with the backend on launch then on an interval to keep entitlement + catalog
        // fresh. The Room/DataStore cache renders the UI immediately/offline meanwhile.
        viewModelScope.launch {
            while (true) {
                playlistRepository.sync(BuildConfig.VERSION_NAME)
                delay(Constants.SYNC_INTERVAL_MILLIS)
            }
        }
        // Resolve the auto-resume target once at startup.
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val lastId = settings.lastChannelId
            if (settings.autoResume && lastId != null && playlistRepository.getChannel(lastId) != null) {
                _resumeChannelId.value = lastId
            }
        }
    }

    fun consumeResume() {
        _resumeChannelId.value = null
    }
}
