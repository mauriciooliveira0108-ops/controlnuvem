package com.flippermobile.domain.model

/** Leitura NFC real, independente de classes Android. */
data class NfcReading(
    val id: Long = 0,
    val timestamp: Long,
    val uidHex: String,
    val technologies: List<String>,
    val tagType: String,
    val maxSize: Int?,
    val isWritable: Boolean?,
    val ndefRecords: List<NdefRecordInfo>,
    val rawHex: String,
    val extraInfo: Map<String, String> = emptyMap(),
    val label: String = "",
)

data class NdefRecordInfo(
    val tnf: Int,
    val tnfName: String,
    val typeHex: String,
    val typeText: String,
    val payloadHex: String,
    val interpreted: String?,
    val kind: String,
)

data class BleDeviceInfo(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeen: Long,
    val connectable: Boolean?,
    val txPower: Int?,
    val serviceUuids: List<String>,
    val manufacturerData: Map<Int, String>,
    val rawAdvertisementHex: String,
    val bondState: String,
    val deviceType: String,
)

data class GattServiceInfo(
    val uuid: String,
    val name: String,
    val characteristics: List<GattCharacteristicInfo>,
)

data class GattCharacteristicInfo(
    val uuid: String,
    val name: String,
    val properties: List<String>,
)

data class WifiConnectionInfo(
    val connected: Boolean,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val band: String?,
    val channel: Int?,
    val linkSpeedMbps: Int?,
    val txSpeedMbps: Int?,
    val rxSpeedMbps: Int?,
    val standard: String?,
    val ipv4: List<String>,
    val ipv6: List<String>,
    val gateway: String?,
    val dns: List<String>,
    val interfaceName: String?,
    val isMetered: Boolean?,
    val hasInternet: Boolean?,
    val isValidated: Boolean?,
    val ssidHiddenReason: String?,
)

data class WifiScanResultInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val band: String,
    val channel: Int?,
    val capabilities: String,
    val security: String,
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

enum class LogCategory(val tag: String) {
    APP("APP"), NFC("NFC"), BLE("BLE"), WIFI("WIFI"), IR("IR"),
    DEVICE("DEV"), PERMISSION("PERM"), STORAGE("DB"), SYSTEM("SYS")
}

data class LogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
)
