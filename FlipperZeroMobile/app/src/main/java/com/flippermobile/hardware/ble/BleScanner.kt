package com.flippermobile.hardware.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import com.flippermobile.core.logging.ActivityLogger
import com.flippermobile.core.util.HexUtils
import com.flippermobile.domain.model.BleDeviceInfo
import com.flippermobile.domain.model.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BleScanState {
    data object Idle : BleScanState
    data class Scanning(val startedAt: Long, val timeoutMs: Long) : BleScanState
    data class Finished(val reason: String) : BleScanState
    data class Failed(val message: String) : BleScanState
}

/**
 * Scanner BLE real baseado em BluetoothLeScanner. Garante encerramento por timeout,
 * por solicitação do usuário ou quando o ViewModel é destruído.
 * Chamar apenas na thread principal.
 */
class BleScanner(
    context: Context,
    private val logger: ActivityLogger,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val manager: BluetoothManager? = appContext.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? get() = manager?.adapter

    val isBleSupported: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null

    fun isEnabled(): Boolean = try { adapter?.isEnabled == true } catch (e: SecurityException) { false }

    private val _state = MutableStateFlow<BleScanState>(BleScanState.Idle)
    val state: StateFlow<BleScanState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<Map<String, BleDeviceInfo>>(emptyMap())
    val devices: StateFlow<Map<String, BleDeviceInfo>> = _devices.asStateFlow()

    private var timeoutJob: Job? = null
    private var callbackRegistered = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            callbackRegistered = false
            timeoutJob?.cancel()
            val msg = scanErrorMessage(errorCode)
            logger.e(LogCategory.BLE, "Falha na varredura BLE (código $errorCode): $msg")
            _state.value = BleScanState.Failed(msg)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(timeoutMs: Long, clearPrevious: Boolean = true) {
        if (!isBleSupported) {
            _state.value = BleScanState.Failed("Bluetooth LE não é suportado neste aparelho")
            return
        }
        if (!isEnabled()) {
            _state.value = BleScanState.Failed("Bluetooth está desligado")
            return
        }
        if (_state.value is BleScanState.Scanning) return
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = BleScanState.Failed("Scanner BLE indisponível (Bluetooth desligando?)")
            return
        }
        if (clearPrevious) _devices.value = emptyMap()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(null, settings, callback)
            callbackRegistered = true
            _state.value = BleScanState.Scanning(System.currentTimeMillis(), timeoutMs)
            logger.i(LogCategory.BLE, "Varredura BLE iniciada (timeout ${timeoutMs / 1000}s)")
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(timeoutMs)
                stop("Tempo limite atingido")
            }
        } catch (e: SecurityException) {
            _state.value = BleScanState.Failed("Permissão Bluetooth negada")
            logger.e(LogCategory.PERMISSION, "startScan sem permissão", e)
        } catch (e: Exception) {
            _state.value = BleScanState.Failed("Erro ao iniciar varredura: ${e.message}")
            logger.e(LogCategory.BLE, "Erro ao iniciar varredura", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(reason: String = "Interrompida pelo usuário") {
        timeoutJob?.cancel()
        timeoutJob = null
        if (callbackRegistered) {
            try {
                if (isEnabled()) adapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (e: Exception) {
                logger.w(LogCategory.BLE, "Falha ao parar varredura: ${e.message}")
            }
            callbackRegistered = false
            logger.i(LogCategory.BLE, "Varredura BLE encerrada: $reason. ${_devices.value.size} dispositivo(s)")
        }
        if (_state.value is BleScanState.Scanning) _state.value = BleScanState.Finished(reason)
    }

    fun clear() {
        _devices.value = emptyMap()
    }

    @SuppressLint("MissingPermission")
    private fun handleResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val name = try { device.name } catch (e: SecurityException) { null } ?: record?.deviceName
        val manufacturer = LinkedHashMap<Int, String>()
        record?.manufacturerSpecificData?.let { sparse ->
            for (i in 0 until sparse.size()) manufacturer[sparse.keyAt(i)] = HexUtils.toHex(sparse.valueAt(i), " ")
        }
        val tx = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
        val bond = try {
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "Pareado"
                BluetoothDevice.BOND_BONDING -> "Pareando"
                else -> "Não pareado"
            }
        } catch (e: SecurityException) { "—" }
        val type = try {
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Clássico"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                else -> "Desconhecido"
            }
        } catch (e: SecurityException) { "—" }
        val info = BleDeviceInfo(
            address = device.address,
            name = name,
            rssi = result.rssi,
            lastSeen = System.currentTimeMillis(),
            connectable = result.isConnectable,
            txPower = tx,
            serviceUuids = record?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
            manufacturerData = manufacturer,
            rawAdvertisementHex = HexUtils.toHex(record?.bytes, " "),
            bondState = bond,
            deviceType = type,
        )
        val isNew = !_devices.value.containsKey(info.address)
        _devices.update { it + (info.address to info) }
        if (isNew) logger.d(LogCategory.BLE, "Novo dispositivo: ${info.name ?: "(sem nome)"} ${info.address} ${info.rssi} dBm")
    }

    companion object {
        fun scanErrorMessage(code: Int): String = when (code) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Varredura já em andamento"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Falha ao registrar o app no Bluetooth (tente desligar e ligar o Bluetooth)"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Erro interno do Bluetooth"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Recurso de varredura não suportado"
            5 -> "Sem recursos de hardware para a varredura"
            6 -> "Varreduras muito frequentes: o Android limita 5 inícios a cada 30 s"
            else -> "Erro desconhecido"
        }
    }
}
