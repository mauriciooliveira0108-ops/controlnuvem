package com.flippermobile.features.nfc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.NfcReading
import com.flippermobile.hardware.nfc.TagReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NfcUiState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val processing: Boolean = false,
    val lastReading: NfcReading? = null,
    val lastSaved: Boolean = false,
    val error: String? = null,
)

class NfcViewModel(private val c: AppContainer) : ViewModel() {
    private val controller = c.nfcController

    private val _state = MutableStateFlow(NfcUiState(supported = controller.isSupported, enabled = controller.isEnabled()))
    val state: StateFlow<NfcUiState> = _state.asStateFlow()

    val readerActive: StateFlow<Boolean> = controller.readerActive

    val history: StateFlow<List<NfcReading>> = c.nfcRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            controller.tags.collect { tag ->
                _state.update { it.copy(processing = true, error = null) }
                try {
                    val reading = withContext(Dispatchers.IO) { TagReader.read(tag) }
                    _state.update { it.copy(processing = false, lastReading = reading, lastSaved = false) }
                    c.haptics.success()
                    c.logger.i(
                        LogCategory.NFC,
                        "Leitura: ${reading.tagType} UID=${reading.uidHex.ifBlank { "?" }} NDEF=${reading.ndefRecords.size} registro(s)"
                    )
                } catch (e: Exception) {
                    c.haptics.error()
                    c.logger.e(LogCategory.NFC, "Falha ao ler tag", e)
                    _state.update { it.copy(processing = false, error = "Falha na leitura: ${e.message ?: e.javaClass.simpleName}. Mantenha a tag encostada.") }
                }
            }
        }
    }

    /** Chamado quando a tela fica visível/oculta. */
    fun setScreenActive(active: Boolean) {
        refreshAvailability()
        controller.setScanningRequested(active)
    }

    fun refreshAvailability() {
        _state.update { it.copy(supported = controller.isSupported, enabled = controller.isEnabled()) }
        c.statusMonitor.refresh()
    }

    fun saveLast() {
        val reading = _state.value.lastReading ?: return
        viewModelScope.launch {
            runCatching { c.nfcRepository.save(reading) }
                .onSuccess {
                    _state.update { it.copy(lastSaved = true) }
                    c.logger.i(LogCategory.STORAGE, "Leitura NFC salva (#$it)")
                    _messages.tryEmit("Leitura salva no histórico")
                }
                .onFailure {
                    c.logger.e(LogCategory.STORAGE, "Erro ao salvar leitura NFC", it)
                    _messages.tryEmit("Erro ao salvar: ${it.message}")
                }
        }
    }

    fun exportLast() {
        val reading = _state.value.lastReading ?: return
        export("nfc_leitura", "txt", "text/plain", ExportFormatter.nfcReadingText(reading))
    }

    fun exportHistory() {
        viewModelScope.launch {
            val all = c.nfcRepository.all()
            if (all.isEmpty()) {
                _messages.tryEmit("Histórico vazio")
                return@launch
            }
            export("nfc_historico", "json", "application/json", ExportFormatter.nfcReadingsJson(all))
        }
    }

    private fun export(name: String, ext: String, mime: String, content: String) {
        viewModelScope.launch {
            c.exporter.export(name, ext, mime, "Exportar NFC", content)
                .onSuccess { c.logger.i(LogCategory.NFC, "Exportado: ${it.name}") }
                .onFailure {
                    c.logger.e(LogCategory.NFC, "Falha na exportação", it)
                    _messages.tryEmit("Falha na exportação: ${it.message}")
                }
        }
    }

    override fun onCleared() {
        controller.setScanningRequested(false)
        super.onCleared()
    }
}
