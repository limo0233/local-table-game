package com.lanboardgame.platform

import com.lanboardgame.games.avalon.AvalonGame
import com.lanboardgame.games.avalon.AvalonConfig
import com.lanboardgame.games.guandan.GuandanGame
import com.lanboardgame.games.timebomb.TimeBombGame
import com.lanboardgame.games.crossfire.CrossfireGame
import com.lanboardgame.games.goodcopbadcop.GoodCopBadCopGame
import com.lanboardgame.games.liarsdice.LiarDiceGame
import com.lanboardgame.games.liarsdice.LiarDiceConfig
import com.lanboardgame.games.poker.PokerConfig
import com.lanboardgame.games.poker.PokerGame
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

class RoomManager {
    private val rooms = ConcurrentHashMap<String, Room>()
    private val sessionIndex = ConcurrentHashMap<String, Pair<String, String>>()
    private val avalonConfigs = ConcurrentHashMap<String, AvalonConfig>()
    private val liarDiceConfigs = ConcurrentHashMap<String, LiarDiceConfig>()
    private val departedWaitingPlayers = ConcurrentHashMap<String, MutableMap<String, Player>>()

    @Synchronized
    fun createRoom(gameType: GameType, nickname: String, sessionId: String = Ids.sessionId()): JoinResult {
        var roomId: String
        do roomId = Ids.roomId() while (rooms.containsKey(roomId))
        val cleanName = nickname.trim().take(24)
        require(cleanName.isNotBlank()) { "昵称不能为空" }
        require(sessionId.isNotBlank()) { "Session 不能为空" }
        require(sessionIndex[sessionId] == null) { "Session 已绑定到其他房间" }
        val host = Player(Ids.playerId(), sessionId, cleanName, isHost = true)
        val room = Room(roomId, gameType, host.id)
        room.players[host.id] = host
        rooms[roomId] = room
        if (gameType == GameType.AVALON) avalonConfigs[roomId] = AvalonGame.defaultConfig(5)
        if (gameType == GameType.LIARS_DICE) liarDiceConfigs[roomId] = LiarDiceConfig()
        sessionIndex[sessionId] = roomId to host.id
        return JoinResult(room, host, resumed = false)
    }

    @Synchronized
    fun joinRoom(roomId: String, nickname: String, sessionId: String): JoinResult {
        val existing = sessionIndex[sessionId]
        if (existing?.first == roomId) {
            val room = requireRoom(roomId)
            val player = room.players.getValue(existing.second)
            player.connected = true
            return JoinResult(room, player, resumed = true)
        }
        require(existing == null) { "Session 已绑定到其他房间" }
        val room = requireRoom(roomId)
        val cleanName = nickname.trim().take(24)
        require(cleanName.isNotBlank()) { "昵称不能为空" }

        // A browser may lose its local session after an explicit leave.  If the
        // old seat is offline, the nickname is the user-facing recovery key.
        // Rebind the server session before checking WAITING/PLAYING so the game
        // instance can continue using the original player id and private state.
        val recovered = room.players.values.firstOrNull {
            !it.connected && it.nickname.equals(cleanName, ignoreCase = true)
        }
        if (recovered != null) {
            sessionIndex.remove(recovered.sessionId, roomId to recovered.id)
            recovered.sessionId = sessionId
            recovered.connected = true
            sessionIndex[sessionId] = roomId to recovered.id
            return JoinResult(room, recovered, resumed = true)
        }

        require(room.status == RoomStatus.WAITING) { "游戏已开始，只有原玩家可以重连" }
        val limit = roomLimit(roomId, room)
        val departedMap = departedWaitingPlayers[roomId]
        val departed = departedMap?.remove(cleanName.lowercase(Locale.ROOT))
        if (departed != null) {
            if (departedMap.isEmpty()) departedWaitingPlayers.remove(roomId, departedMap)
            departed.sessionId = sessionId
            departed.nickname = cleanName
            departed.connected = true
            departed.ready = false
            room.players[departed.id] = departed
            sessionIndex[sessionId] = roomId to departed.id
            return JoinResult(room, departed, resumed = true)
        }
        // A waiting-room departure keeps a reclaimable seat. Do not let new
        // players consume those reserved seats before the original nickname
        // has had a chance to return.
        require(room.players.size + (departedMap?.size ?: 0) < limit) { "房间已满" }
        require(room.players.values.none { it.nickname.equals(cleanName, true) }) { "昵称已被使用" }
        val player = Player(Ids.playerId(), sessionId, cleanName)
        room.players[player.id] = player
        sessionIndex[sessionId] = roomId to player.id
        return JoinResult(room, player, resumed = false)
    }

