package com.flippermobile.domain.permissions

/**
 * Decide quais permissões de runtime cada módulo precisa, de acordo com a versão do Android.
 * Puro (usa strings das permissões) para permitir testes unitários na JVM.
 */
enum class Feature { BLUETOOTH_SCAN, BLUETOOTH_CONNECT, WIFI_SCAN, WIFI_SSID }

data class PermissionRequirement(val permission: String, val rationale: String)

object PermissionPlanner {
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"

    private const val S = 31 // Android 12
    private const val TIRAMISU = 33 // Android 13

    fun requirements(feature: Feature, sdkInt: Int): List<PermissionRequirement> = when (feature) {
        Feature.BLUETOOTH_SCAN -> if (sdkInt >= S) listOf(
            PermissionRequirement(BLUETOOTH_SCAN, "Necessária para procurar dispositivos Bluetooth LE próximos."),
            PermissionRequirement(BLUETOOTH_CONNECT, "Necessária para ler o nome dos dispositivos e conectar ao GATT."),
        ) else listOf(
            PermissionRequirement(ACCESS_FINE_LOCATION, "Até o Android 11, o sistema exige localização precisa para varreduras BLE."),
        )

        Feature.BLUETOOTH_CONNECT -> if (sdkInt >= S) listOf(
            PermissionRequirement(BLUETOOTH_CONNECT, "Necessária para conectar ao dispositivo e descobrir serviços GATT."),
        ) else emptyList()

        Feature.WIFI_SCAN -> buildList {
            addAll(location(sdkInt, "O Android exige localização precisa para listar redes Wi-Fi visíveis."))
            if (sdkInt >= TIRAMISU) {
                add(PermissionRequirement(NEARBY_WIFI_DEVICES, "Android 13+: necessária para acessar dispositivos Wi-Fi próximos."))
            }
        }

        Feature.WIFI_SSID -> location(sdkInt, "O Android só revela o SSID/BSSID da rede atual com localização precisa concedida.")
    }

    /** Android 12+: localização precisa deve ser pedida junto com a aproximada, senão o sistema ignora o pedido. */
    private fun location(sdkInt: Int, rationale: String): List<PermissionRequirement> = buildList {
        add(PermissionRequirement(ACCESS_FINE_LOCATION, rationale))
        if (sdkInt >= S) add(PermissionRequirement(ACCESS_COARSE_LOCATION, "Exigida pelo Android 12+ junto com a localização precisa."))
    }

    fun permissions(feature: Feature, sdkInt: Int): Array<String> =
        requirements(feature, sdkInt).map { it.permission }.distinct().toTypedArray()

    fun missing(feature: Feature, sdkInt: Int, isGranted: (String) -> Boolean): List<PermissionRequirement> =
        requirements(feature, sdkInt).filterNot { isGranted(it.permission) }
}
