package com.flippermobile.hardware.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.flippermobile.core.logging.ActivityLogger
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.WifiConnectionInfo
import com.flippermobile.domain.model.WifiScanResultInfo
import com.flippermobile.domain.wifi.WifiMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.Inet6Address

data class WifiScanState(
    val scanning: Boolean = false,
    val results: List<WifiScanResultInfo> = emptyList(),
    val lastUpdated: Long? = null,
    val message: String? = null,
    val fromCache: Boolean = false,
)

/**
 * Diagnóstico Wi-Fi usando ConnectivityManager/NetworkCapabilities e WifiManager.
 * Respeita as restrições do Android: SSID/BSSID exigem localização; varreduras sofrem throttling.
 */
class WifiAnalyzer(context: Context, private val logger: ActivityLogger) {
    private val appContext = context.applicationContext
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)
    private val connectivity: ConnectivityManager? = appContext.getSystemService(ConnectivityManager::class.java)

    val isWifiSupported: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) && wifiManager != null

    fun isWifiEnabled(): Boolean = try { wifiManager?.isWifiEnabled == true } catch (e: Exception) { false }

    /** Throttling de varredura (Android 11+ informa o estado; Android 9/10 sempre aplica). */
    fun scanThrottleInfo(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            if (wifiManager?.isScanThrottleEnabled == true) "Ativo: no máximo 4 varreduras a cada 2 minutos em primeiro plano"
            else "Desativado nas opções do desenvolvedor"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> "Ativo: no máximo 4 varreduras a cada 2 minutos em primeiro plano"
        else -> "Sem limite documentado nesta versão"
    }

    fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val lm = appContext.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private val _connection = MutableStateFlow<WifiConnectionInfo?>(null)
    val connection: StateFlow<WifiConnectionInfo?> = _connection.asStateFlow()

    private val _scan = MutableStateFlow(WifiScanState())
    val scan: StateFlow<WifiScanState> = _scan.asStateFlow()

    private var currentNetwork: Network? = null
    private var currentCaps: NetworkCapabilities? = null
    private var currentLink: LinkProperties? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var receiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            publishScanResults(fromCache = !updated, message = if (updated) null else "O sistema não atualizou os resultados (throttling ou falha). Exibindo cache.")
        }
    }

    // ---------- conexão atual ----------

    fun startMonitoring() {
        val cm = connectivity ?: return
        if (networkCallback != null) return
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = buildCallback()
        try {
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
            logger.d(LogCategory.WIFI, "Monitoramento de rede Wi-Fi iniciado")
        } catch (e: Exception) {
            logger.e(LogCategory.WIFI, "Falha ao registrar callback de rede", e)
        }
        refreshConnection()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext, scanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    fun stopMonitoring() {
        networkCallback?.let { cb -> runCatching { connectivity?.unregisterNetworkCallback(cb) } }
        networkCallback = null
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(scanReceiver) }
            receiverRegistered = false
        }
        _scan.value = _scan.value.copy(scanning = false)
    }

    private fun buildCallback(): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(network, caps)
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = onLink(network, lp)
                override fun onLost(network: Network) = onNetLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(network, caps)
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = onLink(network, lp)
                override fun onLost(network: Network) = onNetLost(network)
            }
        }
    }

    private fun onCaps(network: Network, caps: NetworkCapabilities) {
        currentNetwork = network; currentCaps = caps
        if (currentLink == null) currentLink = runCatching { connectivity?.getLinkProperties(network) }.getOrNull()
        rebuild()
    }

    private fun onLink(network: Network, lp: LinkProperties) {
        currentNetwork = network; currentLink = lp
        rebuild()
    }

    private fun onNetLost(network: Network) {
        if (network == currentNetwork) {
            currentNetwork = null; currentCaps = null; currentLink = null
            logger.i(LogCategory.WIFI, "Rede Wi-Fi perdida")
            rebuild()
        }
    }

    /** Atualização manual (ex.: após conceder permissão de localização). */
    fun refreshConnection() {
        val cm = connectivity
        if (cm != null) {
            val active = cm.activeNetwork
            val caps = active?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
            if (active != null && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                currentNetwork = active
                // Em Android 12+, as capacidades obtidas por getNetworkCapabilities omitem SSID;
                // o callback com FLAG_INCLUDE_LOCATION_INFO fornece os dados completos.
                if (currentCaps == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) currentCaps = caps
                currentLink = runCatching { cm.getLinkProperties(active) }.getOrNull()
            }
        }
        rebuild()
    }

    @Suppress("DEPRECATION")
    private fun rebuild() {
        val caps = currentCaps
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!connected) {
            _connection.value = WifiConnectionInfo(
                connected = false, ssid = null, bssid = null, rssi = null, frequencyMhz = null, band = null,
                channel = null, linkSpeedMbps = null, txSpeedMbps = null, rxSpeedMbps = null, standard = null,
                ipv4 = emptyList(), ipv6 = emptyList(), gateway = null, dns = emptyList(), interfaceName = null,
                isMetered = null, hasInternet = null, isValidated = null, ssidHiddenReason = null,
            )
            return
        }
        val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps?.transportInfo as? WifiInfo) ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) wifiManager?.connectionInfo else null
        } else {
            wifiManager?.connectionInfo
        }
        val ssid = WifiMath.cleanSsid(info?.ssid)
        val bssid = info?.bssid?.takeIf { it != "02:00:00:00:00:00" }
        val freq = info?.frequency?.takeIf { it > 0 }
        val lp = currentLink
        val addresses = lp?.linkAddresses?.map { it.address } ?: emptyList()
        val ipv4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        val ipv6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress?.substringBefore('%') }
        val gateway = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress
        val hiddenReason = when {
            ssid != null -> null
            !hasFineLocation() -> "permissão de localização não concedida"
            !isLocationEnabled() -> "serviço de localização desligado"
            else -> "o sistema não forneceu o SSID"
        }
        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && info != null) {
            when (info.wifiStandard) {
                1 -> "802.11a/b/g (legado)"
                4 -> "Wi-Fi 4 (802.11n)"
                5 -> "Wi-Fi 5 (802.11ac)"
                6 -> "Wi-Fi 6/6E (802.11ax)"
                7 -> "802.11ad"
                8 -> "Wi-Fi 7 (802.11be)"
                else -> null
            }
        } else null
        val newInfo = WifiConnectionInfo(
            connected = true,
            ssid = ssid,
            bssid = bssid,
            rssi = info?.rssi?.takeIf { it > -127 && it < 0 },
            frequencyMhz = freq,
            band = freq?.let { WifiMath.band(it) },
            channel = freq?.let { WifiMath.channel(it) },
            linkSpeedMbps = info?.linkSpeed?.takeIf { it > 0 },
            txSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info?.txLinkSpeedMbps?.takeIf { it > 0 } else null,
            rxSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info?.rxLinkSpeedMbps?.takeIf { it > 0 } else null,
            standard = standard,
            ipv4 = ipv4.ifEmpty {
                info?.ipAddress?.takeIf { it != 0 }?.let { listOf(WifiMath.ipv4FromLittleEndianInt(it)) } ?: emptyList()
            },
            ipv6 = ipv6,
            gateway = gateway,
            dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
            interfaceName = lp?.interfaceName,
            isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?.not(),
            hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            ssidHiddenReason = hiddenReason,
        )
        val old = _connection.value
        if (old?.ssid != newInfo.ssid || old?.connected != true) {
            logger.i(LogCategory.WIFI, "Wi-Fi conectado: ${newInfo.ssid ?: "(SSID oculto: $hiddenReason)"} ${newInfo.band ?: ""}")
        }
        _connection.value = newInfo
    }

    // ---------- varredura ----------

    /** Solicita varredura. Retorna false se o sistema recusou (throttling, Wi-Fi desligado etc.). */
    fun startScan(): Boolean {
        val wm = wifiManager ?: return false.also { _scan.value = WifiScanState(message = "Wi-Fi não suportado") }
        if (!hasFineLocation()) {
            _scan.value = _scan.value.copy(message = "Permissão de localização necessária para varrer redes")
            return false
        }
        if (!isLocationEnabled()) {
            _scan.value = _scan.value.copy(message = "Ative a localização do sistema: o Android não retorna redes com ela desligada")
            return false
        }
        @Suppress("DEPRECATION")
        val scanAlways = runCatching { wm.isScanAlwaysAvailable }.getOrDefault(false)
        if (!isWifiEnabled() && !scanAlways) {
            _scan.value = _scan.value.copy(message = "Wi-Fi desligado")
            return false
        }
        @Suppress("DEPRECATION")
        val accepted = try { wm.startScan() } catch (e: SecurityException) { false }
        if (accepted) {
            _scan.value = _scan.value.copy(scanning = true, message = null)
            logger.i(LogCategory.WIFI, "Varredura Wi-Fi solicitada")
        } else {
            logger.w(LogCategory.WIFI, "Varredura recusada pelo sistema (provável throttling)")
            publishScanResults(fromCache = true, message = "O Android recusou a varredura (limite: 4 a cada 2 min). Exibindo os últimos resultados do sistema.")
        }
        return accepted
    }

    @Suppress("DEPRECATION")
    private fun publishScanResults(fromCache: Boolean, message: String?) {
        val results: List<ScanResult> = try {
            wifiManager?.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            _scan.value = _scan.value.copy(scanning = false, message = "Permissão negada para ler resultados de varredura")
            logger.e(LogCategory.PERMISSION, "getScanResults sem permissão", e)
            return
        }
        val mapped = results.map { r ->
            WifiScanResultInfo(
                ssid = r.SSID ?: "",
                bssid = r.BSSID ?: "",
                rssi = r.level,
                frequencyMhz = r.frequency,
                band = WifiMath.band(r.frequency),
                channel = WifiMath.channel(r.frequency),
                capabilities = r.capabilities ?: "",
                security = WifiMath.securityFromCapabilities(r.capabilities ?: ""),
            )
        }.sortedByDescending { it.rssi }
        _scan.value = WifiScanState(
            scanning = false, results = mapped, lastUpdated = System.currentTimeMillis(),
            message = message ?: if (mapped.isEmpty()) "Nenhuma rede retornada pelo sistema" else null,
            fromCache = fromCache,
        )
        logger.i(LogCategory.WIFI, "Resultados de varredura: ${mapped.size} rede(s)${if (fromCache) " (cache)" else ""}")
    }

    fun loadCachedResults() = publishScanResults(fromCache = true, message = null)
}
