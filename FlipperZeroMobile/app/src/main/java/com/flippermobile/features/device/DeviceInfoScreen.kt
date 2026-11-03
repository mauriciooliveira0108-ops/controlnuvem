package com.flippermobile.features.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.domain.model.Compat
import com.flippermobile.domain.model.InfoItem
import com.flippermobile.ui.components.EmptyState
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroButton
import com.flippermobile.ui.components.RetroCard
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.components.SegmentedProgress
import com.flippermobile.ui.theme.FlipperColors

@Composable
fun DeviceInfoScreen(viewModel: DeviceInfoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    ScreenScaffold(
        title = "Device Information",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.DEVICE,
        actions = {
            HeaderAction("Atualizar", viewModel::load)
            HeaderAction("Exportar", viewModel::export, enabled = state is DeviceUiState.Ready)
        },
    ) {
        when (val s = state) {
            DeviceUiState.Loading -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Coletando informações...", color = FlipperColors.White)
                Spacer(Modifier.size(8.dp))
                SegmentedProgress(null)
            }
            is DeviceUiState.Error -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState("Erro", s.message)
                RetroButton("Tentar novamente", onClick = viewModel::load)
            }
            is DeviceUiState.Ready -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Legend() }
                items(s.sections, key = { it.title }) { section ->
                    RetroCard(Modifier.fillMaxWidth()) {
                        Text(section.title.uppercase(), style = MaterialTheme.typography.titleSmall, color = FlipperColors.Orange)
                        Spacer(Modifier.size(6.dp))
                        section.items.forEach { CompatRow(it) }
                    }
                }
            }
        }
    }
}

private fun Compat.color() = when (this) {
    Compat.SUPPORTED -> FlipperColors.Green
    Compat.PARTIAL -> FlipperColors.Yellow
    Compat.UNSUPPORTED -> FlipperColors.Red
    Compat.INFO -> FlipperColors.GrayDark
}

private fun Compat.label() = when (this) {
    Compat.SUPPORTED -> "compatível"
    Compat.PARTIAL -> "parcial"
    Compat.UNSUPPORTED -> "indisponível"
    Compat.INFO -> "informação"
}

@Composable
private fun CompatRow(item: InfoItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${item.label}: ${item.value}, ${item.compat.label()}" },
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 4.dp).size(8.dp).background(item.compat.color()))
        Spacer(Modifier.width(8.dp))
        Text(item.label, style = MaterialTheme.typography.bodySmall, color = FlipperColors.Gray, modifier = Modifier.weight(0.45f))
        Spacer(Modifier.width(8.dp))
        Text(item.value, style = MaterialTheme.typography.bodySmall, color = FlipperColors.White, modifier = Modifier.weight(0.55f), textAlign = TextAlign.End)
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Compat.SUPPORTED, Compat.PARTIAL, Compat.UNSUPPORTED).forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(c.color()))
                Spacer(Modifier.width(4.dp))
                Text(c.label(), style = MaterialTheme.typography.labelSmall, color = FlipperColors.Gray)
            }
        }
    }
}
