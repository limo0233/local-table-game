package com.lanboardgame.games.liarsdice

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The app's offline dice tool. The server owns each private hand and only
 * reveals the complete result after every player settles; discussion and
 * judging stay at the table.
 */
@Serializable
enum class LiarDicePhase { PLAYING }

@Serializable
data class LiarDiceConfig(val diceCount: Int = 5)

@Serializable
data class LiarDicePublicState(
    val phase: LiarDicePhase,
    val round: Int,
    val diceCount: Map<String, Int>,
    val settledPlayerIds: Set<String> = emptySet(),
    val allSettled: Boolean = false,
    val nextRoundPlayerIds: Set<String> = emptySet(),
    val resultsByPlayer: Map<String, List<Int>> = emptyMap(),
    val pointCounts: Map<Int, Int> = emptyMap(),
)

@Serializable
data class LiarDicePlayerView(
    val public: LiarDicePublicState,
    val myDice: List<Int>,
    val canReroll: Boolean,
    val canSettle: Boolean,
    val canNextRound: Boolean,
    val settled: Boolean,
)

class LiarDiceGame private constructor(
    val playerIds: List<String>,
    private val dice: LinkedHashMap<String, MutableList<Int>>,
    private val settledPlayers: MutableSet<String>,
    private val nextRoundPlayers: MutableSet<String>,
    private val random: Random,
) : GameInstance {
    override val gameType = GameType.LIARS_DICE

    val phase = LiarDicePhase.PLAYING
    var round = 1
        private set
    /** Reroll the player's complete hand in one operation until they settle. */
    fun reroll(actorId: String) {
        require(actorId in playerIds) { "玩家不存在" }
        require(actorId !in settledPlayers) { "已结算，不能重掷" }
        val hand = dice.getValue(actorId)
        hand.indices.forEach { index -> hand[index] = random.nextInt(1, 7) }
    }

    /** Mark the player's hand as final for the offline discussion. */
    fun settle(actorId: String) {
        require(actorId in playerIds) { "玩家不存在" }
        require(actorId !in settledPlayers) { "该玩家已经结算" }
        settledPlayers += actorId
    }

    /** Start the next round only after every player has settled and confirmed. */
    fun nextRound(actorId: String) {
        require(actorId in playerIds) { "玩家不存在" }
        require(settledPlayers.size == playerIds.size) { "请等待所有玩家结算" }
        require(actorId !in nextRoundPlayers) { "你已经确认下一局" }
        nextRoundPlayers += actorId
        if (nextRoundPlayers.size == playerIds.size) {
            round += 1
            dice.values.forEach { hand -> hand.indices.forEach { index -> hand[index] = random.nextInt(1, 7) } }
            settledPlayers.clear()
            nextRoundPlayers.clear()
        }
    }

    override fun viewFor(playerId: String): LiarDicePlayerView {
        require(playerId in playerIds) { "玩家不存在" }
        val allSettled = settledPlayers.size == playerIds.size
        val public = LiarDicePublicState(
            phase = phase,
            round = round,
            diceCount = dice.mapValues { it.value.size },
            settledPlayerIds = settledPlayers.toSet(),
            allSettled = allSettled,
            nextRoundPlayerIds = nextRoundPlayers.toSet(),
            resultsByPlayer = if (allSettled) dice.mapValues { it.value.toList() } else emptyMap(),
            pointCounts = if (allSettled) dice.values.flatten().groupingBy { it }.eachCount() else emptyMap(),
        )
        val settled = playerId in settledPlayers
        return LiarDicePlayerView(
            public = public,
            myDice = dice.getValue(playerId).toList(),
            canReroll = !settled,
            canSettle = !settled,
            canNextRound = allSettled && playerId !in nextRoundPlayers,
            settled = settled,
        )
    }

    companion object {
        const val DEFAULT_DICE = 5
        const val MIN_DICE = 1
        const val MAX_DICE = 10

        fun create(
            playerIds: List<String>,
            diceCount: Int = DEFAULT_DICE,
            random: Random = Random.Default,
        ): LiarDiceGame {
            require(playerIds.size in 2..10) { "骰子模拟器需要 2 至 10 名玩家" }
            require(playerIds.distinct().size == playerIds.size) { "玩家 ID 不能重复" }
            require(diceCount in MIN_DICE..MAX_DICE) { "骰子数量应为 $MIN_DICE 至 $MAX_DICE" }
            val dice = linkedMapOf<String, MutableList<Int>>()
            playerIds.forEach { id -> dice[id] = MutableList(diceCount) { random.nextInt(1, 7) } }
            return LiarDiceGame(playerIds.toList(), dice, linkedSetOf(), linkedSetOf(), random)
        }

        fun create(playerIds: List<String>, random: Random): LiarDiceGame = create(playerIds, DEFAULT_DICE, random)
    }
}
