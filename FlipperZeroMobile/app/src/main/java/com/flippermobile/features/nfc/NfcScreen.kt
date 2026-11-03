package com.flippermobile.features.nfc

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.core.permissions.Permissions
import com.flippermobile.core.util.Formatters
import com.flippermobile.core.util.HexUtils
import com.flippermobile.domain.model.NfcReading
import com.flippermobile.ui.components.DolphinMascot
import com.flippermobile.ui.components.EmptyState
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.InfoRow
import com.flippermobile.ui.components.MessageBanner
import com.flippermobile.ui.components.MonoBlock
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.components.SectionTitle
import com.flippermobile.ui.components.SegmentedProgress
import com.flippermobile.ui.components.Tone
import com.flippermobile.ui.theme.FlipperColors

@Composable
fun NfcScreen(viewModel: NfcViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active by viewModel.readerActive.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.setScreenActive(true)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = "NFC Tools",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.NFC,
        actions = { HeaderAction("Exportar", viewModel::exportHistory, enabled = history.isNotEmpty()) },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                when {
                    !state.supported -> EmptyState(
                        "NFC indisponível",
                        "Este aparelho não possui controlador NFC. A leitura de tags não é possível.",
                    )
                    !state.enabled -> MessageBanner("O NFC está desligado. Ative-o nas configurações do sistema.", tone = Tone.BAD) {
                        RetroButton("Ativar", onClick = { Permissions.openSettings(context, Settings.ACTION_NFC_SETTINGS) })
                    }
                    else -> ScanCard(active, state.processing)
                }
            }
            state.error?.let { err -> item { MessageBanner(err, tone = Tone.BAD) } }
            state.lastReading?.let { reading ->
                item {
                    ReadingCard(
                        reading = reading,
                        saved = state.lastSaved,
                        onSave = viewModel::saveLast,
                        onExport = viewModel::exportLast,
                    )
                }
            }
            if (history.isNotEmpty()) {
                item { SectionTitle("Histórico recente (${history.size})") }
                items(history.take(10), key = { it.id }) { r ->
                    RetroCard(Modifier.fillMaxWidth()) {
                        Text(r.label.ifBlank { r.tagType }, style = MaterialTheme.typography.titleSmall, color = FlipperColors.White)
                        InfoRow("UID", r.uidHex.ifBlank { "—" })
                        InfoRow("Data", Formatters.timestamp(r.timestamp))
                        InfoRow("NDEF", "${r.ndefRecords.size} registro(s)")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanCard(active: Boolean, processing: Boolean) {
    RetroCard(Modifier.fillMaxWidth(), borderColor = if (active) FlipperColors.Orange else FlipperColors.OrangeDim) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DolphinMascot(size = 72.dp, animated = active)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    processing -> "Lendo tag..."
                    active -> "Aproxime uma tag NFC da parte traseira do aparelho"
                    else -> "Preparando leitor..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = FlipperColors.White,
            )
            Spacer(Modifier.height(8.dp))
            SegmentedProgress(progress = if (active || processing) null else 0f)
            Spacer(Modifier.height(6.dp))
            Text(
                "Suporte depende do chip NFC do aparelho (NfcA/B/F/V, ISO-DEP, NDEF).",
                style = MaterialTheme.typography.labelSmall,
                color = FlipperColors.Gray,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingCard(reading: NfcReading, saved: Boolean, onSave: () -> Unit, onExport: () -> Unit) {
    RetroCard(Modifier.fillMaxWidth(), borderColor = FlipperColors.Orange) {
        Text("TAG LIDA", style = MaterialTheme.typography.titleSmall, color = FlipperColors.Orange)
        Spacer(Modifier.height(6.dp))
        InfoRow("Tipo", reading.tagType)
        InfoRow("UID", reading.uidHex.ifBlank { "Indisponível (UID aleatório/oculto)" })
        InfoRow("Tecnologias", reading.technologies.joinToString())
        reading.maxSize?.let { InfoRow("NDEF máx.", "$it bytes") }
        reading.isWritable?.let { InfoRow("Gravável", Formatters.yesNo(it)) }
        reading.extraInfo.forEach { (k, v) -> InfoRow(k, v) }

        if (reading.ndefRecords.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Nenhuma mensagem NDEF encontrada.", style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray)
        }
        reading.ndefRecords.forEachIndexed { i, rec ->
            SectionTitle("NDEF #${i + 1} • ${rec.kind}")
            InfoRow("TNF", rec.tnfName)
            if (rec.typeText.isNotBlank()) InfoRow("Tipo", rec.typeText)
            rec.interpreted?.let {
                Text("UTF-8:", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
                MonoBlock(it, color = FlipperColors.White)
            }
            Text("HEX:", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
            MonoBlock(rec.payloadHex.ifBlank { "(vazio)" })
        }
        if (reading.rawHex.isNotBlank()) {
            SectionTitle("Memória / bruto")
            val bytes = HexUtils.fromHex(reading.rawHex)
            MonoBlock(if (bytes != null) HexUtils.hexDump(bytes) else reading.rawHex)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton(if (saved) "Salvo" else "Salvar", onClick = onSave, enabled = !saved)
            RetroButton("Exportar", onClick = onExport, filled = false)
        }
    }
}
