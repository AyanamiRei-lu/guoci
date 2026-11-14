package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private val today = 20_000L

    private fun word(id: Int) = Word(id, id, "w$id", "p", "n.", "意思$id")

    private fun session(
        due: List<Word> = emptyList(),
        new: List<Word> = emptyList(),
        progress: Map<Int, WordProgress> = emptyMap(),
    ): Session {
        val all = (due + new).associateBy { it.id }
        return Session(all, DailyQueue(due, new), today, progress)
    }

    @Test
    fun `新词学完考对后进入第 0 档，当天完成`() {
        val w = word(1)
        val s = session(new = listOf(w))

        assertEquals(SessionPhase.LEARN, s.phase)
        s.markLearn(known = true)
        assertEquals(SessionPhase.QUIZ, s.phase)
        // 学习阶段就落 LEARNING：进程被杀掉后这个词明天还能被捞回来
        assertEquals(WordStatus.LEARNING, s.progress.getValue(1).status)

        val fb = s.submit("w1")
        assertNotNull(fb)
        assertTrue(fb!!.correct)
        s.advance()

        assertEquals(SessionPhase.DONE, s.phase)
        val p = s.progress.getValue(1)
        assertEquals(WordStatus.REVIEW, p.status)
        assertEquals(0, p.stage)
        assertEquals(today + 1, p.dueEpochDay)

        val record = s.dayRecord
        assertNotNull(record)
        assertEquals(1, record!!.newLearned)
        assertEquals(0, record.reviewed)
        assertEquals(1, record.firstTryCorrect)
        assertTrue(record.completed)
    }

    @Test
    fun `标不熟的词本轮再看一遍，但只追加一次`() {
        val a = word(1)
        val b = word(2)
        val s = session(new = listOf(a, b))

        s.markLearn(known = false)          // a 不熟 -> 追加到末尾
        assertEquals(3, s.learnList.size)
        assertEquals(b, s.currentLearn)

        s.markLearn(known = true)           // b
        assertEquals(a, s.currentLearn)     // 第二轮回到 a

        s.markLearn(known = false)          // a 再不熟，不重复追加
        assertEquals(3, s.learnList.size)
        assertEquals(SessionPhase.QUIZ, s.phase)
    }

    @Test
    fun `复习词答错降一档且明天再见，重考答对不重复排期`() {
        val w = word(1)
        val before = WordProgress(
            stage = 3, dueEpochDay = today - 1, status = WordStatus.REVIEW, streakCorrect = 2,
        )
        val s = session(due = listOf(w), progress = mapOf(1 to before))

        val wrong = s.submit("bad")!!
        assertFalse(wrong.correct)
        assertTrue(wrong.wasReview)

        var p = s.progress.getValue(1)
        assertEquals(2, p.stage)                 // 3 -> 2，不是清零
        assertEquals(today + 1, p.dueEpochDay)
        assertEquals(1, p.lapses)
        assertEquals(0, p.streakCorrect)

        s.advance()                              // 进入重考轮，只考错词
        assertEquals(1, s.round)
        assertEquals(listOf(w), s.quizList)

        val retry = s.submit("w1")!!
        assertTrue(retry.correct)
        p = s.progress.getValue(1)
        assertEquals(2, p.stage)                 // 重考不改写已定的排期
        assertEquals(today + 1, p.dueEpochDay)
        assertEquals(1, p.lapses)

        s.advance()
        assertEquals(SessionPhase.DONE, s.phase)
        assertEquals(0, s.dayRecord!!.firstTryCorrect)
    }

    @Test
    fun `新词答错时留在 LEARNING，重考答对后才排期且保留错误次数`() {
        val w = word(1)
        val s = session(new = listOf(w))
        s.markLearn(known = true)

        val wrong = s.submit("xx")!!
        assertFalse(wrong.correct)
        var p = s.progress.getValue(1)
        assertEquals(WordStatus.LEARNING, p.status)
        assertEquals(1, p.lapses)

        s.advance()
        assertEquals(1, s.round)

        s.submit("w1")!!
        p = s.progress.getValue(1)
        assertEquals(WordStatus.REVIEW, p.status)
        assertEquals(0, p.stage)
        assertEquals(today + 1, p.dueEpochDay)
        assertEquals(1, p.lapses)

        s.advance()
        assertEquals(SessionPhase.DONE, s.phase)
    }

    @Test
    fun `必须全对才算完成，重考轮只包含错词`() {
        val a = word(1)
        val b = word(2)
        val s = session(new = listOf(a, b))
        s.markLearn(true)
        s.markLearn(true)

        assertTrue(s.submit("w1")!!.correct)
        s.advance()
        assertFalse(s.submit("nope")!!.correct)
        s.advance()

        assertEquals(SessionPhase.QUIZ, s.phase)   // 还有错词，不给完成页
        assertEquals(listOf(b), s.quizList)
        assertEquals(1, s.round)

        assertTrue(s.submit("w2")!!.correct)
        s.advance()
        assertEquals(SessionPhase.DONE, s.phase)

        // firstTryCorrect 只算第一轮：a 一次过关，b 第二次才对
        assertEquals(1, s.dayRecord!!.firstTryCorrect)
        assertEquals(2, s.dayRecord!!.total)
    }

    @Test
    fun `反馈未确认时的重复提交被忽略`() {
        val w = word(1)
        val s = session(new = listOf(w))
        s.markLearn(true)

        s.submit("wrong")
        val again = s.submit("w1")!!            // 仍返回上一条反馈，不会把这次的"对"记进去
        assertFalse(again.correct)
        assertEquals(0, s.progress.getValue(1).stage)
    }

    @Test
    fun `降档后再次答对可以继续往毕业推进`() {
        val w = word(1)
        val before = WordProgress(stage = 5, dueEpochDay = today, status = WordStatus.REVIEW)
        val s = session(due = listOf(w), progress = mapOf(1 to before))
        s.submit("w1")!!
        val p = s.progress.getValue(1)
        assertEquals(WordStatus.GRADUATED, p.status)
        assertEquals(Scheduler.GRADUATED_STAGE, p.stage)
    }

    @Test
    fun `空队列直接算完成，不把用户停在空测验页`() {
        val s = session()
        assertEquals(SessionPhase.DONE, s.phase)
        assertNotNull(s.dayRecord)
        assertEquals(0, s.dayRecord!!.total)
        assertEquals(0, s.quizList.size)
    }

    @Test
    fun `没有待确认反馈时 advance 不推进`() {
        val w = word(1)
        val s = session(new = listOf(w))
        s.markLearn(true)
        s.advance()
        assertEquals(0, s.quizIndex)
        assertEquals(SessionPhase.QUIZ, s.phase)
        assertNull(s.feedback)
    }
}
