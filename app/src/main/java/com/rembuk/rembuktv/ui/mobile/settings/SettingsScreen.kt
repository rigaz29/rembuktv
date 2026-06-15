package com.rembuk.rembuktv.ui.mobile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rembuk.rembuktv.core.DeviceId
import com.rembuk.rembuktv.domain.model.BufferProfile
import com.rembuk.rembuktv.domain.model.ThemeMode
import com.rembuk.rembuktv.ui.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Tampilan")
            Text("Tema", style = MaterialTheme.typography.bodyMedium)
            ChipRow(
                options = ThemeMode.entries.map { it to it.label() },
                selected = settings.themeMode,
                onSelect = viewModel::setTheme,
            )

            HorizontalDivider()
            SectionHeader("Pemutaran")
            SwitchRow(
                title = "Lanjut otomatis ke channel terakhir",
                checked = settings.autoResume,
                onCheckedChange = viewModel::setAutoResume,
            )
            SwitchRow(
                title = "Batasi kualitas saat data seluler",
                subtitle = "Hemat kuota dengan membatasi bitrate di jaringan seluler",
                checked = settings.capBitrateOnCellular,
                onCheckedChange = viewModel::setCapCellular,
            )
            Text("Ukuran buffer", style = MaterialTheme.typography.bodyMedium)
            ChipRow(
                options = BufferProfile.entries.map { it to it.label() },
                selected = settings.bufferProfile,
                onSelect = viewModel::setBufferProfile,
            )

            HorizontalDivider()
            SectionHeader("Data")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = viewModel::clearCache) { Text("Hapus cache") }
                TextButton(onClick = viewModel::clearHistory) { Text("Hapus riwayat") }
            }

            HorizontalDivider()
            SectionHeader("Perangkat")
            val context = LocalContext.current
            val deviceId = remember { DeviceId.get(context) }
            Text("Device ID (kirim ke admin untuk aktivasi)", style = MaterialTheme.typography.bodyMedium)
            Text(
                deviceId,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Sistem"
    ThemeMode.LIGHT -> "Terang"
    ThemeMode.DARK -> "Gelap"
    ThemeMode.AMOLED -> "AMOLED"
}

private fun BufferProfile.label(): String = when (this) {
    BufferProfile.LOW -> "Kecil (latency rendah)"
    BufferProfile.NORMAL -> "Normal"
    BufferProfile.HIGH -> "Besar (stabil)"
}
