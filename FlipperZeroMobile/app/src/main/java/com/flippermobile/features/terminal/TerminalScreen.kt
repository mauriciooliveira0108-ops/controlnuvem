package com.flippermobile.features.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.core.util.Formatters
import com.flippermobile.domain.log.LogFilter
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.LogLevel
import com.flippermobile.ui.components.HeaderAction
import com.flippermobile.ui.components.PixelIcons
import com.flippermobile.ui.components.RetroChip
import com.flippermobile.ui.components.RetroShape
import com.flippermobile.ui.components.ScreenScaffold
import com.flippermobile.ui.theme.FlipperColors

private fun LogLevel.color(): Color = when (this) {
    LogLevel.DEBUG -> FlipperColors.Gray
    LogLevel.INFO -> FlipperColors.Green
    LogLevel.WARN -> FlipperColors.Yellow
    LogLevel.ERROR -> FlipperColors.Red
}

@Composable
fun TerminalScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var follow by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(logs.size, output.size, follow) {
        val total = logs.size + output.size
        if (follow && total > 0) listState.scrollToItem(total - 1)
    }

    ScreenScaffold(
        title = "Activity Terminal",
        onBack = onBack,
        snackbarHostState = snackbar,
        icon = PixelIcons.TERMINAL,
        actions = {
            HeaderAction(if (follow) "Seguir ✓" else "Seguir", { follow = !follow })
            HeaderAction("Limpar", viewModel::clear)
            HeaderAction("Exportar", viewModel::export)
        },
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LogLevel.entries.forEach { l -> RetroChip("≥${l.name}", filter.minLevel == l, { viewModel.setMinLevel(l) }) }
                LogCategory.entries.forEach { cat -> RetroChip(cat.tag, cat in filter.categories, { viewModel.toggleCategory(cat) }) }
            }
            Spacer(Modifier.padding(4.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RetroShape)
                    .border(1.dp, FlipperColors.OrangeDim, RetroShape),
                contentPadding = PaddingValues(8.dp),
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            if (filter.query.isNotBlank() || filter.categories.isNotEmpty()) "Nenhum log corresponde ao filtro." else "Sem eventos registrados.",
                            color = FlipperColors.Gray, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(logs, key = { it.id }) { LogLine(it) }
                items(output) { line ->
                    Text(line, color = FlipperColors.Orange, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.padding(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RetroShape)
                    .border(1.dp, FlipperColors.Orange, RetroShape)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(">", color = FlipperColors.Orange, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = FlipperColors.White),
                    cursorBrush = SolidColor(FlipperColors.Orange),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.execute(input)
                        input = ""
                        follow = true
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Entrada de comando do terminal" },
                )
            }
        }
    }
}

@Composable
private fun LogLine(e: LogEntry) {
    Text(
        "${Formatters.timeOnly(e.timestamp)} ${LogFilter.levelSymbol(e.level)}/${e.category.tag}: ${e.message}",
        color = e.level.color(),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
