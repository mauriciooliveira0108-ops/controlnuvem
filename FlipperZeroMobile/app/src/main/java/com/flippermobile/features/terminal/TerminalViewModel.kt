package com.flippermobile.features.terminal

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.BuildConfig
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.log.LogFilter
import com.flippermobile.domain.log.TerminalCommand
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TerminalFilter(
    val query: String = "",
    val categories: Set<LogCategory> = emptySet(),
    val minLevel: LogLevel = LogLevel.DEBUG,
)

class TerminalViewModel(private val c: AppContainer) : ViewModel() {

    private val _filter = MutableStateFlow(TerminalFilter())
    val filter: StateFlow<TerminalFilter> = _filter

    /** Saída local de comandos (não persistida). */
    private val _output = MutableStateFlow<List<String>>(listOf("Digite 'help' para ver os comandos disponíveis."))
    val output: StateFlow<List<String>> = _output

    @OptIn(ExperimentalCoroutinesApi::class)
    private val raw = c.settings.settings.map { it.logRetention }
        .flatMapLatest { c.logRepository.recent(it) }

    val logs: StateFlow<List<LogEntry>> = combine(raw, _filter) { list, f ->
        LogFilter.filter(list, f.query, f.categories, f.minLevel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    fun setQuery(q: String) { _filter.value = _filter.value.copy(query = q) }

    fun toggleCategory(cat: LogCategory) {
        val cur = _filter.value.categories
        _filter.value = _filter.value.copy(categories = if (cat in cur) cur - cat else cur + cat)
    }

    fun setMinLevel(level: LogLevel) { _filter.value = _filter.value.copy(minLevel = level) }

    fun clear() {
        viewModelScope.launch {
            c.logRepository.clear()
            _output.value = listOf("Terminal limpo.")
            c.logger.i(LogCategory.APP, "Logs apagados pelo usuário")
        }
    }

    fun export() {
        viewModelScope.launch {
            val all = c.logRepository.all()
            if (all.isEmpty()) {
                _messages.tryEmit("Sem logs para exportar")
                return@launch
            }
            c.exporter.export("logs", "txt", "text/plain", "Exportar logs", ExportFormatter.logsText(all))
                .onFailure { _messages.tryEmit("Falha na exportação: ${it.message}") }
        }
    }

    fun execute(input: String) {
        val cmd = TerminalCommand.parse(input)
        if (cmd != TerminalCommand.Empty) emitLine("> ${input.trim()}")
        when (cmd) {
            TerminalCommand.Empty -> Unit
            TerminalCommand.Help -> TerminalCommand.HELP_TEXT.forEach { emitLine(it) }
            TerminalCommand.Clear -> clear()
            TerminalCommand.Export -> export()
            TerminalCommand.Status -> {
                c.statusMonitor.refresh()
                val s = c.statusMonitor.status.value
                emitLine("NFC=${s.nfc} BT=${s.bluetooth} WIFI=${s.wifi} IR=${s.ir}")
                emitLine("Bateria=${if (s.batteryPercent >= 0) "${s.batteryPercent}%" else "?"}${if (s.charging) " (carregando)" else ""} Permissões=${s.permissionsGranted}/${s.permissionsTotal}")
            }
            TerminalCommand.Version -> emitLine("Flipper Zero Mobile ${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            is TerminalCommand.Search -> {
                setQuery(cmd.query)
                emitLine(if (cmd.query.isBlank()) "Filtro removido" else "Filtro: '${cmd.query}'")
            }
            is TerminalCommand.Echo -> {
                c.logger.i(LogCategory.APP, "Nota: ${cmd.text}")
                emitLine("Anotado.")
            }
            is TerminalCommand.Unknown -> emitLine("Comando desconhecido: '${cmd.input}'. Este terminal não executa comandos do sistema; use 'help'.")
        }
    }

    private fun emitLine(line: String) {
        _output.value = (_output.value + line).takeLast(50)
    }
}