    @Synchronized
    fun setReady(roomId: String, playerId: String, ready: Boolean): Room {
        val room = requireRoom(roomId)
        require(room.status == RoomStatus.WAITING) { "只能在等待房间中修改准备状态" }
        room.players.getValue(playerId).ready = ready
        return room
    }

    /** Add server-owned test players while the room is still in its lobby. */
    @Synchronized
    fun addTestBots(roomId: String, actorId: String, count: Int? = null): Room {
        val room = requireHost(roomId, actorId)
        require(room.status == RoomStatus.WAITING) { "只能在等待房间中添加测试机器人" }
        val limit = roomLimit(roomId, room)
        val available = (limit - room.players.size).coerceAtLeast(0)
        val requested = (count ?: available).coerceAtLeast(0).coerceAtMost(available)
        val nextNumber = room.players.values.count { it.isBot } + 1
        repeat(requested) { offset ->
            val bot = Player(
                id = Ids.playerId(),
                sessionId = "bot-${Ids.sessionId()}",
                nickname = "测试机器人 ${nextNumber + offset}",
                connected = true,
                isHost = false,
                ready = true,
                isBot = true,
            )
            room.players[bot.id] = bot
            sessionIndex[bot.sessionId] = roomId to bot.id
        }
        return room
    }

    @Synchronized
    fun removeTestBots(roomId: String, actorId: String): Room {
        val room = requireHost(roomId, actorId)
        require(room.status == RoomStatus.WAITING) { "只能在等待房间中移除测试机器人" }
        val botIds = room.players.values.filter { it.isBot }.map { it.id }
        botIds.forEach { id ->
            val bot = room.players.remove(id) ?: return@forEach
            sessionIndex.remove(bot.sessionId)
        }
        return room
    }

    @Synchronized
    fun rename(roomId: String, playerId: String, nickname: String): Room {
        val room = requireRoom(roomId)
        require(room.status == RoomStatus.WAITING) { "只能在等待房间中修改昵称" }
        val player = room.players.getValue(playerId)
        require(!player.ready) { "已准备后不能修改昵称，请先取消准备" }
        val cleanName = nickname.trim().take(24)
        require(cleanName.isNotBlank()) { "昵称不能为空" }
        require(room.players.values.none { it.id != playerId && it.nickname.equals(cleanName, true) }) { "昵称已被使用" }
        player.nickname = cleanName
        return room
    }

    @Synchronized
    fun configureAvalon(roomId: String, actorId: String, config: AvalonConfig): AvalonConfig {
        val room = requireHost(roomId, actorId)
        require(room.gameType == GameType.AVALON) { "当前不是阿瓦隆房间" }
        require(room.status == RoomStatus.WAITING) { "游戏开始后不能修改配置" }
        val normalized = AvalonGame.validateConfig(config)
        require(normalized.playerCount >= room.players.size) { "设置人数不能少于当前房间人数" }
        avalonConfigs[roomId] = normalized
        return normalized
    }

    fun avalonConfig(roomId: String): AvalonConfig =
        avalonConfigs[roomId] ?: error("阿瓦隆房间配置不存在")

    fun liarDiceConfig(roomId: String): LiarDiceConfig =
        requireRoom(roomId).let { liarDiceConfigs[roomId] ?: LiarDiceConfig() }

    @Synchronized
    fun configureLiarDice(roomId: String, actorId: String, config: LiarDiceConfig): LiarDiceConfig {
        val room = requireHost(roomId, actorId)
        require(room.gameType == GameType.LIARS_DICE) { "当前不是骰子模拟器房间" }
        require(room.status == RoomStatus.WAITING) { "游戏开始后不能修改配置" }
        require(config.diceCount in LiarDiceGame.MIN_DICE..LiarDiceGame.MAX_DICE) { "骰子数量应为 1 至 10" }
        val result = LiarDiceConfig(config.diceCount)
        liarDiceConfigs[roomId] = result
        return result
    }

    @Synchronized
    fun disconnect(sessionId: String) {
        val (roomId, playerId) = sessionIndex[sessionId] ?: return
        rooms[roomId]?.players?.get(playerId)?.connected = false
    }

    @Synchronized
    fun connect(sessionId: String) {
        val (roomId, playerId) = sessionIndex[sessionId] ?: return
        rooms[roomId]?.players?.get(playerId)?.connected = true
    }

    @Synchronized
    fun leave(roomId: String, actorId: String): LeaveResult {
        val room = requireRoom(roomId)
        val actor = room.players[actorId] ?: error("玩家不存在")
        if (actor.isHost) {
            rooms.remove(roomId)
            avalonConfigs.remove(roomId)
            liarDiceConfigs.remove(roomId)
            departedWaitingPlayers.remove(roomId)
            room.players.values.forEach { sessionIndex.remove(it.sessionId) }
            return LeaveResult(roomId, actorId, disbanded = true)
        }

        if (room.status == RoomStatus.WAITING) {
            room.players.remove(actorId)
            sessionIndex.remove(actor.sessionId)
            actor.connected = false
            departedWaitingPlayers.computeIfAbsent(roomId) { linkedMapOf() }[actor.nickname.lowercase(Locale.ROOT)] = actor
        } else {
            actor.connected = false
        }
        return LeaveResult(roomId, actorId, disbanded = false)
    }

