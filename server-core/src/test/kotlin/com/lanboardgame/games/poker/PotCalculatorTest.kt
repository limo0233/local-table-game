package com.lanboardgame.games.poker

import kotlin.test.Test
import kotlin.test.assertEquals

class PotCalculatorTest {
    @Test
    fun `builds main pot and multiple side pots with folded money included`() {
        val pots = PotCalculator.build(
            contributions = linkedMapOf("A" to 100, "B" to 300, "C" to 500, "D" to 500),
            foldedPlayerIds = setOf("D"),
        )
        assertEquals(listOf(400, 600, 400), pots.map { it.amount })
        assertEquals(listOf("A", "B", "C"), pots[0].eligiblePlayerIds)
        assertEquals(listOf("B", "C"), pots[1].eligiblePlayerIds)
        assertEquals(listOf("C"), pots[2].eligiblePlayerIds)
        assertEquals(1400, pots.sumOf { it.amount })
    }

    @Test
    fun `splits tied pots and assigns odd chip clockwise`() {
        val board = listOf("2s", "3d", "4c", "5h", "9s").map(Card::parse)
        val values = mapOf(
            "A" to HandEvaluator.evaluate(board + listOf("As", "Kd").map(Card::parse)),
            "B" to HandEvaluator.evaluate(board + listOf("Ah", "Kc").map(Card::parse)),
        )
        val awards = PotCalculator.award(listOf(Pot(101, listOf("A", "B"))), values, listOf("B", "A"))
        assertEquals(mapOf("B" to 51, "A" to 50), awards.single().shares)
        assertEquals(101, awards.single().shares.values.sum())
    }
}

