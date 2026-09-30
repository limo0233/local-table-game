package com.lanboardgame.games.avalon

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class AvalonRole(val evil: Boolean) {
    MERLIN(false), PERCIVAL(false), LOYAL_SERVANT(false),
    ASSASSIN(true), MORGANA(true), MORDRED(true), OBERON(true), MINION_OF_MORDRED(true),
}

@Serializable
data class AvalonConfig(
    val playerCount: Int,
    val roles: List<AvalonRole>,
)

@Serializable
enum class AvalonPhase {
    TEAM_SELECTION, TEAM_VOTE, MISSION, ASSASSINATION, FINISHED,
}

@Serializable
enum class AvalonWinner { GOOD, EVIL }

@Serializable
data class AvalonKnowledge(val playerId: String, val hint: String)

@Serializable
data class MissionRecord(
    val round: Int,
    val team: List<String>,
    val approved: Boolean,
    val success: Boolean? = null,
    val failCount: Int? = null,
)

@Serializable
data class TeamVoteRecord(
    val round: Int,
    val proposal: Int,
    val leaderId: String,
    val team: List<String>,
    val votes: Map<String, Boolean>,
    val approved: Boolean,
)

@Serializable
data class AvalonPublicState(
    val phase: AvalonPhase,
    val round: Int,
    val leaderId: String,
    val teamSize: Int,
    val selectedTeam: List<String>,
    val proposalRejects: Int,
    val currentProposal: Int,
    val totalVoteCount: Int,
    val voteHistory: List<TeamVoteRecord>,
    val missions: List<MissionRecord>,
    val submittedPlayerIds: Set<String>,
    val winner: AvalonWinner? = null,
    val endReason: String? = null,
)

@Serializable
data class AvalonPlayerView(
    val public: AvalonPublicState,
    val myRole: AvalonRole,
    val knowledge: List<AvalonKnowledge>,
    val canSelectTeam: Boolean,
    val canVote: Boolean,
    val canSubmitMission: Boolean,
    val canAssassinate: Boolean,
)

