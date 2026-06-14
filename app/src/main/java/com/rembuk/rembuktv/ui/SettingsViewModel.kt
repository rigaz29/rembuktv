package com.rembuk.rembuktv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rembuk.rembuktv.domain.model.AppSettings
import com.rembuk.rembuktv.domain.model.BufferProfile
import com.rembuk.rembuktv.domain.model.ThemeMode
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import com.rembuk.rembuktv.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(mode: ThemeMode) = launchEdit { settingsRepository.setThemeMode(mode) }
    fun setAutoResume(enabled: Boolean) = launchEdit { settingsRepository.setAutoResume(enabled) }
    fun setBufferProfile(profile: BufferProfile) = launchEdit { settingsRepository.setBufferProfile(profile) }
    fun setCapCellular(enabled: Boolean) = launchEdit { settingsRepository.setCapBitrateOnCellular(enabled) }

    fun clearCache() = launchEdit { playlistRepository.clearCache() }
    fun clearHistory() = launchEdit { playlistRepository.clearHistory() }

    private inline fun launchEdit(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
