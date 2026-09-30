package com.lanboardgame.games.timebomb

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimeBombRulesAuditTest {
    @Test
    fun `role confirmation cannot be submitted twice`() {
        val game = TimeBombGame.create(listOf("a", "b", "c", "d"), Random(4))
        game.confirmRole("a")
        assertFailsWith<IllegalArgumentException> { game.confirmRole("a") }
    }

    @Test
    fun `private wire information exposes composition but not cut positions`() {
        val game = TimeBombGame.create(listOf("a", "b", "c", "d"), Random(4))
        game.playerIds.forEach { playerId ->
            val shown = game.viewFor(playerId).privateWires
            assertEquals(shown.sortedBy { it.ordinal }, shown)
        }
    }

    @Test
    fun `cutting the bomb immediately gives Moriarty the win`() {
        val game = controlledGame(mapOf(
            "a" to listOf(TimeBombWire.SAFE),
            "b" to listOf(TimeBombWire.BOMB),
            "c" to listOf(TimeBombWire.SAFE),
            "d" to listOf(TimeBombWire.SAFE),
        ))
        confirmAll(game)
        game.cut("a", "b", 0)
        assertEquals(TimeBombPhase.GAME_OVER, game.phase)
        assertEquals(TimeBombRole.MORIARTY, game.winner)
    }

    @Test
    fun `finding one defuse per player gives Sherlock the win`() {
        val game = controlledGame((listOf("a", "b", "c", "d")).associateWith { listOf(TimeBombWire.DEFUSE) })
        confirmAll(game)
        game.cut("a", "b", 0)
        game.cut("b", "c", 0)
        game.cut("c", "d", 0)
        game.cut("d", "a", 0)
        assertEquals(TimeBombRole.SHERLOCK, game.winner)
        assertEquals(4, game.defuseFound)
    }

    @Test
    fun `four complete safe rounds end with Moriarty winning`() {
        val players = listOf("a", "b", "c", "d")
        val game = controlledGame(players.associateWith { List(5) { TimeBombWire.SAFE } })
        confirmAll(game)
        repeat(16) {
            val actor = game.currentCutterId
            val target = players[(players.indexOf(actor) + 1) % players.size]
            game.cut(actor, target, 0)
        }
        assertEquals(TimeBombPhase.GAME_OVER, game.phase)
        assertEquals(TimeBombRole.MORIARTY, game.winner)
        assertTrue(game.endReason?.contains("四轮") == true)
    }

    @Test
    fun `official role pools are used for every supported player count`() {
        fun counts(playerCount: Int, seed: Int) = TimeBombGame.create(
            (1..playerCount).map { "p$it" }, Random(seed),
        ).let { game -> game.playerIds.map { game.viewFor(it).myRole }.groupingBy { it }.eachCount() }

        assertEquals(mapOf(TimeBombRole.SHERLOCK to 3, TimeBombRole.MORIARTY to 2), counts(5, 1))
        assertEquals(mapOf(TimeBombRole.SHERLOCK to 4, TimeBombRole.MORIARTY to 2), counts(6, 1))
        assertEquals(mapOf(TimeBombRole.SHERLOCK to 5, TimeBombRole.MORIARTY to 3), counts(8, 1))
        val fourPlayerEnemyCounts = (0..30).map { counts(4, it)[TimeBombRole.MORIARTY] ?: 0 }.toSet()
        val sevenPlayerEnemyCounts = (0..30).map { counts(7, it)[TimeBombRole.MORIARTY] ?: 0 }.toSet()
        assertEquals(setOf(1, 2), fourPlayerEnemyCounts)
        assertEquals(setOf(2, 3), sevenPlayerEnemyCounts)
    }

    private fun controlledGame(wireLists: Map<String, List<TimeBombWire>>): TimeBombGame {
        val players = listOf("a", "b", "c", "d")
        val wires = linkedMapOf<String, MutableList<TimeBombWire>>()
        players.forEach { wires[it] = wireLists.getValue(it).toMutableList() }
        return TimeBombGame.createForTest(
            players,
            players.associateWith { if (it == "d") TimeBombRole.MORIARTY else TimeBombRole.SHERLOCK },
            wires,
        )
    }

    private fun confirmAll(game: TimeBombGame) = game.playerIds.forEach(game::confirmRole)
}
