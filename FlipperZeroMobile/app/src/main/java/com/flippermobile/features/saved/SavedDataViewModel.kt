package com.flippermobile.features.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.data.repository.SavedBleDevice
import com.flippermobile.data.repository.SavedWifiSnapshot
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.ir.IrRemoteConfig
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.NfcReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SavedTab(val label: String) { NFC("NFC"), BLE("BLE"), WIFI("Wi-Fi"), IR("IR") }

class SavedDataViewModel(private val c: AppContainer) : ViewModel() {
    val nfc: StateFlow<List<NfcReading>> = c.nfcRepository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ble: StateFlow<List<SavedBleDevice>> = c.bleRepository.saved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wifi: StateFlow<List<SavedWifiSnapshot>> = c.wifiRepository.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ir: StateFlow<List<IrRemoteConfig>> = c.irRepository.custom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private fun io(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { c.logger.i(LogCategory.STORAGE, label) }
                .onFailure {
                    c.logger.e(LogCategory.STORAGE, "Falha: $label", it)
                    _messages.tryEmit("Erro: ${it.message}")
                }
        }
    }

    fun deleteNfc(id: Long) = io("Leitura NFC #$id removida") { c.nfcRepository.delete(id) }
    fun renameNfc(id: Long, label: String) = io("Leitura NFC #$id renomeada") { c.nfcRepository.rename(id, label.trim()) }
    fun deleteBle(id: Long) = io("Dispositivo BLE salvo #$id removido") { c.bleRepository.delete(id) }
    fun deleteWifi(id: Long) = io("Snapshot Wi-Fi #$id removido") { c.wifiRepository.delete(id) }
    fun deleteIr(id: Long) = io("Controle IR #$id removido") { c.irRepository.delete(id) }

    fun clear(tab: SavedTab) = io("Dados ${tab.label} apagados") {
        when (tab) {
            SavedTab.NFC -> c.nfcRepository.clear()
            SavedTab.BLE -> c.bleRepository.clear()
            SavedTab.WIFI -> c.wifiRepository.clear()
            SavedTab.IR -> ir.value.forEach { c.irRepository.delete(it.id) }
        }
    }

    fun export(tab: SavedTab) {
        viewModelScope.launch {
            val (name, ext, mime, content) = when (tab) {
                SavedTab.NFC -> Quad("nfc_historico", "json", "application/json", ExportFormatter.nfcReadingsJson(nfc.value))
                SavedTab.BLE -> Quad("ble_salvos", "csv", "text/csv", ExportFormatter.bleDevicesCsv(ble.value.map { it.device }))
                SavedTab.WIFI -> Quad("wifi_snapshots", "txt", "text/plain", wifi.value.joinToString("\n\n") { it.report })
                SavedTab.IR -> Quad("ir_controles", "txt", "text/plain", ir.value.joinToString("\n") { ExportFormatter.irConfigText(it) })
            }
            val empty = when (tab) {
                SavedTab.NFC -> nfc.value.isEmpty()
                SavedTab.BLE -> ble.value.isEmpty()
                SavedTab.WIFI -> wifi.value.isEmpty()
                SavedTab.IR -> ir.value.isEmpty()
            }
            if (empty) {
                _messages.tryEmit("Nada para exportar")
                return@launch
            }
            c.exporter.export(name, ext, mime, "Exportar ${tab.label}", content)
                .onFailure { _messages.tryEmit("Falha na exportação: ${it.message}") }
        }
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)
}
