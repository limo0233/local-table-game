package com.lanboardgame.games.poker

import com.lanboardgame.platform.GameInstance
import com.lanboardgame.platform.GameType
import kotlinx.serialization.Serializable

@Serializable
data class PokerConfig(
    val startingChips: Int = 1000,
    val smallBlind: Int = 10,
    val bigBlind: Int = 20,
    val maxPlayers: Int = 8,
) {
    init {
        require(startingChips > 0)
        require(smallBlind > 0 && bigBlind >= smallBlind)
        require(maxPlayers in 2..8)
    }
}

@Serializable
enum class PokerPhase { WAITING, STARTING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, HAND_FINISHED, GAME_FINISHED }

@Serializable
enum class PokerActionType { FOLD, CHECK, CALL, RAISE_TO, ALL_IN }

@Serializable
data class PokerAction(val type: PokerActionType, val amount: Int? = null)

data class PokerPlayer(
    val id: String,
    var chips: Int,
    var folded: Boolean = false,
    var allIn: Boolean = false,
    var streetBet: Int = 0,
    var totalBet: Int = 0,
    val holeCards: MutableList<Card> = mutableListOf(),
)

@Serializable
data class PokerSeatView(
    val id: String,
    val chips: Int,
    val folded: Boolean,
    val allIn: Boolean,
    val streetBet: Int,
    val isDealer: Boolean,
    val isSmallBlind: Boolean,
    val isBigBlind: Boolean,
    val revealedCards: List<Card>? = null,
)

@Serializable
data class PokerPublicState(
    val phase: PokerPhase,
    val handNumber: Int,
    val seats: List<PokerSeatView>,
    val communityCards: List<Card>,
    val potTotal: Int,
    val currentBet: Int,
    val minRaiseTo: Int,
    val actingPlayerId: String?,
    val lastAwards: List<PotAward>,
)

@Serializable
data class PokerPlayerView(
    val public: PokerPublicState,
    val myCards: List<Card>,
    val toCall: Int,
    val availableActions: Set<PokerActionType>,
)