    @Synchronized
    fun kick(roomId: String, actorId: String, targetId: String): Room {
        val room = requireHost(roomId, actorId)
        require(room.status == RoomStatus.WAITING) { "只能在等待房间中踢出玩家" }
        require(targetId != actorId) { "房主不能踢出自己" }
        val removed = room.players.remove(targetId) ?: error("玩家不存在")
        sessionIndex.remove(removed.sessionId)
        departedWaitingPlayers[roomId]?.remove(removed.nickname.lowercase(Locale.ROOT))
        return room
    }

    @Synchronized
    fun start(roomId: String, actorId: String, pokerConfig: PokerConfig = PokerConfig()): Room {
        val room = requireHost(roomId, actorId)
        require(room.status != RoomStatus.PLAYING) { "游戏已经开始" }
        require(room.players.values.all { it.ready || it.isHost }) { "仍有玩家未准备" }
        departedWaitingPlayers.remove(roomId)
        val ids = room.players.keys.toList()
        room.game = when (room.gameType) {
            GameType.AVALON -> {
                val config = avalonConfig(roomId)
                require(ids.size == config.playerCount) { "需要 ${config.playerCount} 名玩家才能开始" }
                AvalonGame.create(ids, config)
            }
            GameType.GUANDAN -> {
                require(ids.size == 4) { "掼蛋需要 4 名玩家才能开始" }
                GuandanGame.create(ids)
            }
            GameType.TIME_BOMB -> {
                require(ids.size in 4..8) { "TimeBomb 需要 4 至 8 名玩家才能开始" }
                TimeBombGame.create(ids)
            }
            GameType.CROSSFIRE -> {
                require(ids.size in 5..10) { "Crossfire 需要 5 至 10 名玩家才能开始" }
                CrossfireGame.create(ids, room.hostId)
            }
            GameType.GOOD_COP_BAD_COP -> {
                require(ids.size in 4..8) { "Good Cop Bad Cop 需要 4 至 8 名玩家才能开始" }
                GoodCopBadCopGame.create(ids)
            }
            GameType.LIARS_DICE -> {
                require(ids.size in 2..10) { "骰子模拟器需要 2 至 10 名玩家才能开始" }
                LiarDiceGame.create(ids, liarDiceConfig(roomId).diceCount)
            }
            GameType.POKER -> PokerGame.create(ids, pokerConfig)
        }
        room.status = RoomStatus.PLAYING
        return room
    }

    @Synchronized
    fun returnToLobby(roomId: String, actorId: String): Room {
        val room = requireHost(roomId, actorId)
        require(room.game != null) { "当前没有可结束的游戏" }
        room.game = null
        room.status = RoomStatus.WAITING
        room.players.values.forEach { it.ready = it.isBot }
        return room
    }

    fun get(roomId: String): Room? = rooms[roomId]

    @Synchronized
    fun snapshot(roomId: String): RoomSnapshot? {
        val room = rooms[roomId] ?: return null
        return RoomSnapshot(
            room = room.publicView(),
            avalonConfig = avalonConfigs[roomId],
            liarDiceConfig = if (room.gameType == GameType.LIARS_DICE) liarDiceConfig(roomId) else null,
            game = room.game,
        )
    }

    private fun requireRoom(roomId: String) = rooms[roomId] ?: error("房间不存在")

    private fun requireHost(roomId: String, actorId: String): Room {
        val room = requireRoom(roomId)
        require(room.hostId == actorId) { "只有房主可以执行此操作" }
        return room
    }

    private fun roomLimit(roomId: String, room: Room): Int = when (room.gameType) {
        GameType.AVALON -> avalonConfig(roomId).playerCount
        GameType.GUANDAN -> 4
        GameType.TIME_BOMB -> 8
        GameType.CROSSFIRE -> 10
        GameType.GOOD_COP_BAD_COP -> 8
        GameType.LIARS_DICE -> 10
        GameType.POKER -> 8
    }
}

data class JoinResult(val room: Room, val player: Player, val resumed: Boolean)

data class LeaveResult(val roomId: String, val playerId: String, val disbanded: Boolean)

data class RoomSnapshot(
    val room: RoomView,
    val avalonConfig: AvalonConfig?,
    val liarDiceConfig: LiarDiceConfig?,
    val game: GameInstance?,
)
