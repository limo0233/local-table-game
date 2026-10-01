package com.lanboardgame.host.server

import com.lanboardgame.games.crossfire.CrossfireGame
import com.lanboardgame.games.crossfire.CrossfirePhase
import com.lanboardgame.games.liarsdice.LiarDiceGame
import com.lanboardgame.platform.GameType
import com.lanboardgame.platform.RoomManager
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestBotControllerTest {
    @Test
    fun diceBotSettlesWithoutHoldingTheRoundOpen() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.LIARS_DICE, "房主", "host-session")
        manager.addTestBots(created.room.roomId, created.player.id, count = 1)
        manager.start(created.room.roomId, created.player.id)
        val game = created.room.game as LiarDiceGame
        val controller = TestBotController(manager, broadcast = {}, random = Random(4))

        try {
            game.settle(created.player.id)
            controller.schedule(created.room.roomId)

            awaitCondition { game.viewFor(created.player.id).public.allSettled }
            assertTrue(game.viewFor(created.player.id).public.allSettled)
        } finally {
            controller.shutdown()
        }
    }

    @Test
    fun crossfireOnlyConfirmsBotsInsideTheCurrentShuffleGroup() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.CROSSFIRE, "房主", "host-session")
        manager.addTestBots(created.room.roomId, created.player.id, count = 4)
        manager.start(created.room.roomId, created.player.id)
        val game = created.room.game as CrossfireGame
        val bots = created.room.players.values.filter { it.isBot }.map { it.id }

        game.confirmInitial(created.player.id)
        bots.forEach(game::confirmInitial)
        game.confirmIntel(created.player.id)
        bots.forEach(game::confirmIntel)
        assertEquals(CrossfirePhase.SHUFFLE, game.phase)

        val groupBots = game.currentShuffleGroup.filter { it in bots }.toSet()
        assertEquals(2, groupBots.size)
        val controller = TestBotController(manager, broadcast = {}, random = Random(7))

        try {
            controller.schedule(created.room.roomId)

            awaitCondition {
                groupBots.all { it in game.viewFor(created.player.id).public.confirmedPlayerIds }
            }
            assertEquals(
                groupBots,
                game.viewFor(created.player.id).public.confirmedPlayerIds.filter { it in bots }.toSet(),
            )
        } finally {
            controller.shutdown()
        }
    }

    private fun awaitCondition(timeoutMillis: Long = 1_500, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(5)
    }
}
