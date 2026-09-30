package com.lanboardgame.games.guandan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.random.Random

class GuandanGameTest {
    @Test
    fun `deals two decks evenly and only exposes the requesting hand`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))

        val first = game.viewFor("a")

        assertEquals(27, first.myHand.size)
        assertEquals(mapOf("a" to 27, "b" to 27, "c" to 27, "d" to 27), first.public.handSizes)
        assertTrue(first.public.lastPlay == null)
    }

    @Test
    fun `players can play a single and pass until the trick resets`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))
        val firstCard = game.viewFor("a").myHand.first().id

        game.play("a", listOf(firstCard))
        assertEquals("b", game.viewFor("b").public.currentPlayerId)
        game.pass("b")
        game.pass("c")
        game.pass("d")

        val reset = game.viewFor("a")
        assertEquals("a", reset.public.currentPlayerId)
        assertTrue(reset.public.lastPlay == null)
    }

    @Test
    fun `cannot pass before a lead play`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))
        assertFailsWith<IllegalArgumentException> { game.pass("a") }
    }

    @Test
    fun `recognizes common guandan combinations`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))
        fun cards(vararg ranks: String) = ranks.mapIndexed { index, rank -> GuandanCard("t$index", rank, "CLUBS") }
        assertEquals(GuandanCombinationType.SINGLE, game.classifyForTest(cards("7")))
        assertEquals(GuandanCombinationType.PAIR, game.classifyForTest(cards("7", "7")))
        assertEquals(GuandanCombinationType.TRIPLE, game.classifyForTest(cards("7", "7", "7")))
        assertEquals(GuandanCombinationType.TRIPLE_WITH_PAIR, game.classifyForTest(cards("7", "7", "7", "9", "9")))
        assertEquals(GuandanCombinationType.CONSECUTIVE_PAIRS, game.classifyForTest(cards("7", "7", "8", "8", "9", "9")))
        assertEquals(GuandanCombinationType.CONSECUTIVE_TRIPLES, game.classifyForTest(cards("7", "7", "7", "8", "8", "8")))
        assertEquals(null, game.classifyForTest(cards("7", "7", "7", "8", "8", "8", "9", "9", "10", "10")))
        assertEquals(GuandanCombinationType.STRAIGHT, game.classifyForTest(listOf("7", "8", "9", "10", "J").mapIndexed { index, rank -> GuandanCard("m$index", rank, if (index % 2 == 0) "CLUBS" else "SPADES") }))
        assertEquals(GuandanCombinationType.STRAIGHT_FLUSH, game.classifyForTest(cards("7", "8", "9", "10", "J")))
        assertEquals(GuandanCombinationType.BOMB, game.classifyForTest(cards("Q", "Q", "Q", "Q")))
        assertEquals(GuandanCombinationType.ROCKET, game.classifyForTest(
            listOf(
                GuandanCard("s1", "SMALL_JOKER", "JOKER"), GuandanCard("s2", "SMALL_JOKER", "JOKER"),
                GuandanCard("b1", "BIG_JOKER", "JOKER"), GuandanCard("b2", "BIG_JOKER", "JOKER"),
            )
        ))
    }

    @Test
    fun `only red level card is a wildcard and level outranks ace`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7), startingLevel = "7")
        assertEquals(GuandanCombinationType.PAIR, game.classifyForTest(listOf(
            GuandanCard("wild", "7", "HEARTS"), GuandanCard("ordinary", "9", "CLUBS"),
        )))
        assertEquals(null, game.classifyForTest(listOf(
            GuandanCard("not-wild", "7", "DIAMONDS"), GuandanCard("ordinary", "9", "CLUBS"),
        )))
        assertTrue(game.effectiveValueForTest(GuandanCard("level", "7", "SPADES")) > game.effectiveValueForTest(GuandanCard("ace", "A", "SPADES")))
    }

    @Test
    fun `special hand hierarchy is enforced`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))
        fun same(rank: String, count: Int, suit: String = "CLUBS") = (0 until count).map { GuandanCard("$rank-$it", rank, suit) }
        val fourBomb = same("7", 4)
        val fiveBomb = same("8", 5)
        val sixBomb = same("9", 6)
        val straightFlush = listOf("3", "4", "5", "6", "7").mapIndexed { i, rank -> GuandanCard("sf-$i", rank, "HEARTS") }
        val rocket = listOf(
            GuandanCard("s1", "SMALL_JOKER", "JOKER"), GuandanCard("s2", "SMALL_JOKER", "JOKER"),
            GuandanCard("b1", "BIG_JOKER", "JOKER"), GuandanCard("b2", "BIG_JOKER", "JOKER"),
        )
        assertTrue(game.canBeatForTest(fiveBomb, fourBomb))
        assertTrue(game.canBeatForTest(straightFlush, fiveBomb))
        assertTrue(game.canBeatForTest(sixBomb, straightFlush))
        assertTrue(game.canBeatForTest(rocket, sixBomb))
        assertTrue(!game.canBeatForTest(fourBomb, straightFlush))
        assertTrue(!game.canBeatForTest(listOf(GuandanCard("pair", "A", "CLUBS"), GuandanCard("pair2", "A", "DIAMONDS")), fourBomb))
    }

    @Test
    fun `a player who finishes gives the next free lead to their partner after all passes`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))
        val hands = mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b5", "5", "CLUBS"), GuandanCard("b9", "9", "CLUBS")),
            "c" to listOf(GuandanCard("c4", "4", "CLUBS"), GuandanCard("c8", "8", "CLUBS")),
            "d" to listOf(GuandanCard("d6", "6", "CLUBS"), GuandanCard("d10", "10", "CLUBS")),
        )
        game.replaceHandsForTest(hands)
        game.play("a", listOf("a3"))
        game.pass("b")
        game.pass("c")
        game.pass("d")
        val result = game.viewFor("a")
        assertEquals(GuandanPhase.PLAYING, result.public.phase)
        assertEquals("c", result.public.currentPlayerId)
        assertTrue(result.public.lastPlay == null)
        assertEquals("a出完 → 对家c接风", result.public.lastEvent)
    }

    @Test
    fun `continuing a hand applies partner rank upgrade and preserves previous ranking`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(7))
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b5", "5", "CLUBS")),
            "c" to listOf(GuandanCard("c4", "4", "CLUBS")),
            "d" to listOf(GuandanCard("d6", "6", "CLUBS")),
        ))
        game.play("a", listOf("a3")); game.pass("b"); game.play("c", listOf("c4")); game.play("d", listOf("d6"))
        game.continueNextHand()
        val next = game.viewFor("a")
        assertEquals("5", next.public.currentLevel)
        assertEquals(2, next.public.roundNumber)
        assertEquals(listOf("a", "c", "d", "b"), next.public.lastRoundRanking.map { it.playerId })
    }

    @Test
    fun `double down creates two tribute pairs and starts after both returns`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(11))
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b5", "5", "CLUBS")),
            "c" to listOf(GuandanCard("c4", "4", "CLUBS")),
            "d" to listOf(GuandanCard("dJ", "J", "CLUBS")),
        ))
        game.play("a", listOf("a3")); game.pass("b"); game.play("c", listOf("c4")); game.play("d", listOf("dJ"))
        game.continueNextHand()
        val tribute = game.viewFor("a")
        assertEquals(GuandanPhase.TRIBUTE, tribute.public.phase)
        assertEquals(2, tribute.public.tribute?.requirements?.size)
        val requirements = tribute.public.tribute!!.requirements
        assertTrue(requirements.any { it.fromPlayerId == "d" && it.toPlayerId == "a" })
        assertTrue(requirements.any { it.fromPlayerId == "b" && it.toPlayerId == "c" })

        val dCard = game.viewFor("d").myHand.maxBy { game.effectiveValueForTest(it) }
        val bCard = game.viewFor("b").myHand.maxBy { game.effectiveValueForTest(it) }
        game.submitTribute("d", dCard.id)
        game.submitTribute("b", bCard.id)
        game.returnTribute("a", "d", game.viewFor("a").myHand.first().id)
        game.returnTribute("c", "b", game.viewFor("c").myHand.first().id)
        assertEquals(GuandanPhase.PLAYING, game.viewFor("a").public.phase)
    }

    @Test
    fun `double down anti tribute is decided from the newly dealt hands`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(13))
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("bJ", "J", "CLUBS"), GuandanCard("bBig", "BIG_JOKER", "JOKER")),
            "c" to listOf(GuandanCard("c4", "4", "CLUBS")),
            "d" to listOf(GuandanCard("dQ", "Q", "CLUBS"), GuandanCard("dBig", "BIG_JOKER", "JOKER")),
        ))
        game.play("a", listOf("a3")); game.pass("b"); game.play("c", listOf("c4")); game.play("d", listOf("dQ")); game.pass("b"); game.play("d", listOf("dBig"))
        game.continueNextHand()
        val next = game.viewFor("a")
        val losers = listOf("d", "b")
        val newHandBigJokers = losers.map { loser -> game.viewFor(loser).myHand.count { it.rank == "BIG_JOKER" } }
        val shouldResist = newHandBigJokers == listOf(1, 1) || newHandBigJokers.any { it >= 2 }
        assertEquals(shouldResist, next.public.tribute?.antiTribute == true)
        assertEquals(if (shouldResist) GuandanPhase.PLAYING else GuandanPhase.TRIBUTE, next.public.phase)
    }

    @Test
    fun `single down creates one tribute pair and tribute sender leads`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(19))
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b4", "4", "CLUBS")),
            "c" to listOf(GuandanCard("c6", "6", "CLUBS")),
            "d" to listOf(GuandanCard("d5", "5", "CLUBS")),
        ))
        game.play("a", listOf("a3")); game.play("b", listOf("b4")); game.play("c", listOf("c6"))
        game.continueNextHand()
        val next = game.viewFor("a")
        assertEquals(GuandanPhase.TRIBUTE, next.public.phase)
        assertEquals(listOf(GuandanTributeRequirement("d", "a")), next.public.tribute?.requirements)
        val card = game.viewFor("d").myHand.maxBy { game.effectiveValueForTest(it) }
        game.submitTribute("d", card.id)
        game.returnTribute("a", "d", game.viewFor("a").myHand.first().id)
        assertEquals("d", game.viewFor("a").public.currentPlayerId)
    }

    @Test
    fun `A level only ends the match when the partner is second or third`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(17), startingLevel = "A")
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b5", "5", "CLUBS")),
            "c" to listOf(GuandanCard("c4", "4", "CLUBS")),
            "d" to listOf(GuandanCard("d6", "6", "CLUBS")),
        ))
        game.play("a", listOf("a3")); game.pass("b"); game.play("c", listOf("c4")); game.play("d", listOf("d6"))
        game.continueNextHand()
        assertEquals(0, game.viewFor("a").public.matchWinnerTeam)
    }

    @Test
    fun `A level stays active when the partner is last`() {
        val game = GuandanGame.create(listOf("a", "b", "c", "d"), Random(23), startingLevel = "A")
        game.replaceHandsForTest(mapOf(
            "a" to listOf(GuandanCard("a3", "3", "CLUBS")),
            "b" to listOf(GuandanCard("b4", "4", "CLUBS")),
            "c" to listOf(GuandanCard("c6", "6", "CLUBS")),
            "d" to listOf(GuandanCard("d5", "5", "CLUBS")),
        ))
        game.play("a", listOf("a3")); game.play("b", listOf("b4")); game.pass("c"); game.play("d", listOf("d5"))
        game.continueNextHand()
        val next = game.viewFor("a")
        assertEquals("A", next.public.currentLevel)
        assertEquals(null, next.public.matchWinnerTeam)
        assertEquals(2, next.public.roundNumber)
    }

}
