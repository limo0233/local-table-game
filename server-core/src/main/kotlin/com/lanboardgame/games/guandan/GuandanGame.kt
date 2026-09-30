package com.lanboardgame.games.guandan

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Four-player, two-deck 掼蛋. The server is the only rule authority. */
@Serializable
data class GuandanCard(val id: String, val rank: String, val suit: String)

@Serializable
enum class GuandanPhase { PLAYING, TRIBUTE, FINISHED }

@Serializable
enum class GuandanCombinationType {
    SINGLE, PAIR, TRIPLE, TRIPLE_WITH_PAIR, STRAIGHT, CONSECUTIVE_PAIRS,
    CONSECUTIVE_TRIPLES, STRAIGHT_FLUSH, BOMB, ROCKET,
}

@Serializable
data class GuandanPlay(
    val playerId: String,
    val cards: List<GuandanCard>,
    val type: GuandanCombinationType,
    val strength: Int,
)

@Serializable
data class GuandanRanking(val playerId: String, val position: Int)

@Serializable
data class GuandanTributeRequirement(val fromPlayerId: String, val toPlayerId: String)

@Serializable
data class GuandanTributeState(
    val requirements: List<GuandanTributeRequirement>,
    val submittedPlayerIds: Set<String> = emptySet(),
    val returnedPlayerIds: Set<String> = emptySet(),
    val antiTribute: Boolean = false,
)

@Serializable
data class GuandanPublicState(
    val phase: GuandanPhase,
    val currentPlayerId: String,
    val lastPlay: GuandanPlay? = null,
    val passedPlayerIds: Set<String> = emptySet(),
    val handSizes: Map<String, Int>,
    val teamScores: List<Int>,
    val winnerTeam: Int? = null,
    val turnNumber: Int,
    val roundNumber: Int = 1,
    val currentLevel: String = "2",
    val ranking: List<GuandanRanking> = emptyList(),
    val lastRoundRanking: List<GuandanRanking> = emptyList(),
    val tribute: GuandanTributeState? = null,
    val matchWinnerTeam: Int? = null,
    val lastEvent: String? = null,
)

@Serializable
data class GuandanPlayerView(
    val public: GuandanPublicState,
    val myHand: List<GuandanCard>,
    val canPlay: Boolean,
    val canPass: Boolean,
    val canContinue: Boolean = false,
    val canTribute: Boolean = false,
    val canReturnTribute: Boolean = false,
    val tributeTargetPlayerId: String? = null,
)

