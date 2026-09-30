package com.lanboardgame.games.crossfire

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class CrossfireRole { VIP, AGENT, ASSASSIN, BYSTANDER, DECOY, RED_DECOY, BLUE_DECOY }

@Serializable
enum class CrossfireTeam { AGENTS, ASSASSIN, BYSTANDER }

@Serializable
enum class CrossfirePhase { INITIAL_VIEW, INTEL_PASS, SHUFFLE, DISCUSSION, TARGETING, RESOLVE, GAME_OVER }

@Serializable
data class CrossfireIntel(
    val sourcePlayerId: String,
    val role: CrossfireRole,
)

@Serializable
data class CrossfireShuffleRecord(val playerIds: List<String>)

@Serializable
data class CrossfireResult(
    val role: CrossfireRole,
    val team: CrossfireTeam,
    val won: Boolean,
    val reason: String,
)

@Serializable
data class CrossfirePublicState(
    val phase: CrossfirePhase,
    val round: Int,
    val dealerPlayerId: String? = null,
    val seatOrder: List<String> = emptyList(),
    val currentShuffleGroup: List<String> = emptyList(),
    val shuffleGroupIndex: Int = 0,
    val shuffleGroupCount: Int = 0,
    val currentPlayerId: String? = null,
    val discussionDeadline: Long? = null,
    val confirmedPlayerIds: Set<String> = emptySet(),
    val lockedPlayers: Set<String> = emptySet(),
    val targetsSubmitted: Int = 0,
    val history: List<String> = emptyList(),
    val winnerTeam: CrossfireTeam? = null,
    val revealedRoles: Map<String, CrossfireRole> = emptyMap(),
    val resultByPlayer: Map<String, CrossfireResult> = emptyMap(),
)

@Serializable
data class CrossfirePlayerView(
    val public: CrossfirePublicState,
    val initialRole: CrossfireRole,
    val seenRoles: List<CrossfireIntel>,
    val shuffleHistory: List<CrossfireShuffleRecord>,
    /** The player's own card after a completed shuffle, never another player's card. */
    val currentRole: CrossfireRole? = null,
    val finalRole: CrossfireRole?,
    val canConfirmInitial: Boolean,
    val canConfirmIntel: Boolean,
    val canConfirmShuffle: Boolean,
    val canStartTargeting: Boolean,
    val canSubmitTarget: Boolean,
    val targetIds: List<String>,
)

/**
 * Crossfire Mode V1. The phone handles private information, phase
 * confirmation and simultaneous target submission; discussion remains an
 * offline/tabletop activity.
 *
 * After the simultaneous left pass, scheduled centres advance clockwise by
 * three seats. Each centre and their circular left/right neighbours exchange
 * only their current three identity cards. The ordered records are retained
 * per participant so a reconnecting player sees every shuffle they joined.
 */
