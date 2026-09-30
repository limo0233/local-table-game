package com.lanboardgame.games.poker

import kotlinx.serialization.Serializable
import java.security.SecureRandom

@Serializable
enum class Suit(val symbol: String) { CLUBS("♣"), DIAMONDS("♦"), HEARTS("♥"), SPADES("♠") }

@Serializable
enum class Rank(val value: Int, val label: String) {
    TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"),
    SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"),
    JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A"),
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    override fun toString(): String = rank.label + suit.symbol

    companion object {
        fun parse(value: String): Card {
            require(value.length in 2..3)
            val suit = when (value.last().lowercaseChar()) {
                'c', '♣' -> Suit.CLUBS
                'd', '♦' -> Suit.DIAMONDS
                'h', '♥' -> Suit.HEARTS
                's', '♠' -> Suit.SPADES
                else -> error("Invalid suit: $value")
            }
            val rankText = value.dropLast(1).uppercase()
            val rank = Rank.entries.firstOrNull { it.label == rankText } ?: error("Invalid rank: $value")
            return Card(rank, suit)
        }
    }
}

class Deck(private val cards: ArrayDeque<Card>) {
    fun deal(): Card = cards.removeFirstOrNull() ?: error("牌堆已空")
    fun remaining(): Int = cards.size

    companion object {
        fun shuffled(random: SecureRandom = SecureRandom()): Deck {
            val cards = Suit.entries.flatMap { suit -> Rank.entries.map { Card(it, suit) } }.toMutableList()
            for (i in cards.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = cards[i]
                cards[i] = cards[j]
                cards[j] = tmp
            }
            return Deck(ArrayDeque(cards))
        }

        fun fixed(vararg values: String) = Deck(ArrayDeque(values.map(Card::parse)))
    }
}

