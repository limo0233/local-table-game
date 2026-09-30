package com.lanboardgame.games.poker

import kotlinx.serialization.Serializable

@Serializable
enum class HandCategory(val strength: Int, val displayName: String) {
    HIGH_CARD(0, "High Card"),
    ONE_PAIR(1, "One Pair"),
    TWO_PAIR(2, "Two Pair"),
    THREE_OF_A_KIND(3, "Three of a Kind"),
    STRAIGHT(4, "Straight"),
    FLUSH(5, "Flush"),
    FULL_HOUSE(6, "Full House"),
    FOUR_OF_A_KIND(7, "Four of a Kind"),
    STRAIGHT_FLUSH(8, "Straight Flush"),
    ROYAL_FLUSH(9, "Royal Flush"),
}

@Serializable
data class HandValue(
    val category: HandCategory,
    val kickers: List<Int>,
    val bestFive: List<Card>,
) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int {
        val categoryCompare = category.strength.compareTo(other.category.strength)
        if (categoryCompare != 0) return categoryCompare
        for (i in 0 until maxOf(kickers.size, other.kickers.size)) {
            val left = kickers.getOrElse(i) { 0 }
            val right = other.kickers.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}

object HandEvaluator {
    fun evaluate(cards: List<Card>): HandValue {
        require(cards.size in 5..7) { "需要 5 至 7 张牌" }
        return combinations(cards, 5).map(::evaluateFive).maxOrNull()!!
    }

    private fun evaluateFive(cards: List<Card>): HandValue {
        val sorted = cards.sortedByDescending { it.rank.value }
        val values = sorted.map { it.rank.value }
        val groups = values.groupingBy { it }.eachCount()
        val countsThenRanks = groups.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        val flush = cards.map { it.suit }.distinct().size == 1
        val straightHigh = straightHigh(values)

        if (flush && straightHigh != null) {
            val category = if (straightHigh == 14) HandCategory.ROYAL_FLUSH else HandCategory.STRAIGHT_FLUSH
            return HandValue(category, listOf(straightHigh), orderStraight(cards, straightHigh))
        }
        val four = countsThenRanks.firstOrNull { it.value == 4 }?.key
        if (four != null) {
            val kicker = values.first { it != four }
            return HandValue(HandCategory.FOUR_OF_A_KIND, listOf(four, kicker), sortByRanks(cards, listOf(four, kicker)))
        }
        val trips = countsThenRanks.filter { it.value == 3 }.map { it.key }
        val pairs = countsThenRanks.filter { it.value >= 2 }.map { it.key }
        if (trips.isNotEmpty() && pairs.any { it != trips.first() }) {
            val pair = pairs.first { it != trips.first() }
            return HandValue(HandCategory.FULL_HOUSE, listOf(trips.first(), pair), sortByRanks(cards, listOf(trips.first(), pair)))
        }
        if (flush) return HandValue(HandCategory.FLUSH, values, sorted)
        if (straightHigh != null) return HandValue(HandCategory.STRAIGHT, listOf(straightHigh), orderStraight(cards, straightHigh))
        if (trips.isNotEmpty()) {
            val kickers = values.filter { it != trips.first() }.distinct().sortedDescending()
            val order = listOf(trips.first()) + kickers
            return HandValue(HandCategory.THREE_OF_A_KIND, order, sortByRanks(cards, order))
        }
        val exactPairs = groups.filterValues { it == 2 }.keys.sortedDescending()
        if (exactPairs.size >= 2) {
            val highPairs = exactPairs.take(2)
            val kicker = values.first { it !in highPairs }
            val order = highPairs + kicker
            return HandValue(HandCategory.TWO_PAIR, order, sortByRanks(cards, order))
        }
        if (exactPairs.size == 1) {
            val pair = exactPairs.first()
            val kickers = values.filter { it != pair }.sortedDescending()
            val order = listOf(pair) + kickers
            return HandValue(HandCategory.ONE_PAIR, order, sortByRanks(cards, order))
        }
        return HandValue(HandCategory.HIGH_CARD, values, sorted)
    }

    private fun straightHigh(values: List<Int>): Int? {
        val unique = values.distinct().sortedDescending().toMutableList()
        if (14 in unique) unique += 1
        var run = 1
        for (i in 1 until unique.size) {
            if (unique[i - 1] - 1 == unique[i]) {
                run++
                if (run >= 5) return unique[i] + 4
            } else run = 1
        }
        return null
    }

    private fun orderStraight(cards: List<Card>, high: Int): List<Card> {
        val ranks = if (high == 5) listOf(5, 4, 3, 2, 14) else (high downTo high - 4).toList()
        return ranks.map { rank -> cards.first { it.rank.value == rank } }
    }

    private fun sortByRanks(cards: List<Card>, ranks: List<Int>): List<Card> =
        ranks.flatMap { rank -> cards.filter { it.rank.value == rank } }.take(5)

    private fun <T> combinations(items: List<T>, choose: Int): Sequence<List<T>> = sequence {
        val indices = IntArray(choose) { it }
        while (true) {
            yield(indices.map(items::get))
            var i = choose - 1
            while (i >= 0 && indices[i] == items.size - choose + i) i--
            if (i < 0) break
            indices[i]++
            for (j in i + 1 until choose) indices[j] = indices[j - 1] + 1
        }
    }
}
