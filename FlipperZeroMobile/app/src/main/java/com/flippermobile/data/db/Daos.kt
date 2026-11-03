package com.flippermobile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcDao {
    @Query("SELECT * FROM nfc_readings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NfcReadingEntity>>

    @Query("SELECT * FROM nfc_readings ORDER BY timestamp DESC")
    suspend fun getAll(): List<NfcReadingEntity>

    @Insert
    suspend fun insert(entity: NfcReadingEntity): Long

    @Query("UPDATE nfc_readings SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String)

    @Query("DELETE FROM nfc_readings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM nfc_readings")
    suspend fun clear()
}

@Dao
interface BleDao {
    @Query("SELECT * FROM ble_devices ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<BleDeviceEntity>>

    @Query("SELECT * FROM ble_devices ORDER BY savedAt DESC")
    suspend fun getAll(): List<BleDeviceEntity>

    @Insert
    suspend fun insertAll(entities: List<BleDeviceEntity>)

    @Query("DELETE FROM ble_devices WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ble_devices")
    suspend fun clear()
}

@Dao
interface WifiDao {
    @Query("SELECT * FROM wifi_snapshots ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<WifiSnapshotEntity>>

    @Insert
    suspend fun insert(entity: WifiSnapshotEntity): Long

    @Query("DELETE FROM wifi_snapshots WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM wifi_snapshots")
    suspend fun clear()
}

@Dao
interface IrDao {
    @Query("SELECT * FROM ir_remotes ORDER BY name")
    fun observeAll(): Flow<List<IrRemoteEntity>>

    @Insert
    suspend fun insert(entity: IrRemoteEntity): Long

    @Update
    suspend fun update(entity: IrRemoteEntity)

    @Query("DELETE FROM ir_remotes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM (SELECT * FROM logs ORDER BY id DESC LIMIT :limit) ORDER BY id ASC")
    fun observeRecent(limit: Int): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs ORDER BY id ASC")
    suspend fun getAll(): List<LogEntity>

    @Insert
    suspend fun insert(entity: LogEntity): Long

    @Query("DELETE FROM logs")
    suspend fun clear()

    @Query("DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
