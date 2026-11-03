package com.flippermobile.hardware.status

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.flippermobile.domain.permissions.Feature
import com.flippermobile.domain.permissions.PermissionPlanner
import com.flippermobile.hardware.ble.BleScanner
import com.flippermobile.hardware.ir.IrTransmitter
import com.flippermobile.hardware.nfc.NfcController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HwState { UNSUPPORTED, OFF, ON, ACTIVE }

data class SystemStatus(
    val nfc: HwState = HwState.UNSUPPORTED,
    val bluetooth: HwState = HwState.UNSUPPORTED,
    val wifi: HwState = HwState.UNSUPPORTED,
    val wifiConnected: Boolean = false,
    val ir: HwState = HwState.UNSUPPORTED,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val permissionsGranted: Int = 0,
    val permissionsTotal: Int = 0,
)

/** Painel de status em tempo real, alimentado por broadcasts do sistema. */
class SystemStatusMonitor(
    context: Context,
    private val nfc: NfcController,
    private val ble: BleScanner,
    private val ir: IrTransmitter,
) {
    private val ctx = context.applicationContext
    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = _status.asStateFlow()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                else -> refresh()
            }
        }
    }

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction("android.net.wifi.WIFI_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let { updateBattery(it) }
        runCatching {
            ctx.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(netCallback)
        }
        refresh()
    }

    fun refresh() {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) } }.getOrNull()
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val wifiSupported = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        val wifiEnabled = runCatching {
            ctx.getSystemService(android.net.wifi.WifiManager::class.java)?.isWifiEnabled == true
        }.getOrDefault(false)

        val perms = allRuntimePermissions()
        val granted = perms.count { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

        _status.update {
            it.copy(
                nfc = when {
                    !nfc.isSupported -> HwState.UNSUPPORTED
                    nfc.readerActive.value -> HwState.ACTIVE
                    nfc.isEnabled() -> HwState.ON
                    else -> HwState.OFF
                },
                bluetooth = when {
                    ble.adapter == null -> HwState.UNSUPPORTED
                    ble.isEnabled() -> HwState.ON
                    else -> HwState.OFF
                },
                wifi = when {
                    !wifiSupported -> HwState.UNSUPPORTED
                    wifiConnected -> HwState.ACTIVE
                    wifiEnabled -> HwState.ON
                    else -> HwState.OFF
                },
                wifiConnected = wifiConnected,
                ir = if (ir.hasEmitter) HwState.ON else HwState.UNSUPPORTED,
                permissionsGranted = granted,
                permissionsTotal = perms.size,
            )
        }
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        _status.update {
            it.copy(
                batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
    }

    companion object {
        fun allRuntimePermissions(sdk: Int = Build.VERSION.SDK_INT): List<String> =
            Feature.entries.flatMap { f -> PermissionPlanner.permissions(f, sdk).toList() }.distinct()

    }
}
