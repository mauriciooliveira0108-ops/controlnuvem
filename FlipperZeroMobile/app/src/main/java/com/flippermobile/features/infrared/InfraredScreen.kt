package com.flippermobile.features.infrared

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.domain.ir.IrButton
import com.flippermobile.domain.ir.IrCodeParser
import com.flippermobile.domain.ir.IrProtocol
import com.flippermobile.domain.ir.IrRemoteConfig
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.InfoRow
import com.flippermobile.ui.components.MessageBanner
import com.flippermobile.ui.components.PixelArt
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.RetroChip
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.components.SectionTitle
import com.flippermobile.ui.components.Tone
import com.flippermobile.ui.theme.FlipperColors

@Composable
private fun retroFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FlipperColors.Orange,
    unfocusedBorderColor = FlipperColors.OrangeDim,
    cursorColor = FlipperColors.Orange,
    focusedLabelColor = FlipperColors.Orange,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfraredScreen(viewModel: InfraredViewModel, onBack: () -> Unit) {
    val remotes by viewModel.remotes.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<IrRemoteConfig?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = "Infrared Remote",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.INFRARED,
        actions = { HeaderAction("Exportar", viewModel::exportSelected) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (!viewModel.hasEmitter) {
                    RetroCard(Modifier.fillMaxWidth(), borderColor = FlipperColors.Red) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelArt(PixelIcons.INFRARED, size = 40.dp, color = FlipperColors.Red)
                            Spacer(Modifier.size(10.dp))
                            Text("Emissor infravermelho indisponível neste aparelho", style = MaterialTheme.typography.titleSmall, color = FlipperColors.Red)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "O Android informa que não há emissor IR (ConsumerIrManager). A tela, o flash ou a câmera não substituem um emissor infravermelho. " +
                                "Você ainda pode criar e salvar configurações de controle, mas não é possível transmitir.",
                            style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray,
                        )
                    }
                } else {
                    RetroCard(Modifier.fillMaxWidth()) {
                        Text("EMISSOR IR DISPONÍVEL", style = MaterialTheme.typography.titleSmall, color = FlipperColors.Green)
                        InfoRow(
                            "Frequências suportadas",
                            viewModel.carrierRanges.joinToString("\n") { "${it.minHz}–${it.maxHz} Hz" }.ifBlank { "Não informadas pelo fabricante" },
                        )
                    }
                }
            }
            item {
                SectionTitle("Controle")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    remotes.forEach { r -> RetroChip(r.name, r.id == selected.id, { viewModel.select(r) }) }
                    RetroChip("+ Novo", false, {
                        editing = IrRemoteConfig(name = "", protocol = IrProtocol.NEC, address = 0, commands = emptyMap())
                    })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${selected.protocol.displayName} • ${selected.protocol.carrierHz} Hz • endereço ${IrCodeParser.format(selected.address)}" +
                        if (selected.builtIn) " • predefinição (códigos públicos; compatibilidade varia por modelo)" else "",
                    style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroButton(if (selected.builtIn) "Duplicar e editar" else "Editar", onClick = {
                        editing = if (selected.builtIn) selected.copy(id = 0, name = selected.name + " (cópia)", builtIn = false) else selected
                    }, filled = false)
                    if (!selected.builtIn) RetroButton("Excluir", onClick = { viewModel.deleteRemote(selected) }, filled = false, color = FlipperColors.Red)
                }
            }
            item { RemotePad(selected, enabled = viewModel.hasEmitter && !sending, onPress = viewModel::press) }
            status?.let { s -> item { MessageBanner(s, tone = if (s.startsWith("Enviado")) Tone.OK else Tone.WARN) } }
            item { CustomSender(enabled = viewModel.hasEmitter && !sending, onSend = viewModel::sendCustom) }
        }
    }

    editing?.let { cfg ->
        RemoteEditor(cfg, onDismiss = { editing = null }, onSave = { viewModel.saveRemote(it); editing = null })
    }
}

@Composable
private fun PadButton(label: String, button: IrButton, config: IrRemoteConfig, enabled: Boolean, onPress: (IrButton) -> Unit, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = FlipperColors.Orange) {
    RetroButton(
        label,
        onClick = { onPress(button) },
        enabled = enabled && config.commands.containsKey(button),
        modifier = modifier.widthIn(min = 64.dp),
        color = color,
    )
}

