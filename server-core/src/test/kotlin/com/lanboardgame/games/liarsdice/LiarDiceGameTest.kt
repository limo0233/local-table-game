package com.lanboardgame.games.liarsdice

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiarDiceGameTest {
    @Test
    fun `all players receive the same configured dice count`() {
        val game = LiarDiceGame.create(listOf("a", "b", "c"), 10, Random(3))
        assertEquals(10, game.viewFor("a").myDice.size)
        assertEquals(10, game.viewFor("b").myDice.size)
        assertEquals(mapOf("a" to 10, "b" to 10, "c" to 10), game.viewFor("a").public.diceCount)
        assertTrue(!game.viewFor("a").public.allSettled)
        assertTrue(game.viewFor("a").public.resultsByPlayer.isEmpty())
    }

    @Test
    fun `reroll replaces the complete hand and settle locks a player`() {
        val game = LiarDiceGame.create(listOf("a", "b"), 3, Random(3))
        val before = game.viewFor("a").myDice
        game.reroll("a")
        assertEquals(3, game.viewFor("a").myDice.size)
        game.settle("a")
        assertTrue(game.viewFor("a").settled)
        assertFailsWith<IllegalArgumentException> { game.reroll("a") }
        assertFailsWith<IllegalArgumentException> { game.settle("a") }
        assertEquals(before.size, game.viewFor("a").myDice.size)
    }

    @Test
    fun `results and point totals remain hidden until every player settles`() {
        val game = LiarDiceGame.create(listOf("a", "b"), 2, Random(8))
        game.settle("a")
        assertTrue(!game.viewFor("a").public.allSettled)
        assertTrue(game.viewFor("a").public.resultsByPlayer.isEmpty())
        game.settle("b")
        val view = game.viewFor("a").public
        assertTrue(view.allSettled)
        assertEquals(2, view.resultsByPlayer.getValue("a").size)
        assertEquals(2, view.resultsByPlayer.getValue("b").size)
        assertEquals(4, view.pointCounts.values.sum())
    }

    @Test
    fun `next round requires every player and resets private results`() {
        val game = LiarDiceGame.create(listOf("a", "b"), 2, Random(9))
        game.settle("a")
        game.settle("b")
        val firstRound = game.viewFor("a").public.round
        game.nextRound("a")
        assertEquals(setOf("a"), game.viewFor("a").public.nextRoundPlayerIds)
        assertTrue(game.viewFor("a").public.allSettled)
        game.nextRound("b")
        val next = game.viewFor("a")
        assertEquals(firstRound + 1, next.public.round)
        assertTrue(!next.public.allSettled)
        assertTrue(next.public.resultsByPlayer.isEmpty())
        assertTrue(next.canReroll && next.canSettle)
    }

    @Test
    fun `player and dice limits are enforced`() {
        val maximum = LiarDiceGame.create((1..10).map { "p$it" }, 10, Random(1))
        assertEquals(10, maximum.playerIds.size)
        assertTrue(maximum.playerIds.all { maximum.viewFor(it).myDice.size == 10 })
        assertFailsWith<IllegalArgumentException> { LiarDiceGame.create(listOf("only-one"), 5, Random(1)) }
        assertFailsWith<IllegalArgumentException> { LiarDiceGame.create((1..11).map { "p$it" }, 5, Random(1)) }
        assertFailsWith<IllegalArgumentException> { LiarDiceGame.create(listOf("a", "b"), 11, Random(1)) }
        assertFailsWith<IllegalArgumentException> { LiarDiceGame.create(listOf("a", "b"), 0, Random(1)) }
    }

    @Test
    fun `unknown players and premature next round are rejected`() {
        val game = LiarDiceGame.create(listOf("a", "b"), 2, Random(2))
        assertFailsWith<IllegalArgumentException> { game.reroll("intruder") }
        assertFailsWith<IllegalArgumentException> { game.settle("intruder") }
        assertFailsWith<IllegalArgumentException> { game.nextRound("a") }
        assertFailsWith<IllegalArgumentException> { game.viewFor("intruder") }
    }
}
