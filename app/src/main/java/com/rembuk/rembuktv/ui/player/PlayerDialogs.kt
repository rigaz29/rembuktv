package com.rembuk.rembuktv.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun TrackSelectionDialog(
    state: PlayerUiState,
    onSelectVideo: (String) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectText: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Kualitas video", style = MaterialTheme.typography.titleMedium)
                if (state.videoTracks.isEmpty()) {
                    EmptyHint("Tidak ada pilihan kualitas")
                } else {
                    state.videoTracks.forEach { option ->
                        TrackRow(option.label, option.selected) { onSelectVideo(option.id) }
                    }
                }

                SectionSpacer()
                Text("Audio", style = MaterialTheme.typography.titleMedium)
                if (state.audioTracks.isEmpty()) {
                    EmptyHint("Tidak ada track audio alternatif")
                } else {
                    state.audioTracks.forEach { option ->
                        TrackRow(option.label, option.selected) { onSelectAudio(option.id) }
                    }
                }

                SectionSpacer()
                Text("Subtitle", style = MaterialTheme.typography.titleMedium)
                TrackRow("Mati", !state.subtitlesEnabled) { onSelectText(null) }
                state.textTracks.forEach { option ->
                    TrackRow(option.label, option.selected) { onSelectText(option.id) }
                }
            }
        }
    }
}

@Composable
fun SleepTimerDialog(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf<Pair<String, Int?>>(
        "Mati" to null,
        "15 menit" to 15,
        "30 menit" to 30,
        "60 menit" to 60,
        "90 menit" to 90,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
                options.forEach { (label, minutes) ->
                    TrackRow(label, current == minutes) { onSelect(minutes) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionSpacer() {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}
