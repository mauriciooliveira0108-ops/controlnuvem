package com.flippermobile.domain

import com.flippermobile.domain.permissions.Feature
import com.flippermobile.domain.permissions.PermissionPlanner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPlannerTest {
    @Test fun ble_android12UsesNearbyPermissions() {
        assertArrayEquals(
            arrayOf(PermissionPlanner.BLUETOOTH_SCAN, PermissionPlanner.BLUETOOTH_CONNECT),
            PermissionPlanner.permissions(Feature.BLUETOOTH_SCAN, 31)
        )
    }

    @Test fun ble_android11UsesLocation() {
        assertArrayEquals(
            arrayOf(PermissionPlanner.ACCESS_FINE_LOCATION),
            PermissionPlanner.permissions(Feature.BLUETOOTH_SCAN, 30)
        )
        assertTrue(PermissionPlanner.permissions(Feature.BLUETOOTH_CONNECT, 30).isEmpty())
    }

    @Test fun wifi_android13AddsNearbyWifi() {
        assertEquals(1, PermissionPlanner.permissions(Feature.WIFI_SCAN, 30).size)
        assertEquals(2, PermissionPlanner.permissions(Feature.WIFI_SCAN, 32).size)
        assertEquals(3, PermissionPlanner.permissions(Feature.WIFI_SCAN, 33).size)
        assertTrue(PermissionPlanner.ACCESS_COARSE_LOCATION in PermissionPlanner.permissions(Feature.WIFI_SSID, 31))
    }

    @Test fun missing_filtersGranted() {
        val missing = PermissionPlanner.missing(Feature.BLUETOOTH_SCAN, 34) { it == PermissionPlanner.BLUETOOTH_SCAN }
        assertEquals(listOf(PermissionPlanner.BLUETOOTH_CONNECT), missing.map { it.permission })
    }
}
