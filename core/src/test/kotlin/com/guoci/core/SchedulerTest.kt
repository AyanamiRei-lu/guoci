package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    private val today = 20_000L

    private fun word(id: Int, rank: Int = id) = Word(
        id = id, rank = rank, word = "w$id", phonetic = "p$id", pos = "n.", meaning = "意思$id",
    )

    @Test
    fun `新词过关后进入第 0 档且明天到期`() {
        val p = Scheduler.onNewWordPassed(WordProgress(), today)
        assertEquals(0, p.stage)
        assertEquals(today + 1, p.dueEpochDay)
        assertEquals(WordStatus.REVIEW, p.status)
        assertEquals(1, p.streakCorrect)
    }

    @Test
    fun `连对逐档推进到 30 天`() {
        var p = Scheduler.onNewWordPassed(WordProgress(), today)
        // stage 0 已就位，继续答对：1、2、4、7、15、30
        val expected = listOf(2L, 4L, 7L, 15L, 30L)
        for ((i, days) in expected.withIndex()) {
            p = Scheduler.onReviewAnswered(p, today, correct = true)
            assertEquals("第 ${i + 1} 次答对后 stage", i + 1, p.stage)
            assertEquals("第 ${i + 1} 次答对后到期日", today + days, p.dueEpochDay)
            assertEquals(WordStatus.REVIEW, p.status)
        }
    }

    @Test
    fun `第 6 次答对即毕业且不再排期`() {
        var p = WordProgress(stage = 5, dueEpochDay = today, status = WordStatus.REVIEW)
        p = Scheduler.onReviewAnswered(p, today, correct = true)
        assertEquals(WordStatus.GRADUATED, p.status)
        assertEquals(Scheduler.GRADUATED_STAGE, p.stage)
        // 毕业词的到期日必须大到任何待复习筛选都选不中它
        assertTrue(p.dueEpochDay > today + 100_000)
    }

    @Test
    fun `答错退一档而不是清零，并且明天必须再见面`() {
        val before = WordProgress(
            stage = 3, dueEpochDay = today, status = WordStatus.REVIEW,
            lapses = 1, streakCorrect = 4,
        )
        val after = Scheduler.onReviewAnswered(before, today, correct = false)
        assertEquals(2, after.stage)          // 3 -> 2，不是 0
        assertEquals(today + 1, after.dueEpochDay)
        assertEquals(2, after.lapses)         // 错词本计数
        assertEquals(0, after.streakCorrect)
        assertEquals(WordStatus.REVIEW, after.status)
    }

    @Test
    fun `第 0 档答错不会退到负数`() {
        val before = WordProgress(stage = 0, dueEpochDay = today, status = WordStatus.REVIEW)
        val after = Scheduler.onReviewAnswered(before, today, correct = false)
        assertEquals(0, after.stage)
        assertEquals(today + 1, after.dueEpochDay)
    }

    @Test
    fun `队列包含到期复习词并排除未到期与已毕业`() {
        val words = (1..5).map { word(it) }
        val progress = mapOf(
            1 to WordProgress(stage = 1, dueEpochDay = today, status = WordStatus.REVIEW),
            2 to WordProgress(stage = 1, dueEpochDay = today + 1, status = WordStatus.REVIEW),
            3 to WordProgress(stage = 6, dueEpochDay = Long.MAX_VALUE, status = WordStatus.GRADUATED),
            4 to WordProgress(stage = 2, dueEpochDay = today - 3, status = WordStatus.REVIEW),
        )
        val q = Scheduler.buildQueue(words, progress, today, newPerDay = 10)
        // 4 更早到期所以排在 1 前面；2 未到期、3 已毕业、5 是新词
        assertEquals(listOf(4, 1), q.due.map { it.id })
        assertEquals(listOf(5), q.newWords.map { it.id })
        assertEquals(2, q.dueTotal)
        assertFalse(q.newWordsCapped)
    }

    @Test
    fun `新词按 rank 升序且不超过每日上限`() {
        val words = (1..30).map { word(id = it, rank = 31 - it) }  // rank 与 id 相反
        val q = Scheduler.buildQueue(words, emptyMap(), today, newPerDay = 10)
        assertEquals(10, q.newWords.size)
        assertEquals((1..10).map { it }, q.newWords.map { it.rank }.sorted())
        assertEquals(listOf(30, 29, 28, 27, 26, 25, 24, 23, 22, 21), q.newWords.map { it.id })
    }

    @Test
    fun `复习量超过上限时不再加新词`() {
        val words = (1..200).map { word(it) }
        val progress = (1..150).associateWith {
            WordProgress(stage = 1, dueEpochDay = today - 1, status = WordStatus.REVIEW)
        }
        val q = Scheduler.buildQueue(words, progress, today, newPerDay = 10)
        assertEquals(Scheduler.MAX_REVIEW_PER_DAY, q.due.size)
        assertEquals(150, q.dueTotal)      // 真实欠账要如实告诉用户
        assertTrue(q.newWordsCapped)
        assertTrue(q.newWords.isEmpty())   // 先清复习，别再加新词
    }

    @Test
    fun `空词库时队列为空`() {
        val q = Scheduler.buildQueue(emptyList(), emptyMap(), today, newPerDay = 10)
        assertTrue(q.isEmpty)
        assertEquals(0, q.dueTotal)
    }

    @Test
    fun `学到一半被杀掉的词不会消失，会留在待考队列里`() {
        val words = (1..3).map { word(it) }
        val progress = mapOf(
            1 to WordProgress(status = WordStatus.LEARNING, lastSeenEpochDay = today),
        )
        val q = Scheduler.buildQueue(words, progress, today, newPerDay = 5)
        // 1 已学过但没考过：既不是新词，也不该被当成新词再发一次
        assertEquals(listOf(2, 3), q.newWords.map { it.id })
        assertEquals(listOf(1), q.pending.map { it.id })
        // 它必须出现在学习与测验两个阶段里，否则这个词永远卡在 LEARNING
        assertEquals(listOf(2, 3, 1), q.toLearn.map { it.id })
        assertTrue(q.toQuiz.any { it.id == 1 })
        assertEquals(3, q.total)
    }
}
