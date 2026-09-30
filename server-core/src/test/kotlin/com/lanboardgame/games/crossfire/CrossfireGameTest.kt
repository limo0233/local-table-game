package com.lanboardgame.games.crossfire

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CrossfireGameTest {
    @Test
    fun `all information confirmations lead to discussion`() {
        val game = completeToDiscussion(5)
        val view = game.viewFor("p1")

        assertEquals(CrossfirePhase.DISCUSSION, view.public.phase)
        assertTrue(view.seenRoles.isNotEmpty())
        assertEquals("p2", view.seenRoles.single().sourcePlayerId)
        assertEquals(1, view.shuffleHistory.size)
        assertEquals(listOf("p5", "p1", "p2"), view.shuffleHistory.first().playerIds)
        assertTrue(view.finalRole != null)
    }

    @Test
    fun `scheduled groups match the official 5 to 10 player patterns`() {
        val expected = mapOf(
            5 to listOf(listOf("p5", "p1", "p2"), listOf("p3", "p4", "p5")),
            6 to listOf(listOf("p6", "p1", "p2"), listOf("p3", "p4", "p5")),
            7 to listOf(listOf("p7", "p1", "p2"), listOf("p3", "p4", "p5"), listOf("p6", "p7", "p1")),
            8 to listOf(listOf("p8", "p1", "p2"), listOf("p3", "p4", "p5"), listOf("p6", "p7", "p8")),
            9 to listOf(listOf("p9", "p1", "p2"), listOf("p3", "p4", "p5"), listOf("p6", "p7", "p8")),
            10 to listOf(listOf("p10", "p1", "p2"), listOf("p3", "p4", "p5"), listOf("p6", "p7", "p8"), listOf("p9", "p10", "p1")),
        )
        expected.forEach { (count, groups) ->
            val game = CrossfireGame.create((1..count).map { "p$it" }, Random(100 + count))
            assertEquals(groups, game.shuffleGroups)
            game.playerIds.forEach(game::confirmInitial)
            game.playerIds.forEach(game::confirmIntel)
            completeShuffle(game)
            assertEquals(CrossfirePhase.DISCUSSION, game.phase)
            game.playerIds.forEach { playerId ->
                val expectedHistory = groups.filter { playerId in it }
                assertEquals(expectedHistory, game.viewFor(playerId).shuffleHistory.map { it.playerIds })
            }
            assertTrue(game.playerIds.none { game.viewFor(it).shuffleHistory.size >= 3 })
        }
    }

    @Test
    fun `dealer rotates the group schedule and circular neighbours`() {
        val players = (1..10).map { "p$it" }
        val game = CrossfireGame.create(players, "p4", Random(2))
        assertEquals("p4", game.dealerPlayerId)
        assertEquals(
            listOf(
                listOf("p3", "p4", "p5"),
                listOf("p6", "p7", "p8"),
                listOf("p9", "p10", "p1"),
                listOf("p2", "p3", "p4"),
            ),
            game.shuffleGroups,
        )
        game.playerIds.forEach(game::confirmInitial)
        game.playerIds.forEach(game::confirmIntel)
        completeShuffle(game)
        assertEquals(2, game.viewFor("p4").shuffleHistory.size)
        assertEquals(1, game.viewFor("p1").shuffleHistory.size)
    }

    @Test
    fun `overlapping groups use the prior group current roles`() {
        val game = CrossfireGame.create((1..5).map { "p$it" }, Random(4))
        game.playerIds.forEach(game::confirmInitial)
        game.playerIds.forEach(game::confirmIntel)
        val firstGroup = game.currentShuffleGroup
        assertTrue(game.viewFor("p1").canConfirmShuffle)
        assertTrue(!game.viewFor("p3").canConfirmShuffle)
        assertFailsWith<IllegalArgumentException> { game.confirmShuffle("p3") }
        firstGroup.forEach(game::confirmShuffle)
        assertEquals(1, game.viewFor("p5").shuffleHistory.size)
        assertTrue(game.viewFor("p1").currentRole != null)
        assertEquals(null, game.viewFor("p3").currentRole)
        val priorPool = listOf(
            game.viewFor("p3").seenRoles.single().role,
            game.viewFor("p4").seenRoles.single().role,
            requireNotNull(game.viewFor("p5").currentRole),
        ).sortedBy { it.name }
        assertEquals(listOf("p3", "p4", "p5"), game.currentShuffleGroup)
        game.currentShuffleGroup.forEach(game::confirmShuffle)
        assertEquals(CrossfirePhase.DISCUSSION, game.phase)
        assertEquals(2, game.viewFor("p5").shuffleHistory.size)
        val secondPool = listOf("p3", "p4", "p5").mapNotNull { game.viewFor(it).finalRole }.sortedBy { it.name }
        assertEquals(priorPool, secondPool)
    }

    @Test
    fun `left pass and every scheduled shuffle update the real current role map`() {
        val players = (1..10).map { "p$it" }
        val seed = 37
        val oracle = Random(seed)
        val dealtRoles = players.zip(CrossfireGame.defaultRoles(players.size).shuffled(oracle)).toMap()
        val expectedCurrent = linkedMapOf<String, CrossfireRole>()
        players.forEachIndexed { index, playerId ->
            expectedCurrent[playerId] = dealtRoles.getValue(players[(index + 1) % players.size])
        }

        val game = CrossfireGame.create(players, "p1", Random(seed))
        players.forEach { playerId -> assertEquals(dealtRoles.getValue(playerId), game.viewFor(playerId).initialRole) }
        players.forEach(game::confirmInitial)
        players.forEach { playerId ->
            val expectedSource = players[(players.indexOf(playerId) + 1) % players.size]
            assertEquals(CrossfireIntel(expectedSource, expectedCurrent.getValue(playerId)), game.viewFor(playerId).seenRoles.single())
        }
        players.forEach(game::confirmIntel)

        game.shuffleGroups.forEach { group ->
            val shuffled = group.map { expectedCurrent.getValue(it) }.shuffled(oracle)
            group.forEachIndexed { index, playerId -> expectedCurrent[playerId] = shuffled[index] }
            group.forEach(game::confirmShuffle)
        }

        players.forEach { playerId -> assertEquals(expectedCurrent.getValue(playerId), game.viewFor(playerId).finalRole) }
    }

    @Test
    fun `target submissions resolve simultaneously without leaking roles before game over`() {
        val game = completeToDiscussion(5)
        assertTrue(game.playerIds.all { game.viewFor(it).finalRole != null })
        assertTrue(game.viewFor("p1").public.revealedRoles.isEmpty())
        game.startTargeting("p1", "p1")
        game.playerIds.forEachIndexed { index, player -> game.submitTarget(player, game.playerIds[(index + 1) % game.playerIds.size]) }

        assertEquals(CrossfirePhase.GAME_OVER, game.viewFor("p1").public.phase)
        assertEquals(5, game.viewFor("p1").public.revealedRoles.size)
    }

    @Test
    fun `ten player role table keeps both team decoys and a bystander`() {
        val roles = CrossfireGame.defaultRoles(10)
        assertEquals(10, roles.size)
        assertEquals(1, roles.count { it == CrossfireRole.VIP })
        assertEquals(3, roles.count { it == CrossfireRole.AGENT })
        assertEquals(3, roles.count { it == CrossfireRole.ASSASSIN })
        assertEquals(0, roles.count { it == CrossfireRole.DECOY })
        assertEquals(1, roles.count { it == CrossfireRole.BLUE_DECOY })
        assertEquals(1, roles.count { it == CrossfireRole.RED_DECOY })
        assertEquals(1, roles.count { it == CrossfireRole.BYSTANDER })
    }

    @Test
    fun `official standard role table is used from five through ten players`() {
        val expected = mapOf(
            5 to mapOf(CrossfireRole.VIP to 1, CrossfireRole.AGENT to 1, CrossfireRole.ASSASSIN to 1, CrossfireRole.RED_DECOY to 1, CrossfireRole.BYSTANDER to 1),
            6 to mapOf(CrossfireRole.VIP to 1, CrossfireRole.AGENT to 1, CrossfireRole.ASSASSIN to 2, CrossfireRole.BLUE_DECOY to 1, CrossfireRole.BYSTANDER to 1),
            7 to mapOf(CrossfireRole.VIP to 1, CrossfireRole.AGENT to 2, CrossfireRole.ASSASSIN to 2, CrossfireRole.DECOY to 1, CrossfireRole.BYSTANDER to 1),
            8 to mapOf(CrossfireRole.VIP to 1, CrossfireRole.AGENT to 2, CrossfireRole.ASSASSIN to 2, CrossfireRole.DECOY to 1, CrossfireRole.RED_DECOY to 1, CrossfireRole.BYSTANDER to 1),
            9 to mapOf(CrossfireRole.VIP to 1, CrossfireRole.AGENT to 3, CrossfireRole.ASSASSIN to 3, CrossfireRole.DECOY to 1, CrossfireRole.BYSTANDER to 1),
            10 to mapOf(CrossfireRole.VIP to 1, CrossfireRole.AGENT to 3, CrossfireRole.ASSASSIN to 3, CrossfireRole.RED_DECOY to 1, CrossfireRole.BLUE_DECOY to 1, CrossfireRole.BYSTANDER to 1),
        )
        expected.forEach { (count, roleCounts) ->
            assertEquals(roleCounts, CrossfireGame.defaultRoles(count).groupingBy { it }.eachCount())
        }
    }

    private fun completeToDiscussion(playerCount: Int): CrossfireGame {
        val game = CrossfireGame.create((1..playerCount).map { "p$it" }, Random(8))
        game.playerIds.forEach(game::confirmInitial)
        game.playerIds.forEach(game::confirmIntel)
        completeShuffle(game)
        return game
    }

    private fun completeShuffle(game: CrossfireGame) {
        while (game.phase == CrossfirePhase.SHUFFLE) {
            game.currentShuffleGroup.forEach(game::confirmShuffle)
        }
    }
}
