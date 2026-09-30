package com.lanboardgame.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SocketMessage<T>(
    val type: String,
    val requestId: String? = null,
    val payload: T,
)

@Serializable
data class ClientEnvelope(
    val type: String,
    val requestId: String? = null,
    val payload: JsonElement? = null,
)

@Serializable
data class ErrorPayload(val code: String, val message: String)

object Events {
    const val SYSTEM_PING = "system:ping"
    const val SYSTEM_PONG = "system:pong"
    const val ROOM_CREATE = "room:create"
    const val ROOM_JOIN = "room:join"
    const val ROOM_LEAVE = "room:leave"
    const val ROOM_LEFT = "room:left"
    const val ROOM_CLOSED = "room:closed"
    const val ROOM_STATE = "room:state"
    const val PLAYER_READY = "player:ready"
    const val PLAYER_RENAME = "player:rename"
    const val PLAYER_KICK = "player:kick"
    const val ROOM_BOTS = "room:bots"
    const val GAME_START = "game:start"
    const val GAME_RESTART = "game:restart"
    const val GAME_STATE = "game:state"
    const val GAME_ERROR = "game:error"
    const val AVALON_SELECT_TEAM = "avalon:select_team"
    const val AVALON_CONFIGURE = "avalon:configure"
    const val AVALON_CONFIG = "avalon:config"
    const val AVALON_VOTE = "avalon:vote"
    const val AVALON_MISSION = "avalon:mission"
    const val AVALON_ASSASSINATE = "avalon:assassinate"
    const val LIAR_DICE_CONFIGURE = "liarsdice:configure"
    const val POKER_ACTION = "poker:action"
    const val GUANDAN_PLAY = "guandan:play"
    const val GUANDAN_PASS = "guandan:pass"
    const val GUANDAN_CONTINUE = "guandan:continue"
    const val GUANDAN_TRIBUTE = "guandan:tribute"
    const val GUANDAN_RETURN_TRIBUTE = "guandan:return_tribute"
    const val TIME_BOMB_CONFIRM_ROLE = "timebomb:confirm_role"
    const val TIME_BOMB_CUT = "timebomb:cut"
    const val CROSSFIRE_CONFIRM_INITIAL = "crossfire:confirm_initial"
    const val CROSSFIRE_CONFIRM_INTEL = "crossfire:confirm_intel"
    const val CROSSFIRE_CONFIRM_SHUFFLE = "crossfire:confirm_shuffle"
    const val CROSSFIRE_START_TARGETING = "crossfire:start_targeting"
    const val CROSSFIRE_TARGET = "crossfire:target"
    const val GOOD_COP_INVESTIGATE = "goodcop:investigate"
    const val GOOD_COP_EQUIP = "goodcop:equip"
    const val GOOD_COP_ARM = "goodcop:arm"
    const val GOOD_COP_AIM = "goodcop:aim"
    const val GOOD_COP_SHOOT = "goodcop:shoot"
    const val LIAR_DICE_REROLL = "liarsdice:reroll"
    const val LIAR_DICE_SETTLE = "liarsdice:settle"
    const val LIAR_DICE_NEXT_ROUND = "liarsdice:next_round"
}
