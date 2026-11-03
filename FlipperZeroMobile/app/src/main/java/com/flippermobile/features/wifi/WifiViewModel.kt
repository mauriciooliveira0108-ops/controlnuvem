package com.flippermobile.features.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.WifiConnectionInfo
import com.flippermobile.hardware.wifi.WifiScanState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class WifiEnvironment(
    val supported: Boolean,
    val enabled: Boolean,
    val locationGranted: Boolean,
    val locationEnabled: Boolean,
    val throttleInfo: String,
)

class WifiViewModel(private val c: AppContainer) : ViewModel() {
    private val analyzer = c.wifiAnalyzer

    val connection: StateFlow<WifiConnectionInfo?> = analyzer.connection
    val scan: StateFlow<WifiScanState> = analyzer.scan

    private val _env = MutableStateFlow(readEnv())
    val env: StateFlow<WifiEnvironment> = _env

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private fun readEnv() = WifiEnvironment(
        supported = analyzer.isWifiSupported,
        enabled = analyzer.isWifiEnabled(),
        locationGranted = analyzer.hasFineLocation(),
        locationEnabled = analyzer.isLocationEnabled(),
        throttleInfo = analyzer.scanThrottleInfo(),
    )

    fun onVisible() {
        analyzer.startMonitoring()
        refresh()
        if (_env.value.locationGranted) analyzer.loadCachedResults()
    }

    fun onHidden() = analyzer.stopMonitoring()

    fun refresh() {
        _env.value = readEnv()
        analyzer.refreshConnection()
    }

    fun scan() {
        refresh()
        c.haptics.tick()
        analyzer.startScan()
    }

    fun saveSnapshot() {
        val conn = connection.value
        val results = scan.value.results
        val report = ExportFormatter.wifiReport(conn, results, System.currentTimeMillis())
        val summary = if (conn?.connected == true) {
            listOfNotNull(conn.band, conn.rssi?.let { "$it dBm" }, conn.ipv4.firstOrNull()).joinToString(" • ")
        } else "Desconectado"
        viewModelScope.launch {
            runCatching { c.wifiRepository.save(conn?.ssid, summary, results.size, report) }
                .onSuccess {
                    c.logger.i(LogCategory.STORAGE, "Snapshot Wi-Fi salvo")
                    _messages.tryEmit("Snapshot salvo")
                }
                .onFailure { _messages.tryEmit("Erro ao salvar: ${it.message}") }
        }
    }

    fun export() {
        val report = ExportFormatter.wifiReport(connection.value, scan.value.results, System.currentTimeMillis())
        viewModelScope.launch {
            c.exporter.export("wifi_relatorio", "txt", "text/plain", "Exportar Wi-Fi", report)
                .onFailure { _messages.tryEmit("Falha na exportação: ${it.message}") }
        }
    }

    override fun onCleared() {
        analyzer.stopMonitoring()
        super.onCleared()
    }
}
