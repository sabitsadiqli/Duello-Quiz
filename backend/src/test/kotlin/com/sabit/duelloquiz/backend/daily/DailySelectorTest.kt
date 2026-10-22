package com.sabit.duelloquiz.backend.daily

import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailySelectorTest {
    private var n = 0
    private fun c(category: Int, playable: Boolean = true, recent: Boolean = false) =
        DailyCandidate(UUID.nameUUIDFromBytes("q${n++}".toByteArray()), category, playable, recent)

    private fun pool(categories: Int, perCategory: Int, recentPerCategory: Int = 0) =
        (1..categories).flatMap { cat -> (1..perCategory).map { i -> c(cat, recent = i <= recentPerCategory) } }

    @Test
    fun `one fresh question per playable category, five distinct`() {
        val candidates = pool(categories = 5, perCategory = 12, recentPerCategory = 4)
        repeat(200) { seed ->
            val picked = DailySelector.select(candidates, 5, Random(seed))!!
            assertEquals(5, picked.size)
            assertEquals(5, picked.map { it.questionId }.toSet().size)
            assertEquals((1..5).toSet(), picked.map { it.categoryId }.toSet(), "one per category")
            assertTrue(picked.none { it.recentlyUsed }, "not used in the last 60 days")
        }
    }

    @Test
    fun `more than five playable categories picks five different categories`() {
        val picked = DailySelector.select(pool(categories = 8, perCategory = 10), 5, Random(3))!!
        assertEquals(5, picked.map { it.categoryId }.toSet().size)
    }

    @Test
    fun `the 60-day rule is relaxed when a category has only recently used questions`() {
        val candidates = pool(categories = 4, perCategory = 10) + (1..10).map { c(5, recent = true) }
        val picked = DailySelector.select(candidates, 5, Random(1))!!
        assertEquals((1..5).toSet(), picked.map { it.categoryId }.toSet())
        assertEquals(1, picked.count { it.recentlyUsed })
    }

    @Test
    fun `fewer playable categories are filled from playable ones, then from any active question`() {
        val twoPlayable = pool(categories = 2, perCategory = 10)
        val picked = DailySelector.select(twoPlayable, 5, Random(2))!!
        assertEquals(5, picked.map { it.questionId }.toSet().size)
        assertEquals(setOf(1, 2), picked.map { it.categoryId }.toSet())

        val nonePlayable = (1..3).map { c(9, playable = false) } + (1..3).map { c(8, playable = false) }
        assertEquals(5, DailySelector.select(nonePlayable, 5, Random(2))!!.size)
    }

    @Test
    fun `fewer than five active questions means no challenge`() {
        assertNull(DailySelector.select(pool(categories = 2, perCategory = 2), 5, Random(1)))
        assertNull(DailySelector.select(emptyList(), 5, Random(1)))
    }

    @Test
    fun `selection and order depend on the random source, not on the input order`() {
        val candidates = pool(categories = 5, perCategory = 20)
        val a = DailySelector.select(candidates, 5, Random(10))!!.map { it.questionId }
        val b = DailySelector.select(candidates, 5, Random(11))!!.map { it.questionId }
        assertNotEquals(a, b)
        val orders = (0 until 50).map { DailySelector.select(candidates, 5, Random(it))!!.map { q -> q.categoryId } }.toSet()
        assertTrue(orders.size > 1, "category order is random")
    }
}
