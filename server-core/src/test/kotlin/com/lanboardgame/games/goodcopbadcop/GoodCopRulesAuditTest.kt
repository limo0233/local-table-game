package com.lanboardgame.games.goodcopbadcop

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoodCopRulesAuditTest {
    @Test
    fun `agent or kingpin is wounded once and eliminated by the second unblocked shot`() {
        val game = GoodCopBadCopGame.create((1..4).map { "p$it" }, Random(17))
        val shooter = game.currentPlayerId
        val leader = game.playerIds.first { playerId ->
            playerId != shooter && game.viewFor(playerId).myIntegrity.any { it.type == IntegrityType.AGENT || it.type == IntegrityType.KINGPIN }
        }
        val expectedWinner = if (game.viewFor(leader).myIntegrity.any { it.type == IntegrityType.AGENT }) GoodCopTeam.CROOKED else GoodCopTeam.HONEST

        shoot(game, shooter, leader)
        assertTrue(leader in game.viewFor(shooter).public.wounded)
        assertTrue(game.viewFor(leader).myIntegrity.all { it.revealed })
        while (game.phase == GoodCopPhase.TURN && game.currentPlayerId != shooter) {
            val actor = game.currentPlayerId
            val target = game.playerIds.first { it != actor && it != leader }
            game.investigate(actor, target, 0)
        }
        shoot(game, shooter, leader)

        assertEquals(GoodCopPhase.GAME_OVER, game.phase)
        assertEquals(expectedWinner, game.winnerTeam)
        assertTrue(leader in game.viewFor(shooter).public.eliminated)
    }

    @Test
    fun `shield is consumed once and prevents that shot from revealing or eliminating`() {
        val game = GoodCopBadCopGame.create((1..4).map { "p$it" }, Random(9))
        game.equip("p1")
        val hiddenAfterEquip = game.viewFor("p1").myIntegrity.count { !it.revealed }
        shoot(game, "p2", "p1")
        val after = game.viewFor("p1")
        assertEquals(0, after.myEquipment.size)
        assertEquals(hiddenAfterEquip, after.myIntegrity.count { !it.revealed })
        assertTrue("p1" !in after.public.eliminated)
        assertTrue(after.public.history.last().detail?.contains("抵消") == true)
    }

    private fun shoot(game: GoodCopBadCopGame, shooter: String, target: String) {
        game.arm(shooter)
        game.aim(shooter, target)
        game.shoot(shooter)
    }
}
