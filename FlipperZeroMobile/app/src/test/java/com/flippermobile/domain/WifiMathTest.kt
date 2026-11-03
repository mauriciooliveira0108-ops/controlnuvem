package com.flippermobile.domain

import com.flippermobile.domain.wifi.WifiMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiMathTest {
    @Test fun bandsAndChannels() {
        assertEquals("2,4 GHz", WifiMath.band(2437))
        assertEquals(6, WifiMath.channel(2437))
        assertEquals(14, WifiMath.channel(2484))
        assertEquals("5 GHz", WifiMath.band(5180))
        assertEquals(36, WifiMath.channel(5180))
        assertEquals("6 GHz", WifiMath.band(5955))
        assertEquals(1, WifiMath.channel(5955))
        assertNull(WifiMath.channel(1000))
    }

    @Test fun ipv4LittleEndian() {
        // 192.168.0.10 em little-endian
        val v = 192 or (168 shl 8) or (0 shl 16) or (10 shl 24)
        assertEquals("192.168.0.10", WifiMath.ipv4FromLittleEndianInt(v))
    }

    @Test fun signal() {
        assertEquals(4, WifiMath.signalLevel(-40))
        assertEquals(0, WifiMath.signalLevel(-95))
        assertEquals("Regular", WifiMath.signalQuality(-70))
    }

    @Test fun security() {
        assertEquals("WPA3", WifiMath.securityFromCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA2", WifiMath.securityFromCapabilities("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("Aberta", WifiMath.securityFromCapabilities("[ESS]"))
    }

    @Test fun ssidCleanup() {
        assertEquals("Casa", WifiMath.cleanSsid("\"Casa\""))
        assertNull(WifiMath.cleanSsid("<unknown ssid>"))
    }
}
