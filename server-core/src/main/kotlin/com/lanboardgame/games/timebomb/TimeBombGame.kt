package com.lanboardgame.games.timebomb

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class TimeBombRole { SHERLOCK, MORIARTY }

@Serializable
enum class TimeBombWire { SAFE, DEFUSE, BOMB }

@Serializable
enum class TimeBombPhase { ROLE_REVEAL, ROUND, CUT_RESULT, ROUND_RESET, GAME_OVER }

@Serializable
data class TimeBombCutRecord(
    val round: Int,
    val cutterId: String,
    val targetId: String,
    val wire: TimeBombWire,
    val remainingTargetWires: Int,
)

@Serializable
data class TimeBombPublicState(
    val phase: TimeBombPhase,
    val round: Int,
    val currentCutterId: String,
    val wireCountsByPlayer: Map<String, Int>,
    val cutsThisRound: Int,
    val defuseFound: Int,
    val totalDefuse: Int,
    val history: List<TimeBombCutRecord>,
    val lastCut: TimeBombCutRecord? = null,
    val winner: TimeBombRole? = null,
    val endReason: String? = null,
    val confirmedPlayerIds: Set<String> = emptySet(),
)

@Serializable
data class TimeBombPlayerView(
    val public: TimeBombPublicState,
    val myRole: TimeBombRole,
    val privateWires: List<TimeBombWire>,
    val canConfirmRole: Boolean,
    val canCut: Boolean,
    val targetIds: List<String>,
)

/**
 * A compact implementation of the core TimeBomb flow.  Wire identities remain
 * private to the owner; only the result of a cut is broadcast publicly.
 */
