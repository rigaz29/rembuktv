package com.rembuk.rembuktv.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rembuk.rembuktv.core.Constants
import com.rembuk.rembuktv.core.DeviceId
import com.rembuk.rembuktv.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SubscribeViewModel @Inject constructor(
    repository: PlaylistRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** Device id shown to the user / passed to the website so admin can activate. */
    val deviceId: String = DeviceId.get(context)

    /** Subscription website URL from remote config, with a sensible fallback. */
    val websiteUrl: StateFlow<String> = repository.observeRemoteConfig()
        .map { it.websiteUrl.ifBlank { Constants.SUBSCRIBE_URL_FALLBACK } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.SUBSCRIBE_URL_FALLBACK)
}
