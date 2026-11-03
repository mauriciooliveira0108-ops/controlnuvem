package com.flippermobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NfcReadingEntity::class,
        BleDeviceEntity::class,
        WifiSnapshotEntity::class,
        IrRemoteEntity::class,
        LogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nfcDao(): NfcDao
    abstract fun bleDao(): BleDao
    abstract fun wifiDao(): WifiDao
    abstract fun irDao(): IrDao
    abstract fun logDao(): LogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "flipper_mobile.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
