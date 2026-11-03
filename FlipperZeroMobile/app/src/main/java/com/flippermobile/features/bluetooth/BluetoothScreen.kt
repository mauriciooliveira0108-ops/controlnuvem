package com.flippermobile.features.bluetooth

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.core.permissions.Permissions
import com.flippermobile.domain.ble.BleParsing
import com.flippermobile.domain.ble.BleSort
import com.flippermobile.domain.model.BleDeviceInfo
import com.flippermobile.domain.permissions.Feature
import com.flippermobile.domain.wifi.WifiMath
import com.flippermobile.hardware.ble.BleScanState
import com.flippermobile.hardware.ble.GattState
import com.flippermobile.ui.components.EmptyState
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.InfoRow
import com.flippermobile.ui.components.MessageBanner
import com.flippermobile.ui.components.MonoBlock
import com.flippermobile.ui.components.PermissionCard
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.RetroChip
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.components.SectionTitle
import com.flippermobile.ui.components.SegmentedProgress
import com.flippermobile.ui.components.SignalBars
import com.flippermobile.ui.components.Tone
import com.flippermobile.ui.components.rememberFeaturePermission
import com.flippermobile.ui.theme.FlipperColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BluetoothScreen(viewModel: BluetoothViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val total by viewModel.allCount.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scanPerm = rememberFeaturePermission(Feature.BLUETOOTH_SCAN) { granted -> if (granted) viewModel.startScan() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshEnabled()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val scanning = scanState is BleScanState.Scanning

    ScreenScaffold(
        title = "Bluetooth Scanner",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.BLUETOOTH,
        actions = {
            HeaderAction("Salvar", viewModel::saveResults, enabled = devices.isNotEmpty())
            HeaderAction("CSV", viewModel::exportResults, enabled = devices.isNotEmpty())
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!viewModel.supported) {
                item { EmptyState("Bluetooth LE indisponível", "Este aparelho não suporta Bluetooth Low Energy.") }
                return@LazyColumn
            }
            if (!scanPerm.granted) item { PermissionCard(scanPerm, "Permissões necessárias para a varredura BLE") }
            if (!enabled) item {
                MessageBanner("Bluetooth desligado.", tone = Tone.BAD) {
                    RetroButton("Abrir", onClick = { Permissions.openSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) })
                }
            }
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (val s = scanState) {
                                    is BleScanState.Scanning -> "Varredura em andamento"
                                    is BleScanState.Finished -> "Concluída: ${s.reason}"
                                    is BleScanState.Failed -> "Erro: ${s.message}"
                                    BleScanState.Idle -> "Pronto para varrer"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (scanState is BleScanState.Failed) FlipperColors.Red else FlipperColors.White,
                            )
                            Text("Exibindo ${devices.size} de $total", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
                        }
                        if (scanning) RetroButton("Parar", onClick = viewModel::stopScan, color = FlipperColors.Red)
                        else RetroButton(
                            "Varrer",
                            onClick = { if (scanPerm.granted) viewModel.startScan() else scanPerm.request() },
                            enabled = enabled,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val s = scanState
                    if (s is BleScanState.Scanning) {
                        val progress by produceState(0f, s) {
                            while (true) {
                                value = ((System.currentTimeMillis() - s.startedAt).toFloat() / s.timeoutMs).coerceIn(0f, 1f)
                                delay(250)
                            }
                        }
                        SegmentedProgress(progress)
                    } else {
                        SegmentedProgress(0f)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Filtrar por nome, endereço ou UUID") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FlipperColors.Orange,
                        unfocusedBorderColor = FlipperColors.OrangeDim,
                        cursorColor = FlipperColors.Orange,
                        focusedLabelColor = FlipperColors.Orange,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RetroChip("Com nome", filter.namedOnly, viewModel::toggleNamed)
                    RetroChip("Conectáveis", filter.connectableOnly, viewModel::toggleConnectable)
                    BleSort.entries.forEach { s -> RetroChip("↓ ${s.label}", filter.sort == s, { viewModel.setSort(s) }) }
                    RetroChip("Limpar", false, viewModel::clear)
                }
            }
            if (devices.isEmpty()) {
                item {
                    EmptyState(
                        if (scanning) "Procurando..." else "Nenhum dispositivo",
                        if (scanning) "Dispositivos BLE anunciando por perto aparecerão aqui."
                        else "Inicie uma varredura. O Android limita 5 inícios de varredura a cada 30 segundos.",
                        icon = PixelIcons.BLUETOOTH,
                    )
                }
            }
            items(devices, key = { it.address }) { d -> DeviceRow(d) { viewModel.select(d.address) } }
        }
    }

    selected?.let { address ->
        DeviceDialog(viewModel, address, onDismiss = { viewModel.select(null) })
    }
}

