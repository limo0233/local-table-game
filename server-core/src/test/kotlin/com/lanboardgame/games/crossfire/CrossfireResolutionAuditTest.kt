package com.lanboardgame.games.crossfire

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossfireResolutionAuditTest {
    @Test
    fun `an agent shot disables the assassin before the assassin can hit VIP`() {
        val game = readyGame(9)
        val roleOwners = game.playerIds.associateWith { requireNotNull(game.viewFor(it).finalRole) }
        val vip = roleOwners.entries.single { it.value == CrossfireRole.VIP }.key
        val assassin = roleOwners.entries.first { it.value == CrossfireRole.ASSASSIN }.key
        val agent = roleOwners.entries.first { it.value == CrossfireRole.AGENT }.key

        game.startTargeting(game.dealerPlayerId, game.dealerPlayerId)
        game.playerIds.forEach { actor ->
            val target = when (actor) {
                agent -> assassin
                assassin -> vip
                else -> game.playerIds.first { it != actor && it != vip }
            }
            game.submitTarget(actor, target)
        }

        assertEquals(CrossfireTeam.AGENTS, game.winnerTeam)
        assertTrue(game.viewFor(agent).public.history.any { "失去射击能力" in it })
    }

    @Test
    fun `an agent can accidentally shoot the VIP and give red team the win`() {
        val game = readyGame(5)
        val owners = ownersByRole(game)
        val vip = owners.getValue(CrossfireRole.VIP).single()
        val agent = owners.getValue(CrossfireRole.AGENT).single()
        val assassin = owners.getValue(CrossfireRole.ASSASSIN).single()

        resolveWith(game, mapOf(agent to vip, assassin to agent))

        assertEquals(CrossfireTeam.ASSASSIN, game.winnerTeam)
        assertFalse(game.viewFor(agent).public.resultByPlayer.getValue(agent).won)
        assertTrue(game.viewFor(assassin).public.resultByPlayer.getValue(assassin).won)
    }

    @Test
    fun `decoy wins when shot while unshot bystander also wins`() {
        val game = readyGame(7)
        val owners = ownersByRole(game)
        val agents = owners.getValue(CrossfireRole.AGENT)
        val assassins = owners.getValue(CrossfireRole.ASSASSIN)
        val decoy = owners.getValue(CrossfireRole.DECOY).single()
        val bystander = owners.getValue(CrossfireRole.BYSTANDER).single()

        resolveWith(game, mapOf(agents[0] to decoy, agents[1] to assassins[0], assassins[0] to agents[0], assassins[1] to agents[0]))

        val results = game.viewFor(decoy).public.resultByPlayer
        assertTrue(results.getValue(decoy).won)
        assertTrue(results.getValue(bystander).won)
    }

    @Test
    fun `shooting a bystander makes both bystander and actual shooter lose`() {
        val game = readyGame(7)
        val owners = ownersByRole(game)
        val agents = owners.getValue(CrossfireRole.AGENT)
        val assassins = owners.getValue(CrossfireRole.ASSASSIN)
        val bystander = owners.getValue(CrossfireRole.BYSTANDER).single()

        resolveWith(game, mapOf(agents[0] to bystander, agents[1] to assassins[0], assassins[0] to agents[0], assassins[1] to agents[0]))

        val results = game.viewFor(bystander).public.resultByPlayer
        assertFalse(results.getValue(bystander).won)
        assertFalse(results.getValue(agents[0]).won)
        assertTrue(results.getValue(agents[1]).won)
    }

    @Test
    fun `team decoys honor their alternate shot win conditions`() {
        val redGame = readyGame(5)
        val redOwners = ownersByRole(redGame)
        val redDecoy = redOwners.getValue(CrossfireRole.RED_DECOY).single()
        resolveWith(redGame, mapOf(
            redOwners.getValue(CrossfireRole.AGENT).single() to redDecoy,
            redOwners.getValue(CrossfireRole.ASSASSIN).single() to redOwners.getValue(CrossfireRole.AGENT).single(),
        ))
        assertEquals(CrossfireTeam.AGENTS, redGame.winnerTeam)
        assertTrue(redGame.viewFor(redDecoy).public.resultByPlayer.getValue(redDecoy).won)

        val blueGame = readyGame(6)
        val blueOwners = ownersByRole(blueGame)
        val blueDecoy = blueOwners.getValue(CrossfireRole.BLUE_DECOY).single()
        val assassins = blueOwners.getValue(CrossfireRole.ASSASSIN)
        val vip = blueOwners.getValue(CrossfireRole.VIP).single()
        val agent = blueOwners.getValue(CrossfireRole.AGENT).single()
        resolveWith(blueGame, mapOf(agent to blueDecoy, assassins[0] to blueDecoy, assassins[1] to vip))
        assertEquals(CrossfireTeam.ASSASSIN, blueGame.winnerTeam)
        assertTrue(blueGame.viewFor(blueDecoy).public.resultByPlayer.getValue(blueDecoy).won)
    }

    private fun ownersByRole(game: CrossfireGame): Map<CrossfireRole, List<String>> =
        game.playerIds.groupBy { requireNotNull(game.viewFor(it).finalRole) }

    private fun resolveWith(game: CrossfireGame, targets: Map<String, String>) {
        game.startTargeting(game.dealerPlayerId, game.dealerPlayerId)
        game.playerIds.forEach { actor ->
            game.submitTarget(actor, targets[actor] ?: game.playerIds.first { it != actor })
        }
    }

    private fun readyGame(count: Int): CrossfireGame {
        val game = CrossfireGame.create((1..count).map { "p$it" }, Random(83))
        game.playerIds.forEach(game::confirmInitial)
        game.playerIds.forEach(game::confirmIntel)
        while (game.phase == CrossfirePhase.SHUFFLE) game.currentShuffleGroup.forEach(game::confirmShuffle)
        return game
    }
}
