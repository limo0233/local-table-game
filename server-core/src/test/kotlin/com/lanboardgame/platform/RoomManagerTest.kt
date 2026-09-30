package com.lanboardgame.platform

import com.lanboardgame.games.guandan.GuandanGame
import com.lanboardgame.games.crossfire.CrossfireGame
import com.lanboardgame.games.goodcopbadcop.GoodCopBadCopGame
import com.lanboardgame.games.liarsdice.LiarDiceGame
import com.lanboardgame.games.liarsdice.LiarDiceConfig
import com.lanboardgame.games.timebomb.TimeBombGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RoomManagerTest {
    @Test
    fun `host nickname and session must not be blank`() {
        val manager = RoomManager()
        assertFailsWith<IllegalArgumentException> { manager.createRoom(GameType.POKER, "   ", "session") }
        assertFailsWith<IllegalArgumentException> { manager.createRoom(GameType.POKER, "Host", "") }
    }

    @Test
    fun `one session cannot create multiple rooms`() {
        val manager = RoomManager()
        manager.createRoom(GameType.POKER, "Host", "same-session")
        assertFailsWith<IllegalArgumentException> {
            manager.createRoom(GameType.AVALON, "Host", "same-session")
        }
    }

    @Test
    fun `reconnect restores original player instead of creating a duplicate`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        val joined = manager.joinRoom(created.room.roomId, "Guest", "guest-session")
        manager.disconnect("guest-session")
        assertFalse(joined.player.connected)

        val resumed = manager.joinRoom(created.room.roomId, "Ignored new name", "guest-session")
        assertTrue(resumed.resumed)
        assertEquals(joined.player.id, resumed.player.id)
        assertEquals("Guest", resumed.player.nickname)
        assertEquals(2, resumed.room.players.size)
    }

    @Test
    fun `nickname recovery restores a disconnected player in a running game`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.GUANDAN, "Host", "host-session")
        val guests = (1..3).map { index ->
            val guest = manager.joinRoom(created.room.roomId, "Guest $index", "guest-session-$index")
            manager.setReady(created.room.roomId, guest.player.id, true)
            guest
        }
        manager.start(created.room.roomId, created.player.id)
        val original = guests[0]
        val originalHand = (created.room.game as GuandanGame).viewFor(original.player.id).myHand
        manager.leave(created.room.roomId, original.player.id)

        val resumed = manager.joinRoom(created.room.roomId, " guest 1 ", "replacement-session")

        assertTrue(resumed.resumed)
        assertEquals(original.player.id, resumed.player.id)
        assertEquals("Guest 1", resumed.player.nickname)
        assertEquals("replacement-session", resumed.player.sessionId)
        assertEquals(RoomStatus.PLAYING, resumed.room.status)
        assertEquals(originalHand, (resumed.room.game as GuandanGame).viewFor(resumed.player.id).myHand)
    }

    @Test
    fun `guandan room requires four players`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.GUANDAN, "Host", "host-session")
        repeat(2) { index -> manager.joinRoom(created.room.roomId, "Guest $index", "guest-session-$index") }
        assertFailsWith<IllegalArgumentException> { manager.start(created.room.roomId, created.player.id) }
    }

    @Test
    fun `host can kick a guest from a waiting room`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        val guest = manager.joinRoom(created.room.roomId, "Guest", "guest-session")
        manager.kick(created.room.roomId, created.player.id, guest.player.id)
        assertEquals(listOf(created.player.id), created.room.players.keys.toList())
    }

    @Test
    fun `host leaving disbands room and releases every session`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        manager.joinRoom(created.room.roomId, "Guest", "guest-session")

        val result = manager.leave(created.room.roomId, created.player.id)

        assertTrue(result.disbanded)
        assertEquals(null, manager.get(created.room.roomId))
        manager.createRoom(GameType.POKER, "New Host", "host-session")
        manager.createRoom(GameType.POKER, "Former Guest", "guest-session")
    }

    @Test
    fun `guest leaving waiting room removes only that player`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        val guest = manager.joinRoom(created.room.roomId, "Guest", "guest-session")

        val result = manager.leave(created.room.roomId, guest.player.id)

        assertFalse(result.disbanded)
        assertEquals(listOf(created.player.id), manager.get(created.room.roomId)?.players?.keys?.toList())
        manager.createRoom(GameType.POKER, "Former Guest", "guest-session")
    }

    @Test
    fun `guest can rejoin a waiting room by the original nickname`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        val guest = manager.joinRoom(created.room.roomId, "Guest", "guest-session")

        manager.leave(created.room.roomId, guest.player.id)
        val resumed = manager.joinRoom(created.room.roomId, " guest ", "replacement-session")

        assertTrue(resumed.resumed)
        assertEquals(guest.player.id, resumed.player.id)
        assertEquals(2, resumed.room.players.size)
    }

    @Test
    fun `waiting room reserves a departed seat for nickname recovery`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        val guests = (1..7).map { index ->
            manager.joinRoom(created.room.roomId, "Guest $index", "guest-session-$index")
        }

        manager.leave(created.room.roomId, guests.first().player.id)

        assertFailsWith<IllegalArgumentException> {
            manager.joinRoom(created.room.roomId, "Late guest", "late-session")
        }
        val resumed = manager.joinRoom(created.room.roomId, "guest 1", "replacement-session")
        assertTrue(resumed.resumed)
        assertEquals(guests.first().player.id, resumed.player.id)
        assertEquals(8, resumed.room.players.size)
    }

    @Test
    fun `player can rename only while not ready`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.AVALON, "Host", "host-session")
        val guest = manager.joinRoom(created.room.roomId, "Guest", "guest-session")
        manager.rename(created.room.roomId, guest.player.id, "New name")
        assertEquals("New name", guest.player.nickname)

        manager.setReady(created.room.roomId, guest.player.id, true)
        assertFailsWith<IllegalArgumentException> {
            manager.rename(created.room.roomId, guest.player.id, "Blocked")
        }
    }

    @Test
    fun `returning to lobby clears game and requires everyone to prepare again`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.AVALON, "Host", "host-session")
        repeat(4) { index ->
            val guest = manager.joinRoom(created.room.roomId, "Guest $index", "guest-session-$index")
            manager.setReady(created.room.roomId, guest.player.id, true)
        }
        manager.start(created.room.roomId, created.player.id)
        assertEquals(RoomStatus.PLAYING, created.room.status)

        manager.returnToLobby(created.room.roomId, created.player.id)
        assertEquals(RoomStatus.WAITING, created.room.status)
        assertEquals(null, created.room.game)
        assertTrue(created.room.players.values.none { it.ready })
    }

    @Test
    fun `avalon room respects configured player limit`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.AVALON, "Host", "host-session")
        repeat(4) { index -> manager.joinRoom(created.room.roomId, "Guest $index", "guest-session-$index") }
        assertFailsWith<IllegalArgumentException> {
            manager.joinRoom(created.room.roomId, "Extra", "extra-session")
        }
    }

    @Test
    fun `new game room types start through the shared room lifecycle`() {
        val cases = listOf(
            GameType.TIME_BOMB to 4,
            GameType.CROSSFIRE to 5,
            GameType.GOOD_COP_BAD_COP to 4,
            GameType.LIARS_DICE to 2,
        )
        cases.forEach { (gameType, count) ->
            val manager = RoomManager()
            val created = manager.createRoom(gameType, "Host", "host-$gameType")
            repeat(count - 1) { index ->
                val guest = manager.joinRoom(created.room.roomId, "Guest $index", "guest-$gameType-$index")
                manager.setReady(created.room.roomId, guest.player.id, true)
            }
            manager.start(created.room.roomId, created.player.id)
            assertEquals(RoomStatus.PLAYING, created.room.status)
            when (gameType) {
                GameType.TIME_BOMB -> assertTrue(created.room.game is TimeBombGame)
                GameType.CROSSFIRE -> assertTrue(created.room.game is CrossfireGame)
                GameType.GOOD_COP_BAD_COP -> assertTrue(created.room.game is GoodCopBadCopGame)
                GameType.LIARS_DICE -> assertTrue(created.room.game is LiarDiceGame)
                else -> error("unexpected test case")
            }
        }
    }

    @Test
    fun `crossfire reconnect preserves private intel shuffle history and final role`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.CROSSFIRE, "Host", "host-session")
        val guests = (1..4).map { index ->
            manager.joinRoom(created.room.roomId, "Guest $index", "guest-session-$index").also {
                manager.setReady(created.room.roomId, it.player.id, true)
            }
        }
        manager.start(created.room.roomId, created.player.id)
        val game = created.room.game as CrossfireGame
        game.playerIds.forEach(game::confirmInitial)
        game.playerIds.forEach(game::confirmIntel)
        while (game.phase == com.lanboardgame.games.crossfire.CrossfirePhase.SHUFFLE) {
            game.currentShuffleGroup.forEach(game::confirmShuffle)
        }

        val guest = guests.first()
        val before = game.viewFor(guest.player.id)
        manager.disconnect(guest.player.sessionId)
        val resumed = manager.joinRoom(created.room.roomId, guest.player.nickname, guest.player.sessionId)
        val after = (resumed.room.game as CrossfireGame).viewFor(resumed.player.id)

        assertTrue(resumed.resumed)
        assertEquals(guest.player.id, resumed.player.id)
        assertEquals(before, after)
    }

    @Test
    fun `host can fill and clear server-owned test bots without changing real seats`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.POKER, "Host", "host-session")
        manager.addTestBots(created.room.roomId, created.player.id)

        assertEquals(8, created.room.players.size)
        assertEquals(7, created.room.players.values.count { it.isBot })
        assertTrue(created.room.players.values.filter { it.isBot }.all { it.ready && it.connected })

        manager.removeTestBots(created.room.roomId, created.player.id)
        assertEquals(listOf(created.player.id), created.room.players.keys.toList())
    }

    @Test
    fun `test bots cannot be added after a game starts`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.LIARS_DICE, "Host", "host-session")
        val guest = manager.joinRoom(created.room.roomId, "Guest", "guest-session")
        manager.setReady(created.room.roomId, guest.player.id, true)
        manager.start(created.room.roomId, created.player.id)

        assertFailsWith<IllegalArgumentException> { manager.addTestBots(created.room.roomId, created.player.id) }
        assertFailsWith<IllegalArgumentException> { manager.removeTestBots(created.room.roomId, created.player.id) }
    }

    @Test
    fun `host can configure dice count before starting liar dice`() {
        val manager = RoomManager()
        val created = manager.createRoom(GameType.LIARS_DICE, "Host", "host-session")
        val guest = manager.joinRoom(created.room.roomId, "Guest", "guest-session")
        manager.setReady(created.room.roomId, guest.player.id, true)
        manager.configureLiarDice(created.room.roomId, created.player.id, LiarDiceConfig(8))
        manager.start(created.room.roomId, created.player.id)
        val game = created.room.game as LiarDiceGame
        assertEquals(8, game.viewFor(created.player.id).myDice.size)
        assertEquals(8, game.viewFor(guest.player.id).myDice.size)
    }
}
