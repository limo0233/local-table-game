package com.lanboardgame.games.goodcopbadcop

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoodCopBadCopGameTest {
    @Test
    fun `players receive private integrity and can investigate`() {
        val game = GoodCopBadCopGame.create((1..4).map { "p$it" }, Random(5))
        val first = game.viewFor("p1")
        assertEquals(3, first.myIntegrity.size)
        assertTrue(first.canInvestigate)

        game.investigate("p1", "p2", 0)
        assertTrue(game.viewFor("p1").investigationResult != null)
        assertTrue(game.viewFor("p2").investigationResult == null)
    }

    @Test
    fun `arm aim and shoot use the normal turn flow`() {
        val game = GoodCopBadCopGame.create((1..4).map { "p$it" }, Random(5))
        game.arm("p1")
        assertEquals("p1", game.viewFor("p1").public.gunHolder)
        game.aim("p1", "p2")
        game.shoot("p1")
        assertTrue(game.viewFor("p1").public.history.any { it.action == "SHOOT" })
    }

    @Test
    fun `equip grants shield and reveals one own integrity card`() {
        val game = GoodCopBadCopGame.create((1..4).map { "p$it" }, Random(5))
        val before = game.viewFor("p1").myIntegrity.count { !it.revealed }
        game.equip("p1")
        val after = game.viewFor("p1")
        assertEquals(before - 1, after.myIntegrity.count { !it.revealed })
        assertEquals(1, after.myEquipment.size)
    }
}
