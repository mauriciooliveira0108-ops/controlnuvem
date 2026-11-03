package com.flippermobile.features.bluetooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.ble.BleFilter
import com.flippermobile.domain.ble.BleSort
import com.flippermobile.domain.ble.applyBleFilter
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.model.BleDeviceInfo
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.hardware.ble.BleScanState
import com.flippermobile.hardware.ble.GattState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BluetoothViewModel(private val c: AppContainer) : ViewModel() {
    private val scanner = c.bleScanner
    private val gatt = c.gattInspector

    val supported: Boolean get() = scanner.isBleSupported
    private val _enabled = MutableStateFlow(scanner.isEnabled())
    val enabled: StateFlow<Boolean> = _enabled

    val scanState: StateFlow<BleScanState> = scanner.state
    val gattState: StateFlow<GattState> = gatt.state

    private val _filter = MutableStateFlow(BleFilter())
    val filter: StateFlow<BleFilter> = _filter

    val allCount: StateFlow<Int> = scanner.devices.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val devices: StateFlow<List<BleDeviceInfo>> = combine(scanner.devices, _filter) { map, f -> applyBleFilter(map.values, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val gattSummaries = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            _filter.value = _filter.value.copy(namedOnly = !c.settings.settings.first().bleShowUnnamed)
        }
        viewModelScope.launch {
            gatt.state.collect { s ->
                if (s is GattState.Discovered) {
                    gattSummaries[s.address] = s.services.joinToString("; ") { "${it.name} (${it.uuid}) [${it.characteristics.size} car.]" }
                }
            }
        }
    }

    fun refreshEnabled() {
        _enabled.value = scanner.isEnabled()
    }

    fun startScan() {
        refreshEnabled()
        viewModelScope.launch {
            val timeout = c.settings.settings.first().bleScanTimeoutSeconds * 1000L
            c.haptics.tick()
            scanner.start(timeout)
        }
    }

    fun stopScan() {
        scanner.stop()
        c.haptics.tick()
    }

    /** App em segundo plano: encerra a varredura para não drenar bateria. */
    fun onScreenPaused() {
        if (scanner.state.value is BleScanState.Scanning) scanner.stop("App em segundo plano")
    }

    fun clear() = scanner.clear()

    fun setQuery(q: String) { _filter.value = _filter.value.copy(query = q) }
    fun toggleNamed() { _filter.value = _filter.value.copy(namedOnly = !_filter.value.namedOnly) }
    fun toggleConnectable() { _filter.value = _filter.value.copy(connectableOnly = !_filter.value.connectableOnly) }
    fun setSort(s: BleSort) { _filter.value = _filter.value.copy(sort = s) }

    fun select(address: String?) {
        if (address == null) gatt.disconnect()
        _selected.value = address
    }

    fun device(address: String): BleDeviceInfo? = scanner.devices.value[address]

    fun connectGatt(address: String) = gatt.connect(address)

    fun saveResults() {
        val list = devices.value
        if (list.isEmpty()) {
            _messages.tryEmit("Nenhum dispositivo para salvar")
            return
        }
        viewModelScope.launch {
            runCatching { c.bleRepository.save(list, gattSummaries.toMap()) }
                .onSuccess {
                    c.logger.i(LogCategory.STORAGE, "${list.size} dispositivo(s) BLE salvo(s)")
                    _messages.tryEmit("${list.size} dispositivo(s) salvo(s)")
                }
                .onFailure {
                    c.logger.e(LogCategory.STORAGE, "Erro ao salvar BLE", it)
                    _messages.tryEmit("Erro ao salvar: ${it.message}")
                }
        }
    }

    fun exportResults() {
        val list = devices.value
        if (list.isEmpty()) {
            _messages.tryEmit("Nada para exportar")
            return
        }
        viewModelScope.launch {
            c.exporter.export("ble_scan", "csv", "text/csv", "Exportar BLE", ExportFormatter.bleDevicesCsv(list))
                .onFailure { _messages.tryEmit("Falha na exportação: ${it.message}") }
        }
    }

    override fun onCleared() {
        scanner.stop("Tela fechada")
        gatt.disconnect()
        super.onCleared()
    }
}
