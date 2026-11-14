package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {

    @Test
    fun `大小写与首尾空白不影响判分`() {
        assertTrue(Grader.isCorrect("  Abandon ", "abandon"))
        assertTrue(Grader.isCorrect("CHINA", "China"))
    }

    @Test
    fun `空格与连字符差异判对`() {
        assertTrue(Grader.isCorrect("icecream", "ice cream"))
        assertTrue(Grader.isCorrect("ice-cream", "ice cream"))
        assertTrue(Grader.isCorrect("wellknown", "well-known"))
    }

    @Test
    fun `弯直引号与句末标点判对`() {
        assertTrue(Grader.isCorrect("o’clock", "o'clock"))
        assertTrue(Grader.isCorrect("abandon.", "abandon"))
        assertTrue(Grader.isCorrect("abandon。", "abandon"))
    }

    @Test
    fun `空答案判错`() {
        assertFalse(Grader.isCorrect("", "abandon"))
        assertFalse(Grader.isCorrect("   ", "abandon"))
    }

    @Test
    fun `拼错与词序错误判错`() {
        assertFalse(Grader.isCorrect("abandan", "abandon"))
        assertFalse(Grader.isCorrect("cream ice", "ice cream"))
        assertFalse(Grader.isCorrect("aband", "abandon"))
    }

    @Test
    fun `首字母提示只露出第一个字母`() {
        assertEquals("a······", Grader.hint("abandon", HintLevel.INITIAL))
        assertEquals("·······", Grader.hint("abandon", HintLevel.LENGTH))
        assertEquals("", Grader.hint("abandon", HintLevel.NONE))
    }

    @Test
    fun `多词短语每个词都给首字母`() {
        assertEquals("i·· c····", Grader.hint("ice cream", HintLevel.INITIAL))
        assertEquals("w···-·····", Grader.hint("well-known", HintLevel.INITIAL))
    }

    @Test
    fun `逐字母差异能标出中间拼错的那一位`() {
        val marks = Grader.markDiff("cat", "cot")
        assertEquals(3, marks.size)
        assertTrue(marks[0].matched)
        assertFalse(marks[1].matched)
        assertTrue(marks[2].matched)
        assertEquals(listOf('c', 'a', 't'), marks.map { it.char })
    }

    @Test
    fun `少打字母时后面的位全部标记为错`() {
        val marks = Grader.markDiff("cat", "ca")
        assertEquals(listOf(true, true, false), marks.map { it.matched })
    }

    @Test
    fun `差异标记对大小写不敏感`() {
        assertTrue(Grader.markDiff("China", "CHINA").all { it.matched })
    }
}
