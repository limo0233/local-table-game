package com.lanboardgame.games.goodcopbadcop

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class IntegrityType { HONEST, CROOKED, AGENT, KINGPIN }

@Serializable
enum class GoodCopTeam { HONEST, CROOKED }

@Serializable
enum class EquipmentType { SHIELD }

@Serializable
enum class GoodCopPhase { TURN, ACTION_RESOLVE, GAME_OVER }

@Serializable
data class IntegrityCard(
    val id: String,
    val type: IntegrityType,
    val revealed: Boolean = false,
)

@Serializable
data class GoodCopActionRecord(
    val actorId: String,
    val action: String,
    val targetId: String? = null,
    val detail: String? = null,
)

@Serializable
data class GoodCopPublicState(
    val phase: GoodCopPhase,
    val currentPlayerId: String,
    val hiddenIntegrityCounts: Map<String, Int>,
    val revealedIntegrity: Map<String, List<IntegrityType>>,
    val equipmentCounts: Map<String, Int>,
    val gunHolder: String? = null,
    val aimTarget: String? = null,
    val wounded: Set<String> = emptySet(),
    val eliminated: Set<String> = emptySet(),
    val history: List<GoodCopActionRecord> = emptyList(),
    val winnerTeam: GoodCopTeam? = null,
    val revealedTeams: Map<String, GoodCopTeam> = emptyMap(),
)

@Serializable
data class GoodCopPlayerView(
    val public: GoodCopPublicState,
    val myIntegrity: List<IntegrityCard>,
    val myTeam: GoodCopTeam,
    val myEquipment: List<EquipmentType>,
    val investigationResult: IntegrityCard? = null,
    val canInvestigate: Boolean,
    val canEquip: Boolean,
    val canArm: Boolean,
    val canAim: Boolean,
    val canShoot: Boolean,
    val targetIds: List<String>,
)

/**
 * Good Cop Bad Cop V1.  The base Integrity/Investigate/Equip/Arm/Aim/Shoot
 * loop is implemented; equipment is intentionally kept to one simple shield
 * effect so the core turn system remains easy to verify and extend.
 */
