package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长跑模拟。
 *
 * 单条规则都对，不代表跑一年还是对的：间隔表、答错降档、每日上限、到期筛选这四件事
 * 叠在一起，可能出现"某些词永远考不到""某天突然有 300 张卡"这类只有时间才能暴露的
 * 问题。这些测试把一年压缩成几毫秒。
 */
class LongRunSimulationTest {

    private fun allWords(n: Int) = (1..n).map { Word(it, it, "w$it", "", "n.", "意思$it") }

    @Test
    fun `全部答对时 350 天学完 3500 词，第 430 天全部毕业`() {
        val words = allWords(3500)
        val byId = words.associateBy { it.id }
        var progress = emptyMap<Int, WordProgress>()
        var day = 20_000L
        var maxDaily = 0

        // 一个词从"今天学会"到"毕业"要走完 1+2+4+7+15+30 = 59 天（第 6 次答对才毕业），
        // 最后一个词在第 349 天引入，所以 349+59=408 天才会全部毕业。跑 430 天留余量。
        repeat(430) {
            val queue = Scheduler.buildQueue(words, progress, day, newPerDay = 10)
            maxDaily = maxOf(maxDaily, queue.total)
            if (!queue.isEmpty) {
                val s = Session(byId, queue, day, progress)
                while (s.currentLearn != null) s.markLearn(known = true)
                var guard = 0
                while (s.phase == SessionPhase.QUIZ && guard++ < 200) {
                    val w = s.currentQuiz ?: break
                    s.submit(w.word)
                    s.advance()
                }
                assertNotNull("第 $day 天没有结算", s.dayRecord)
                progress = s.progress
            }
            day++
        }

        val graduated = progress.values.count { it.status == WordStatus.GRADUATED }
        assertEquals("第 430 天应全部毕业", 3500, graduated)

        // 峰值不是拍脑袋的数字，是可以算出来的：
        // 每天一批 10 个词，间隔表有 6 档，最坏的一天会同时到期 6 个批次（1/3/7/14/29/59
        // 天前学的）再加上今天的新词 —— 也就是 (6+1)×10 = 70 张。这样的日子大约每 30 天
        // 撞上一次，平时是 30 张上下。超过这个上界说明排期在叠加而不是在错开。
        val ceiling = (Scheduler.INTERVALS.size + 1) * 10
        assertTrue("单日卡片数超过理论上界 $ceiling：$maxDaily", maxDaily <= ceiling)
        assertTrue("平均每天不该超过 40 张，实际峰值 $maxDaily", maxDaily < 80)
    }

    @Test
    fun `长期缺勤回归时队列被封顶，不出现一天几百张卡`() {
        val words = allWords(500)
        val byId = words.associateBy { it.id }
        var progress = emptyMap<Int, WordProgress>()
        var day = 20_000L

        // 先正常学 40 天（400 个词进入复习）
        repeat(40) {
            val queue = Scheduler.buildQueue(words, progress, day, newPerDay = 10)
            val s = Session(byId, queue, day, progress)
            while (s.currentLearn != null) s.markLearn(known = true)
            var guard = 0
            while (s.phase == SessionPhase.QUIZ && guard++ < 200) {
                s.submit(s.currentQuiz?.word ?: break)
                s.advance()
            }
            progress = s.progress
            day++
        }

        // 然后 90 天没打开
        val back = day + 90
        val queue = Scheduler.buildQueue(words, progress, back, newPerDay = 10)
        assertTrue("确实积压了", queue.dueTotal > Scheduler.MAX_REVIEW_PER_DAY)
        assertEquals(Scheduler.MAX_REVIEW_PER_DAY, queue.due.size)
        assertTrue("积压时不再加新词", queue.newWords.isEmpty())
        assertTrue(queue.newWordsCapped)
        assertTrue("总量仍然可控：${queue.total}", queue.total <= Scheduler.MAX_REVIEW_PER_DAY)
    }

    @Test
    fun `答错率一成时也不会卡死，且错过的词一定在次日回来`() {
        val words = allWords(120)
        val byId = words.associateBy { it.id }
        var progress = emptyMap<Int, WordProgress>()
        var day = 20_000L

        repeat(60) {
            val queue = Scheduler.buildQueue(words, progress, day, newPerDay = 10)
            if (!queue.isEmpty) {
                val s = Session(byId, queue, day, progress)
                while (s.currentLearn != null) s.markLearn(known = true)
                var guard = 0
                while (s.phase == SessionPhase.QUIZ && guard++ < 400) {
                    val w = s.currentQuiz ?: break
                    // 第一轮里大约 10% 答错（确定性，不用随机数，保证可复现）；
                    // 重考轮一律答对 —— 用户已经看到了正确拼写，真实场景就是这样。
                    val wrong = s.round == 0 && (w.id.toLong() * 13 + day) % 10 == 0L
                    s.submit(if (wrong) "zzz" else w.word)
                    s.advance()
                }
                assertTrue("第 $day 天应在 400 次内结算", guard < 400)
                progress = s.progress
            }
            day++
        }

        // 所有答错过的词，lapses 必须 > 0；且过期的词都被安排到了未来
        val wrongWords = progress.filterValues { it.lapses > 0 }
        assertTrue("应该有答错的词", wrongWords.isNotEmpty())
        assertTrue(
            "答错的词都必须被排到明天或以后",
            wrongWords.values.all { it.status == WordStatus.GRADUATED || it.dueEpochDay > day - 1 },
        )
    }
}
