package com.flippermobile.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.hardware.status.HwState
import com.flippermobile.hardware.status.SystemStatus
import com.flippermobile.ui.components.DolphinMascot
import com.flippermobile.ui.components.PixelArt
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.StatusPill
import com.flippermobile.ui.components.Tone
import com.flippermobile.ui.navigation.Routes
import com.flippermobile.ui.theme.FlipperColors

private data class MenuEntry(val number: Int, val title: String, val subtitle: String, val icon: List<String>, val route: String)

private val MENU = listOf(
    MenuEntry(1, "NFC Tools", "Ler tags e NDEF", PixelIcons.NFC, Routes.NFC),
    MenuEntry(2, "Bluetooth Scanner", "Varredura BLE e GATT", PixelIcons.BLUETOOTH, Routes.BLUETOOTH),
    MenuEntry(3, "Wi-Fi Analyzer", "Conexão e redes", PixelIcons.WIFI, Routes.WIFI),
    MenuEntry(4, "Infrared Remote", "Controle IR", PixelIcons.INFRARED, Routes.INFRARED),
    MenuEntry(5, "Device Information", "Hardware e sistema", PixelIcons.DEVICE, Routes.DEVICE),
    MenuEntry(6, "Activity Terminal", "Logs em tempo real", PixelIcons.TERMINAL, Routes.TERMINAL),
    MenuEntry(7, "Saved Data", "Histórico local", PixelIcons.SAVED, Routes.SAVED),
    MenuEntry(8, "Settings", "Preferências", PixelIcons.SETTINGS, Routes.SETTINGS),
)

@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigate: (String) -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header() }
        item(span = { GridItemSpan(maxLineSpan) }) { StatusPanel(status) }
        items(MENU, key = { it.route }) { entry ->
            MenuCard(entry) {
                viewModel.tick()
                onNavigate(entry.route)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Uso apenas com hardware embutido do celular • Dados locais",
                style = MaterialTheme.typography.labelSmall,
                color = FlipperColors.GrayDark,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DolphinMascot(size = 84.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("FLIPPER ZERO", style = MaterialTheme.typography.displaySmall, color = FlipperColors.Orange)
            Text("MOBILE", style = MaterialTheme.typography.headlineSmall, color = FlipperColors.White)
            Text("> multiferramenta de diagnóstico_", style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray)
        }
    }
}

private fun HwState.tone(): Tone = when (this) {
    HwState.UNSUPPORTED -> Tone.NEUTRAL
    HwState.OFF -> Tone.BAD
    HwState.ON -> Tone.OK
    HwState.ACTIVE -> Tone.ACTIVE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusPanel(s: SystemStatus) {
    RetroCard(Modifier.fillMaxWidth()) {
        Text("STATUS DO SISTEMA", style = MaterialTheme.typography.titleSmall, color = FlipperColors.Orange)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                "NFC",
                when (s.nfc) {
                    HwState.UNSUPPORTED -> "Indisponível"
                    HwState.OFF -> "Desligado"
                    HwState.ON -> "Disponível"
                    HwState.ACTIVE -> "Lendo"
                },
                s.nfc.tone(),
            )
            StatusPill(
                "Bluetooth",
                when (s.bluetooth) {
                    HwState.UNSUPPORTED -> "Indisponível"
                    HwState.OFF -> "Desligado"
                    else -> "Ligado"
                },
                s.bluetooth.tone(),
            )
            StatusPill(
                "Wi-Fi",
                when (s.wifi) {
                    HwState.UNSUPPORTED -> "Indisponível"
                    HwState.OFF -> "Desligado"
                    HwState.ON -> "Desconectado"
                    HwState.ACTIVE -> "Conectado"
                },
                s.wifi.tone(),
            )
            StatusPill("IR", if (s.ir == HwState.ON) "Disponível" else "Indisponível", s.ir.tone())
            StatusPill(
                "Bateria",
                if (s.batteryPercent >= 0) "${s.batteryPercent}%${if (s.charging) " ⚡" else ""}" else "—",
                when {
                    s.batteryPercent < 0 -> Tone.NEUTRAL
                    s.batteryPercent <= 15 -> Tone.BAD
                    s.batteryPercent <= 35 -> Tone.WARN
                    else -> Tone.OK
                },
            )
            StatusPill(
                "Permissões",
                "${s.permissionsGranted}/${s.permissionsTotal}",
                if (s.permissionsGranted == s.permissionsTotal) Tone.OK else Tone.WARN,
            )
        }
    }
}

@Composable
private fun MenuCard(entry: MenuEntry, onClick: () -> Unit) {
    RetroCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 110.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${entry.title}: ${entry.subtitle}" },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelArt(entry.icon, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                entry.number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = FlipperColors.OrangeDim,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(entry.title, style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray)
    }
}
