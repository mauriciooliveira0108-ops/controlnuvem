package com.flippermobile.domain

import com.flippermobile.domain.ble.BleParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleParsingTest {
    @Test fun parsesAdStructuresAndStopsAtPadding() {
        val raw = byteArrayOf(0x02, 0x01, 0x06, 0x04, 0x09, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(), 0, 0, 0)
        val ad = BleParsing.parseAdvertisement(raw)
        assertEquals(2, ad.size)
        assertEquals("Flags", ad[0].typeName)
        assertEquals("06", ad[0].dataHex)
        assertEquals("41 42 43", ad[1].dataHex)
    }

    @Test fun truncatedStructureIsIgnored() {
        assertEquals(0, BleParsing.parseAdvertisement(byteArrayOf(0x05, 0x09, 0x41)).size)
    }

    @Test fun uuidNames() {
        assertEquals("Battery Service", BleParsing.gattServiceName("0000180f-0000-1000-8000-00805f9b34fb"))
        assertEquals("Serviço personalizado", BleParsing.gattServiceName("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
        assertNull(BleParsing.shortUuid("bad"))
        assertEquals("Apple", BleParsing.companyName(0x004C))
    }

    @Test fun properties() {
        assertEquals(listOf("READ", "NOTIFY"), BleParsing.characteristicProperties(0x12))
    }
}

class BleFilterTest {
    private fun dev(addr: String, name: String?, rssi: Int, seen: Long, conn: Boolean?, uuids: List<String> = emptyList()) =
        com.flippermobile.domain.model.BleDeviceInfo(addr, name, rssi, seen, conn, null, uuids, emptyMap(), "", "—", "LE")

    private val list = listOf(
        dev("AA:00", "Fone", -70, 3, true),
        dev("BB:00", null, -40, 1, false),
        dev("CC:00", "balança", -90, 2, true, listOf("0000181d-0000-1000-8000-00805f9b34fb")),
    )

    @Test fun sortsByRssiByDefault() {
        val r = com.flippermobile.domain.ble.applyBleFilter(list, com.flippermobile.domain.ble.BleFilter())
        assertEquals(listOf("BB:00", "AA:00", "CC:00"), r.map { it.address })
    }

    @Test fun filtersNamedConnectableAndQuery() {
        val f = com.flippermobile.domain.ble.BleFilter(namedOnly = true, connectableOnly = true, sort = com.flippermobile.domain.ble.BleSort.NAME)
        assertEquals(listOf("CC:00", "AA:00"), com.flippermobile.domain.ble.applyBleFilter(list, f).map { it.address })
        assertEquals(listOf("CC:00"), com.flippermobile.domain.ble.applyBleFilter(list, com.flippermobile.domain.ble.BleFilter(query = "181d")).map { it.address })
        assertEquals(listOf("AA:00", "CC:00", "BB:00"),
            com.flippermobile.domain.ble.applyBleFilter(list, com.flippermobile.domain.ble.BleFilter(sort = com.flippermobile.domain.ble.BleSort.RECENT)).map { it.address })
    }
}