class AvalonGame private constructor(
    val playerIds: List<String>,
    private val roles: Map<String, AvalonRole>,
    private val random: Random,
) : GameInstance {
    override val gameType = GameType.AVALON
    var phase = AvalonPhase.TEAM_SELECTION
        private set
    var round = 1
        private set
    var leaderIndex = 0
        private set
    var selectedTeam: List<String> = emptyList()
        private set
    var proposalRejects = 0
        private set
    var winner: AvalonWinner? = null
        private set
    var endReason: String? = null
        private set
    private val votes = linkedMapOf<String, Boolean>()
    private val missionChoices = linkedMapOf<String, Boolean>()
    private val missionHistory = mutableListOf<MissionRecord>()
    private val teamVoteHistory = mutableListOf<TeamVoteRecord>()

    val leaderId: String get() = playerIds[leaderIndex]
    val requiredTeamSize: Int get() = TEAM_SIZES.getValue(playerIds.size)[round - 1]

    fun roleOf(playerId: String): AvalonRole = roles.getValue(playerId)

    fun selectTeam(actorId: String, team: List<String>) {
        require(phase == AvalonPhase.TEAM_SELECTION) { "当前不是组队阶段" }
        require(actorId == leaderId) { "只有队长可以选择队伍" }
        require(team.distinct().size == requiredTeamSize) { "任务需要 $requiredTeamSize 名队员" }
        require(team.all(playerIds::contains)) { "队伍包含无效玩家" }
        selectedTeam = team.distinct()
        votes.clear()
        phase = AvalonPhase.TEAM_VOTE
    }

    fun vote(actorId: String, approve: Boolean) {
        require(phase == AvalonPhase.TEAM_VOTE) { "当前不是投票阶段" }
        require(actorId in playerIds) { "玩家不存在" }
        require(actorId !in votes) { "不能重复投票" }
        votes[actorId] = approve
        if (votes.size != playerIds.size) return

        val approved = votes.values.count { it } > playerIds.size / 2
        teamVoteHistory += TeamVoteRecord(
            round = round,
            proposal = proposalRejects + 1,
            leaderId = leaderId,
            team = selectedTeam.toList(),
            votes = votes.toMap(),
            approved = approved,
        )
        if (approved) {
            missionChoices.clear()
            phase = AvalonPhase.MISSION
        } else {
            missionHistory += MissionRecord(round, selectedTeam, approved = false)
            proposalRejects++
            if (proposalRejects >= 5) finish(AvalonWinner.EVIL, "连续五次组队未获通过")
            else rotateLeaderAndSelect()
        }
    }

    /** success=true means a success card. Good roles may never submit failure. */
    fun submitMission(actorId: String, success: Boolean) {
        require(phase == AvalonPhase.MISSION) { "当前不是任务阶段" }
        require(actorId in selectedTeam) { "只有任务队员可以行动" }
        require(actorId !in missionChoices) { "不能重复提交" }
        require(roles.getValue(actorId).evil || success) { "好人只能选择任务成功" }
        missionChoices[actorId] = success
        if (missionChoices.size != selectedTeam.size) return

        val fails = missionChoices.values.count { !it }
        val requiredFails = if (playerIds.size >= 7 && round == 4) 2 else 1
        val successResult = fails < requiredFails
        missionHistory += MissionRecord(round, selectedTeam, approved = true, successResult, fails)
        proposalRejects = 0

        val successes = missionHistory.count { it.success == true }
        val failures = missionHistory.count { it.success == false }
        when {
            failures >= 3 -> finish(AvalonWinner.EVIL, "坏人阵营破坏了三次任务")
            successes >= 3 -> phase = AvalonPhase.ASSASSINATION
            else -> {
                round++
                rotateLeaderAndSelect()
            }
        }
    }

    fun assassinate(actorId: String, targetId: String) {
        require(phase == AvalonPhase.ASSASSINATION) { "当前不是刺杀阶段" }
        require(roles.getValue(actorId) == AvalonRole.ASSASSIN) { "只有刺客可以刺杀" }
        require(targetId in playerIds) { "目标玩家不存在" }
        if (roles.getValue(targetId) == AvalonRole.MERLIN) {
            finish(AvalonWinner.EVIL, "刺客成功找出梅林")
        } else {
            finish(AvalonWinner.GOOD, "刺客未能找出梅林")
        }
    }

    fun knowledgeFor(playerId: String): List<AvalonKnowledge> {
        val role = roles.getValue(playerId)
        return when (role) {
            AvalonRole.MERLIN -> roles.filterValues { it.evil && it != AvalonRole.MORDRED }
                .keys.shuffled(random).map { AvalonKnowledge(it, "邪恶阵营") }
            AvalonRole.PERCIVAL -> roles.filterValues { it == AvalonRole.MERLIN || it == AvalonRole.MORGANA }
                .keys.shuffled(random).map { AvalonKnowledge(it, "梅林或莫甘娜") }
            AvalonRole.OBERON -> emptyList()
            else -> if (role.evil) roles.filter { (id, otherRole) ->
                id != playerId && otherRole.evil && otherRole != AvalonRole.OBERON
            }.keys.shuffled(random).map { AvalonKnowledge(it, "邪恶同伴") } else emptyList()
        }
    }

    override fun viewFor(playerId: String): AvalonPlayerView {
        val submitted = when (phase) {
            AvalonPhase.TEAM_VOTE -> votes.keys
            AvalonPhase.MISSION -> missionChoices.keys
            else -> emptySet()
        }
        val public = AvalonPublicState(
            phase, round, leaderId, requiredTeamSize.coerceAtMost(playerIds.size), selectedTeam,
            proposalRejects, minOf(proposalRejects + 1, 5), teamVoteHistory.size, teamVoteHistory.toList(),
            missionHistory.toList(), submitted, winner, endReason,
        )
        return AvalonPlayerView(
            public = public,
            myRole = roles.getValue(playerId),
            knowledge = knowledgeFor(playerId),
            canSelectTeam = phase == AvalonPhase.TEAM_SELECTION && playerId == leaderId,
            canVote = phase == AvalonPhase.TEAM_VOTE && playerId !in votes,
            canSubmitMission = phase == AvalonPhase.MISSION && playerId in selectedTeam && playerId !in missionChoices,
            canAssassinate = phase == AvalonPhase.ASSASSINATION && roles.getValue(playerId) == AvalonRole.ASSASSIN,
        )
    }

    private fun rotateLeaderAndSelect() {
        leaderIndex = (leaderIndex + 1) % playerIds.size
        selectedTeam = emptyList()
        votes.clear()
        missionChoices.clear()
        phase = AvalonPhase.TEAM_SELECTION
    }

    private fun finish(result: AvalonWinner, reason: String) {
        winner = result
        endReason = reason
        phase = AvalonPhase.FINISHED
    }

    companion object {
        private val TEAM_SIZES = mapOf(
            5 to listOf(2, 3, 2, 3, 3),
            6 to listOf(2, 3, 4, 3, 4),
            7 to listOf(2, 3, 3, 4, 4),
            8 to listOf(3, 4, 4, 5, 5),
            9 to listOf(3, 4, 4, 5, 5),
            10 to listOf(3, 4, 4, 5, 5),
        )

        fun create(playerIds: List<String>, random: Random = Random.Default): AvalonGame =
            create(playerIds, defaultConfig(playerIds.size), random)

        fun create(playerIds: List<String>, config: AvalonConfig, random: Random = Random.Default): AvalonGame {
            require(playerIds.size in 5..10) { "阿瓦隆需要 5 至 10 名玩家" }
            require(playerIds.distinct().size == playerIds.size) { "玩家 ID 不能重复" }
            val normalized = validateConfig(config)
            require(playerIds.size == normalized.playerCount) { "当前玩家人数与房主设置不一致" }
            val roleList = normalized.roles.shuffled(random)
            return AvalonGame(playerIds.shuffled(random), playerIds.zip(roleList).toMap(), random)
        }

        fun defaultConfig(count: Int) = AvalonConfig(count, rolesFor(count))

        fun validateConfig(config: AvalonConfig): AvalonConfig {
            val count = config.playerCount
            require(count in 5..10) { "阿瓦隆人数必须为 5 至 10 人" }
            require(config.roles.size == count) { "角色数量必须与玩家人数一致" }
            val evilCount = when (count) { 5, 6 -> 2; 7, 8, 9 -> 3; else -> 4 }
            require(config.roles.count(AvalonRole::evil) == evilCount) { "邪恶阵营人数不符合规则" }
            require(config.roles.count { it == AvalonRole.MERLIN } == 1) { "必须且只能有一名梅林" }
            require(config.roles.count { it == AvalonRole.ASSASSIN } == 1) { "必须且只能有一名刺客" }
            val uniqueRoles = config.roles.filterNot {
                it == AvalonRole.LOYAL_SERVANT || it == AvalonRole.MINION_OF_MORDRED
            }
            require(uniqueRoles.distinct().size == uniqueRoles.size) { "特殊角色不能重复" }
            return config.copy(roles = config.roles.toList())
        }

        fun fromRoles(roles: LinkedHashMap<String, AvalonRole>, leaderId: String? = null): AvalonGame {
            require(roles.size in 5..10)
            val ordered = roles.keys.toMutableList()
            if (leaderId != null) {
                val index = ordered.indexOf(leaderId)
                require(index >= 0)
                java.util.Collections.rotate(ordered, -index)
            }
            return AvalonGame(ordered, roles, Random(0))
        }

        fun rolesFor(count: Int): List<AvalonRole> {
            val evilCount = when (count) { 5, 6 -> 2; 7, 8, 9 -> 3; 10 -> 4; else -> error("人数无效") }
            val evil = when (count) {
                5, 6 -> listOf(AvalonRole.ASSASSIN, AvalonRole.MORGANA)
                7, 8 -> listOf(AvalonRole.ASSASSIN, AvalonRole.MORGANA, AvalonRole.OBERON)
                9 -> listOf(AvalonRole.ASSASSIN, AvalonRole.MORGANA, AvalonRole.MORDRED)
                10 -> listOf(AvalonRole.ASSASSIN, AvalonRole.MORGANA, AvalonRole.MORDRED, AvalonRole.OBERON)
                else -> error("人数无效")
            }
            val good = mutableListOf(AvalonRole.MERLIN, AvalonRole.PERCIVAL)
            repeat(count - evilCount - good.size) { good += AvalonRole.LOYAL_SERVANT }
            return good + evil
        }
    }
}