class GoodCopBadCopGame private constructor(
    val playerIds: List<String>,
    private val integrity: LinkedHashMap<String, MutableList<IntegrityCard>>,
    private val teams: Map<String, GoodCopTeam>,
    private val random: Random,
) : GameInstance {
    override val gameType = GameType.GOOD_COP_BAD_COP

    var phase = GoodCopPhase.TURN
        private set
    var currentPlayerId = playerIds.first()
        private set
    var gunHolder: String? = null
        private set
    var aimTarget: String? = null
        private set
    var winnerTeam: GoodCopTeam? = null
        private set

    private val equipment = linkedMapOf<String, MutableList<EquipmentType>>()
    private val wounded = linkedSetOf<String>()
    private val eliminated = linkedSetOf<String>()
    private val history = mutableListOf<GoodCopActionRecord>()
    private val investigationResults = linkedMapOf<String, IntegrityCard>()

    init {
        playerIds.forEach { equipment[it] = mutableListOf() }
    }

    fun investigate(actorId: String, targetId: String, hiddenIndex: Int) {
        requireTurn(actorId)
        require(targetId in playerIds && targetId != actorId && targetId !in eliminated) { "调查目标无效" }
        val hidden = integrity.getValue(targetId).filterNot { it.revealed }
        require(hiddenIndex in hidden.indices) { "隐藏身份牌编号无效" }
        investigationResults[actorId] = hidden[hiddenIndex].copy()
        history += GoodCopActionRecord(actorId, "INVESTIGATE", targetId, "查看了目标的一张隐藏身份牌")
        advanceTurn()
    }

    fun equip(actorId: String) {
        requireTurn(actorId)
        equipment.getValue(actorId) += EquipmentType.SHIELD
        // Equipping also exposes one of the actor's own hidden Integrity
        // cards, matching the base game's public-action cost.
        revealOne(actorId)
        history += GoodCopActionRecord(actorId, "EQUIP", detail = "获得一张防弹装备")
        advanceTurn()
    }

    fun arm(actorId: String) {
        requireTurn(actorId)
        require(gunHolder == null) { "当前已有玩家持枪" }
        revealOne(actorId)
        gunHolder = actorId
        aimTarget = null
        history += GoodCopActionRecord(actorId, "ARM", detail = "公开一张身份牌并拿到枪")
        phase = GoodCopPhase.ACTION_RESOLVE
    }

    fun aim(actorId: String, targetId: String) {
        require(phase == GoodCopPhase.ACTION_RESOLVE) { "当前不在瞄准阶段" }
        require(gunHolder == actorId && currentPlayerId == actorId) { "当前玩家没有枪" }
        require(targetId in playerIds && targetId != actorId && targetId !in eliminated) { "瞄准目标无效" }
        aimTarget = targetId
    }

    fun shoot(actorId: String) {
        require(phase == GoodCopPhase.ACTION_RESOLVE) { "当前不在射击阶段" }
        require(gunHolder == actorId && currentPlayerId == actorId) { "当前玩家没有枪" }
        val targetId = requireNotNull(aimTarget) { "请先选择瞄准目标" }
        require(targetId !in eliminated) { "目标已经淘汰" }

        val targetTeam = teams.getValue(targetId)
        val targetHasLeader = integrity.getValue(targetId).any { it.type == IntegrityType.AGENT || it.type == IntegrityType.KINGPIN }
        val shield = equipment.getValue(targetId).indexOf(EquipmentType.SHIELD)
        val detail = when {
            shield >= 0 -> {
                equipment.getValue(targetId).removeAt(shield)
                "目标使用防弹装备，抵消本次射击"
            }
            targetHasLeader && targetId !in wounded -> {
                revealAll(targetId)
                wounded += targetId
                "目标受伤"
            }
            else -> {
                eliminated += targetId
                revealAll(targetId)
                "目标被淘汰"
            }
        }
        history += GoodCopActionRecord(actorId, "SHOOT", targetId, detail)
        gunHolder = null
        aimTarget = null
        if (checkWinner(targetId)) return
        advanceTurn()
    }

    override fun viewFor(playerId: String): GoodCopPlayerView {
        require(playerId in playerIds) { "玩家不存在" }
        val publicRevealed = integrity.mapValues { (_, cards) -> cards.filter { it.revealed }.map { it.type } }
        val targetIds = playerIds.filter { it != playerId && it !in eliminated }
        val isCurrent = currentPlayerId == playerId && playerId !in eliminated
        val hasGun = gunHolder == playerId
        return GoodCopPlayerView(
            public = GoodCopPublicState(
                phase = phase,
                currentPlayerId = currentPlayerId,
                hiddenIntegrityCounts = integrity.mapValues { (_, cards) -> cards.count { !it.revealed } },
                revealedIntegrity = publicRevealed,
                equipmentCounts = equipment.mapValues { it.value.size },
                gunHolder = gunHolder,
                aimTarget = aimTarget,
                wounded = wounded.toSet(),
                eliminated = eliminated.toSet(),
                history = history.toList(),
                winnerTeam = winnerTeam,
                revealedTeams = if (phase == GoodCopPhase.GAME_OVER) teams.toMap() else emptyMap(),
            ),
            myIntegrity = integrity.getValue(playerId).toList(),
            myTeam = teams.getValue(playerId),
            myEquipment = equipment.getValue(playerId).toList(),
            investigationResult = investigationResults[playerId],
            canInvestigate = phase == GoodCopPhase.TURN && isCurrent,
            canEquip = phase == GoodCopPhase.TURN && isCurrent,
            canArm = phase == GoodCopPhase.TURN && isCurrent && gunHolder == null,
            canAim = phase == GoodCopPhase.ACTION_RESOLVE && isCurrent && hasGun,
            canShoot = phase == GoodCopPhase.ACTION_RESOLVE && isCurrent && hasGun && aimTarget != null,
            targetIds = if (isCurrent) targetIds else emptyList(),
        )
    }

    private fun requireTurn(actorId: String) {
        require(phase == GoodCopPhase.TURN) { "当前不能执行该行动" }
        require(actorId == currentPlayerId && actorId !in eliminated) { "还没有轮到该玩家" }
    }

    private fun advanceTurn() {
        val index = playerIds.indexOf(currentPlayerId)
        for (step in 1..playerIds.size) {
            val candidate = playerIds[(index + step) % playerIds.size]
            if (candidate !in eliminated) {
                currentPlayerId = candidate
                phase = GoodCopPhase.TURN
                return
            }
        }
        finish(winnerTeam ?: GoodCopTeam.HONEST)
    }

    private fun revealOne(playerId: String) {
        val cards = integrity.getValue(playerId)
        val index = cards.indexOfFirst { !it.revealed }
        if (index >= 0) cards[index] = cards[index].copy(revealed = true)
    }

    private fun revealAll(playerId: String) {
        val cards = integrity.getValue(playerId)
        cards.indices.forEach { index -> cards[index] = cards[index].copy(revealed = true) }
    }

    private fun checkWinner(lastTargetId: String): Boolean {
        val targetCards = integrity.getValue(lastTargetId)
        val leaderEliminated = lastTargetId in eliminated && targetCards.any { it.type == IntegrityType.AGENT || it.type == IntegrityType.KINGPIN }
        if (leaderEliminated) {
            val leaderType = targetCards.first { it.type == IntegrityType.AGENT || it.type == IntegrityType.KINGPIN }.type
            finish(if (leaderType == IntegrityType.AGENT) GoodCopTeam.CROOKED else GoodCopTeam.HONEST)
            return true
        }
        val remainingTeams = playerIds.filter { it !in eliminated }.map { teams.getValue(it) }.toSet()
        if (remainingTeams.size == 1) {
            finish(remainingTeams.first())
            return true
        }
        return false
    }

    private fun finish(team: GoodCopTeam) {
        winnerTeam = team
        phase = GoodCopPhase.GAME_OVER
    }

    companion object {
        fun create(playerIds: List<String>, random: Random = Random.Default): GoodCopBadCopGame {
            require(playerIds.size in 4..8) { "Good Cop Bad Cop 需要 4 至 8 名玩家" }
            require(playerIds.distinct().size == playerIds.size) { "玩家 ID 不能重复" }
            val shuffled = playerIds.shuffled(random)
            val cards = linkedMapOf<String, MutableList<IntegrityCard>>()
            shuffled.forEachIndexed { index, playerId ->
                val playerCards = mutableListOf<IntegrityCard>()
                repeat(3) { cardIndex ->
                    val type = when {
                        index == 0 && cardIndex == 0 -> IntegrityType.AGENT
                        index == 1 && cardIndex == 0 -> IntegrityType.KINGPIN
                        random.nextBoolean() -> IntegrityType.HONEST
                        else -> IntegrityType.CROOKED
                    }
                    playerCards += IntegrityCard("integrity-$index-$cardIndex", type)
                }
                cards[playerId] = playerCards
            }
            val teams = cards.mapValues { (_, playerCards) -> teamOf(playerCards.map { it.type }) }
            return GoodCopBadCopGame(playerIds.toList(), cards, teams, random)
        }

        private fun teamOf(cards: List<IntegrityType>): GoodCopTeam {
            if (IntegrityType.AGENT in cards) return GoodCopTeam.HONEST
            if (IntegrityType.KINGPIN in cards) return GoodCopTeam.CROOKED
            val honest = cards.count { it == IntegrityType.HONEST }
            val crooked = cards.count { it == IntegrityType.CROOKED }
            return if (honest >= crooked) GoodCopTeam.HONEST else GoodCopTeam.CROOKED
        }
    }
}
