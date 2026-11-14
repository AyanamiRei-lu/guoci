package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetPlannerTest {

    private fun words(n: Int) = (1..n).map {
        Word(id = it, rank = it, word = "word$it", phonetic = "", pos = "n.", meaning = "含义$it")
    }

    @Test
    fun `中译英题型题面是释义、答案是单词`() {
        val plan = SheetPlanner.plan(words(3), SheetPlanner.Options(kind = SheetPlanner.Kind.ZH_TO_EN, count = 3))
        assertEquals(listOf("含义1", "含义2", "含义3"), plan.items.map { it.prompt })
        assertEquals(listOf("word1", "word2", "word3"), plan.items.map { it.answer })
        assertEquals(listOf(1, 2, 3), plan.items.map { it.number })
    }

    @Test
    fun `英译中题型题面是单词、答案是释义，且不给首字母提示`() {
        val plan = SheetPlanner.plan(
            words(2),
            SheetPlanner.Options(kind = SheetPlanner.Kind.EN_TO_ZH, count = 2, hintFirstLetter = true),
        )
        assertEquals(listOf("word1", "word2"), plan.items.map { it.prompt })
        assertEquals(listOf("含义1", "含义2"), plan.items.map { it.answer })
        assertTrue(plan.items.all { it.hint.isEmpty() })
    }

    @Test
    fun `首字母提示只在中译英且开启时出现`() {
        val withHint = SheetPlanner.plan(
            words(1), SheetPlanner.Options(count = 1, hintFirstLetter = true),
        )
        assertEquals("w····", withHint.items[0].hint)

        val without = SheetPlanner.plan(words(1), SheetPlanner.Options(count = 1))
        assertEquals("", without.items[0].hint)
    }

    @Test
    fun `数量上限与空集合`() {
        assertEquals(5, SheetPlanner.plan(words(50), SheetPlanner.Options(count = 5)).items.size)
        assertEquals(50, SheetPlanner.plan(words(50), SheetPlanner.Options(count = 100)).items.size)
        assertTrue(SheetPlanner.plan(words(10), SheetPlanner.Options(count = 0)).items.isEmpty())
        assertTrue(SheetPlanner.plan(emptyList(), SheetPlanner.Options(count = 20)).items.isEmpty())
    }

    @Test
    fun `不打乱时按 rank 排序`() {
        val shuffled = listOf(words(3)[2], words(3)[0], words(3)[1])
        val plan = SheetPlanner.plan(shuffled, SheetPlanner.Options(count = 3, shuffle = false))
        assertEquals(listOf("word1", "word2", "word3"), plan.items.map { it.answer })
    }

    @Test
    fun `相同 seed 的乱序结果可复现`() {
        val a = SheetPlanner.plan(words(50), SheetPlanner.Options(count = 20, shuffle = true, seed = 42))
        val b = SheetPlanner.plan(words(50), SheetPlanner.Options(count = 20, shuffle = true, seed = 42))
        assertEquals(a.items, b.items)
    }

    @Test
    fun `不同 seed 的乱序结果不同`() {
        val a = SheetPlanner.plan(words(50), SheetPlanner.Options(count = 50, shuffle = true, seed = 1))
        val b = SheetPlanner.plan(words(50), SheetPlanner.Options(count = 50, shuffle = true, seed = 2))
        assertNotEquals(a.items.map { it.answer }, b.items.map { it.answer })
    }

    @Test
    fun `答案列表与题目一一对应`() {
        val plan = SheetPlanner.plan(words(10), SheetPlanner.Options(count = 10))
        assertEquals(plan.items.map { it.answer }, plan.answers)
        assertTrue(plan.includeAnswers)
        val noAnswers = SheetPlanner.plan(words(10), SheetPlanner.Options(count = 10, includeAnswers = false))
        assertFalse(noAnswers.includeAnswers)
        // 答案本身仍然要算出来，才能"打印时不含、屏幕上可对答案"
        assertEquals(10, noAnswers.answers.size)
    }
}