@Composable
private fun RemotePad(config: IrRemoteConfig, enabled: Boolean, onPress: (IrButton) -> Unit) {
    RetroCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton("Power", IrButton.POWER, config, enabled, onPress, color = FlipperColors.Red)
                PadButton("Mudo", IrButton.MUTE, config, enabled, onPress)
                PadButton("Input", IrButton.INPUT, config, enabled, onPress)
            }
            Spacer(Modifier.height(4.dp))
            PadButton("▲", IrButton.UP, config, enabled, onPress)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PadButton("◄", IrButton.LEFT, config, enabled, onPress)
                PadButton("OK", IrButton.OK, config, enabled, onPress)
                PadButton("►", IrButton.RIGHT, config, enabled, onPress)
            }
            PadButton("▼", IrButton.DOWN, config, enabled, onPress)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton("Menu", IrButton.MENU, config, enabled, onPress)
                PadButton("Voltar", IrButton.BACK, config, enabled, onPress)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PadButton("Vol +", IrButton.VOL_UP, config, enabled, onPress)
                    PadButton("Vol -", IrButton.VOL_DOWN, config, enabled, onPress)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PadButton("Ch +", IrButton.CH_UP, config, enabled, onPress)
                    PadButton("Ch -", IrButton.CH_DOWN, config, enabled, onPress)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomSender(enabled: Boolean, onSend: (IrProtocol, String, String) -> Unit) {
    var protocol by rememberSaveable { mutableStateOf(IrProtocol.NEC) }
    var address by rememberSaveable { mutableStateOf("0x04") }
    var command by rememberSaveable { mutableStateOf("0x08") }
    RetroCard(Modifier.fillMaxWidth()) {
        SectionTitle("Código manual")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IrProtocol.entries.forEach { p -> RetroChip(p.displayName, p == protocol, { protocol = p }) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(address, { address = it }, Modifier.weight(1f), label = { Text("Endereço") }, singleLine = true, colors = retroFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            OutlinedTextField(command, { command = it }, Modifier.weight(1f), label = { Text("Comando") }, singleLine = true, colors = retroFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        }
        Text(
            "Máx.: endereço ${IrCodeParser.format(protocol.maxAddress)}, comando ${IrCodeParser.format(protocol.maxCommand)}. Aceita 0x1F, 1Fh ou decimal.",
            style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray,
        )
        Spacer(Modifier.height(8.dp))
        RetroButton("Transmitir", onClick = { onSend(protocol, address, command) }, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteEditor(initial: IrRemoteConfig, onDismiss: () -> Unit, onSave: (IrRemoteConfig) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var protocol by remember { mutableStateOf(initial.protocol) }
    var address by remember { mutableStateOf(IrCodeParser.format(initial.address)) }
    val fields = remember {
        mutableStateMapOf<IrButton, String>().apply {
            IrButton.entries.forEach { b -> put(b, initial.commands[b]?.let { IrCodeParser.format(it) } ?: "") }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlipperColors.Surface,
        title = { Text(if (initial.id > 0) "Editar controle" else "Novo controle", color = FlipperColors.Orange) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome") }, singleLine = true, colors = retroFieldColors())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IrProtocol.entries.forEach { p -> RetroChip(p.displayName, p == protocol, { protocol = p }) }
                }
                OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Endereço") }, singleLine = true, colors = retroFieldColors())
                Text("Comandos (deixe vazio para não usar):", style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
                IrButton.entries.forEach { b ->
                    OutlinedTextField(
                        fields[b] ?: "", { fields[b] = it }, Modifier.fillMaxWidth(),
                        label = { Text(b.label) }, singleLine = true, colors = retroFieldColors(),
                    )
                }
                error?.let { Text(it, color = FlipperColors.Red, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            RetroButton("Salvar", onClick = {
                val addr = IrCodeParser.parseCode(address)
                if (name.isBlank()) { error = "Informe um nome"; return@RetroButton }
                if (addr == null || addr !in 0..protocol.maxAddress) { error = "Endereço inválido (0..${IrCodeParser.format(protocol.maxAddress)})"; return@RetroButton }
                val cmds = LinkedHashMap<IrButton, Int>()
                for ((b, text) in fields) {
                    if (text.isBlank()) continue
                    val v = IrCodeParser.parseCode(text)
                    if (v == null || v !in 0..protocol.maxCommand) { error = "Comando inválido em ${b.label}"; return@RetroButton }
                    cmds[b] = v
                }
                onSave(initial.copy(name = name.trim(), protocol = protocol, address = addr, commands = cmds, builtIn = false))
            })
        },
        dismissButton = { RetroButton("Cancelar", onClick = onDismiss, filled = false) },
    )
}