class TimeBombGame private constructor(
    val playerIds: List<String>,
    private val roles: Map<String, TimeBombRole>,
    private val wires: LinkedHashMap<String, MutableList<TimeBombWire>>,
    private val random: Random,
) : GameInstance {
    override val gameType = GameType.TIME_BOMB

    var phase = TimeBombPhase.ROLE_REVEAL
        private set
    var round = 1
        private set
    var currentCutterId: String = playerIds.first()
        private set
    var cutsThisRound = 0
        private set
    var defuseFound = 0
        private set
    var winner: TimeBombRole? = null
        private set
    var endReason: String? = null
        private set

    private val confirmed = linkedSetOf<String>()
    private val history = mutableListOf<TimeBombCutRecord>()
    private var lastCut: TimeBombCutRecord? = null

    fun confirmRole(playerId: String) {
        require(playerId in playerIds) { "玩家不存在" }
        require(phase == TimeBombPhase.ROLE_REVEAL) { "当前不在身份确认阶段" }
        require(playerId !in confirmed) { "该玩家已经确认" }
        confirmed += playerId
        if (confirmed.size == playerIds.size) phase = TimeBombPhase.ROUND
    }

    fun cut(playerId: String, targetId: String, wireIndex: Int) {
        require(phase == TimeBombPhase.ROUND) { "当前不是剪线阶段" }
        require(playerId == currentCutterId) { "还没有轮到该玩家" }
        require(targetId in playerIds && targetId != playerId) { "不能剪自己或无效玩家" }
        val targetWires = wires.getValue(targetId)
        require(wireIndex in targetWires.indices) { "线路编号无效" }

        val wire = targetWires.removeAt(wireIndex)
        val record = TimeBombCutRecord(round, playerId, targetId, wire, targetWires.size)
        history += record
        lastCut = record
        cutsThisRound++
        currentCutterId = targetId
        when (wire) {
            TimeBombWire.DEFUSE -> {
                defuseFound++
                if (defuseFound >= playerIds.size) {
                    finish(TimeBombRole.SHERLOCK, "全部拆弹线已找到")
                    return
                }
            }
            TimeBombWire.BOMB -> {
                finish(TimeBombRole.MORIARTY, "剪中了炸弹")
                return
            }
            TimeBombWire.SAFE -> Unit
        }

        if (cutsThisRound >= playerIds.size) {
            if (round >= MAX_ROUNDS) {
                finish(TimeBombRole.MORIARTY, "四轮结束仍未拆完炸弹")
            } else {
                resetRound()
            }
        }
    }

    override fun viewFor(playerId: String): TimeBombPlayerView {
        require(playerId in playerIds) { "玩家不存在" }
        val canCut = phase == TimeBombPhase.ROUND && currentCutterId == playerId
        return TimeBombPlayerView(
            public = TimeBombPublicState(
                phase = phase,
                round = round,
                currentCutterId = currentCutterId,
                wireCountsByPlayer = wires.mapValues { it.value.size },
                cutsThisRound = cutsThisRound,
                defuseFound = defuseFound,
                totalDefuse = playerIds.size,
                history = history.toList(),
                lastCut = lastCut,
                winner = winner,
                endReason = endReason,
                confirmedPlayerIds = confirmed.toSet(),
            ),
            myRole = roles.getValue(playerId),
            // The owner may know the composition, but not which displayed item
            // maps to a cut index after the private hand has been shuffled.
            privateWires = wires.getValue(playerId).sortedBy { it.ordinal },
            canConfirmRole = phase == TimeBombPhase.ROLE_REVEAL && playerId !in confirmed,
            canCut = canCut,
            targetIds = if (canCut) playerIds.filter { it != playerId && wires.getValue(it).isNotEmpty() } else emptyList(),
        )
    }

    private fun resetRound() {
        phase = TimeBombPhase.ROUND_RESET
        round++
        cutsThisRound = 0
        lastCut = null
        val remaining = wires.values.flatMap { it }.shuffled(random)
        wires.values.forEach { it.clear() }
        val each = remaining.size / playerIds.size
        require(each * playerIds.size == remaining.size) { "线路无法平均分配" }
        playerIds.forEachIndexed { index, id ->
            wires.getValue(id).addAll(remaining.drop(index * each).take(each))
        }
        phase = TimeBombPhase.ROUND
    }

    private fun finish(result: TimeBombRole, reason: String) {
        winner = result
        endReason = reason
        phase = TimeBombPhase.GAME_OVER
    }

    companion object {
        private const val MAX_ROUNDS = 4

        internal fun createForTest(
            playerIds: List<String>,
            roles: Map<String, TimeBombRole>,
            wires: LinkedHashMap<String, MutableList<TimeBombWire>>,
            random: Random = Random(0),
        ): TimeBombGame {
            require(roles.keys == playerIds.toSet())
            require(wires.keys == playerIds.toSet())
            return TimeBombGame(playerIds.toList(), roles.toMap(), wires, random)
        }

        fun create(playerIds: List<String>, random: Random = Random.Default): TimeBombGame {
            require(playerIds.size in 4..8) { "TimeBomb 需要 4 至 8 名玩家" }
            require(playerIds.distinct().size == playerIds.size) { "玩家 ID 不能重复" }
            val rolePool = when (playerIds.size) {
                4, 5 -> List(3) { TimeBombRole.SHERLOCK } + List(2) { TimeBombRole.MORIARTY }
                6 -> List(4) { TimeBombRole.SHERLOCK } + List(2) { TimeBombRole.MORIARTY }
                7, 8 -> List(5) { TimeBombRole.SHERLOCK } + List(3) { TimeBombRole.MORIARTY }
                else -> error("TimeBomb 人数无效")
            }
            // Four- and seven-player games intentionally leave one shuffled
            // role undealt, so the exact enemy count is not knowable.
            val roleList = rolePool.shuffled(random).take(playerIds.size)
            val roleMap = playerIds.zip(roleList).toMap()
            val wirePool = buildList {
                repeat(playerIds.size) { add(TimeBombWire.DEFUSE) }
                add(TimeBombWire.BOMB)
                repeat(playerIds.size * 5 - size) { add(TimeBombWire.SAFE) }
            }.shuffled(random)
            val hands = linkedMapOf<String, MutableList<TimeBombWire>>()
            playerIds.forEachIndexed { index, id ->
                hands[id] = wirePool.drop(index * 5).take(5).toMutableList()
            }
            return TimeBombGame(playerIds.toList(), roleMap, hands, random)
        }
    }
}
