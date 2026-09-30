package com.lanboardgame.games.guandan

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuandanRulesAuditTest {
    private fun game(level: String = "2", seed: Int = 7) =
        GuandanGame.create(listOf("a", "b", "c", "d"), Random(seed), startingLevel = level)

    private fun cards(vararg ranks: String, suit: String = "CLUBS") =
        ranks.mapIndexed { index, rank -> GuandanCard("$rank-$index", rank, suit) }

    @Test
    fun `standard sequence patterns use their exact card counts`() {
        val game = game()
        fun mixed(vararg ranks: String) = ranks.mapIndexed { index, rank ->
            GuandanCard("mixed-$rank-$index", rank, if (index % 2 == 0) "CLUBS" else "SPADES")
        }
        assertEquals(GuandanCombinationType.STRAIGHT, game.classifyForTest(mixed("3", "4", "5", "6", "7")))
        assertEquals(null, game.classifyForTest(mixed("3", "4", "5", "6", "7", "8")))
        assertEquals(GuandanCombinationType.CONSECUTIVE_PAIRS, game.classifyForTest(cards("3", "3", "4", "4", "5", "5")))
        assertEquals(null, game.classifyForTest(cards("3", "3", "4", "4", "5", "5", "6", "6")))
        assertEquals(GuandanCombinationType.CONSECUTIVE_TRIPLES, game.classifyForTest(cards("3", "3", "3", "4", "4", "4")))
        assertEquals(null, game.classifyForTest(cards("3", "3", "3", "4", "4", "4", "5", "5", "5")))
        assertEquals(null, game.classifyForTest(cards("3", "3", "3", "4", "4", "4", "7", "7", "8", "8")))
    }

    @Test
    fun `straight flush contains exactly five cards`() {
        val game = game()
        assertEquals(GuandanCombinationType.STRAIGHT_FLUSH, game.classifyForTest(cards("3", "4", "5", "6", "7", suit = "SPADES")))
        assertEquals(null, game.classifyForTest(cards("3", "4", "5", "6", "7", "8", suit = "SPADES")))
    }

    @Test
    fun `level upgrades stop at ace instead of wrapping to two`() {
        val game = game(level = "K")
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b5", "5", "CLUBS")),
            "c" to listOf(GuandanCard("c4", "4", "CLUBS")),
            "d" to listOf(GuandanCard("d6", "6", "CLUBS")),
        ))
        game.play("a", listOf("a3"))
        game.pass("b")
        game.play("c", listOf("c4"))
        game.play("d", listOf("d6"))

        game.continueNextHand()

        assertEquals("A", game.viewFor("a").public.currentLevel)
    }

    @Test
    fun `red heart level wildcard forms every supported composite pattern`() {
        val game = game(level = "7")
        val wild = GuandanCard("wild", "7", "HEARTS")
        assertEquals(GuandanCombinationType.TRIPLE, game.classifyForTest(listOf(
            GuandanCard("9a", "9", "CLUBS"), GuandanCard("9b", "9", "DIAMONDS"), wild,
        )))
        assertEquals(GuandanCombinationType.TRIPLE_WITH_PAIR, game.classifyForTest(listOf(
            GuandanCard("9a", "9", "CLUBS"), GuandanCard("9b", "9", "DIAMONDS"), wild,
            GuandanCard("qa", "Q", "CLUBS"), GuandanCard("qb", "Q", "DIAMONDS"),
        )))
        assertEquals(GuandanCombinationType.STRAIGHT, game.classifyForTest(listOf(
            GuandanCard("3", "3", "CLUBS"), GuandanCard("4", "4", "SPADES"),
            GuandanCard("5", "5", "CLUBS"), GuandanCard("6", "6", "SPADES"), wild,
        )))
        assertEquals(GuandanCombinationType.CONSECUTIVE_PAIRS, game.classifyForTest(listOf(
            GuandanCard("3a", "3", "CLUBS"), GuandanCard("3b", "3", "SPADES"),
            GuandanCard("4a", "4", "CLUBS"), GuandanCard("4b", "4", "SPADES"),
            GuandanCard("5", "5", "CLUBS"), wild,
        )))
        assertEquals(GuandanCombinationType.CONSECUTIVE_TRIPLES, game.classifyForTest(listOf(
            GuandanCard("3a", "3", "CLUBS"), GuandanCard("3b", "3", "SPADES"), wild,
            GuandanCard("4a", "4", "CLUBS"), GuandanCard("4b", "4", "SPADES"), GuandanCard("4c", "4", "DIAMONDS"),
        )))
        assertEquals(GuandanCombinationType.BOMB, game.classifyForTest(listOf(
            GuandanCard("9a", "9", "CLUBS"), GuandanCard("9b", "9", "SPADES"), GuandanCard("9c", "9", "DIAMONDS"), wild,
        )))
    }

    @Test
    fun `round ends after the third finisher and records the remaining player as last`() {
        val game = game()
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b4", "4", "CLUBS")),
            "c" to listOf(GuandanCard("c5", "5", "CLUBS")),
            "d" to listOf(GuandanCard("d6", "6", "CLUBS"), GuandanCard("d7", "7", "CLUBS")),
        ))
        game.play("a", listOf("a3"))
        game.play("b", listOf("b4"))
        game.play("c", listOf("c5"))
        val result = game.viewFor("d").public
        assertEquals(GuandanPhase.FINISHED, result.phase)
        assertEquals(listOf("a", "b", "c", "d"), result.ranking.map { it.playerId })
        assertTrue(game.viewFor("d").myHand.isNotEmpty())
    }
}
