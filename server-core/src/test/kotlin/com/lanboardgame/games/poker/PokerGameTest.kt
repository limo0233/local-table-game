package com.lanboardgame.games.poker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PokerGameTest {
    private fun deck() = Deck.fixed(
        "As", "Ks", "Ah", "Kh", "2c", "3c", "4c", "5d", "9s",
        "Qd", "Jd", "10d", "8d", "7d", "6d", "5h", "4h", "3h", "2h",
    )

    @Test
    fun `heads up action order and street progression are correct`() {
        val game = PokerGame.create(listOf("A", "B"), PokerConfig(startingChips = 100), deck())
        assertEquals(PokerPhase.PREFLOP, game.phase)
        assertEquals("A", game.viewFor("A").public.actingPlayerId)
        game.act("A", PokerAction(PokerActionType.CALL))
        game.act("B", PokerAction(PokerActionType.CHECK))
        assertEquals(PokerPhase.FLOP, game.phase)
        assertEquals("B", game.viewFor("B").public.actingPlayerId)
        assertEquals(3, game.communityCards.size)
    }

    @Test
    fun `full raise resets action and enforces minimum raise`() {
        val game = PokerGame.create(listOf("A", "B", "C"), PokerConfig(startingChips = 1000), Deck.shuffled())
        assertEquals("A", game.viewFor("A").public.actingPlayerId)
        game.act("A", PokerAction(PokerActionType.RAISE_TO, 60))
        assertEquals("B", game.viewFor("B").public.actingPlayerId)
        assertEquals(100, game.viewFor("B").public.minRaiseTo)
        assertFailsWith<IllegalArgumentException> {
            game.validateAction("B", PokerAction(PokerActionType.RAISE_TO, 80))
        }
    }

    @Test
    fun `all in is available and private cards never leak before showdown`() {
        val game = PokerGame.create(listOf("A", "B", "C"), PokerConfig(startingChips = 100), Deck.shuffled())
        val view = game.viewFor("A")
        assertTrue(PokerActionType.ALL_IN in view.availableActions)
        assertEquals(2, view.myCards.size)
        assertTrue(view.public.seats.all { it.revealedCards == null })
    }

    @Test
    fun `short all in does not reopen raising for a player who already acted`() {
        val game = PokerGame.create(listOf("A", "B", "C"), PokerConfig(startingChips = 100), Deck.shuffled())
        game.act("A", PokerAction(PokerActionType.RAISE_TO, 60))
        game.players.first { it.id == "B" }.chips = 60
        game.act("B", PokerAction(PokerActionType.ALL_IN))
        game.act("C", PokerAction(PokerActionType.CALL))
        val actions = game.availableActions("A")
        assertTrue(PokerActionType.CALL in actions)
        assertTrue(PokerActionType.RAISE_TO !in actions)
        assertTrue(PokerActionType.ALL_IN !in actions)
    }
}
