package com.lanboardgame.games.avalon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AvalonRulesAuditTest {
    @Test
    fun `six player tied team vote is rejected`() {
        val game = AvalonGame.fromRoles(linkedMapOf(
            "merlin" to AvalonRole.MERLIN,
            "percival" to AvalonRole.PERCIVAL,
            "loyal1" to AvalonRole.LOYAL_SERVANT,
            "loyal2" to AvalonRole.LOYAL_SERVANT,
            "assassin" to AvalonRole.ASSASSIN,
            "morgana" to AvalonRole.MORGANA,
        ), "merlin")
        game.selectTeam("merlin", listOf("merlin", "assassin"))
        game.playerIds.take(3).forEach { game.vote(it, true) }
        game.playerIds.drop(3).forEach { game.vote(it, false) }
        assertEquals(AvalonPhase.TEAM_SELECTION, game.phase)
        assertEquals(1, game.proposalRejects)
        assertFalse(game.viewFor("merlin").public.voteHistory.single().approved)
    }

    @Test
    fun `five rejected proposals immediately give evil the win`() {
        val game = fivePlayerGame()
        repeat(5) {
            game.selectTeam(game.leaderId, game.playerIds.take(game.requiredTeamSize))
            game.playerIds.forEach { game.vote(it, false) }
        }
        assertEquals(AvalonPhase.FINISHED, game.phase)
        assertEquals(AvalonWinner.EVIL, game.winner)
    }

    @Test
    fun `duplicate and out of turn actions are rejected by the server engine`() {
        val game = fivePlayerGame()
        assertFailsWith<IllegalArgumentException> { game.selectTeam("assassin", listOf("merlin", "assassin")) }
        game.selectTeam("merlin", listOf("merlin", "assassin"))
        game.vote("merlin", true)
        assertFailsWith<IllegalArgumentException> { game.vote("merlin", false) }
        assertFailsWith<IllegalArgumentException> { game.submitMission("merlin", true) }
    }

    @Test
    fun `three failed missions immediately give evil the win`() {
        val game = fivePlayerGame()
        repeat(3) {
            runMission(game, setOf("assassin"))
        }
        assertEquals(AvalonPhase.FINISHED, game.phase)
        assertEquals(AvalonWinner.EVIL, game.winner)
    }

    @Test
    fun `assassin hitting Merlin overturns three successful missions`() {
        val game = fivePlayerGame()
        repeat(3) { runMission(game, emptySet()) }
        assertEquals(AvalonPhase.ASSASSINATION, game.phase)
        game.assassinate("assassin", "merlin")
        assertEquals(AvalonWinner.EVIL, game.winner)
    }

    @Test
    fun `seven player fourth mission needs two failures`() {
        val oneFailure = sevenPlayerGame()
        reachFourthMission(oneFailure)
        runMission(oneFailure, setOf("assassin"))
        assertTrue(oneFailure.viewFor("merlin").public.missions.last().success == true)

        val twoFailures = sevenPlayerGame()
        reachFourthMission(twoFailures)
        runMission(twoFailures, setOf("assassin", "morgana"))
        assertTrue(twoFailures.viewFor("merlin").public.missions.last().success == false)
    }

    private fun reachFourthMission(game: AvalonGame) {
        runMission(game, emptySet())
        runMission(game, emptySet())
        runMission(game, setOf("assassin"))
        assertEquals(4, game.round)
    }

    private fun runMission(game: AvalonGame, failingPlayers: Set<String>) {
        val selected = (failingPlayers + game.playerIds.filterNot { it in failingPlayers })
            .take(game.requiredTeamSize)
        game.selectTeam(game.leaderId, selected)
        game.playerIds.forEach { game.vote(it, true) }
        selected.forEach { playerId -> game.submitMission(playerId, playerId !in failingPlayers) }
    }

    private fun sevenPlayerGame() = AvalonGame.fromRoles(linkedMapOf(
        "merlin" to AvalonRole.MERLIN,
        "percival" to AvalonRole.PERCIVAL,
        "loyal1" to AvalonRole.LOYAL_SERVANT,
        "loyal2" to AvalonRole.LOYAL_SERVANT,
        "assassin" to AvalonRole.ASSASSIN,
        "morgana" to AvalonRole.MORGANA,
        "oberon" to AvalonRole.OBERON,
    ), "merlin")

    private fun fivePlayerGame() = AvalonGame.fromRoles(linkedMapOf(
        "merlin" to AvalonRole.MERLIN,
        "percival" to AvalonRole.PERCIVAL,
        "loyal" to AvalonRole.LOYAL_SERVANT,
        "assassin" to AvalonRole.ASSASSIN,
        "morgana" to AvalonRole.MORGANA,
    ), "merlin")
}
