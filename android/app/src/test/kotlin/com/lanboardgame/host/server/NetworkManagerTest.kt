package com.lanboardgame.host.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkManagerTest {
    @Test
    fun `wlan address works without an active network`() {
        val result = NetworkManager.rankCandidates(
            listOf(NetworkCandidate("192.168.50.7", "wlan0", NetworkTransport.WIFI)),
            activeInterface = null,
            activeIsWifi = false,
        )

        assertEquals(listOf("192.168.50.7"), result.map { it.address })
        assertTrue(result.single().recommended)
    }

    @Test
    fun `cellular and vpn addresses are excluded while wifi remains`() {
        val result = NetworkManager.rankCandidates(
            listOf(
                NetworkCandidate("10.20.30.40", "rmnet_data0", NetworkTransport.CELLULAR),
                NetworkCandidate("10.8.0.2", "tun0", NetworkTransport.VPN),
                NetworkCandidate("192.168.1.12", "wlan0", NetworkTransport.WIFI),
            ),
            activeInterface = "rmnet_data0",
            activeIsWifi = false,
        )

        assertEquals(listOf("192.168.1.12"), result.map { it.address })
    }

    @Test
    fun `wifi without internet capability is accepted and preferred`() {
        val result = NetworkManager.rankCandidates(
            listOf(
                NetworkCandidate("192.168.2.20", "eth0", NetworkTransport.ETHERNET),
                NetworkCandidate("192.168.2.30", "wlan0", NetworkTransport.WIFI),
            ),
            activeInterface = "wlan0",
            activeIsWifi = true,
        )

        assertEquals("192.168.2.30", result.first().address)
        assertTrue(result.first().label.startsWith("当前 Wi-Fi"))
    }

    @Test
    fun `wifi direct public and link local addresses are rejected`() {
        val result = NetworkManager.rankCandidates(
            listOf(
                NetworkCandidate("192.168.49.1", "p2p0", NetworkTransport.WIFI),
                NetworkCandidate("169.254.1.2", "wlan0", NetworkTransport.WIFI),
                NetworkCandidate("8.8.8.8", "wlan0", NetworkTransport.WIFI),
            ),
            activeInterface = null,
            activeIsWifi = false,
        )

        assertTrue(result.isEmpty())
    }
}
