package com.guoci.core

/**
 * 拼写判分。
 *
 * 这个 App 的"真的背好了"完全靠这一个对象的判定，所以规则必须写死、可测、
 * 并且对用户是宽容的：拼写测验考的是"会不会这个词"，不是"能不能精确复现音标符号"。
 *
 * 判对规则（全部忽略）：
 *   - 首尾空白、连续空格
 *   - 大小写（China / china 都算对；高中生不该因为首字母大小写被扣分）
 *   - 词内空格与连字符（ice cream / icecream / ice-cream 都算对）
 *   - 弯引号与直引号（o’clock / o'clock）
 *   - 句末标点
 *
 * 判错规则：
 *   - 多词短语的词序错误（"cream ice" ≠ "ice cream"）
 *   - 空答案
 */
object Grader {

    private val APOSTROPHES = charArrayOf('\u2019', '\u2018', '\u02BC', '`')
    private val TRAILING_PUNCT = setOf('.', '。', ',', '，', '!', '！', '?', '？', ';', '；', ':', '：')

    /** 归一化到可比较形式。对中文答案同样适用（去掉空白与标点）。 */
    fun normalize(input: String): String {
        var s = input.trim()
        for (a in APOSTROPHES) s = s.replace(a, '\'')
        while (s.isNotEmpty() && s.last() in TRAILING_PUNCT) s = s.dropLast(1)
        return s
            .replace(" ", "")
            .replace("-", "")
            .replace("\t", "")
            .lowercase()
    }

    fun isCorrect(answer: String, expected: String): Boolean {
        val a = normalize(answer)
        return a.isNotEmpty() && a == normalize(expected)
    }

    /**
     * 提示串。
     *
     * INITIAL 给每个词的首字母，其余位置用 "·"；LENGTH 只给长度；NONE 什么都不给。
     * 首字母之所以是默认档：对高中生"知道它从哪个字母开始"往往就足以唤起整词，
     * 但它不足以让人靠猜过关 —— 长度信息不提供任何可猜的字母。
     */
    fun hint(expected: String, level: HintLevel): String {
        if (level == HintLevel.NONE) return ""
        val words = expected.split(" ").filter { it.isNotBlank() }
        return words.joinToString(" ") { w ->
            val letters = w.map { if (it == '-') '-' else '·' }
            if (level == HintLevel.INITIAL) {
                val firstLetterIndex = w.indexOfFirst { it.isLetter() }
                if (firstLetterIndex >= 0) {
                    letters.toMutableList().also { it[firstLetterIndex] = w[firstLetterIndex] }.joinToString("")
                } else letters.joinToString("")
            } else {
                letters.joinToString("")
            }
        }
    }

    /**
     * 逐字母对错，用于"拼错了"之后把差异标出来。
     *
     * 比较时忽略空格/连字符/大小写（与判分规则一致），返回的是 expected 的字母序列，
     * 每一位标出用户在对应位置是否拼对 —— 少了字母时后面全部标错，这正是学生需要看到的。
     */
    fun markDiff(expected: String, answer: String): List<CharMark> {
        val expectedLetters = expected.filter { it != ' ' && it != '-' }
        val answerLetters = normalize(answer)
        return expectedLetters.mapIndexed { i, ch ->
            val matched = i < answerLetters.length && answerLetters[i] == ch.lowercaseChar()
            CharMark(ch, matched)
        }
    }
}

data class CharMark(val char: Char, val matched: Boolean)
