package com.flippermobile.hardware.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.flippermobile.core.logging.ActivityLogger
import com.flippermobile.domain.ble.BleParsing
import com.flippermobile.domain.model.GattCharacteristicInfo
import com.flippermobile.domain.model.GattServiceInfo
import com.flippermobile.domain.model.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GattState {
    data object Idle : GattState
    data class Connecting(val address: String) : GattState
    data class Discovering(val address: String) : GattState
    data class Discovered(val address: String, val services: List<GattServiceInfo>) : GattState
    data class Failed(val address: String, val message: String) : GattState
}

/** Conecta a um dispositivo BLE somente por ação explícita do usuário e lista serviços GATT. */
class GattInspector(
    context: Context,
    private val scanner: BleScanner,
    private val logger: ActivityLogger,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<GattState>(GattState.Idle)
    val state: StateFlow<GattState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var timeoutJob: Job? = null

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val address = g.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(address, "Conexão falhou (status GATT $status)")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    logger.i(LogCategory.BLE, "GATT conectado a $address; descobrindo serviços")
                    _state.value = GattState.Discovering(address)
                    try {
                        if (!g.discoverServices()) fail(address, "discoverServices() recusado pelo sistema")
                    } catch (e: SecurityException) {
                        fail(address, "Permissão BLUETOOTH_CONNECT negada")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (_state.value !is GattState.Discovered) fail(address, "Dispositivo desconectou")
                    closeGatt()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val address = g.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail(address, "Falha na descoberta de serviços (status $status)")
                return
            }
            timeoutJob?.cancel()
            val services = g.services.map { s ->
                val uuid = s.uuid.toString()
                GattServiceInfo(
                    uuid = uuid,
                    name = BleParsing.gattServiceName(uuid),
                    characteristics = s.characteristics.map { c ->
                        val cu = c.uuid.toString()
                        GattCharacteristicInfo(cu, BleParsing.gattCharacteristicName(cu), BleParsing.characteristicProperties(c.properties))
                    },
                )
            }
            logger.i(LogCategory.BLE, "GATT $address: ${services.size} serviço(s) descoberto(s)")
            _state.value = GattState.Discovered(address, services)
            // Inspeção concluída: libera a conexão para não manter o rádio ocupado.
            disconnect(keepResult = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, timeoutMs: Long = 20_000) {
        disconnect()
        val adapter = scanner.adapter
        if (adapter == null || !scanner.isEnabled()) {
            _state.value = GattState.Failed(address, "Bluetooth indisponível ou desligado")
            return
        }
        scanner.stop("Conexão GATT solicitada")
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            _state.value = GattState.Connecting(address)
            logger.i(LogCategory.BLE, "Conectando ao GATT de $address")
            gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(timeoutMs)
                if (_state.value is GattState.Connecting || _state.value is GattState.Discovering) {
                    fail(address, "Tempo limite de conexão")
                }
            }
        } catch (e: SecurityException) {
            fail(address, "Permissão BLUETOOTH_CONNECT negada")
        } catch (e: IllegalArgumentException) {
            fail(address, "Endereço inválido")
        }
    }

    private fun fail(address: String, message: String) {
        val current = _state.value
        if (current is GattState.Discovered && current.address == address) return
        logger.e(LogCategory.BLE, "GATT $address: $message")
        _state.value = GattState.Failed(address, message)
        disconnect(keepResult = true)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(keepResult: Boolean = false) {
        timeoutJob?.cancel()
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        closeGatt()
        if (!keepResult) _state.value = GattState.Idle
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }
}
