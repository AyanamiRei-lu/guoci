package com.guoci.core

import kotlin.random.Random

/**
 * 错词本与进度导出。
 *
 * 格式选择：
 *   - TXT：给人看的。一行一个词，粘到微信里不会被压成一坨。
 *   - CSV：给 Excel / Numbers 看的。UTF-8 带 BOM，否则在 Windows 版 Excel 里
 *     中文释义会变成乱码 —— 这是导出功能最常见的"看起来能用但其实不能用"。
 */
object Exporter {

    data class Row(
        val word: String,
        val phonetic: String,
        val pos: String,
        val meaning: String,
        val lapses: Int,
        val lastWrongEpochDay: Long,
    )

    const val CSV_HEADER = "word,phonetic,pos,meaning,lapses,last_wrong_day"
    const val UTF8_BOM = "\uFEFF"

    fun escapeCsv(field: String): String {
        val needsQuote = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = field.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    fun toCsv(rows: List<Row>, withBom: Boolean = true): String {
        val sb = StringBuilder()
        if (withBom) sb.append(UTF8_BOM)
        sb.append(CSV_HEADER).append("\r\n")
        for (r in rows) {
            sb.append(escapeCsv(r.word)).append(',')
                .append(escapeCsv(if (r.phonetic.isEmpty()) "" else "/${r.phonetic}/")).append(',')
                .append(escapeCsv(r.pos)).append(',')
                .append(escapeCsv(r.meaning)).append(',')
                .append(r.lapses).append(',')
                .append(if (r.lastWrongEpochDay < 0) "" else r.lastWrongEpochDay.toString())
                .append("\r\n")
        }
        return sb.toString()
    }

    fun toTxt(rows: List<Row>): String {
        if (rows.isEmpty()) return ""
        val sb = StringBuilder()
        for (r in rows) {
            sb.append(r.word)
            if (r.phonetic.isNotEmpty()) sb.append("  /").append(r.phonetic).append('/')
            if (r.pos.isNotEmpty()) sb.append("  ").append(r.pos)
            sb.append("  ").append(r.meaning)
            if (r.lapses > 0) sb.append("  （错 ").append(r.lapses).append(" 次）")
            sb.append('\n')
        }
        return sb.toString()
    }
}

/**
 * 默写单排版。
 *
 * 排的是"哪几个词、以什么顺序、题面是什么、答案是什么"，打印与 PDF 的像素细节
 * 留给 :app。这样"选词规则"这一层可以被测试穷举，而不用画一张 PDF 才能验证。
 */
object SheetPlanner {

    enum class Kind { ZH_TO_EN, EN_TO_ZH }

    data class Options(
        val kind: Kind = Kind.ZH_TO_EN,
        val count: Int = 20,
        val shuffle: Boolean = false,
        val seed: Long = 0L,
        val hintFirstLetter: Boolean = false,
        val includeAnswers: Boolean = true,
        val title: String = "默写单",
    )

    data class Item(val number: Int, val prompt: String, val hint: String, val answer: String)

    data class Plan(
        val title: String,
        val kind: Kind,
        val items: List<Item>,
        val includeAnswers: Boolean,
        val answers: List<String>,
    )

    fun plan(words: List<Word>, options: Options): Plan {
        val ordered = if (options.shuffle) {
            words.shuffled(Random(options.seed))
        } else {
            words.sortedBy { it.rank }
        }
        val picked = ordered.take(options.count.coerceAtLeast(0))

        val items = picked.mapIndexed { index, w ->
            val prompt = when (options.kind) {
                Kind.ZH_TO_EN -> w.meaning
                Kind.EN_TO_ZH -> w.word
            }
            val answer = when (options.kind) {
                Kind.ZH_TO_EN -> w.word
                Kind.EN_TO_ZH -> w.meaning
            }
            val hint = when {
                options.kind != Kind.ZH_TO_EN -> ""
                !options.hintFirstLetter -> ""
                else -> Grader.hint(w.word, HintLevel.INITIAL)
            }
            Item(number = index + 1, prompt = prompt, hint = hint, answer = answer)
        }

        return Plan(
            title = options.title,
            kind = options.kind,
            items = items,
            includeAnswers = options.includeAnswers,
            answers = items.map { it.answer },
        )
    }
}