class GuandanGame private constructor(
    val playerIds: List<String>,
    private val hands: LinkedHashMap<String, MutableList<GuandanCard>>,
    private val random: Random,
    startingLevel: String,
) : GameInstance {
    override val gameType = GameType.GUANDAN

    var phase = GuandanPhase.PLAYING
        private set
    private var currentIndex = 0
    private var turnNumber = 1
    private var roundNumber = 1
    private var lastPlay: GuandanPlay? = null
    private val passedPlayerIds = linkedSetOf<String>()
    private var winnerTeam: Int? = null
    private var matchWinnerTeam: Int? = null
    private val teamScores = mutableListOf(0, 0)
    private var currentLevel = startingLevel
    private var finishOrder = mutableListOf<String>()
    private var currentRanking = emptyList<GuandanRanking>()
    private var lastRoundRanking = emptyList<GuandanRanking>()
    private var lastEvent: String? = null
    private var tributeRequirements = emptyList<GuandanTributeRequirement>()
    private val submittedTributeCards = linkedMapOf<String, GuandanCard>()
    private val returnedTributeCards = linkedMapOf<String, GuandanCard>()
    private var antiTribute = false

    val currentPlayerId: String get() = playerIds[currentIndex]

    fun play(actorId: String, cardIds: List<String>) {
        require(phase == GuandanPhase.PLAYING) { "当前不是出牌阶段" }
        require(actorId == currentPlayerId) { "还没有轮到该玩家" }
        require(cardIds.isNotEmpty()) { "至少选择一张牌" }
        require(cardIds.distinct().size == cardIds.size) { "不能重复出牌" }
        val hand = hands.getValue(actorId)
        val cards = cardIds.map { id -> hand.firstOrNull { it.id == id } ?: error("所选牌不在手牌中") }
        val combination = combinationOf(cards) ?: error("牌型无效，请选择标准掼蛋牌型")
        lastPlay?.let { require(canBeat(combination, it)) { "牌型或牌力不足，无法压过上一手" } }

        hand.removeAll { it.id in cardIds }
        lastPlay = GuandanPlay(actorId, cards.sortedWith(cardComparator()), combination.type, combination.strength)
        passedPlayerIds.clear()
        lastEvent = null
        if (hand.isEmpty()) {
            if (actorId !in finishOrder) finishOrder += actorId
            val team = teamOf(actorId)
            if (winnerTeam == null && teamComplete(team)) winnerTeam = team
            if (finishOrder.size == playerIds.size - 1) {
                finishOrder += playerIds.single { hands.getValue(it).isNotEmpty() }
                finishRound()
                return
            }
        }
        if (hands.values.all { it.isEmpty() }) finishRound() else advance()
    }

    fun pass(actorId: String) {
        require(phase == GuandanPhase.PLAYING) { "当前不是出牌阶段" }
        require(actorId == currentPlayerId) { "还没有轮到该玩家" }
        val previous = requireNotNull(lastPlay) { "新一轮必须先出牌" }
        require(previous.playerId != actorId) { "上一手由你打出，不能跳过整轮" }
        passedPlayerIds += actorId
        val active = activePlayerIds()
        val requiredPasses = active.count { it != previous.playerId }.coerceAtLeast(1)
        if (passedPlayerIds.intersect(active.toSet()).size >= requiredPasses) {
            val partner = partnerOf(previous.playerId)
            val lead = if (hands.getValue(previous.playerId).isEmpty() && hands.getValue(partner).isNotEmpty()) {
                lastEvent = "${previous.playerId}出完 → 对家${partner}接风"
                partner
            } else if (hands.getValue(previous.playerId).isNotEmpty()) {
                previous.playerId
            } else nextActiveId(previous.playerId)
            currentIndex = playerIds.indexOf(lead)
            lastPlay = null
            passedPlayerIds.clear()
            turnNumber++
        } else {
            lastEvent = null
            advance()
        }
    }

    /** Host chooses to continue after the result screen. */
    fun continueNextHand() {
        require(phase == GuandanPhase.FINISHED) { "当前没有可继续的掼蛋局" }
        require(matchWinnerTeam == null) { "整场比赛已经结束" }
        val ranking = currentRanking
        val winner = winnerTeam ?: error("上一局没有胜方")
        val head = ranking.firstOrNull()?.playerId ?: error("上一局排名不存在")
        val partnerPosition = ranking.indexOfFirst { it.playerId == partnerOf(head) }
        if (currentLevel == "A") {
            if (partnerPosition == 1 || partnerPosition == 2) {
                matchWinnerTeam = winner
                return
            }
        } else {
            val step = when (partnerPosition) { 1 -> 3; 2 -> 2; 3 -> 1; else -> 0 }
            currentLevel = advanceLevel(currentLevel, step)
        }
        roundNumber++
        dealHand()
        prepareTribute(ranking, winner)
        if (antiTribute || tributeRequirements.isEmpty()) {
            beginPlaying(firstPlayerWithoutTribute(ranking))
        } else {
            phase = GuandanPhase.TRIBUTE
            currentIndex = playerIds.indexOf(head)
            lastPlay = null
            passedPlayerIds.clear()
            turnNumber = 1
            lastEvent = null
        }
    }

    fun submitTribute(actorId: String, cardId: String) {
        require(phase == GuandanPhase.TRIBUTE) { "当前不是进贡阶段" }
        require(!antiTribute) { "本轮已经抗贡" }
        val requirement = tributeRequirements.firstOrNull { it.fromPlayerId == actorId } ?: error("你不需要进贡")
        require(actorId !in submittedTributeCards) { "你已经完成进贡" }
        val hand = hands.getValue(actorId)
        val card = hand.firstOrNull { it.id == cardId } ?: error("进贡牌不在手中")
        val maxValue = hand.maxOf { effectiveRankValue(it.rank) }
        require(effectiveRankValue(card.rank) == maxValue) { "进贡必须选择自己最大的牌" }
        hand.remove(card)
        hands.getValue(requirement.toPlayerId).add(card)
        submittedTributeCards[actorId] = card
        if (submittedTributeCards.keys.containsAll(tributeRequirements.map { it.fromPlayerId }) && returnedTributeCards.keys.containsAll(tributeRequirements.map { it.toPlayerId })) finishTribute()
    }

    fun returnTribute(actorId: String, targetPlayerId: String, cardId: String) {
        require(phase == GuandanPhase.TRIBUTE) { "当前不是还贡阶段" }
        require(!antiTribute) { "本轮已经抗贡" }
        val requirement = tributeRequirements.firstOrNull { it.toPlayerId == actorId && it.fromPlayerId == targetPlayerId } ?: error("不是对应的还贡对象")
        require(requirement.fromPlayerId in submittedTributeCards) { "等待对方进贡" }
        require(actorId !in returnedTributeCards) { "你已经完成还贡" }
        val hand = hands.getValue(actorId)
        val card = hand.firstOrNull { it.id == cardId } ?: error("还贡牌不在手中")
        hand.remove(card)
        hands.getValue(targetPlayerId).add(card)
        returnedTributeCards[actorId] = card
        if (returnedTributeCards.keys.containsAll(tributeRequirements.map { it.toPlayerId })) finishTribute()
    }

    override fun viewFor(playerId: String): GuandanPlayerView {
        require(playerId in playerIds) { "玩家不存在" }
        val from = tributeRequirements.firstOrNull { it.fromPlayerId == playerId }
        val to = tributeRequirements.firstOrNull { it.toPlayerId == playerId && it.fromPlayerId in submittedTributeCards }
        val tributeView = if (tributeRequirements.isEmpty() && !antiTribute) null else GuandanTributeState(tributeRequirements, submittedTributeCards.keys.toSet(), returnedTributeCards.keys.toSet(), antiTribute)
        return GuandanPlayerView(
            public = GuandanPublicState(phase, currentPlayerId, lastPlay, passedPlayerIds.toSet(), hands.mapValues { it.value.size }, teamScores.toList(), winnerTeam, turnNumber, roundNumber, currentLevel, currentRanking, lastRoundRanking, tributeView, matchWinnerTeam, lastEvent),
            myHand = hands.getValue(playerId).sortedWith(cardComparator()),
            canPlay = phase == GuandanPhase.PLAYING && currentPlayerId == playerId,
            canPass = phase == GuandanPhase.PLAYING && currentPlayerId == playerId && lastPlay != null && lastPlay?.playerId != playerId,
            canContinue = phase == GuandanPhase.FINISHED && matchWinnerTeam == null,
            canTribute = phase == GuandanPhase.TRIBUTE && !antiTribute && from != null && playerId !in submittedTributeCards,
            canReturnTribute = phase == GuandanPhase.TRIBUTE && !antiTribute && to != null && playerId !in returnedTributeCards,
            tributeTargetPlayerId = to?.fromPlayerId,
        )
    }

    private data class Combination(val type: GuandanCombinationType, val strength: Int, val size: Int, val bombPower: Int = 0)

    private fun combinationOf(cards: List<GuandanCard>): Combination? {
        if (cards.isEmpty()) return null
        val wildIndices = cards.indices.filter { isWildcard(cards[it]) }
        if (wildIndices.isEmpty()) return classifyConcrete(cards, cards.map { it.rank })
        if (cards.size == 1) return classifyConcrete(cards, listOf(cards.single().rank))
        val assignments = mutableListOf<List<String>>()
        fun build(index: Int, ranks: MutableList<String>) {
            if (index == wildIndices.size) { assignments += ranks.toList(); return }
            for (rank in naturalRanks) { ranks[wildIndices[index]] = rank; build(index + 1, ranks) }
        }
        build(0, cards.map { it.rank }.toMutableList())
        return assignments.mapNotNull { classifyConcrete(cards, it) }.maxWithOrNull(compareBy<Combination>({ categoryPower(it) }, { it.bombPower }, { it.strength }))
    }

    private fun classifyConcrete(cards: List<GuandanCard>, ranks: List<String>): Combination? {
        val distinct = ranks.distinct()
        val counts = ranks.groupingBy { it }.eachCount()
        val noJokers = ranks.none { it == SMALL_JOKER || it == BIG_JOKER }
        val ordered = distinct.filterNot { it == SMALL_JOKER || it == BIG_JOKER }.map { naturalValue(it) }.sorted()
        val consecutive = noJokers && ordered.size == distinct.size && ordered.zipWithNext().all { (a, b) -> b == a + 1 }
        val nonWildSuits = cards.indices.filterNot { isWildcard(cards[it]) }.map { cards[it].suit }.distinct()
        val sameSuit = nonWildSuits.size <= 1 && noJokers
        val highRank = distinct.filterNot { it == SMALL_JOKER || it == BIG_JOKER }.maxByOrNull { naturalValue(it) }
        return when {
            cards.size == 1 -> Combination(GuandanCombinationType.SINGLE, effectiveRankValue(ranks.single()), 1)
            cards.size == 2 && distinct.size == 1 -> Combination(GuandanCombinationType.PAIR, effectiveRankValue(distinct.single()), 2)
            cards.size == 3 && distinct.size == 1 -> Combination(GuandanCombinationType.TRIPLE, effectiveRankValue(distinct.single()), 3)
            cards.size == 4 && counts[SMALL_JOKER] == 2 && counts[BIG_JOKER] == 2 -> Combination(GuandanCombinationType.ROCKET, 0, 4, 100)
            cards.size >= 4 && distinct.size == 1 && noJokers -> Combination(GuandanCombinationType.BOMB, effectiveRankValue(distinct.single()), cards.size, cards.size)
            cards.size == 5 && counts.values.sorted() == listOf(2, 3) -> Combination(GuandanCombinationType.TRIPLE_WITH_PAIR, effectiveRankValue(counts.entries.first { it.value == 3 }.key), 5)
            cards.size == 6 && counts.values.all { it == 2 } && consecutive -> Combination(GuandanCombinationType.CONSECUTIVE_PAIRS, effectiveRankValue(highRank!!), cards.size)
            cards.size == 6 && counts.values.all { it == 3 } && consecutive -> Combination(GuandanCombinationType.CONSECUTIVE_TRIPLES, effectiveRankValue(highRank!!), cards.size)
            cards.size == 5 && consecutive && sameSuit -> Combination(GuandanCombinationType.STRAIGHT_FLUSH, effectiveRankValue(highRank!!), cards.size, 5)
            cards.size == 5 && consecutive && noJokers -> Combination(GuandanCombinationType.STRAIGHT, effectiveRankValue(highRank!!), cards.size)
            else -> null
        }
    }

    private fun canBeat(candidate: Combination, previous: GuandanPlay): Boolean {
        val old = combinationOf(previous.cards) ?: return false
        if (candidate.type == GuandanCombinationType.ROCKET) return previous.type != GuandanCombinationType.ROCKET
        if (previous.type == GuandanCombinationType.ROCKET) return false
        val candidatePower = categoryPower(candidate)
        val oldPower = categoryPower(old)
        if (candidatePower != 0 || oldPower != 0) {
            if (candidatePower != oldPower) return candidatePower > oldPower
            if (candidate.type == GuandanCombinationType.BOMB && old.type == GuandanCombinationType.BOMB) return candidate.size > old.size || candidate.size == old.size && candidate.strength > old.strength
            if (candidate.type != old.type || candidate.size != old.size) return false
            return candidate.strength > old.strength
        }
        return candidate.type == old.type && candidate.size == old.size && candidate.strength > old.strength
    }

    private fun categoryPower(c: Combination): Int = when (c.type) {
        GuandanCombinationType.ROCKET -> 1_000
        GuandanCombinationType.BOMB -> if (c.size >= 6) 700 + c.size else if (c.size == 5) 500 else 300
        GuandanCombinationType.STRAIGHT_FLUSH -> 600
        else -> 0
    }

    private fun finishRound() {
        winnerTeam = winnerTeam ?: error("无法确定胜方")
        currentRanking = finishOrder.mapIndexed { index, id -> GuandanRanking(id, index + 1) }
        lastRoundRanking = currentRanking
        teamScores[winnerTeam!!]++
        phase = GuandanPhase.FINISHED
        currentIndex = playerIds.indexOf(currentRanking.first().playerId)
        lastPlay = null
        passedPlayerIds.clear()
    }

    private fun prepareTribute(ranking: List<GuandanRanking>, winner: Int) {
        tributeRequirements = emptyList()
        submittedTributeCards.clear()
        returnedTributeCards.clear()
        val doubleDown = ranking.size >= 2 && teamOf(ranking[0].playerId) == winner && teamOf(ranking[1].playerId) == winner
        val losers = ranking.drop(2).map { it.playerId }
        antiTribute = if (doubleDown) {
            val counts = losers.map { loserId -> hands.getValue(loserId).count { it.rank == BIG_JOKER } }
            counts == listOf(1, 1) || counts.any { it >= 2 }
        } else {
            val loser = ranking.lastOrNull()?.playerId
            loser != null && hands.getValue(loser).count { it.rank == BIG_JOKER } >= 2
        }
        if (antiTribute) return
        tributeRequirements = if (doubleDown && losers.size == 2) listOf(GuandanTributeRequirement(losers[0], ranking[0].playerId), GuandanTributeRequirement(losers[1], ranking[1].playerId)) else if (ranking.isNotEmpty()) listOf(GuandanTributeRequirement(ranking.last().playerId, ranking.first().playerId)) else emptyList()
    }

    private fun firstPlayerWithoutTribute(ranking: List<GuandanRanking>): String {
        if (antiTribute || tributeRequirements.isEmpty()) return ranking.first().playerId
        if (tributeRequirements.size == 1) return tributeRequirements.single().fromPlayerId
        val a = submittedTributeCards[tributeRequirements[0].fromPlayerId]
        val b = submittedTributeCards[tributeRequirements[1].fromPlayerId]
        if (a == null || b == null) return ranking.first().playerId
        val av = effectiveRankValue(a.rank)
        val bv = effectiveRankValue(b.rank)
        return when { av > bv -> tributeRequirements[0].fromPlayerId; bv > av -> tributeRequirements[1].fromPlayerId; random.nextBoolean() -> tributeRequirements[0].fromPlayerId; else -> tributeRequirements[1].fromPlayerId }
    }

    private fun finishTribute() = beginPlaying(firstPlayerWithoutTribute(lastRoundRanking))
    private fun beginPlaying(firstPlayerId: String) {
        phase = GuandanPhase.PLAYING
        currentIndex = playerIds.indexOf(firstPlayerId)
        lastPlay = null
        passedPlayerIds.clear()
        turnNumber = 1
        lastEvent = null
    }

    private fun dealHand() {
        val deck = buildDeck().shuffled(random)
        winnerTeam = null
        finishOrder.clear()
        currentRanking = emptyList()
        playerIds.forEachIndexed { index, id ->
            hands.getValue(id).clear()
            hands.getValue(id).addAll(deck.drop(index * 27).take(27).sortedWith(cardComparator()))
        }
    }

    private fun advance() { currentIndex = nextActiveIndex(currentPlayerId); turnNumber++ }
    private fun teamOf(id: String): Int = playerIds.indexOf(id) % 2
    private fun partnerOf(id: String): String = playerIds[(playerIds.indexOf(id) + 2) % playerIds.size]
    private fun teamComplete(team: Int): Boolean = playerIds.withIndex().filter { it.index % 2 == team }.all { hands.getValue(it.value).isEmpty() }
    private fun activePlayerIds(): List<String> = playerIds.filter { hands.getValue(it).isNotEmpty() }
    private fun nextActiveId(fromId: String): String {
        val active = activePlayerIds()
        require(active.isNotEmpty()) { "没有可行动玩家" }
        val start = playerIds.indexOf(fromId)
        for (step in 1..playerIds.size) {
            val id = playerIds[(start + step) % playerIds.size]
            if (id in active) return id
        }
        return active.first()
    }
    private fun nextActiveIndex(fromId: String): Int = playerIds.indexOf(nextActiveId(fromId))
    private fun isWildcard(card: GuandanCard): Boolean = card.rank == currentLevel && card.suit == HEARTS
    private fun effectiveRankValue(rank: String): Int = when { rank == SMALL_JOKER -> 17; rank == BIG_JOKER -> 18; rank == currentLevel -> 16; else -> naturalValue(rank) + 3 }
    private fun naturalValue(rank: String): Int = naturalRanks.indexOf(rank).takeIf { it >= 0 } ?: error("未知牌面 $rank")
    private fun cardComparator(): Comparator<GuandanCard> = compareBy({ effectiveRankValue(it.rank) }, { it.suit }, { it.id })

    internal fun classifyForTest(cards: List<GuandanCard>): GuandanCombinationType? = combinationOf(cards)?.type
    internal fun canBeatForTest(candidate: List<GuandanCard>, previous: List<GuandanCard>): Boolean {
        val c = combinationOf(candidate) ?: return false
        val p = combinationOf(previous) ?: return false
        return canBeat(c, GuandanPlay("previous", previous, p.type, p.strength))
    }
    internal fun effectiveValueForTest(card: GuandanCard): Int = effectiveRankValue(card.rank)
    internal fun replaceHandsForTest(newHands: Map<String, List<GuandanCard>>, firstPlayerId: String = playerIds.first()) {
        require(newHands.keys == playerIds.toSet())
        newHands.forEach { (id, cards) -> hands.getValue(id).apply { clear(); addAll(cards) } }
        phase = GuandanPhase.PLAYING
        currentIndex = playerIds.indexOf(firstPlayerId)
        lastPlay = null
        passedPlayerIds.clear()
        finishOrder.clear()
        currentRanking = emptyList()
        winnerTeam = null
        matchWinnerTeam = null
        turnNumber = 1
    }

    companion object {
        private const val SMALL_JOKER = "SMALL_JOKER"
        private const val BIG_JOKER = "BIG_JOKER"
        private const val HEARTS = "HEARTS"
        private val naturalRanks = listOf("3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2")
        private val levelRanks = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")

        fun create(playerIds: List<String>, random: Random = Random.Default, startingLevel: String = "2"): GuandanGame {
            require(playerIds.size == 4) { "掼蛋需要 4 名玩家" }
            require(playerIds.distinct().size == playerIds.size) { "玩家 ID 不能重复" }
            require(startingLevel in naturalRanks) { "未知级牌" }
            val deck = buildDeck().shuffled(random)
            val hands = linkedMapOf<String, MutableList<GuandanCard>>()
            playerIds.forEachIndexed { index, id -> hands[id] = deck.drop(index * 27).take(27).sortedWith(compareBy({ naturalRanks.indexOf(it.rank) }, { it.suit }, { it.id })).toMutableList() }
            return GuandanGame(playerIds.toList(), hands, random, startingLevel)
        }

        private fun buildDeck(): List<GuandanCard> = buildList {
            var index = 0
            repeat(2) {
                for (suit in listOf("CLUBS", "DIAMONDS", "HEARTS", "SPADES")) for (rank in naturalRanks) add(GuandanCard("card-${index++}", rank, suit))
                add(GuandanCard("card-${index++}", SMALL_JOKER, "JOKER"))
                add(GuandanCard("card-${index++}", BIG_JOKER, "JOKER"))
            }
        }

        private fun advanceLevel(level: String, step: Int): String {
            val index = levelRanks.indexOf(level)
            return levelRanks[(index + step).coerceAtMost(levelRanks.lastIndex)]
        }
    }
}
