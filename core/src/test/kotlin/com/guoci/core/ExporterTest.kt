package com.guoci.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExporterTest {

    private fun row(
        word: String = "abandon",
        phonetic: String = "əˈbændən",
        pos: String = "v.",
        meaning: String = "放弃",
        lapses: Int = 2,
    ) = Exporter.Row(word, phonetic, pos, meaning, lapses, 20_001L)

    @Test
    fun `纯文本导出忽略空字段`() {
        val txt = Exporter.toTxt(listOf(row()))
        assertEquals("abandon  /əˈbændən/  v.  放弃  （错 2 次）\n", txt)
    }

    @Test
    fun `没有音标时不输出空斜杠`() {
        val txt = Exporter.toTxt(listOf(row(phonetic = "", pos = "")))
        assertTrue(txt.startsWith("abandon  放弃"))
        assertTrue(!txt.contains("//"))
    }

    @Test
    fun `空列表导出为空字符串`() {
        assertEquals("", Exporter.toTxt(emptyList()))
    }

    @Test
    fun `CSV 字段里出现逗号与引号要被正确转义`() {
        assertEquals("\"a,b\"", Exporter.escapeCsv("a,b"))
        assertEquals("\"he said \"\"hi\"\"\"", Exporter.escapeCsv("he said \"hi\""))
        assertEquals("plain", Exporter.escapeCsv("plain"))
        assertEquals("\"line1\nline2\"", Exporter.escapeCsv("line1\nline2"))
    }

    @Test
    fun `CSV 带 BOM 与表头，行尾是 CRLF`() {
        val csv = Exporter.toCsv(listOf(row(meaning = "放弃，抛弃")))
        assertTrue(csv.startsWith(Exporter.UTF8_BOM))
        assertTrue(csv.contains(Exporter.CSV_HEADER))
        assertTrue(csv.contains("\r\n"))
        // 中文逗号不是 CSV 分隔符，不该被加引号；这里断言内容原样落进字段
        assertTrue(csv.contains("，"))
    }

    @Test
    fun `CSV 可以在不带 BOM 时生成`() {
        val csv = Exporter.toCsv(listOf(row()), withBom = false)
        assertTrue(csv.startsWith("word,"))
    }

    @Test
    fun `CSV 行数与数据一致，空音标不写斜杠`() {
        val csv = Exporter.toCsv(listOf(row(), row(word = "ability", phonetic = "")))
        val lines = csv.trim().split("\r\n")
        assertEquals(3, lines.size)             // 表头 + 2 行
        assertTrue(lines[2].startsWith("ability,"))
        assertTrue(!lines[2].contains("//"))
    }
}
