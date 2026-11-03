package com.flippermobile.features.wifi

import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.core.permissions.Permissions
import com.flippermobile.core.util.Formatters
import com.flippermobile.domain.model.WifiConnectionInfo
import com.flippermobile.domain.model.WifiScanResultInfo
import com.flippermobile.domain.permissions.Feature
import com.flippermobile.domain.wifi.WifiMath
import com.flippermobile.ui.components.EmptyState
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.InfoRow
import com.flippermobile.ui.components.MessageBanner
import com.flippermobile.ui.components.PermissionCard
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.components.SectionTitle
import com.flippermobile.ui.components.SegmentedProgress
import com.flippermobile.ui.components.SignalBars
import com.flippermobile.ui.components.Tone
import com.flippermobile.ui.components.rememberFeaturePermission
import com.flippermobile.ui.theme.FlipperColors

@Composable
fun WifiScreen(viewModel: WifiViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val conn by viewModel.connection.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val env by viewModel.env.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val perm = rememberFeaturePermission(Feature.WIFI_SCAN) { viewModel.onVisible() }

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = "Wi-Fi Analyzer",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.WIFI,
        actions = {
            HeaderAction("Salvar", viewModel::saveSnapshot)
            HeaderAction("Exportar", viewModel::export)
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!env.supported) {
                item { EmptyState("Wi-Fi indisponível", "Este aparelho não informa suporte a Wi-Fi.") }
                return@LazyColumn
            }
            if (!perm.granted) item { PermissionCard(perm, "Permissões para SSID e varredura") }
            if (!env.enabled) item {
                MessageBanner("Wi-Fi desligado.", tone = Tone.BAD) {
                    RetroButton("Abrir", onClick = { Permissions.openSettings(context, Settings.ACTION_WIFI_SETTINGS) })
                }
            }
            if (env.locationGranted && !env.locationEnabled) item {
                MessageBanner("Localização do sistema desligada: o Android oculta SSID e resultados de varredura.", tone = Tone.WARN) {
                    RetroButton("Ativar", onClick = { Permissions.openSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) })
                }
            }
            item { ConnectionCard(conn) }
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Redes visíveis: ${scan.results.size}", style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
                            scan.lastUpdated?.let {
                                Text(
                                    "Atualizado ${Formatters.timeOnly(it)}${if (scan.fromCache) " (cache do sistema)" else ""}",
                                    style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray,
                                )
                            }
                        }
                        RetroButton(
                            "Varrer",
                            onClick = { if (perm.granted) viewModel.scan() else perm.request() },
                            enabled = !scan.scanning,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SegmentedProgress(if (scan.scanning) null else 0f)
                    Spacer(Modifier.height(6.dp))
                    Text("Throttling: ${env.throttleInfo}", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
                }
            }
            scan.message?.let { m -> item { MessageBanner(m, tone = Tone.WARN) } }
            if (scan.results.isNotEmpty()) item { SectionTitle("Redes encontradas") }
            items(scan.results) { NetworkRow(it, it.bssid == conn?.bssid) }
            item {
                SectionTitle("Limitações do Android")
                Text(
                    "• SSID/BSSID exigem permissão de localização precisa e localização ativa.\n" +
                        "• Android 9+: até 4 varreduras a cada 2 minutos em primeiro plano.\n" +
                        "• Apps não podem forçar varreduras em segundo plano nem ler o tráfego da rede.\n" +
                        "• Velocidade e padrão Wi-Fi dependem do que o driver informa.",
                    style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray,
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(c: WifiConnectionInfo?) {
    RetroCard(Modifier.fillMaxWidth(), borderColor = if (c?.connected == true) FlipperColors.Orange else FlipperColors.OrangeDim) {
        Text("CONEXÃO ATUAL", style = MaterialTheme.typography.titleSmall, color = FlipperColors.Orange)
        Spacer(Modifier.height(6.dp))
        if (c == null) {
            Text("Carregando...", color = FlipperColors.Gray, style = MaterialTheme.typography.bodySmall)
            return@RetroCard
        }
        if (!c.connected) {
            Text("Não conectado a uma rede Wi-Fi.", color = FlipperColors.Gray, style = MaterialTheme.typography.bodySmall)
            return@RetroCard
        }
        InfoRow("SSID", c.ssid ?: "Oculto (${c.ssidHiddenReason})", valueColor = if (c.ssid != null) FlipperColors.White else FlipperColors.Yellow)
        c.bssid?.let { InfoRow("BSSID", it) }
        c.rssi?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoRow("Sinal", "$it dBm • ${WifiMath.signalQuality(it)}", modifier = Modifier.weight(1f))
                Spacer(Modifier.height(0.dp))
                SignalBars(WifiMath.signalLevel(it))
            }
        }
        c.frequencyMhz?.let { InfoRow("Frequência", "$it MHz") }
        c.band?.let { InfoRow("Banda", it) }
        c.channel?.let { InfoRow("Canal", it.toString()) }
        c.standard?.let { InfoRow("Padrão", it) }
        c.linkSpeedMbps?.let { InfoRow("Velocidade do link", "$it Mbps") }
        c.txSpeedMbps?.let { InfoRow("TX", "$it Mbps") }
        c.rxSpeedMbps?.let { InfoRow("RX", "$it Mbps") }
        SectionTitle("Rede local")
        InfoRow("IPv4", c.ipv4.joinToString().ifBlank { "—" })
        if (c.ipv6.isNotEmpty()) InfoRow("IPv6", c.ipv6.joinToString("\n"))
        InfoRow("Gateway", c.gateway ?: "—")
        InfoRow("DNS", c.dns.joinToString().ifBlank { "—" })
        c.interfaceName?.let { InfoRow("Interface", it) }
        c.hasInternet?.let { InfoRow("Capacidade de internet", Formatters.yesNo(it)) }
        c.isValidated?.let { InfoRow("Validada pelo sistema", Formatters.yesNo(it)) }
        c.isMetered?.let { InfoRow("Tarifada", Formatters.yesNo(it)) }
    }
}

@Composable
private fun NetworkRow(n: WifiScanResultInfo, current: Boolean) {
    RetroCard(Modifier.fillMaxWidth(), borderColor = if (current) FlipperColors.Orange else FlipperColors.GrayDark) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    n.ssid.ifBlank { "(SSID oculto)" } + if (current) "  ◄ atual" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (n.ssid.isBlank()) FlipperColors.Gray else FlipperColors.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("${n.bssid} • ${n.band} • ch ${n.channel ?: "?"}", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
                Text(n.security, style = MaterialTheme.typography.labelSmall, color = if (n.security == "Aberta") FlipperColors.Yellow else FlipperColors.Orange)
            }
            Column(horizontalAlignment = Alignment.End) {
                SignalBars(WifiMath.signalLevel(n.rssi))
                Text("${n.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = FlipperColors.White)
            }
        }
    }
}
