package com.flippermobile.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_readings")
data class NfcReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val uidHex: String,
    val technologies: String, // separado por vírgula
    val tagType: String,
    val maxSize: Int?,
    val isWritable: Boolean?,
    val ndefJson: String, // registros NDEF serializados (formato interno)
    val rawHex: String,
    val extraJson: String,
    val label: String,
)

@Entity(tableName = "ble_devices")
data class BleDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedAt: Long,
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPower: Int?,
    val connectable: Boolean?,
    val deviceType: String,
    val serviceUuids: String,
    val manufacturerData: String,
    val rawAdvertisementHex: String,
    val gattSummary: String?,
)

@Entity(tableName = "wifi_snapshots")
data class WifiSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedAt: Long,
    val ssid: String?,
    val summary: String,
    val visibleNetworks: Int,
    val report: String,
)

@Entity(tableName = "ir_remotes")
data class IrRemoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val address: Int,
    val commands: String,
    val updatedAt: Long,
)

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val category: String,
    val message: String,
)
