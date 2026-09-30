package com.lanboardgame.games.poker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandEvaluatorTest {
    private fun hand(vararg cards: String) = HandEvaluator.evaluate(cards.map(Card::parse))

    @Test
    fun `recognizes every requested category`() {
        assertEquals(HandCategory.HIGH_CARD, hand("As", "Kd", "9c", "7h", "3s").category)
        assertEquals(HandCategory.ONE_PAIR, hand("As", "Ad", "9c", "7h", "3s").category)
        assertEquals(HandCategory.TWO_PAIR, hand("As", "Ad", "9c", "9h", "3s").category)
        assertEquals(HandCategory.THREE_OF_A_KIND, hand("As", "Ad", "Ac", "7h", "3s").category)
        assertEquals(HandCategory.STRAIGHT, hand("9s", "8d", "7c", "6h", "5s").category)
        assertEquals(HandCategory.FLUSH, hand("As", "Js", "8s", "4s", "2s").category)
        assertEquals(HandCategory.FULL_HOUSE, hand("As", "Ad", "Ac", "7h", "7s").category)
        assertEquals(HandCategory.FOUR_OF_A_KIND, hand("As", "Ad", "Ac", "Ah", "7s").category)
        assertEquals(HandCategory.STRAIGHT_FLUSH, hand("9s", "8s", "7s", "6s", "5s").category)
        assertEquals(HandCategory.ROYAL_FLUSH, hand("As", "Ks", "Qs", "Js", "10s").category)
    }

    @Test
    fun `selects best five from seven and supports wheel`() {
        val wheel = hand("As", "2d", "3c", "4h", "5s", "Kh", "Qh")
        assertEquals(HandCategory.STRAIGHT, wheel.category)
        assertEquals(5, wheel.kickers.first())

        val fullHouse = hand("As", "Ad", "Ac", "Ks", "Kd", "Kh", "2c")
        assertEquals(listOf(14, 13), fullHouse.kickers)
        assertEquals(5, fullHouse.bestFive.size)
    }

    @Test
    fun `compares kickers and exact ties correctly`() {
        assertTrue(hand("As", "Ad", "Kc", "7h", "3s") > hand("As", "Ad", "Qc", "7h", "3s"))
        assertEquals(0, hand("As", "Kd", "Qc", "Jh", "9s").compareTo(hand("Ah", "Kc", "Qd", "Js", "9c")))
    }
}
