package com.flippermobile.data.repository

import com.flippermobile.data.db.BleDao
import com.flippermobile.data.db.BleDeviceEntity
import com.flippermobile.data.db.IrDao
import com.flippermobile.data.db.IrRemoteEntity
import com.flippermobile.data.db.LogDao
import com.flippermobile.data.db.LogEntity
import com.flippermobile.data.db.NfcDao
import com.flippermobile.data.db.NfcReadingEntity
import com.flippermobile.data.db.WifiDao
import com.flippermobile.data.db.WifiSnapshotEntity
import com.flippermobile.domain.ir.IrProtocol
import com.flippermobile.domain.ir.IrRemoteConfig
import com.flippermobile.domain.model.BleDeviceInfo
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.LogLevel
import com.flippermobile.domain.model.NfcReading
import com.flippermobile.domain.nfc.NfcReadingCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NfcRepository(private val dao: NfcDao) {
    val history: Flow<List<NfcReading>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(reading: NfcReading): Long = dao.insert(reading.toEntity())
    suspend fun all(): List<NfcReading> = dao.getAll().map { it.toDomain() }
    suspend fun rename(id: Long, label: String) = dao.updateLabel(id, label)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()

    private fun NfcReading.toEntity() = NfcReadingEntity(
        timestamp = timestamp, uidHex = uidHex, technologies = technologies.joinToString(","),
        tagType = tagType, maxSize = maxSize, isWritable = isWritable,
        ndefJson = NfcReadingCodec.encodeRecords(ndefRecords), rawHex = rawHex,
        extraJson = NfcReadingCodec.encodeMap(extraInfo), label = label,
    )

    private fun NfcReadingEntity.toDomain() = NfcReading(
        id = id, timestamp = timestamp, uidHex = uidHex,
        technologies = technologies.split(',').filter { it.isNotBlank() },
        tagType = tagType, maxSize = maxSize, isWritable = isWritable,
        ndefRecords = NfcReadingCodec.decodeRecords(ndefJson), rawHex = rawHex,
        extraInfo = NfcReadingCodec.decodeMap(extraJson), label = label,
    )
}

data class SavedBleDevice(val id: Long, val savedAt: Long, val device: BleDeviceInfo, val gattSummary: String?)

class BleRepository(private val dao: BleDao) {
    val saved: Flow<List<SavedBleDevice>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(devices: List<BleDeviceInfo>, gattSummaries: Map<String, String> = emptyMap()) {
        val now = System.currentTimeMillis()
        dao.insertAll(devices.map { d ->
            BleDeviceEntity(
                savedAt = now, address = d.address, name = d.name, rssi = d.rssi, txPower = d.txPower,
                connectable = d.connectable, deviceType = d.deviceType,
                serviceUuids = d.serviceUuids.joinToString(","),
                manufacturerData = NfcReadingCodec.encodeMap(d.manufacturerData.mapKeys { it.key.toString() }),
                rawAdvertisementHex = d.rawAdvertisementHex, gattSummary = gattSummaries[d.address],
            )
        })
    }

    suspend fun allDevices(): List<BleDeviceInfo> = dao.getAll().map { it.toDomain().device }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()

    private fun BleDeviceEntity.toDomain() = SavedBleDevice(
        id = id, savedAt = savedAt, gattSummary = gattSummary,
        device = BleDeviceInfo(
            address = address, name = name, rssi = rssi, lastSeen = savedAt, connectable = connectable,
            txPower = txPower, serviceUuids = serviceUuids.split(',').filter { it.isNotBlank() },
            manufacturerData = NfcReadingCodec.decodeMap(manufacturerData)
                .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
            rawAdvertisementHex = rawAdvertisementHex, bondState = "—", deviceType = deviceType,
        )
    )
}

data class SavedWifiSnapshot(val id: Long, val savedAt: Long, val ssid: String?, val summary: String, val visibleNetworks: Int, val report: String)

class WifiRepository(private val dao: WifiDao) {
    val snapshots: Flow<List<SavedWifiSnapshot>> = dao.observeAll().map { list ->
        list.map { SavedWifiSnapshot(it.id, it.savedAt, it.ssid, it.summary, it.visibleNetworks, it.report) }
    }

    suspend fun save(ssid: String?, summary: String, visible: Int, report: String): Long =
        dao.insert(WifiSnapshotEntity(savedAt = System.currentTimeMillis(), ssid = ssid, summary = summary, visibleNetworks = visible, report = report))

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
}

class IrRepository(private val dao: IrDao) {
    val custom: Flow<List<IrRemoteConfig>> = dao.observeAll().map { list ->
        list.mapNotNull { e ->
            val protocol = IrProtocol.fromName(e.protocol) ?: return@mapNotNull null
            IrRemoteConfig(id = e.id, name = e.name, protocol = protocol, address = e.address,
                commands = IrRemoteConfig.decodeCommands(e.commands))
        }
    }

    suspend fun save(config: IrRemoteConfig): Long {
        val entity = IrRemoteEntity(
            id = if (config.id > 0) config.id else 0, name = config.name, protocol = config.protocol.name,
            address = config.address, commands = IrRemoteConfig.encodeCommands(config.commands),
            updatedAt = System.currentTimeMillis(),
        )
        return if (config.id > 0) { dao.update(entity); config.id } else dao.insert(entity)
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

class LogRepository(private val dao: LogDao) {
    fun recent(limit: Int): Flow<List<LogEntry>> = dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
    suspend fun all(): List<LogEntry> = dao.getAll().map { it.toDomain() }
    suspend fun insert(entry: LogEntry) {
        dao.insert(LogEntity(timestamp = entry.timestamp, level = entry.level.name, category = entry.category.name, message = entry.message))
    }
    suspend fun clear() = dao.clear()
    suspend fun trim(keep: Int) = dao.trim(keep)

    private fun LogEntity.toDomain() = LogEntry(
        id = id, timestamp = timestamp,
        level = runCatching { LogLevel.valueOf(level) }.getOrDefault(LogLevel.INFO),
        category = runCatching { LogCategory.valueOf(category) }.getOrDefault(LogCategory.APP),
        message = message,
    )
}