class CrossfireGame private constructor(
    val playerIds: List<String>,
    val dealerPlayerId: String,
    private val initialRoles: Map<String, CrossfireRole>,
    private val random: Random,
) : GameInstance {
    override val gameType = GameType.CROSSFIRE

    var phase = CrossfirePhase.INITIAL_VIEW
        private set
    var round = 1
        private set
    var discussionDeadline: Long? = null
        private set
    var winnerTeam: CrossfireTeam? = null
        private set

    /** The only mutable role map. Every shuffle reads and rewrites this map. */
    private val currentRoles = LinkedHashMap(initialRoles)
    private val seenRoleMap = linkedMapOf<String, MutableList<CrossfireIntel>>()
    private val shuffleHistory = linkedMapOf<String, MutableList<CrossfireShuffleRecord>>()
    val shuffleGroups: List<List<String>>
    private var shuffleGroupIndex = 0
    private val confirmed = linkedSetOf<String>()
    private val targets = linkedMapOf<String, String>()
    private val history = mutableListOf<String>()
    private val results = linkedMapOf<String, CrossfireResult>()

    init {
        playerIds.forEach { shuffleHistory[it] = mutableListOf() }
        require(dealerPlayerId in playerIds) { "庄家必须是房间内玩家" }
        shuffleGroups = buildShuffleGroups()
    }

    fun confirmInitial(playerId: String) {
        require(playerId in playerIds) { "玩家不存在" }
        require(phase == CrossfirePhase.INITIAL_VIEW) { "当前不在初始身份阶段" }
        require(playerId !in confirmed) { "该玩家已经确认" }
        confirmed += playerId
        if (confirmed.size == playerIds.size) {
            confirmed.clear()
            buildInitialIntel()
            phase = CrossfirePhase.INTEL_PASS
        }
    }

    fun confirmIntel(playerId: String) {
        require(playerId in playerIds) { "玩家不存在" }
        require(phase == CrossfirePhase.INTEL_PASS) { "当前不在情报传递阶段" }
        require(playerId !in confirmed) { "该玩家已经确认" }
        confirmed += playerId
        if (confirmed.size == playerIds.size) {
            confirmed.clear()
            phase = CrossfirePhase.SHUFFLE
        }
    }

    /** Confirm participation in the current scheduled three-player shuffle. */
    fun confirmShuffle(playerId: String) {
        require(playerId in playerIds) { "玩家不存在" }
        require(phase == CrossfirePhase.SHUFFLE) { "当前不在混洗确认阶段" }
        val group = currentShuffleGroup
        require(playerId in group) { "你不在当前混洗组中" }
        require(playerId !in confirmed) { "该玩家已经确认" }
        confirmed += playerId
        if (confirmed.containsAll(group)) {
            executeShuffle(group)
            confirmed.clear()
            shuffleGroupIndex += 1
            if (shuffleGroupIndex >= shuffleGroups.size) {
                discussionDeadline = System.currentTimeMillis() + DISCUSSION_MILLIS
                phase = CrossfirePhase.DISCUSSION
            }
        }
    }

    /** The currently scheduled group, or empty after all groups have run. */
    val currentShuffleGroup: List<String>
        get() = shuffleGroups.getOrNull(shuffleGroupIndex).orEmpty()

    fun startTargeting(actorId: String, hostId: String) {
        require(phase == CrossfirePhase.DISCUSSION) { "当前不是讨论阶段" }
        require(actorId == hostId || System.currentTimeMillis() >= (discussionDeadline ?: 0L)) { "讨论尚未结束" }
        phase = CrossfirePhase.TARGETING
        targets.clear()
    }

    fun submitTarget(playerId: String, targetId: String) {
        require(phase == CrossfirePhase.TARGETING) { "当前不是射击阶段" }
        require(playerId in playerIds && targetId in playerIds) { "玩家不存在" }
        require(playerId != targetId) { "不能选择自己" }
        require(playerId !in targets) { "不能重复提交" }
        targets[playerId] = targetId
        if (targets.size == playerIds.size) resolve()
    }

    override fun viewFor(playerId: String): CrossfirePlayerView {
        require(playerId in playerIds) { "玩家不存在" }
        val finalRole = if (phase in FINAL_ROLE_PHASES) currentRoles[playerId] else null
        val confirmedSet = confirmed.toSet()
        return CrossfirePlayerView(
            public = publicState(),
            initialRole = initialRoles.getValue(playerId),
            seenRoles = seenRoleMap[playerId]?.toList().orEmpty(),
            shuffleHistory = shuffleHistory.getValue(playerId).toList(),
            currentRole = if (phase == CrossfirePhase.SHUFFLE && shuffleHistory.getValue(playerId).isNotEmpty()) currentRoles[playerId] else null,
            finalRole = finalRole,
            canConfirmInitial = phase == CrossfirePhase.INITIAL_VIEW && playerId !in confirmedSet,
            canConfirmIntel = phase == CrossfirePhase.INTEL_PASS && playerId !in confirmedSet,
            canConfirmShuffle = phase == CrossfirePhase.SHUFFLE && playerId in currentShuffleGroup && playerId !in confirmedSet,
            canStartTargeting = phase == CrossfirePhase.DISCUSSION,
            canSubmitTarget = phase == CrossfirePhase.TARGETING && playerId !in targets,
            targetIds = if (phase == CrossfirePhase.TARGETING && playerId !in targets) playerIds.filter { it != playerId } else emptyList(),
        )
    }

    private fun buildInitialIntel() {
        val passedRoles = linkedMapOf<String, CrossfireRole>()
        playerIds.forEachIndexed { index, playerId ->
            val rightPlayer = playerIds[(index + 1) % playerIds.size]
            val passedRole = initialRoles.getValue(rightPlayer)
            // Every card moves one seat to the left at the same time. The
            // receiver sees the card that came from their right neighbour.
            seenRoleMap[playerId] = mutableListOf(CrossfireIntel(rightPlayer, passedRole))
            passedRoles[playerId] = passedRole
        }
        currentRoles.clear()
        currentRoles.putAll(passedRoles)
    }

    private fun buildShuffleGroups(): List<List<String>> {
        val groups = mutableListOf<List<String>>()
        val covered = linkedSetOf<String>()
        var centerIndex = playerIds.indexOf(dealerPlayerId)
        while (covered.size < playerIds.size) {
            val left = playerIds[(centerIndex - 1 + playerIds.size) % playerIds.size]
            val center = playerIds[centerIndex]
            val right = playerIds[(centerIndex + 1) % playerIds.size]
            val group = listOf(left, center, right)
            groups += group
            covered += group
            centerIndex = (centerIndex + 3) % playerIds.size
        }
        return groups
    }

    private fun executeShuffle(group: List<String>) {
        // Always read the current map so a later overlapping group receives
        // the roles produced by earlier groups, never the initial roles.
        val shuffledRoles = group.map { currentRoles.getValue(it) }.shuffled(random)
        group.forEachIndexed { index, id -> currentRoles[id] = shuffledRoles[index] }
        val record = CrossfireShuffleRecord(group)
        group.forEach { id -> shuffleHistory.getValue(id) += record }
    }

    private fun publicState() = CrossfirePublicState(
        phase = phase,
        round = round,
        dealerPlayerId = dealerPlayerId,
        seatOrder = playerIds.toList(),
        currentShuffleGroup = currentShuffleGroup,
        shuffleGroupIndex = shuffleGroupIndex,
        shuffleGroupCount = shuffleGroups.size,
        currentPlayerId = null,
        discussionDeadline = discussionDeadline,
        confirmedPlayerIds = confirmed.toSet(),
        lockedPlayers = targets.keys.toSet(),
        targetsSubmitted = targets.size,
        history = history.toList(),
        winnerTeam = winnerTeam,
        revealedRoles = if (phase == CrossfirePhase.GAME_OVER) currentRoles.toMap() else emptyMap(),
        resultByPlayer = if (phase == CrossfirePhase.GAME_OVER) results.toMap() else emptyMap(),
    )

    private fun resolve() {
        phase = CrossfirePhase.RESOLVE
        val vipId = currentRoles.entries.firstOrNull { it.value == CrossfireRole.VIP }?.key
        val agentIds = currentRoles.filterValues { it == CrossfireRole.AGENT }.keys
        val assassinIds = currentRoles.filterValues { it == CrossfireRole.ASSASSIN }.keys
        val disabledAssassins = assassinIds.filterTo(linkedSetOf()) { assassinId ->
            agentIds.any { agentId -> targets[agentId] == assassinId }
        }
        val activeAssassins = assassinIds - disabledAssassins
        // Only armed roles fire. Agents fire first; an Assassin hit by an Agent
        // drops their gun and therefore never contributes a shot.
        val activeShooters = agentIds + activeAssassins
        val shootersByTarget = activeShooters.groupBy { targets.getValue(it) }
        val vipShot = vipId != null && vipId in shootersByTarget
        winnerTeam = if (vipShot) CrossfireTeam.ASSASSIN else CrossfireTeam.AGENTS
        val bystanderIds = currentRoles.filterValues { it == CrossfireRole.BYSTANDER }.keys
        val bystanderShooters = bystanderIds.flatMapTo(linkedSetOf()) { shootersByTarget[it].orEmpty() }
        if (disabledAssassins.isNotEmpty()) {
            history += "特工先命中刺客，${disabledAssassins.joinToString("、")}失去射击能力"
        }
        history += if (vipShot) "VIP 被射中，红方达成目标" else "VIP 未被射中，蓝方达成目标"
        if (bystanderShooters.isNotEmpty()) history += "路人被射中，路人与开枪者均失败"
        currentRoles.forEach { (id, role) ->
            val team = teamOf(role)
            val wasShot = id in shootersByTarget
            val shotByAgent = shootersByTarget[id].orEmpty().any { it in agentIds }
            val shotByAssassin = shootersByTarget[id].orEmpty().any { it in activeAssassins }
            val baseWin = when (role) {
                CrossfireRole.VIP, CrossfireRole.AGENT -> !vipShot
                CrossfireRole.ASSASSIN -> vipShot
                CrossfireRole.DECOY -> wasShot
                CrossfireRole.RED_DECOY -> vipShot || shotByAgent
                CrossfireRole.BLUE_DECOY -> !vipShot || shotByAssassin
                CrossfireRole.BYSTANDER -> !wasShot
            }
            val won = baseWin && id !in bystanderShooters
            val reason = when {
                id in bystanderShooters -> "你射中了路人，按路人规则失败"
                role == CrossfireRole.VIP && vipShot -> "VIP 被射中"
                role == CrossfireRole.VIP -> "VIP 未被射中"
                role == CrossfireRole.AGENT -> if (vipShot) "VIP 被射中" else "VIP 未被射中"
                role == CrossfireRole.ASSASSIN && id in disabledAssassins -> "先被特工命中，失去射击能力"
                role == CrossfireRole.ASSASSIN -> if (vipShot) "VIP 被射中" else "VIP 未被射中"
                role == CrossfireRole.DECOY -> if (wasShot) "诱饵被射中" else "诱饵未被射中"
                role == CrossfireRole.RED_DECOY && shotByAgent -> "红方诱饵被特工射中"
                role == CrossfireRole.RED_DECOY -> if (vipShot) "红方达成目标" else "红方未达成目标"
                role == CrossfireRole.BLUE_DECOY && shotByAssassin -> "蓝方诱饵被刺客射中"
                role == CrossfireRole.BLUE_DECOY -> if (!vipShot) "蓝方达成目标" else "蓝方未达成目标"
                role == CrossfireRole.BYSTANDER -> if (wasShot) "路人被射中" else "路人未被射中"
                else -> "按角色条件结算"
            }
            results[id] = CrossfireResult(role, team, won, reason)
        }
        phase = CrossfirePhase.GAME_OVER
    }

    private fun teamOf(role: CrossfireRole): CrossfireTeam = when (role) {
        CrossfireRole.ASSASSIN, CrossfireRole.RED_DECOY -> CrossfireTeam.ASSASSIN
        CrossfireRole.BYSTANDER, CrossfireRole.DECOY -> CrossfireTeam.BYSTANDER
        else -> CrossfireTeam.AGENTS
    }

    companion object {
        private const val DISCUSSION_MILLIS = 3 * 60 * 1000L
        private val FINAL_ROLE_PHASES = setOf(
            CrossfirePhase.DISCUSSION,
            CrossfirePhase.TARGETING,
            CrossfirePhase.RESOLVE,
            CrossfirePhase.GAME_OVER,
        )

        fun create(playerIds: List<String>, random: Random = Random.Default): CrossfireGame = create(playerIds, playerIds.firstOrNull() ?: "", random)

        fun create(playerIds: List<String>, dealerPlayerId: String, random: Random = Random.Default): CrossfireGame {
            require(playerIds.size in 5..10) { "Crossfire 需要 5 至 10 名玩家" }
            require(playerIds.distinct().size == playerIds.size) { "玩家 ID 不能重复" }
            require(dealerPlayerId in playerIds) { "庄家必须是房间内玩家" }
            val roles = defaultRoles(playerIds.size)
            return CrossfireGame(playerIds.toList(), dealerPlayerId, playerIds.zip(roles.shuffled(random)).toMap(), random)
        }

        fun defaultRoles(playerCount: Int): List<CrossfireRole> {
            require(playerCount in 5..10)
            return when (playerCount) {
                5 -> listOf(VIP, AGENT, ASSASSIN, RED_DECOY, BYSTANDER)
                6 -> listOf(VIP, AGENT, ASSASSIN, ASSASSIN, BLUE_DECOY, BYSTANDER)
                7 -> listOf(VIP, AGENT, AGENT, ASSASSIN, ASSASSIN, DECOY, BYSTANDER)
                8 -> listOf(VIP, AGENT, AGENT, ASSASSIN, ASSASSIN, DECOY, RED_DECOY, BYSTANDER)
                9 -> listOf(VIP, AGENT, AGENT, AGENT, ASSASSIN, ASSASSIN, ASSASSIN, DECOY, BYSTANDER)
                10 -> listOf(VIP, AGENT, AGENT, AGENT, ASSASSIN, ASSASSIN, ASSASSIN, RED_DECOY, BLUE_DECOY, BYSTANDER)
                else -> error("Crossfire 人数无效")
            }
        }

        private val VIP = CrossfireRole.VIP
        private val AGENT = CrossfireRole.AGENT
        private val ASSASSIN = CrossfireRole.ASSASSIN
        private val DECOY = CrossfireRole.DECOY
        private val RED_DECOY = CrossfireRole.RED_DECOY
        private val BLUE_DECOY = CrossfireRole.BLUE_DECOY
        private val BYSTANDER = CrossfireRole.BYSTANDER
    }
}
