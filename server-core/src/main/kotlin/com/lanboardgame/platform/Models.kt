package com.lanboardgame.platform

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Serializable
enum class GameType {
    AVALON,
    GUANDAN,
    TIME_BOMB,
    CROSSFIRE,
    GOOD_COP_BAD_COP,
    LIARS_DICE,
    POKER,
}

@Serializable
enum class RoomStatus { WAITING, PLAYING, FINISHED }

@Serializable
data class Player(
    val id: String,
    var sessionId: String,
    var nickname: String,
    var connected: Boolean = true,
    var isHost: Boolean = false,
    var ready: Boolean = false,
    val isBot: Boolean = false,
)

data class Room(
    val roomId: String,
    val gameType: GameType,
    var hostId: String,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val players: LinkedHashMap<String, Player> = linkedMapOf(),
    var status: RoomStatus = RoomStatus.WAITING,
    var game: GameInstance? = null,
)

@Serializable
data class PublicPlayer(
    val id: String,
    val nickname: String,
    val connected: Boolean,
    val isHost: Boolean,
    val ready: Boolean,
    val isBot: Boolean = false,
)

@Serializable
data class RoomView(
    val roomId: String,
    val gameType: GameType,
    val status: RoomStatus,
    val hostId: String,
    val players: List<PublicPlayer>,
)

interface GameInstance {
    val gameType: GameType
    fun viewFor(playerId: String): Any
}

object Ids {
    private val random = SecureRandom()
    fun roomId(): String = (100000 + random.nextInt(900000)).toString()
    fun playerId(): String = UUID.randomUUID().toString()
    fun sessionId(): String = UUID.randomUUID().toString()
}

fun Room.publicView() = RoomView(
    roomId = roomId,
    gameType = gameType,
    status = status,
    hostId = hostId,
    players = players.values.map {
        PublicPlayer(it.id, it.nickname, it.connected, it.isHost, it.ready, it.isBot)
    },
)
