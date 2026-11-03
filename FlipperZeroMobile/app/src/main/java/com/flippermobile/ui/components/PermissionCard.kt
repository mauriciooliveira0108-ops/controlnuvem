package com.flippermobile.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.flippermobile.core.permissions.Permissions
import com.flippermobile.domain.permissions.Feature
import com.flippermobile.domain.permissions.PermissionRequirement
import com.flippermobile.ui.theme.FlipperColors

class FeaturePermissionState(
    val missing: List<PermissionRequirement>,
    val deniedOnce: Boolean,
    val request: () -> Unit,
) {
    val granted: Boolean get() = missing.isEmpty()
}

/** Estado de permissões de um recurso, reavaliado ao voltar para o app (ex.: após Configurações). */
@Composable
fun rememberFeaturePermission(feature: Feature, onResult: (granted: Boolean) -> Unit = {}): FeaturePermissionState {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var deniedOnce by rememberSaveable(feature) { mutableStateOf(false) }
    LifecycleResumeEffect(feature) {
        version++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result.values.all { it }
        if (!ok) deniedOnce = true
        version++
        onResult(ok)
    }
    val missing = remember(version, feature) { Permissions.missing(context, feature) }
    return FeaturePermissionState(missing, deniedOnce) {
        launcher.launch(missing.map { it.permission }.toTypedArray())
    }
}

@Composable
fun PermissionCard(state: FeaturePermissionState, title: String, modifier: Modifier = Modifier) {
    if (state.granted) return
    val context = LocalContext.current
    RetroCard(modifier.fillMaxWidth(), borderColor = FlipperColors.Yellow) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = FlipperColors.Yellow)
        Spacer(Modifier.height(6.dp))
        state.missing.forEach {
            Text("• ${it.permission.substringAfterLast('.')}", style = MaterialTheme.typography.bodySmall, color = FlipperColors.White)
            Text("  ${it.rationale}", style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton("Conceder", onClick = state.request)
            if (state.deniedOnce) {
                RetroButton("Configurações", onClick = { Permissions.openAppSettings(context) }, filled = false)
            }
        }
        if (state.deniedOnce) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Se o sistema não exibir o pedido novamente, conceda manualmente em Configurações > Apps.",
                style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray,
            )
        }
    }
}
