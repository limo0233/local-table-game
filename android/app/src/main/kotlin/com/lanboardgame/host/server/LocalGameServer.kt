package com.lanboardgame.host.server

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lanboardgame.games.avalon.AvalonGame
import com.lanboardgame.games.avalon.AvalonConfig
import com.lanboardgame.games.avalon.AvalonPlayerView
import com.lanboardgame.games.guandan.GuandanGame
import com.lanboardgame.games.guandan.GuandanPlayerView
import com.lanboardgame.games.timebomb.TimeBombGame
import com.lanboardgame.games.timebomb.TimeBombPlayerView
import com.lanboardgame.games.crossfire.CrossfireGame
import com.lanboardgame.games.crossfire.CrossfirePlayerView
import com.lanboardgame.games.goodcopbadcop.GoodCopBadCopGame
import com.lanboardgame.games.goodcopbadcop.GoodCopPlayerView
import com.lanboardgame.games.liarsdice.LiarDiceGame
import com.lanboardgame.games.liarsdice.LiarDiceConfig
import com.lanboardgame.games.liarsdice.LiarDicePlayerView
import com.lanboardgame.games.poker.PokerAction
import com.lanboardgame.games.poker.PokerActionType
import com.lanboardgame.games.poker.PokerConfig
import com.lanboardgame.games.poker.PokerGame
import com.lanboardgame.games.poker.PokerPlayerView
import com.lanboardgame.platform.GameType
import com.lanboardgame.platform.RoomManager
import com.lanboardgame.platform.RoomView
import com.lanboardgame.protocol.ClientEnvelope
import com.lanboardgame.protocol.Events
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LocalGameServer(
    private val context: Context,
    bindAddress: String,
    port: Int,
    private val roomManager: RoomManager,
    private val hostStateProvider: () -> HostServerState,
    private val deployHandler: (String, Int) -> DeploymentResult,
    private val undeployHandler: () -> DeploymentResult,
) : NanoWSD(bindAddress, port) {
    private val socketsByRoom = ConcurrentHashMap<String, MutableSet<ClientSocket>>()
    private val socketsBySession = ConcurrentHashMap<String, MutableSet<ClientSocket>>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val roomsAwaitingNextHand = ConcurrentHashMap.newKeySet<String>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val testBotController = TestBotController(roomManager, ::broadcast, { !hostStateProvider().deployed })

    override fun serveHttp(session: IHTTPSession): Response {
        if (session.uri == "/api/health") {
            val state = hostStateProvider()
            val options = NetworkManager.localIpv4Addresses(context)
            val body = buildJsonObject {
                put("ok", true)
                put("port", listeningPort)
                put("selectedAddress", state.selectedAddress)
                put("deployed", state.deployed)
                put("addresses", kotlinx.serialization.json.JsonArray(options.map { kotlinx.serialization.json.JsonPrimitive(it.address) }))
            }
            return jsonResponse(body)
        }
        if (session.uri == "/api/host/config") {
            if (!isLoopbackClient(session)) return jsonError(Response.Status.FORBIDDEN, "只能在房主手机中修改部署设置")
            return jsonResponse(hostConfigJson())
        }
        if (session.uri == "/api/host/deploy") {
            if (!isLoopbackClient(session)) return jsonError(Response.Status.FORBIDDEN, "只能在房主手机中部署服务器")
            if (session.method != Method.POST) return jsonError(Response.Status.METHOD_NOT_ALLOWED, "请使用 POST 请求")
            val address = session.parameters["address"]?.firstOrNull().orEmpty()
            val port = session.parameters["port"]?.firstOrNull()?.toIntOrNull()
                ?: return jsonError(Response.Status.BAD_REQUEST, "端口号无效")
            return runCatching { deployHandler(address, port) }
                .fold(
                    onSuccess = { result -> jsonResponse(buildJsonObject {
                        put("ok", true)
                        put("selectedAddress", result.selectedAddress)
                        put("port", result.port)
                        put("deployed", result.deployed)
                        put("restarting", result.restarting)
                    }) },
                    onFailure = { jsonError(Response.Status.BAD_REQUEST, it.message ?: "部署失败") },
                )
        }
        if (session.uri == "/api/host/undeploy") {
            if (!isLoopbackClient(session)) return jsonError(Response.Status.FORBIDDEN, "只能在房主手机中关闭部署")
            if (session.method != Method.POST) return jsonError(Response.Status.METHOD_NOT_ALLOWED, "请使用 POST 请求")
            return runCatching(undeployHandler)
                .fold(
                    onSuccess = { result -> jsonResponse(buildJsonObject {
                        put("ok", true)
                        put("selectedAddress", result.selectedAddress)
                        put("port", result.port)
                        put("deployed", result.deployed)
                        put("restarting", result.restarting)
                    }) },
                    onFailure = { jsonError(Response.Status.BAD_REQUEST, it.message ?: "关闭部署失败") },
                )
        }
        if (session.uri == "/api/qr") {
            val text = session.parameters["text"]?.firstOrNull().orEmpty()
            if (text.isBlank()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing text")
            return qrResponse(text)
        }
        return assetResponse(session.uri)
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = ClientSocket(handshake)

    fun shutdown() {
        testBotController.shutdown()
        scheduler.shutdownNow()
        stop()
    }

    private inner class ClientSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        var roomId: String? = null
        var playerId: String? = null
        var sessionId: String? = null

        override fun onOpen() {
            Log.i(TAG, "websocket open remote=${handshakeRequest.remoteIpAddress}")
            sendEvent("server:hello", buildJsonObject { put("version", "0.6.0") })
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            val room = roomId
            val session = sessionId
            if (room != null) removeSocket(socketsByRoom, room, this)
            if (session != null) {
                val lastSessionSocket = synchronized(socketsBySession) {
                    val sockets = socketsBySession[session]
                    sockets?.remove(this)
                    if (sockets.isNullOrEmpty()) {
                        socketsBySession.remove(session)
                        true
                    } else {
                        false
                    }
                }
                if (lastSessionSocket) roomManager.disconnect(session)
            }
            roomId = null
            playerId = null
            sessionId = null
            Log.i(
                TAG,
                "websocket close room=${room.orEmpty()} session=${session.orEmpty()} code=$code " +
                    "remote=$initiatedByRemote reason=${reason.orEmpty()}",
            )
            if (room != null) broadcast(room)
        }

        override fun onMessage(message: WebSocketFrame) {
            val envelope = runCatching { json.decodeFromString<ClientEnvelope>(message.textPayload) }
                .getOrElse { return sendError("BAD_MESSAGE", "消息格式无效") }
            Log.d(TAG, "event type=${envelope.type} room=${roomId.orEmpty()} player=${playerId.orEmpty()}")
            runCatching { handle(envelope) }
                .onFailure {
                    Log.w(TAG, "event rejected type=${envelope.type} room=${roomId.orEmpty()} player=${playerId.orEmpty()}", it)
                    sendError("ACTION_REJECTED", it.message ?: "操作失败", envelope.requestId)
                }
        }

        override fun onPong(pong: WebSocketFrame?) = Unit
        override fun onException(exception: java.io.IOException?) {
            Log.e(TAG, "websocket exception room=${roomId.orEmpty()} player=${playerId.orEmpty()}", exception)
        }

        fun sendEvent(type: String, payload: JsonElement, requestId: String? = null) {
            val body = buildJsonObject {
                put("type", type)
                requestId?.let { put("requestId", it) }
                put("payload", payload)
            }
            send(json.encodeToString(JsonObject.serializer(), body))
        }

        fun sendError(code: String, message: String, requestId: String? = null) =
            sendEvent(Events.GAME_ERROR, buildJsonObject { put("code", code); put("message", message) }, requestId)

        private fun handle(envelope: ClientEnvelope) {
            val payload = envelope.payload?.jsonObject ?: JsonObject(emptyMap())
            when (envelope.type) {
                Events.SYSTEM_PING -> sendEvent(Events.SYSTEM_PONG, buildJsonObject { })
                Events.ROOM_CREATE -> {
                    val type = GameType.valueOf(payload.requiredString("gameType"))
                    val nickname = payload.requiredString("nickname")
                    val requestedSession = payload["sessionId"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
                    val result = roomManager.createRoom(type, nickname, requestedSession ?: com.lanboardgame.platform.Ids.sessionId())
                    attach(result.room.roomId, result.player.id, result.player.sessionId)
                    sendAttached(result.resumed, envelope.requestId)
                    broadcast(result.room.roomId)
                }
                Events.ROOM_JOIN -> {
                    val result = roomManager.joinRoom(
                        payload.requiredString("roomId"), payload.requiredString("nickname"), payload.requiredString("sessionId")
                    )
                    attach(result.room.roomId, result.player.id, result.player.sessionId)
                    sendAttached(result.resumed, envelope.requestId)
                    broadcast(result.room.roomId)
                }
                Events.ROOM_LEAVE -> {
                    val room = requireContext()
                    val result = roomManager.leave(room.first, room.second)
                    val roomSockets = socketsByRoom[room.first]?.toList().orEmpty()
                    val affected = if (result.disbanded) roomSockets else roomSockets.filter { it.playerId == room.second }
                    affected.forEach { leaving ->
                        val closedForGuest = result.disbanded && leaving.playerId != room.second
                        leaving.sendEvent(
                            if (closedForGuest) Events.ROOM_CLOSED else Events.ROOM_LEFT,
                            buildJsonObject {
                                put("message", if (closedForGuest) "房主已解散房间" else "已返回主界面")
                            },
                        )
                        detach(leaving)
                    }
                    if (result.disbanded) roomsAwaitingNextHand.remove(room.first) else broadcast(room.first)
                }
                Events.PLAYER_READY -> {
                    val room = requireContext()
                    roomManager.setReady(room.first, room.second, payload["ready"]?.jsonPrimitive?.content?.toBooleanStrict() ?: true)
                    broadcast(room.first)
                }
                Events.PLAYER_RENAME -> {
                    val room = requireContext()
                    roomManager.rename(room.first, room.second, payload.requiredString("nickname"))
                    broadcast(room.first)
                }
                Events.PLAYER_KICK -> {
                    val room = requireContext()
                    val targetId = payload.requiredString("targetId")
                    roomManager.kick(room.first, room.second, targetId)
                    socketsByRoom[room.first]?.toList()?.filter { it.playerId == targetId }?.forEach { kicked ->
                        kicked.sendEvent("session:kicked", buildJsonObject { put("message", "你已被房主移出房间") })
                        removeSocket(socketsByRoom, room.first, kicked)
                        kicked.sessionId?.let { removeSocket(socketsBySession, it, kicked) }
                        kicked.roomId = null
                        kicked.playerId = null
                        kicked.sessionId = null
                    }
                    broadcast(room.first)
                }
                Events.ROOM_BOTS -> {
                    require(!hostStateProvider().deployed) { "服务器部署上线后不能使用测试机器人" }
                    val room = requireContext()
                    when (payload["action"]?.jsonPrimitive?.content ?: "fill") {
                        "fill", "add" -> {
                            val count = payload["count"]?.jsonPrimitive?.content?.toIntOrNull()
                            roomManager.addTestBots(room.first, room.second, count)
                        }
                        "clear", "remove" -> roomManager.removeTestBots(room.first, room.second)
                        else -> error("机器人操作无效")
                    }
                    broadcast(room.first)
                }
                Events.GAME_START -> {
                    val room = requireContext()
                    val hasTestBots = roomManager.get(room.first)?.players?.values?.any { it.isBot } == true
                    require(!hostStateProvider().deployed || !hasTestBots) {
                        "服务器部署上线后不能启动含测试机器人的房间，请先关闭部署并移除机器人"
                    }
                    val config = payload["pokerConfig"]?.let { json.decodeFromJsonElement(PokerConfig.serializer(), it) } ?: PokerConfig()
                    roomManager.start(room.first, room.second, config)
                    broadcast(room.first)
                }
                Events.GAME_RESTART -> {
                    val room = requireContext()
                    roomManager.returnToLobby(room.first, room.second)
                    roomsAwaitingNextHand.remove(room.first)
                    broadcast(room.first)
                }
                Events.AVALON_CONFIGURE -> {
                    val room = requireContext()
                    val config = json.decodeFromJsonElement(AvalonConfig.serializer(), payload)
                    roomManager.configureAvalon(room.first, room.second, config)
                    broadcast(room.first)
                }
                Events.LIAR_DICE_CONFIGURE -> {
                    val room = requireContext()
                    val config = json.decodeFromJsonElement(LiarDiceConfig.serializer(), payload)
                    roomManager.configureLiarDice(room.first, room.second, config)
                    broadcast(room.first)
                }
                Events.AVALON_SELECT_TEAM -> withAvalon { game, actor ->
                    game.selectTeam(actor, payload.requiredStrings("team"))
                }
                Events.AVALON_VOTE -> withAvalon { game, actor ->
                    game.vote(actor, payload.requiredBoolean("approve"))
                }
                Events.AVALON_MISSION -> withAvalon { game, actor ->
                    game.submitMission(actor, payload.requiredBoolean("success"))
                }
                Events.AVALON_ASSASSINATE -> withAvalon { game, actor ->
                    game.assassinate(actor, payload.requiredString("targetId"))
                }
                Events.POKER_ACTION -> withPoker { game, actor ->
                    val type = PokerActionType.valueOf(payload.requiredString("action"))
                    val amount = payload["amount"]?.jsonPrimitive?.content?.toIntOrNull()
                    game.act(actor, PokerAction(type, amount))
                }
                Events.GUANDAN_PLAY -> withGuandan { game, actor ->
                    game.play(actor, payload.requiredStrings("cards"))
                }
                Events.GUANDAN_PASS -> withGuandan { game, actor ->
                    game.pass(actor)
                }
                Events.GUANDAN_CONTINUE -> {
                    val room = requireContext()
                    val target = roomManager.get(room.first) ?: error("房间不存在")
                    require(target.hostId == room.second) { "只有房主可以开始下一局" }
                    val game = target.game as? GuandanGame ?: error("掼蛋尚未开始")
                    synchronized(game) { game.continueNextHand() }
                    target.status = if (game.phase == com.lanboardgame.games.guandan.GuandanPhase.FINISHED) {
                        com.lanboardgame.platform.RoomStatus.FINISHED
                    } else {
                        com.lanboardgame.platform.RoomStatus.PLAYING
                    }
                    broadcast(room.first)
                }
                Events.GUANDAN_TRIBUTE -> withGuandan { game, actor ->
                    game.submitTribute(actor, payload.requiredString("cardId"))
                }
                Events.GUANDAN_RETURN_TRIBUTE -> withGuandan { game, actor ->
                    game.returnTribute(actor, payload.requiredString("targetPlayerId"), payload.requiredString("cardId"))
                }
                Events.TIME_BOMB_CONFIRM_ROLE -> withTimeBomb { game, actor ->
                    game.confirmRole(actor)
                }
                Events.TIME_BOMB_CUT -> withTimeBomb { game, actor ->
                    game.cut(actor, payload.requiredString("targetId"), payload.requiredString("wireIndex").toInt())
                }
                Events.CROSSFIRE_CONFIRM_INITIAL -> withCrossfire { game, actor ->
                    game.confirmInitial(actor)
                }
                Events.CROSSFIRE_CONFIRM_INTEL -> withCrossfire { game, actor ->
                    game.confirmIntel(actor)
                }
                Events.CROSSFIRE_CONFIRM_SHUFFLE -> withCrossfire { game, actor ->
                    game.confirmShuffle(actor)
                }
                Events.CROSSFIRE_START_TARGETING -> {
                    val room = requireContext()
                    val game = roomManager.get(room.first)?.game as? CrossfireGame ?: error("Crossfire 尚未开始")
                    synchronized(game) { game.startTargeting(room.second, roomManager.get(room.first)?.hostId ?: error("房间不存在")) }
                    broadcast(room.first)
                }
                Events.CROSSFIRE_TARGET -> withCrossfire { game, actor ->
                    game.submitTarget(actor, payload.requiredString("targetId"))
                }
                Events.GOOD_COP_INVESTIGATE -> withGoodCop { game, actor ->
                    game.investigate(actor, payload.requiredString("targetId"), payload.requiredString("cardIndex").toInt())
                }
                Events.GOOD_COP_EQUIP -> withGoodCop { game, actor ->
                    game.equip(actor)
                }
                Events.GOOD_COP_ARM -> withGoodCop { game, actor ->
                    game.arm(actor)
                }
                Events.GOOD_COP_AIM -> withGoodCop { game, actor ->
                    game.aim(actor, payload.requiredString("targetId"))
                }
                Events.GOOD_COP_SHOOT -> withGoodCop { game, actor ->
                    game.shoot(actor)
                }
                Events.LIAR_DICE_REROLL -> withLiarDice { game, actor ->
                    game.reroll(actor)
                }
                Events.LIAR_DICE_SETTLE -> withLiarDice { game, actor ->
                    game.settle(actor)
                }
                Events.LIAR_DICE_NEXT_ROUND -> withLiarDice { game, actor ->
                    game.nextRound(actor)
                }
                else -> error("未知事件：${envelope.type}")
            }
        }

        private fun withAvalon(action: (AvalonGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? AvalonGame ?: error("阿瓦隆尚未开始")
            synchronized(game) { action(game, actor) }
            if (game.phase == com.lanboardgame.games.avalon.AvalonPhase.FINISHED) {
                roomManager.get(roomId)?.status = com.lanboardgame.platform.RoomStatus.FINISHED
            }
            broadcast(roomId)
        }

        private fun withPoker(action: (PokerGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? PokerGame ?: error("德州扑克尚未开始")
            synchronized(game) { action(game, actor) }
            broadcast(roomId)
            if (game.phase == com.lanboardgame.games.poker.PokerPhase.HAND_FINISHED && roomsAwaitingNextHand.add(roomId)) {
                scheduler.schedule({
                    runCatching {
                        if (roomManager.get(roomId)?.game !== game) return@runCatching
                        synchronized(game) { if (game.phase == com.lanboardgame.games.poker.PokerPhase.HAND_FINISHED) game.startHand() }
                        if (game.phase == com.lanboardgame.games.poker.PokerPhase.GAME_FINISHED) {
                            roomManager.get(roomId)?.status = com.lanboardgame.platform.RoomStatus.FINISHED
                        }
                        broadcast(roomId)
                    }
                    roomsAwaitingNextHand.remove(roomId)
                }, 4, TimeUnit.SECONDS)
            }
        }

        private fun withGuandan(action: (GuandanGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? GuandanGame ?: error("掼蛋尚未开始")
            synchronized(game) { action(game, actor) }
            if (game.phase == com.lanboardgame.games.guandan.GuandanPhase.FINISHED) {
                roomManager.get(roomId)?.status = com.lanboardgame.platform.RoomStatus.FINISHED
            }
            broadcast(roomId)
        }

        private fun withTimeBomb(action: (TimeBombGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? TimeBombGame ?: error("TimeBomb 尚未开始")
            synchronized(game) { action(game, actor) }
            if (game.phase == com.lanboardgame.games.timebomb.TimeBombPhase.GAME_OVER) {
                roomManager.get(roomId)?.status = com.lanboardgame.platform.RoomStatus.FINISHED
            }
            broadcast(roomId)
        }

        private fun withCrossfire(action: (CrossfireGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? CrossfireGame ?: error("Crossfire 尚未开始")
            synchronized(game) { action(game, actor) }
            if (game.phase == com.lanboardgame.games.crossfire.CrossfirePhase.GAME_OVER) {
                roomManager.get(roomId)?.status = com.lanboardgame.platform.RoomStatus.FINISHED
            }
            broadcast(roomId)
        }

        private fun withGoodCop(action: (GoodCopBadCopGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? GoodCopBadCopGame ?: error("Good Cop Bad Cop 尚未开始")
            synchronized(game) { action(game, actor) }
            if (game.phase == com.lanboardgame.games.goodcopbadcop.GoodCopPhase.GAME_OVER) {
                roomManager.get(roomId)?.status = com.lanboardgame.platform.RoomStatus.FINISHED
            }
            broadcast(roomId)
        }

        private fun withLiarDice(action: (LiarDiceGame, String) -> Unit) {
            val (roomId, actor) = requireContext()
            val game = roomManager.get(roomId)?.game as? LiarDiceGame ?: error("骰子模拟器尚未开始")
            synchronized(game) { action(game, actor) }
            broadcast(roomId)
        }

        private fun attach(room: String, player: String, session: String) {
            val previousRoom = roomId
            val previousSession = sessionId
            if (previousRoom != null) removeSocket(socketsByRoom, previousRoom, this)
            if (previousSession != null) {
                val previousSessionBecameEmpty = synchronized(socketsBySession) {
                    val sockets = socketsBySession[previousSession]
                    sockets?.remove(this)
                    if (sockets.isNullOrEmpty()) {
                        socketsBySession.remove(previousSession)
                        true
                    } else {
                        false
                    }
                }
                if (previousSession != session && previousSessionBecameEmpty) {
                    roomManager.disconnect(previousSession)
                }
            }
            roomId = room
            playerId = player
            sessionId = session
            socketsByRoom.computeIfAbsent(room) { ConcurrentHashMap.newKeySet() }.add(this)
            synchronized(socketsBySession) {
                socketsBySession.computeIfAbsent(session) { ConcurrentHashMap.newKeySet() }.add(this)
            }
            roomManager.connect(session)
            Log.i(TAG, "session attached room=$room player=$player session=$session sockets=${socketsBySession[session]?.size ?: 0}")
        }

        private fun sendAttached(resumed: Boolean, requestId: String?) {
            sendEvent("session:attached", buildJsonObject {
                put("roomId", roomId!!)
                put("playerId", playerId!!)
                put("sessionId", sessionId!!)
                put("resumed", resumed)
            }, requestId)
        }

        private fun requireContext(): Pair<String, String> =
            (roomId ?: error("尚未加入房间")) to (playerId ?: error("尚未加入房间"))
    }

    private fun broadcast(roomId: String) {
        val snapshot = roomManager.snapshot(roomId) ?: return
        socketsByRoom[roomId]?.toList()?.forEach { socket ->
            runCatching {
                socket.sendEvent(Events.ROOM_STATE, json.encodeToJsonElement(RoomView.serializer(), snapshot.room))
                val avalonConfig = snapshot.avalonConfig
                if (snapshot.room.gameType == GameType.AVALON && avalonConfig != null) {
                    socket.sendEvent(
                        Events.AVALON_CONFIG,
                        json.encodeToJsonElement(AvalonConfig.serializer(), avalonConfig),
                    )
                }
                val liarDiceConfig = snapshot.liarDiceConfig
                if (snapshot.room.gameType == GameType.LIARS_DICE && liarDiceConfig != null) {
                    socket.sendEvent(
                        Events.LIAR_DICE_CONFIGURE,
                        json.encodeToJsonElement(LiarDiceConfig.serializer(), liarDiceConfig),
                    )
                }
                val id = socket.playerId
                val game = snapshot.game
                if (id != null && game != null) {
                    val view = synchronized(game) { game.viewFor(id) }
                    val element = when (view) {
                        is AvalonPlayerView -> json.encodeToJsonElement(AvalonPlayerView.serializer(), view)
                        is PokerPlayerView -> json.encodeToJsonElement(PokerPlayerView.serializer(), view)
                        is GuandanPlayerView -> json.encodeToJsonElement(GuandanPlayerView.serializer(), view)
                        is TimeBombPlayerView -> json.encodeToJsonElement(TimeBombPlayerView.serializer(), view)
                        is CrossfirePlayerView -> json.encodeToJsonElement(CrossfirePlayerView.serializer(), view)
                        is GoodCopPlayerView -> json.encodeToJsonElement(GoodCopPlayerView.serializer(), view)
                        is LiarDicePlayerView -> json.encodeToJsonElement(LiarDicePlayerView.serializer(), view)
                        else -> error("未知游戏视图")
                    }
                    socket.sendEvent(Events.GAME_STATE, element)
                }
            }.onFailure { Log.e(TAG, "broadcast failed room=$roomId player=${socket.playerId.orEmpty()}", it) }
        }
        if (!hostStateProvider().deployed) testBotController.schedule(roomId)
    }

    private fun detach(socket: ClientSocket) {
        socket.roomId?.let { removeSocket(socketsByRoom, it, socket) }
        socket.sessionId?.let { removeSocket(socketsBySession, it, socket) }
        socket.roomId = null
        socket.playerId = null
        socket.sessionId = null
    }

    private fun <K> removeSocket(
        index: ConcurrentHashMap<K, MutableSet<ClientSocket>>,
        key: K,
        socket: ClientSocket,
    ) {
        val sockets = index[key] ?: return
        sockets.remove(socket)
        if (sockets.isEmpty()) index.remove(key, sockets)
    }

    private fun JsonObject.requiredString(key: String) = this[key]?.jsonPrimitive?.content ?: error("缺少 $key")
    private fun JsonObject.requiredBoolean(key: String) = requiredString(key).toBooleanStrict()
    private fun JsonObject.requiredStrings(key: String) = this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: error("缺少 $key")

    private fun jsonResponse(value: JsonElement): Response = newFixedLengthResponse(
        Response.Status.OK, "application/json; charset=utf-8", json.encodeToString(JsonElement.serializer(), value)
    ).apply { addHeader("Cache-Control", "no-store") }

    private fun jsonError(status: Response.Status, message: String): Response = newFixedLengthResponse(
        status,
        "application/json; charset=utf-8",
        json.encodeToString(JsonElement.serializer(), buildJsonObject { put("ok", false); put("message", message) }),
    ).apply { addHeader("Cache-Control", "no-store") }

    private fun hostConfigJson(): JsonElement {
        val state = hostStateProvider()
        val options = NetworkManager.localIpv4Addresses(context)
        return buildJsonObject {
            put("port", state.port)
            put("selectedAddress", state.selectedAddress)
            put("deployed", state.deployed)
            put("networkOptions", kotlinx.serialization.json.JsonArray(options.map { option -> buildJsonObject {
                put("address", option.address)
                put("interfaceName", option.interfaceName)
                put("label", option.label)
                put("recommended", option.recommended)
            } }))
        }
    }

    private fun isLoopbackClient(session: IHTTPSession): Boolean = runCatching {
        InetAddress.getByName(session.remoteIpAddress).isLoopbackAddress
    }.getOrDefault(false)

    private fun qrResponse(text: String): Response {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 640, 640)
        val pixels = IntArray(640 * 640)
        for (y in 0 until 640) for (x in 0 until 640) pixels[y * 640 + x] = if (matrix[x, y]) 0xff071117.toInt() else 0xffffffff.toInt()
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, 640, 0, 0, 640, 640)
        val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.toByteArray() }
        return newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    private fun assetResponse(uri: String): Response {
        val raw = uri.removePrefix("/").substringBefore('?')
        val requested = if (raw.isBlank()) "index.html" else raw
        val safe = requested.takeUnless { ".." in it } ?: return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        val candidates = listOf("web/$safe", "web/index.html")
        for (asset in candidates) {
            val bytes = runCatching { context.assets.open(asset).use { it.readBytes() } }.getOrNull() ?: continue
            val mime = when (asset.substringAfterLast('.')) {
                "html" -> "text/html; charset=utf-8"
                "js" -> "text/javascript; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "svg" -> "image/svg+xml"
                "png" -> "image/png"
                "json" -> "application/json"
                else -> "application/octet-stream"
            }
            return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
                addHeader("Cache-Control", if (asset.endsWith("index.html")) "no-cache" else "public, max-age=31536000, immutable")
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Web assets missing. Run npm run sync:web")
    }

    companion object {
        private const val TAG = "LanGame.Server"
    }
}
