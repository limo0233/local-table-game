package com.lanboardgame.games.poker

import kotlinx.serialization.Serializable

@Serializable
data class Pot(val amount: Int, val eligiblePlayerIds: List<String>)

@Serializable
data class PotAward(val potIndex: Int, val amount: Int, val winnerIds: List<String>, val shares: Map<String, Int>)

object PotCalculator {
    fun build(contributions: Map<String, Int>, foldedPlayerIds: Set<String>): List<Pot> {
        require(contributions.values.all { it >= 0 })
        val levels = contributions.values.filter { it > 0 }.distinct().sorted()
        var previous = 0
        return levels.mapNotNull { level ->
            val contributors = contributions.filterValues { it >= level }.keys
            val amount = (level - previous) * contributors.size
            previous = level
            if (amount == 0) null else Pot(amount, contributors.filterNot(foldedPlayerIds::contains))
        }
    }

    fun award(
        pots: List<Pot>,
        values: Map<String, HandValue>,
        clockwiseOrder: List<String>,
    ): List<PotAward> = pots.mapIndexed { index, pot ->
        val eligible = pot.eligiblePlayerIds.filter(values::containsKey)
        require(eligible.isNotEmpty()) { "边池没有可获胜玩家" }
        val best = eligible.maxOf { values.getValue(it) }
        val winners = eligible.filter { values.getValue(it).compareTo(best) == 0 }
        val base = pot.amount / winners.size
        var odd = pot.amount % winners.size
        val shares = linkedMapOf<String, Int>()
        clockwiseOrder.filter(winners::contains).forEach { id ->
            shares[id] = base + if (odd-- > 0) 1 else 0
        }
        PotAward(index, pot.amount, winners, shares)
    }
}