@Composable
private fun DeviceRow(d: BleDeviceInfo, onClick: () -> Unit) {
    RetroCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name ?: "(sem nome)", style = MaterialTheme.typography.titleSmall, color = if (d.name != null) FlipperColors.White else FlipperColors.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(d.address, style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray)
                val company = d.manufacturerData.keys.firstNotNullOfOrNull { BleParsing.companyName(it) }
                if (company != null) Text("Fabricante: $company", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Orange)
            }
            Column(horizontalAlignment = Alignment.End) {
                SignalBars(WifiMath.signalLevel(d.rssi + 10))
                Text("${d.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = FlipperColors.White)
            }
        }
    }
}

@Composable
private fun DeviceDialog(viewModel: BluetoothViewModel, address: String, onDismiss: () -> Unit) {
    val gattState by viewModel.gattState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val device = devices.firstOrNull { it.address == address } ?: viewModel.device(address)
    val connectPerm = rememberFeaturePermission(Feature.BLUETOOTH_CONNECT) { if (it) viewModel.connectGatt(address) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlipperColors.Surface,
        title = { Text(device?.name ?: "(sem nome)", style = MaterialTheme.typography.titleMedium, color = FlipperColors.Orange) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (device == null) {
                    Text("Dispositivo fora da lista atual.", color = FlipperColors.Gray)
                } else {
                    InfoRow("Endereço", device.address)
                    InfoRow("RSSI", "${device.rssi} dBm")
                    device.txPower?.let { InfoRow("Potência TX", "$it dBm") }
                    InfoRow("Conectável", device.connectable?.let { if (it) "Sim" else "Não" } ?: "—")
                    InfoRow("Tipo", device.deviceType)
                    InfoRow("Pareamento", device.bondState)
                    if (device.serviceUuids.isNotEmpty()) {
                        SectionTitle("Serviços anunciados")
                        device.serviceUuids.forEach { Text("${BleParsing.gattServiceName(it)}\n$it", style = MaterialTheme.typography.bodySmall, color = FlipperColors.White) }
                    }
                    if (device.manufacturerData.isNotEmpty()) {
                        SectionTitle("Dados do fabricante")
                        device.manufacturerData.forEach { (id, hex) ->
                            InfoRow(String.format("0x%04X %s", id, BleParsing.companyName(id) ?: ""), "")
                            MonoBlock(hex)
                        }
                    }
                    val ad = BleParsing.parseAdvertisement(com.flippermobile.core.util.HexUtils.fromHex(device.rawAdvertisementHex))
                    if (ad.isNotEmpty()) {
                        SectionTitle("Estruturas AD")
                        ad.forEach { InfoRow(it.typeName, it.dataHex) }
                    }
                    SectionTitle("GATT")
                    val gs = gattState
                    val forThis = when (gs) {
                        is GattState.Connecting -> gs.address == address
                        is GattState.Discovering -> gs.address == address
                        is GattState.Discovered -> gs.address == address
                        is GattState.Failed -> gs.address == address
                        GattState.Idle -> false
                    }
                    if (!forThis || gs is GattState.Idle) {
                        Text(
                            "A conexão só é iniciada quando você tocar em Conectar. Serviços são listados e a conexão é encerrada em seguida.",
                            style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray,
                        )
                    } else when (gs) {
                        is GattState.Connecting -> { Text("Conectando...", color = FlipperColors.White); SegmentedProgress(null) }
                        is GattState.Discovering -> { Text("Descobrindo serviços...", color = FlipperColors.White); SegmentedProgress(null) }
                        is GattState.Failed -> Text(gs.message, color = FlipperColors.Red, style = MaterialTheme.typography.bodySmall)
                        is GattState.Discovered -> {
                            if (gs.services.isEmpty()) Text("Nenhum serviço exposto.", color = FlipperColors.Gray)
                            gs.services.forEach { svc ->
                                Text(svc.name, style = MaterialTheme.typography.titleSmall, color = FlipperColors.Orange, modifier = Modifier.padding(top = 6.dp))
                                Text(svc.uuid, style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
                                svc.characteristics.forEach { ch ->
                                    Text("  • ${ch.name} [${ch.properties.joinToString("|")}]", style = MaterialTheme.typography.bodySmall, color = FlipperColors.White)
                                }
                            }
                        }
                        GattState.Idle -> Unit
                    }
                    if (!connectPerm.granted) {
                        Spacer(Modifier.height(8.dp))
                        PermissionCard(connectPerm, "Permissão para conectar")
                    }
                }
            }
        },
        confirmButton = {
            val busy = gattState is GattState.Connecting || gattState is GattState.Discovering
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton(
                    if (busy) "..." else "Conectar",
                    onClick = { if (connectPerm.granted) viewModel.connectGatt(address) else connectPerm.request() },
                    enabled = !busy && device?.connectable != false,
                )
                RetroButton("Fechar", onClick = onDismiss, filled = false)
            }
        },
    )
}

