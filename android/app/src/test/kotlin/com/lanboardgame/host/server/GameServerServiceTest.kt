package com.lanboardgame.host.server

import org.junit.Assert.assertEquals
import org.junit.Test

class GameServerServiceTest {
    @Test
    fun undeployedServerIsRestrictedToLoopback() {
        assertEquals("127.0.0.1", serverBindAddress(deployed = false))
    }

    @Test
    fun deployedServerListensOnLanInterfaces() {
        assertEquals("0.0.0.0", serverBindAddress(deployed = true))
    }
}
