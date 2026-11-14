package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    private val today = 20_000L

    private fun days(vararg completed: Long): Map<Long, DailyRecord> =
        completed.associateWith { DailyRecord(completed = true, total = 10) }

    private fun withToday(completedToday: Boolean, vararg previous: Long): Map<Long, DailyRecord> =
        days(*previous) + (today to DailyRecord(completed = completedToday))

    @Test
    fun `今天已完成时连续天数包含今天`() {
        val d = withToday(true, today - 1, today - 2)
        assertEquals(3, Streak.current(d, today))
    }

    @Test
    fun `今天还没完成时从昨天开始数，不显示 0`() {
        val d = withToday(false, today - 1, today - 2)
        assertEquals(2, Streak.current(d, today))
    }

    @Test
    fun `断了一天就停止`() {
        val d = withToday(true, today - 2, today - 3)
        assertEquals(1, Streak.current(d, today))
    }

    @Test
    fun `没有任何记录时连续天数为 0`() {
        assertEquals(0, Streak.current(emptyMap(), today))
        assertEquals(0, Streak.longest(emptyMap()))
    }

    @Test
    fun `最长连续跨越中间的断签`() {
        val d = days(today - 20, today - 19, today - 18, today - 10, today - 9)
        assertEquals(3, Streak.longest(d))
    }

    @Test
    fun `徽章在阈值上解锁`() {
        assertEquals(setOf("streak7"), Streak.earnedBadges(streak = 7, mastered = 0))
        assertTrue(Streak.earnedBadges(streak = 6, mastered = 0).isEmpty())
        assertTrue("words100" in Streak.earnedBadges(streak = 0, mastered = 100))
        assertTrue("words3500" in Streak.earnedBadges(streak = 100, mastered = 3500))
    }

    @Test
    fun `徽章文案可读`() {
        assertEquals("连续 30 天", Streak.badgeLabel("streak30"))
        assertEquals("掌握 500 词", Streak.badgeLabel("words500"))
    }

    @Test
    fun `下一个目标优先选更近的那个`() {
        // 连续 6 天：再 1 天就有徽章，比"再 100 个词"近得多
        val g = Streak.nextGoal(streak = 6, mastered = 0)
        assertEquals("streak7", g?.badgeId)
        assertEquals(1, g?.remaining)
    }

    @Test
    fun `词数目标更近时给出词数目标`() {
        val g = Streak.nextGoal(streak = 0, mastered = 95)
        assertEquals("words100", g?.badgeId)
        assertEquals(5, g?.remaining)
    }

    @Test
    fun `全部达成后没有下一个目标`() {
        assertNull(Streak.nextGoal(streak = 500, mastered = 4000))
    }
}
