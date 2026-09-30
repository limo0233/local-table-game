package com.lanboardgame.games.avalon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AvalonGameTest {
    private fun game(): AvalonGame = AvalonGame.fromRoles(linkedMapOf(
        "merlin" to AvalonRole.MERLIN,
        "percival" to AvalonRole.PERCIVAL,
        "loyal" to AvalonRole.LOYAL_SERVANT,
        "assassin" to AvalonRole.ASSASSIN,
        "morgana" to AvalonRole.MORGANA,
    ), leaderId = "merlin")

    @Test
    fun `role distribution has correct good and evil counts`() {
        for (count in 5..10) {
            val roles = AvalonGame.rolesFor(count)
            val expectedEvil = when (count) { 5, 6 -> 2; 7, 8, 9 -> 3; else -> 4 }
            assertEquals(count, roles.size)
            assertEquals(expectedEvil, roles.count { it.evil })
            assertEquals(1, roles.count { it == AvalonRole.MERLIN })
            assertEquals(1, roles.count { it == AvalonRole.ASSASSIN })
        }
    }

    @Test
    fun `private role visibility is server filtered`() {
        val game = game()
        assertEquals(setOf("assassin", "morgana"), game.knowledgeFor("merlin").map { it.playerId }.toSet())
        assertEquals(setOf("merlin", "morgana"), game.knowledgeFor("percival").map { it.playerId }.toSet())
        assertEquals(setOf("morgana"), game.knowledgeFor("assassin").map { it.playerId }.toSet())
        assertTrue(game.knowledgeFor("loyal").isEmpty())
    }

    @Test
    fun `vote mission and assassination determine winner`() {
        val game = game()
        repeat(3) {
            val team = if (game.requiredTeamSize == 2) listOf("merlin", "loyal") else listOf("merlin", "percival", "loyal")
            game.selectTeam(game.leaderId, team)
            listOf("merlin", "percival", "loyal", "assassin", "morgana").forEach { game.vote(it, true) }
            team.forEach { game.submitMission(it, true) }
        }
        assertEquals(AvalonPhase.ASSASSINATION, game.phase)
        game.assassinate("assassin", "loyal")
        assertEquals(AvalonWinner.GOOD, game.winner)
    }

    @Test
    fun `good players cannot submit a failure card`() {
        val game = game()
        game.selectTeam("merlin", listOf("merlin", "assassin"))
        listOf("merlin", "percival", "loyal", "assassin", "morgana").forEach { game.vote(it, true) }
        assertFalse(game.roleOf("merlin").evil)
        kotlin.test.assertFailsWith<IllegalArgumentException> { game.submitMission("merlin", false) }
    }

    @Test
    fun `host role configuration is validated and used`() {
        val config = AvalonConfig(6, listOf(
            AvalonRole.MERLIN,
            AvalonRole.LOYAL_SERVANT,
            AvalonRole.LOYAL_SERVANT,
            AvalonRole.LOYAL_SERVANT,
            AvalonRole.ASSASSIN,
            AvalonRole.MORDRED,
        ))
        val ids = (1..6).map { "p$it" }
        val game = AvalonGame.create(ids, config, kotlin.random.Random(7))
        assertEquals(config.roles.groupingBy { it }.eachCount(), ids.map(game::roleOf).groupingBy { it }.eachCount())

        assertFailsWith<IllegalArgumentException> {
            AvalonGame.validateConfig(config.copy(roles = config.roles.dropLast(1) + AvalonRole.MERLIN))
        }
    }

    @Test
    fun `completed team votes expose individual approval history`() {
        val game = game()
        game.selectTeam("merlin", listOf("merlin", "assassin"))
        game.vote("merlin", true)
        game.vote("percival", true)
        game.vote("loyal", true)
        game.vote("assassin", false)
        game.vote("morgana", false)

        val public = game.viewFor("loyal").public
        assertEquals(1, public.totalVoteCount)
        assertEquals(1, public.voteHistory.single().round)
        assertEquals(1, public.voteHistory.single().proposal)
        assertTrue(public.voteHistory.single().approved)
        assertEquals(false, public.voteHistory.single().votes.getValue("assassin"))
        assertEquals(true, public.voteHistory.single().votes.getValue("merlin"))
    }
}