class PokerGame private constructor(
    val config: PokerConfig,
    val players: MutableList<PokerPlayer>,
    private var deck: Deck,
) : GameInstance {
    override val gameType = GameType.POKER
    var phase = PokerPhase.WAITING
        private set
    var handNumber = 0
        private set
    var dealerIndex = -1
        private set
    var smallBlindIndex = -1
        private set
    var bigBlindIndex = -1
        private set
    var actingIndex: Int? = null
        private set
    var currentBet = 0
        private set
    var minRaiseIncrement = config.bigBlind
        private set
    val communityCards = mutableListOf<Card>()
    var lastAwards: List<PotAward> = emptyList()
        private set
    private val pendingToAct = linkedSetOf<String>()
    private val raiseAllowed = linkedSetOf<String>()

    init {
        require(players.size in 2..config.maxPlayers)
    }

    fun startHand(newDeck: Deck = Deck.shuffled()) {
        require(phase == PokerPhase.WAITING || phase == PokerPhase.HAND_FINISHED) { "当前不能开始新牌局" }
        val funded = players.indices.filter { players[it].chips > 0 }
        if (funded.size < 2) {
            phase = PokerPhase.GAME_FINISHED
            actingIndex = null
            return
        }
        deck = newDeck
        handNumber++
        lastAwards = emptyList()
        communityCards.clear()
        players.forEach {
            it.folded = it.chips <= 0
            it.allIn = false
            it.streetBet = 0
            it.totalBet = 0
            it.holeCards.clear()
        }
        dealerIndex = nextFundedIndex(dealerIndex)
        if (funded.size == 2) {
            smallBlindIndex = dealerIndex
            bigBlindIndex = nextFundedIndex(smallBlindIndex)
        } else {
            smallBlindIndex = nextFundedIndex(dealerIndex)
            bigBlindIndex = nextFundedIndex(smallBlindIndex)
        }
        postBlind(smallBlindIndex, config.smallBlind)
        postBlind(bigBlindIndex, config.bigBlind)
        dealHoleCards()
        currentBet = config.bigBlind
        minRaiseIncrement = config.bigBlind
        phase = PokerPhase.PREFLOP
        beginBettingRound(nextEligibleIndex(bigBlindIndex))
        resolveProgress()
    }

    fun validateAction(playerId: String, action: PokerAction) {
        require(phase in setOf(PokerPhase.PREFLOP, PokerPhase.FLOP, PokerPhase.TURN, PokerPhase.RIVER)) { "当前不能下注" }
        val index = players.indexOfFirst { it.id == playerId }
        require(index >= 0 && actingIndex == index) { "还没有轮到该玩家" }
        val player = players[index]
        require(!player.folded && !player.allIn && player.chips > 0) { "玩家不能行动" }
        val toCall = currentBet - player.streetBet
        when (action.type) {
            PokerActionType.FOLD -> Unit
            PokerActionType.CHECK -> require(toCall == 0) { "需要跟注，不能过牌" }
            PokerActionType.CALL -> require(toCall > 0) { "当前无需跟注" }
            PokerActionType.RAISE_TO -> {
                require(player.id in raiseAllowed) { "短码 All-in 后未重新开放加注" }
                val target = requireNotNull(action.amount) { "缺少加注金额" }
                require(target > currentBet) { "加注必须高于当前下注" }
                require(target <= player.streetBet + player.chips) { "筹码不足" }
                val raise = target - currentBet
                val isAllIn = target == player.streetBet + player.chips
                require(raise >= minRaiseIncrement || isAllIn) { "最小加注到 ${currentBet + minRaiseIncrement}" }
            }
            PokerActionType.ALL_IN -> {
                require(player.chips > 0)
                val target = player.streetBet + player.chips
                require(target <= currentBet || player.id in raiseAllowed) { "短码 All-in 后未重新开放加注" }
            }
        }
    }

    fun act(playerId: String, action: PokerAction) {
        validateAction(playerId, action)
        val index = actingIndex!!
        val player = players[index]
        pendingToAct.remove(player.id)
        raiseAllowed.remove(player.id)
        when (action.type) {
            PokerActionType.FOLD -> player.folded = true
            PokerActionType.CHECK -> Unit
            PokerActionType.CALL -> commit(player, minOf(currentBet - player.streetBet, player.chips))
            PokerActionType.RAISE_TO -> raiseTo(player, action.amount!!)
            PokerActionType.ALL_IN -> {
                val target = player.streetBet + player.chips
                if (target > currentBet) raiseTo(player, target) else commit(player, player.chips)
            }
        }
        actingIndex = nextPendingIndex(index)
        resolveProgress()
    }

    override fun viewFor(playerId: String): PokerPlayerView {
        val player = players.firstOrNull { it.id == playerId } ?: error("玩家不存在")
        val showCards = phase in setOf(PokerPhase.SHOWDOWN, PokerPhase.HAND_FINISHED)
        val seats = players.mapIndexed { index, seat ->
            PokerSeatView(
                seat.id, seat.chips, seat.folded, seat.allIn, seat.streetBet,
                index == dealerIndex, index == smallBlindIndex, index == bigBlindIndex,
                if (showCards && !seat.folded) seat.holeCards.toList() else null,
            )
        }
        return PokerPlayerView(
            public = PokerPublicState(
                phase, handNumber, seats, communityCards.toList(), players.sumOf { it.totalBet },
                currentBet, currentBet + minRaiseIncrement, actingIndex?.let { players[it].id }, lastAwards,
            ),
            myCards = player.holeCards.toList(),
            toCall = maxOf(0, currentBet - player.streetBet),
            availableActions = availableActions(playerId),
        )
    }

    fun availableActions(playerId: String): Set<PokerActionType> {
        val index = players.indexOfFirst { it.id == playerId }
        if (index < 0 || actingIndex != index) return emptySet()
        val player = players[index]
        val toCall = currentBet - player.streetBet
        return buildSet {
            add(PokerActionType.FOLD)
            add(PokerActionType.ALL_IN)
            if (toCall == 0) add(PokerActionType.CHECK) else add(PokerActionType.CALL)
            if (player.id in raiseAllowed && player.streetBet + player.chips > currentBet) add(PokerActionType.RAISE_TO)
            if (player.streetBet + player.chips > currentBet && player.id !in raiseAllowed) remove(PokerActionType.ALL_IN)
        }
    }

    private fun raiseTo(player: PokerPlayer, target: Int) {
        val oldBet = currentBet
        val raiseSize = target - oldBet
        commit(player, target - player.streetBet)
        currentBet = target
        if (raiseSize >= minRaiseIncrement) {
            minRaiseIncrement = raiseSize
            pendingToAct.clear()
            raiseAllowed.clear()
            players.filter { canAct(it) && it.id != player.id }.forEach {
                pendingToAct += it.id
                raiseAllowed += it.id
            }
        } else {
            players.filter { canAct(it) && it.streetBet < currentBet && it.id != player.id }.forEach { pendingToAct += it.id }
        }
    }

    private fun commit(player: PokerPlayer, amount: Int) {
        require(amount in 0..player.chips)
        player.chips -= amount
        player.streetBet += amount
        player.totalBet += amount
        if (player.chips == 0) player.allIn = true
    }

    private fun postBlind(index: Int, amount: Int) = commit(players[index], minOf(amount, players[index].chips))

    private fun dealHoleCards() {
        var index = dealerIndex
        repeat(2) {
            repeat(players.count { it.chips > 0 || it.totalBet > 0 }) {
                index = nextParticipatingIndex(index)
                players[index].holeCards += deck.deal()
            }
        }
    }

    private fun beginBettingRound(firstIndex: Int?) {
        pendingToAct.clear()
        raiseAllowed.clear()
        players.filter(::canAct).forEach {
            pendingToAct += it.id
            raiseAllowed += it.id
        }
        actingIndex = firstIndex?.takeIf { players[it].id in pendingToAct } ?: nextPendingIndex(dealerIndex)
    }

    private fun resolveProgress() {
        while (true) {
            val contenders = players.filter { !it.folded && it.holeCards.isNotEmpty() }
            if (contenders.size == 1) {
                awardUncontested(contenders.first())
                return
            }
            pendingToAct.removeAll { id -> players.first { it.id == id }.let { !canAct(it) } }
            if (pendingToAct.isNotEmpty()) {
                if (actingIndex == null || players[actingIndex!!].id !in pendingToAct) actingIndex = nextPendingIndex(actingIndex ?: dealerIndex)
                return
            }
            when (phase) {
                PokerPhase.PREFLOP -> advanceStreet(PokerPhase.FLOP, 3)
                PokerPhase.FLOP -> advanceStreet(PokerPhase.TURN, 1)
                PokerPhase.TURN -> advanceStreet(PokerPhase.RIVER, 1)
                PokerPhase.RIVER -> {
                    showdown()
                    return
                }
                else -> return
            }
        }
    }

    private fun advanceStreet(next: PokerPhase, cards: Int) {
        repeat(cards) { communityCards += deck.deal() }
        players.forEach { it.streetBet = 0 }
        currentBet = 0
        minRaiseIncrement = config.bigBlind
        phase = next
        beginBettingRound(nextEligibleIndex(dealerIndex))
    }

    private fun showdown() {
        phase = PokerPhase.SHOWDOWN
        actingIndex = null
        val folded = players.filter { it.folded }.mapTo(mutableSetOf()) { it.id }
        val contributions = players.associate { it.id to it.totalBet }
        val values = players.filter { !it.folded }.associate { it.id to HandEvaluator.evaluate(it.holeCards + communityCards) }
        val pots = PotCalculator.build(contributions, folded)
        val clockwise = clockwiseAfterDealer().map { players[it].id }
        lastAwards = PotCalculator.award(pots, values, clockwise)
        lastAwards.flatMap { it.shares.entries }.forEach { (id, amount) -> players.first { it.id == id }.chips += amount }
        phase = PokerPhase.HAND_FINISHED
    }

    private fun awardUncontested(winner: PokerPlayer) {
        val amount = players.sumOf { it.totalBet }
        winner.chips += amount
        lastAwards = listOf(PotAward(0, amount, listOf(winner.id), mapOf(winner.id to amount)))
        actingIndex = null
        pendingToAct.clear()
        phase = PokerPhase.HAND_FINISHED
    }

    private fun canAct(player: PokerPlayer) = !player.folded && !player.allIn && player.chips > 0 && player.holeCards.isNotEmpty()

    private fun nextFundedIndex(from: Int): Int = nextIndex(from) { players[it].chips > 0 }
    private fun nextParticipatingIndex(from: Int): Int = nextIndex(from) { players[it].chips > 0 || players[it].totalBet > 0 }
    private fun nextEligibleIndex(from: Int): Int? = nextIndexOrNull(from) { canAct(players[it]) }
    private fun nextPendingIndex(from: Int): Int? = nextIndexOrNull(from) { players[it].id in pendingToAct }
    private fun nextIndex(from: Int, predicate: (Int) -> Boolean): Int = nextIndexOrNull(from, predicate) ?: error("没有符合条件的玩家")

    private fun nextIndexOrNull(from: Int, predicate: (Int) -> Boolean): Int? {
        for (step in 1..players.size) {
            val index = (from + step + players.size) % players.size
            if (predicate(index)) return index
        }
        return null
    }

    private fun clockwiseAfterDealer(): List<Int> = (1..players.size).map { (dealerIndex + it) % players.size }

    companion object {
        fun create(playerIds: List<String>, config: PokerConfig = PokerConfig(), deck: Deck = Deck.shuffled()): PokerGame {
            require(playerIds.size in 2..config.maxPlayers) { "德州扑克需要 2 至 ${config.maxPlayers} 名玩家" }
            require(playerIds.distinct().size == playerIds.size)
            return PokerGame(config, playerIds.map { PokerPlayer(it, config.startingChips) }.toMutableList(), deck).also {
                it.startHand(deck)
            }
        }
    }
}
