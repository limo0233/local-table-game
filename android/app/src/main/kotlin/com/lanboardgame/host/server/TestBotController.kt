package com.lanboardgame.host.server

import android.util.Log
import com.lanboardgame.games.avalon.AvalonGame
import com.lanboardgame.games.crossfire.CrossfireGame
import com.lanboardgame.games.goodcopbadcop.GoodCopBadCopGame
import com.lanboardgame.games.guandan.GuandanGame
import com.lanboardgame.games.liarsdice.LiarDiceGame
import com.lanboardgame.games.poker.PokerAction
import com.lanboardgame.games.poker.PokerActionType
import com.lanboardgame.games.poker.PokerGame
import com.lanboardgame.games.timebomb.TimeBombGame
import com.lanboardgame.platform.Room
import com.lanboardgame.platform.RoomManager
import com.lanboardgame.platform.RoomStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Server-owned test players.  They use the same game methods as a WebSocket
 * player and only emit the normal room/game broadcasts after an action.
 */
class TestBotController(
    private val roomManager: RoomManager,
    private val broadcast: (String) -> Unit,
    private val isEnabled: () -> Boolean = { true },
    private val random: Random = Random.Default,
    private val actionDelayMillis: Long = DEFAULT_ACTION_DELAY_MILLIS,
) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val scheduledRooms = ConcurrentHashMap.newKeySet<String>()

    fun schedule(roomId: String) {
        if (!isEnabled()) return
        if (!scheduledRooms.add(roomId)) return
        scheduler.schedule({
            scheduledRooms.remove(roomId)
            runRoom(roomId)
        }, actionDelayMillis, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        scheduler.shutdownNow()
        scheduledRooms.clear()
    }

    private fun runRoom(roomId: String) {
        if (!isEnabled()) return
        var actions = 0
        try {
            while (actions < MAX_ACTIONS_PER_TICK) {
                val room = roomManager.get(roomId) ?: return
                if (room.status != RoomStatus.PLAYING) return
                val game = room.game ?: return
                val acted = runCatching { synchronized(game) { takeTurn(room, game) } }
                    .onFailure { Log.w(TAG, "test bot action rejected room=$roomId", it) }
                    .getOrDefault(false)
                if (!acted) return
                actions++
                markFinished(room, game)
                broadcast(roomId)
                if (room.status != RoomStatus.PLAYING) return
            }
        } finally {
            val room = roomManager.get(roomId)
            if (actions > 0 && room?.status == RoomStatus.PLAYING) schedule(roomId)
        }
    }

    private fun takeTurn(room: Room, game: com.lanboardgame.platform.GameInstance): Boolean = when (game) {
        is AvalonGame -> takeAvalon(room, game)
        is PokerGame -> takePoker(room, game)
        is GuandanGame -> takeGuandan(room, game)
        is TimeBombGame -> takeTimeBomb(room, game)
        is CrossfireGame -> takeCrossfire(room, game)
        is GoodCopBadCopGame -> takeGoodCop(room, game)
        is LiarDiceGame -> takeLiarDice(room, game)
        else -> false
    }

    private fun takeAvalon(room: Room, game: AvalonGame): Boolean {
        val botIds = room.players.values.filter { it.isBot }.map { it.id }
        return when (game.phase) {
            com.lanboardgame.games.avalon.AvalonPhase.TEAM_SELECTION -> {
                if (game.leaderId !in botIds) false else {
                    val team = game.playerIds.shuffled(random).take(game.requiredTeamSize)
                    game.selectTeam(game.leaderId, team)
                    true
                }
            }
            com.lanboardgame.games.avalon.AvalonPhase.TEAM_VOTE -> {
                val pending = botIds.firstOrNull { it !in game.viewFor(it).public.submittedPlayerIds } ?: return false
                game.vote(pending, random.nextBoolean())
                true
            }
            com.lanboardgame.games.avalon.AvalonPhase.MISSION -> {
                val pending = botIds.firstOrNull { it in game.selectedTeam && it !in game.viewFor(it).public.submittedPlayerIds } ?: return false
                val success = !game.roleOf(pending).evil || random.nextBoolean()
                game.submitMission(pending, success)
                true
            }
            com.lanboardgame.games.avalon.AvalonPhase.ASSASSINATION -> {
                val assassin = botIds.firstOrNull { game.roleOf(it) == com.lanboardgame.games.avalon.AvalonRole.ASSASSIN } ?: return false
                val target = game.playerIds.filter { it != assassin }.randomOrNull(random) ?: return false
                game.assassinate(assassin, target)
                true
            }
            else -> false
        }
    }

    private fun takePoker(room: Room, game: PokerGame): Boolean {
        val botId = game.players.getOrNull(game.actingIndex ?: return false)?.id ?: return false
        if (room.players.values.none { it.id == botId && it.isBot }) return false
        val actions = game.availableActions(botId)
        val selected = actions.randomOrNull(random) ?: return false
        val action = when (selected) {
            PokerActionType.RAISE_TO -> {
                val view = game.viewFor(botId)
                val player = game.players.first { it.id == botId }
                val maximum = player.streetBet + player.chips
                if (maximum < view.public.minRaiseTo) PokerAction(PokerActionType.ALL_IN)
                else PokerAction(PokerActionType.RAISE_TO, random.nextInt(view.public.minRaiseTo, maximum + 1))
            }
            else -> PokerAction(selected)
        }
        game.act(botId, action)
        return true
    }

    private fun takeGuandan(room: Room, game: GuandanGame): Boolean {
        if (game.phase == com.lanboardgame.games.guandan.GuandanPhase.TRIBUTE) {
            val bot = room.players.values.firstOrNull { it.isBot && (game.viewFor(it.id).canTribute || game.viewFor(it.id).canReturnTribute) } ?: return false
            val view = game.viewFor(bot.id)
            if (view.canTribute) {
                val level = view.public.currentLevel
                val order = listOf("3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2")
                val card = view.myHand.maxWithOrNull(compareBy({ if (it.rank == "BIG_JOKER") 18 else if (it.rank == "SMALL_JOKER") 17 else if (it.rank == level) 16 else order.indexOf(it.rank) + 3 }, { it.suit })) ?: return false
                game.submitTribute(bot.id, card.id)
                return true
            }
            if (view.canReturnTribute) {
                val target = view.tributeTargetPlayerId ?: return false
                val card = view.myHand.firstOrNull() ?: return false
                game.returnTribute(bot.id, target, card.id)
                return true
            }
            return false
        }
        val actor = game.currentPlayerId
        if (room.players.values.none { it.id == actor && it.isBot }) return false
        val view = game.viewFor(actor)
        if (view.canPass && view.public.lastPlay != null) {
            game.pass(actor)
            return true
        }
        if (!view.canPlay) return false
        val candidates = guandanCandidates(view.myHand, view.public.lastPlay?.type).shuffled(random)
        for (candidate in candidates) {
            if (runCatching { game.play(actor, candidate) }.isSuccess) return true
        }
        if (view.canPass) {
            game.pass(actor)
            return true
        }
        return false
    }

    private fun guandanCandidates(
        hand: List<com.lanboardgame.games.guandan.GuandanCard>,
        type: com.lanboardgame.games.guandan.GuandanCombinationType?,
    ): List<List<String>> {
        val singles = hand.map { listOf(it.id) }
        if (type == null) return singles
        val groups = hand.groupBy { it.rank }.values
        return when (type) {
            com.lanboardgame.games.guandan.GuandanCombinationType.SINGLE -> singles
            com.lanboardgame.games.guandan.GuandanCombinationType.PAIR -> groups.filter { it.size >= 2 }.map { it.take(2).map { card -> card.id } }
            com.lanboardgame.games.guandan.GuandanCombinationType.TRIPLE -> groups.filter { it.size >= 3 }.map { it.take(3).map { card -> card.id } }
            com.lanboardgame.games.guandan.GuandanCombinationType.TRIPLE_WITH_PAIR,
            com.lanboardgame.games.guandan.GuandanCombinationType.CONSECUTIVE_PAIRS,
            com.lanboardgame.games.guandan.GuandanCombinationType.CONSECUTIVE_TRIPLES,
            com.lanboardgame.games.guandan.GuandanCombinationType.STRAIGHT,
            com.lanboardgame.games.guandan.GuandanCombinationType.STRAIGHT_FLUSH,
            com.lanboardgame.games.guandan.GuandanCombinationType.ROCKET -> emptyList()
            com.lanboardgame.games.guandan.GuandanCombinationType.BOMB -> groups.filter { it.size >= 4 }.map { it.map { card -> card.id } }
        }
    }

    private fun takeTimeBomb(room: Room, game: TimeBombGame): Boolean {
        val botIds = room.players.values.filter { it.isBot }.map { it.id }
        if (game.phase == com.lanboardgame.games.timebomb.TimeBombPhase.ROLE_REVEAL) {
            val pending = botIds.firstOrNull { it !in game.viewFor(it).public.confirmedPlayerIds } ?: return false
            game.confirmRole(pending)
            return true
        }
        if (game.phase != com.lanboardgame.games.timebomb.TimeBombPhase.ROUND || game.currentCutterId !in botIds) return false
        val view = game.viewFor(game.currentCutterId)
        val target = view.targetIds.randomOrNull(random) ?: return false
        val wireCount = view.public.wireCountsByPlayer[target] ?: return false
        game.cut(game.currentCutterId, target, random.nextInt(wireCount))
        return true
    }

    private fun takeCrossfire(room: Room, game: CrossfireGame): Boolean {
        val bots = room.players.values.filter { it.isBot }.map { it.id }
        when (game.phase) {
            com.lanboardgame.games.crossfire.CrossfirePhase.INITIAL_VIEW -> {
                val pending = bots.firstOrNull { it !in game.viewFor(it).public.confirmedPlayerIds } ?: return false
                game.confirmInitial(pending)
                return true
            }
            com.lanboardgame.games.crossfire.CrossfirePhase.INTEL_PASS -> {
                val pending = bots.firstOrNull { it !in game.viewFor(it).public.confirmedPlayerIds } ?: return false
                game.confirmIntel(pending)
                return true
            }
            com.lanboardgame.games.crossfire.CrossfirePhase.SHUFFLE -> {
                val group = game.currentShuffleGroup.toSet()
                val confirmed = game.viewFor(bots.firstOrNull() ?: return false).public.confirmedPlayerIds
                val pending = bots.firstOrNull { it in group && it !in confirmed } ?: return false
                game.confirmShuffle(pending)
                return true
            }
            com.lanboardgame.games.crossfire.CrossfirePhase.TARGETING -> {
                val locked = game.viewFor(bots.firstOrNull() ?: return false).public.lockedPlayers
                val actor = bots.firstOrNull { it !in locked } ?: return false
                val target = room.players.values.map { it.id }.filter { it != actor }.randomOrNull(random) ?: return false
                game.submitTarget(actor, target)
                return true
            }
            else -> return false
        }
    }

    private fun takeGoodCop(room: Room, game: GoodCopBadCopGame): Boolean {
        val bots = room.players.values.filter { it.isBot }.map { it.id }
        val actor = game.currentPlayerId
        if (actor !in bots) return false
        val view = game.viewFor(actor)
        if (game.phase == com.lanboardgame.games.goodcopbadcop.GoodCopPhase.ACTION_RESOLVE) {
            val target = view.targetIds.randomOrNull(random) ?: return false
            return when {
                view.canAim && view.public.aimTarget == null -> { game.aim(actor, target); true }
                view.canShoot -> { game.shoot(actor); true }
                else -> false
            }
        }
        if (game.phase != com.lanboardgame.games.goodcopbadcop.GoodCopPhase.TURN) return false
        val target = view.targetIds.filter { (view.public.hiddenIntegrityCounts[it] ?: 0) > 0 }.randomOrNull(random)
            ?: view.targetIds.randomOrNull(random)
        val available = buildList {
            if (view.canInvestigate && target != null && (view.public.hiddenIntegrityCounts[target] ?: 0) > 0) add("INVESTIGATE")
            if (view.canEquip) add("EQUIP")
            if (view.canArm) add("ARM")
        }
        return when (available.randomOrNull(random)) {
            "INVESTIGATE" -> {
                val hiddenCount = view.public.hiddenIntegrityCounts.getValue(target!!)
                game.investigate(actor, target, random.nextInt(hiddenCount))
                true
            }
            "EQUIP" -> { game.equip(actor); true }
            "ARM" -> { game.arm(actor); true }
            else -> false
        }
    }

    private fun takeLiarDice(room: Room, game: LiarDiceGame): Boolean {
        val bots = room.players.values.filter { it.isBot }.map { it.id }
        val snapshot = game.viewFor(bots.firstOrNull() ?: return false)
        if (snapshot.public.allSettled) {
            val actor = bots.firstOrNull { it !in snapshot.public.nextRoundPlayerIds } ?: return false
            game.nextRound(actor)
            return true
        }
        val actor = bots.firstOrNull { !game.viewFor(it).settled } ?: return false
        repeat(random.nextInt(3)) { game.reroll(actor) }
        game.settle(actor)
        return true
    }

    private fun markFinished(room: Room, game: com.lanboardgame.platform.GameInstance) {
        val finished = when (game) {
            is AvalonGame -> game.phase == com.lanboardgame.games.avalon.AvalonPhase.FINISHED
            is PokerGame -> game.phase == com.lanboardgame.games.poker.PokerPhase.GAME_FINISHED
            is GuandanGame -> game.phase == com.lanboardgame.games.guandan.GuandanPhase.FINISHED
            is TimeBombGame -> game.phase == com.lanboardgame.games.timebomb.TimeBombPhase.GAME_OVER
            is CrossfireGame -> game.phase == com.lanboardgame.games.crossfire.CrossfirePhase.GAME_OVER
            is GoodCopBadCopGame -> game.phase == com.lanboardgame.games.goodcopbadcop.GoodCopPhase.GAME_OVER
            is LiarDiceGame -> false
            else -> false
        }
        if (finished) room.status = RoomStatus.FINISHED
    }

    companion object {
        private const val DEFAULT_ACTION_DELAY_MILLIS = 0L
        private const val MAX_ACTIONS_PER_TICK = 8
        private const val TAG = "LanGame.TestBot"
    }
}
