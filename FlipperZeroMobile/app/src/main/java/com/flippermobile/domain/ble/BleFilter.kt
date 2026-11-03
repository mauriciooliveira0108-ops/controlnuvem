package com.flippermobile.domain.ble

import com.flippermobile.domain.model.BleDeviceInfo

enum class BleSort(val label: String) { RSSI("Sinal"), NAME("Nome"), RECENT("Recentes") }

data class BleFilter(val query: String = "", val namedOnly: Boolean = false, val connectableOnly: Boolean = false, val sort: BleSort = BleSort.RSSI)

/** Filtro puro aplicado à lista de dispositivos. */
fun applyBleFilter(devices: Collection<BleDeviceInfo>, f: BleFilter): List<BleDeviceInfo> {
    val q = f.query.trim()
    return devices.asSequence()
        .filter { !f.namedOnly || !it.name.isNullOrBlank() }
        .filter { !f.connectableOnly || it.connectable == true }
        .filter {
            q.isEmpty() || it.address.contains(q, true) || (it.name?.contains(q, true) == true) ||
                it.serviceUuids.any { u -> u.contains(q, true) }
        }
        .let { seq ->
            when (f.sort) {
                BleSort.RSSI -> seq.sortedByDescending { it.rssi }
                BleSort.NAME -> seq.sortedWith(compareBy(nullsLast()) { it.name?.lowercase() })
                BleSort.RECENT -> seq.sortedByDescending { it.lastSeen }
            }
        }
        .toList()
}
