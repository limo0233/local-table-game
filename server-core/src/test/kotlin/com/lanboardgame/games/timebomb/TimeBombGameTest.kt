package com.lanboardgame.games.timebomb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimeBombGameTest {
    @Test
    fun `role confirmation starts the first cutting round`() {
        val game = TimeBombGame.create(listOf("a", "b", "c", "d"), Random(4))
        game.playerIds.forEach(game::confirmRole)

        val view = game.viewFor("a")
        assertEquals(TimeBombPhase.ROUND, view.public.phase)
        assertEquals(5, view.privateWires.size)
        assertTrue(view.canCut)
    }

    @Test
    fun `cutter cannot select self or an invalid wire`() {
        val game = TimeBombGame.create(listOf("a", "b", "c", "d"), Random(4))
        game.playerIds.forEach(game::confirmRole)
        assertFailsWith<IllegalArgumentException> { game.cut("a", "a", 0) }
        assertFailsWith<IllegalArgumentException> { game.cut("a", "b", 99) }
    }
}
