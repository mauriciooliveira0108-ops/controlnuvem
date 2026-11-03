package com.flippermobile.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.BuildConfig
import com.flippermobile.core.permissions.Permissions
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.components.SectionTitle
import com.flippermobile.ui.theme.FlipperColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ScreenScaffold(title = "Settings", onBack = onBack, icon = PixelIcons.SETTINGS) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Interface")
                    ToggleRow("Feedback tátil", if (viewModel.hapticsAvailable) "Vibração curta em ações" else "Vibrador não detectado", s.hapticsEnabled, viewModel::setHaptics)
                    ToggleRow("Linhas de varredura (CRT)", "Efeito visual retrô sobre a tela", s.scanlinesEnabled, viewModel::setScanlines)
                }
            }
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Bluetooth")
                    StepSlider("Tempo limite da varredura", s.bleScanTimeoutSeconds, 5, 120, "s", viewModel::setBleTimeout)
                    ToggleRow("Mostrar dispositivos sem nome", "Padrão do filtro ao abrir o scanner", s.bleShowUnnamed, viewModel::setBleShowUnnamed)
                }
            }
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Terminal")
                    StepSlider("Logs mantidos", s.logRetention, 500, 10_000, "", viewModel::setLogRetention, step = 500)
                    ToggleRow("Registrar eventos de depuração", "Inclui cada dispositivo BLE e eventos detalhados", s.debugLogs, viewModel::setDebugLogs)
                }
            }
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Privacidade e permissões")
                    Text(
                        "Todos os dados ficam no armazenamento local do app. O app não solicita acesso à internet; exportações só saem do aparelho quando você escolhe um destino.",
                        style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray,
                    )
                    Spacer(Modifier.width(8.dp))
                    RetroButton("Permissões do app", onClick = { Permissions.openAppSettings(context) }, filled = false)
                }
            }
            item {
                RetroCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Sobre")
                    Text("Flipper Zero Mobile ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = FlipperColors.White)
                    Text(
                        "Projeto independente inspirado na estética de multiferramentas de hardware. Não afiliado à Flipper Devices Inc. " +
                            "Usa apenas o hardware embutido do celular via APIs públicas do Android.",
                        style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = FlipperColors.White)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FlipperColors.Black,
                checkedTrackColor = FlipperColors.Orange,
                uncheckedThumbColor = FlipperColors.Gray,
                uncheckedTrackColor = FlipperColors.SurfaceHigh,
            ),
        )
    }
}

@Composable
private fun StepSlider(title: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit, step: Int = 5) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Text("$title: ${local.roundToInt()}$unit", style = MaterialTheme.typography.bodyMedium, color = FlipperColors.White)
    Slider(
        value = local,
        onValueChange = { local = ((it / step).roundToInt() * step).toFloat().coerceIn(min.toFloat(), max.toFloat()) },
        onValueChangeFinished = { onChange(local.roundToInt()) },
        valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(thumbColor = FlipperColors.Orange, activeTrackColor = FlipperColors.Orange, inactiveTrackColor = FlipperColors.GrayDark),
    )
}
