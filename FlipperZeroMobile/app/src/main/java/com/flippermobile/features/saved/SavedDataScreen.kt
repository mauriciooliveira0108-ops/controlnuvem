package com.flippermobile.features.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.core.util.Formatters
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.ir.IrCodeParser
import com.flippermobile.domain.model.NfcReading
import com.flippermobile.ui.components.EmptyState
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.InfoRow
import com.flippermobile.ui.components.MonoBlock
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.theme.FlipperColors

@Composable
fun SavedDataScreen(viewModel: SavedDataViewModel, onBack: () -> Unit) {
    val nfc by viewModel.nfc.collectAsStateWithLifecycle()
    val ble by viewModel.ble.collectAsStateWithLifecycle()
    val wifi by viewModel.wifi.collectAsStateWithLifecycle()
    val ir by viewModel.ir.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val tab = SavedTab.entries[tabIndex]
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<NfcReading?>(null) }
    var renaming by remember { mutableStateOf<NfcReading?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = "Saved Data",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.SAVED,
        actions = {
            HeaderAction("Exportar", { viewModel.export(tab) })
            HeaderAction("Apagar", { confirmClear = true })
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = FlipperColors.Black,
                contentColor = FlipperColors.Orange,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tabIndex]), color = FlipperColors.Orange)
                },
            ) {
                SavedTab.entries.forEachIndexed { i, t ->
                    val count = when (t) {
                        SavedTab.NFC -> nfc.size
                        SavedTab.BLE -> ble.size
                        SavedTab.WIFI -> wifi.size
                        SavedTab.IR -> ir.size
                    }
                    Tab(
                        selected = i == tabIndex,
                        onClick = { tabIndex = i },
                        text = { Text("${t.label} ($count)", style = MaterialTheme.typography.labelMedium) },
                        selectedContentColor = FlipperColors.Orange,
                        unselectedContentColor = FlipperColors.Gray,
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    SavedTab.NFC -> {
                        if (nfc.isEmpty()) item { EmptyState("Sem leituras NFC", "Leia uma tag em NFC Tools e toque em Salvar.", icon = PixelIcons.NFC) }
                        items(nfc, key = { it.id }) { r ->
                            RetroCard(Modifier.fillMaxWidth(), onClick = { detail = r }) {
                                Text(r.label.ifBlank { r.tagType }, style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
                                InfoRow("UID", r.uidHex.ifBlank { "—" })
                                InfoRow("Data", Formatters.timestamp(r.timestamp))
                                InfoRow("NDEF", "${r.ndefRecords.size} registro(s)")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RetroButton("Renomear", onClick = { renaming = r }, filled = false)
                                    RetroButton("Excluir", onClick = { viewModel.deleteNfc(r.id) }, filled = false, color = FlipperColors.Red)
                                }
                            }
                        }
                    }
                    SavedTab.BLE -> {
                        if (ble.isEmpty()) item { EmptyState("Sem dispositivos BLE salvos", "Faça uma varredura e toque em Salvar.", icon = PixelIcons.BLUETOOTH) }
                        items(ble, key = { it.id }) { s ->
                            RetroCard(Modifier.fillMaxWidth()) {
                                Text(s.device.name ?: "(sem nome)", style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
                                InfoRow("Endereço", s.device.address)
                                InfoRow("RSSI", "${s.device.rssi} dBm")
                                InfoRow("Salvo em", Formatters.timestamp(s.savedAt))
                                if (s.device.serviceUuids.isNotEmpty()) InfoRow("Serviços", s.device.serviceUuids.size.toString())
                                s.gattSummary?.let { MonoBlock(it, color = FlipperColors.White) }
                                Spacer(Modifier.height(6.dp))
                                RetroButton("Excluir", onClick = { viewModel.deleteBle(s.id) }, filled = false, color = FlipperColors.Red)
                            }
                        }
                    }
                    SavedTab.WIFI -> {
                        if (wifi.isEmpty()) item { EmptyState("Sem snapshots Wi-Fi", "No Wi-Fi Analyzer, toque em Salvar.", icon = PixelIcons.WIFI) }
                        items(wifi, key = { it.id }) { s ->
                            RetroCard(Modifier.fillMaxWidth()) {
                                Text(s.ssid ?: "(SSID indisponível)", style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
                                InfoRow("Resumo", s.summary)
                                InfoRow("Redes visíveis", s.visibleNetworks.toString())
                                InfoRow("Salvo em", Formatters.timestamp(s.savedAt))
                                Spacer(Modifier.height(6.dp))
                                RetroButton("Excluir", onClick = { viewModel.deleteWifi(s.id) }, filled = false, color = FlipperColors.Red)
                            }
                        }
                    }
                    SavedTab.IR -> {
                        if (ir.isEmpty()) item { EmptyState("Sem controles personalizados", "Crie um controle em Infrared Remote.", icon = PixelIcons.INFRARED) }
                        items(ir, key = { it.id }) { cfg ->
                            RetroCard(Modifier.fillMaxWidth()) {
                                Text(cfg.name, style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
                                InfoRow("Protocolo", cfg.protocol.displayName)
                                InfoRow("Endereço", IrCodeParser.format(cfg.address))
                                InfoRow("Botões", cfg.commands.size.toString())
                                Spacer(Modifier.height(6.dp))
                                RetroButton("Excluir", onClick = { viewModel.deleteIr(cfg.id) }, filled = false, color = FlipperColors.Red)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = FlipperColors.Surface,
            title = { Text("Apagar dados ${tab.label}?", color = FlipperColors.Orange) },
            text = { Text("Esta ação remove permanentemente os dados locais desta aba.", color = FlipperColors.White) },
            confirmButton = { RetroButton("Apagar", onClick = { viewModel.clear(tab); confirmClear = false }, color = FlipperColors.Red) },
            dismissButton = { RetroButton("Cancelar", onClick = { confirmClear = false }, filled = false) },
        )
    }

    detail?.let { r ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = FlipperColors.Surface,
            title = { Text(r.label.ifBlank { r.tagType }, color = FlipperColors.Orange) },
            text = {
                LazyColumn(Modifier.height(420.dp)) {
                    item { MonoBlock(ExportFormatter.nfcReadingText(r), color = FlipperColors.White) }
                }
            },
            confirmButton = { RetroButton("Fechar", onClick = { detail = null }) },
        )
    }

    renaming?.let { r ->
        var text by remember(r.id) { mutableStateOf(r.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = FlipperColors.Surface,
            title = { Text("Renomear leitura", color = FlipperColors.Orange) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Rótulo") }) },
            confirmButton = { RetroButton("Salvar", onClick = { viewModel.renameNfc(r.id, text); renaming = null }) },
            dismissButton = { RetroButton("Cancelar", onClick = { renaming = null }, filled = false) },
        )
    }
}
