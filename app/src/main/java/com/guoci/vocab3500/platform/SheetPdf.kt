package com.guoci.vocab3500.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.guoci.core.SheetPlanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 默写单 PDF。
 *
 * 为什么自己画而不引 PDF 库：Android 自带 PdfDocument，标准库之外一行的依赖都不用；
 * 而这个版面简单到只有"标题 + 若干行题目 + 答案页"，用 iText 那种东西属于用吊车
 * 搬一箱书。
 *
 * 版面是 A4（595×842 pt），每页 21 行，行距 34pt —— 高中生写字大，行距再小会写不下。
 * 中文用系统默认字体（含中文回退字形），英文题面用衬线体，和试卷的样子接近。
 */
class SheetPdf(private val context: Context) {

    fun write(plan: SheetPlanner.Plan, sourceLabel: String): File {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateText = SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINA).format(Date())

        val rowsPerPage = 21
        val pages = if (plan.items.isEmpty()) 1 else (plan.items.size + rowsPerPage - 1) / rowsPerPage

        for (pageIndex in 0 until pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex + 1).create())
            val canvas = page.canvas
            drawHeader(canvas, paint, plan, sourceLabel, dateText)

            val slice = plan.items.drop(pageIndex * rowsPerPage).take(rowsPerPage)
            var y = HEADER_BOTTOM
            slice.forEachIndexed { i, item ->
                val number = pageIndex * rowsPerPage + i + 1
                drawRow(canvas, paint, number, item, y)
                y += ROW_H
            }
            drawFooter(canvas, paint, pageIndex + 1, pages)
            document.finishPage(page)
        }

        if (plan.includeAnswers && plan.items.isNotEmpty()) {
            drawAnswerPages(document, paint, plan, sourceLabel)
        }

        val dir = File(context.filesDir, "export").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
        val out = File(dir, "默写单-$stamp.pdf")
        out.outputStream().use { document.writeTo(it) }
        document.close()
        return out
    }

    private fun drawHeader(
        canvas: Canvas,
        paint: Paint,
        plan: SheetPlanner.Plan,
        sourceLabel: String,
        dateText: String,
    ) {
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        val kindLabel = when (plan.kind) {
            SheetPlanner.Kind.ZH_TO_EN -> "中文写英文"
            SheetPlanner.Kind.EN_TO_ZH -> "英文写中文"
        }
        canvas.drawText("${plan.title} · $kindLabel", MARGIN, 58f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10f
        paint.color = Color.rgb(90, 90, 90)
        canvas.drawText("$dateText    范围：$sourceLabel    共 ${plan.items.size} 题", MARGIN, 78f, paint)

        paint.color = Color.BLACK
        canvas.drawText("姓名：__________    得分：______", PAGE_W - MARGIN - 190f, 78f, paint)

        // 分隔线
        paint.strokeWidth = 1f
        paint.color = Color.rgb(160, 160, 160)
        canvas.drawLine(MARGIN, HEADER_BOTTOM - 18f, PAGE_W - MARGIN, HEADER_BOTTOM - 18f, paint)
    }

    private fun drawRow(canvas: Canvas, paint: Paint, number: Int, item: SheetPlanner.Item, y: Float) {
        paint.color = Color.rgb(60, 60, 60)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9f
        canvas.drawText("$number.", MARGIN, y, paint)

        paint.color = Color.BLACK
        paint.typeface = if (item.prompt.any { it.code in 0x4E00..0x9FFF }) Typeface.DEFAULT else Typeface.SERIF
        paint.textSize = 13f
        val maxWidth = PAGE_W - MARGIN * 2 - 24f
        canvas.drawText(ellipsize(item.prompt, paint, maxWidth), MARGIN + 22f, y, paint)

        if (item.hint.isNotEmpty()) {
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 10f
            paint.color = Color.rgb(140, 140, 140)
            canvas.drawText(item.hint, MARGIN + 22f, y + 14f, paint)
        }

        // 书写横线（虚线，打印出来比实线轻，不抢题面）
        paint.color = Color.rgb(190, 190, 190)
        paint.strokeWidth = 0.7f
        paint.pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 0f)
        canvas.drawLine(MARGIN + 22f, y + 24f, PAGE_W - MARGIN, y + 24f, paint)
        paint.pathEffect = null
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, page: Int, total: Int) {
        paint.color = Color.rgb(150, 150, 150)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9f
        canvas.drawText("过词 · 高中 3500", MARGIN, PAGE_H - 24f, paint)
        canvas.drawText("$page / $total", PAGE_W - MARGIN - 30f, PAGE_H - 24f, paint)
    }

    private fun drawAnswerPages(
        document: PdfDocument,
        paint: Paint,
        plan: SheetPlanner.Plan,
        sourceLabel: String,
    ) {
        val perPage = 30
        val pages = (plan.items.size + perPage - 1) / perPage
        for (p in 0 until pages) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1000 + p).create(),
            )
            val canvas = page.canvas
            paint.color = Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 18f
            canvas.drawText("答案 · $sourceLabel", MARGIN, 58f, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 11f
            var y = HEADER_BOTTOM
            val slice = plan.items.drop(p * perPage).take(perPage)
            slice.forEachIndexed { i, item ->
                val number = p * perPage + i + 1
                paint.color = Color.rgb(120, 120, 120)
                paint.textSize = 9f
                canvas.drawText("$number.", MARGIN, y, paint)
                paint.color = Color.BLACK
                paint.textSize = 11f
                canvas.drawText(
                    ellipsize(item.answer, paint, PAGE_W - MARGIN * 2 - 24f),
                    MARGIN + 22f,
                    y,
                    paint,
                )
                y += 26f
            }
            paint.color = Color.rgb(150, 150, 150)
            paint.textSize = 9f
            canvas.drawText("答案 ${p + 1} / $pages", PAGE_W - MARGIN - 60f, PAGE_H - 24f, paint)
            document.finishPage(page)
        }
    }

    /** 单行截断：默写单的题面不需要换行，超长就截断（释义在生成时已限长）。 */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    companion object {
        // A4 @ 72dpi
        private const val PAGE_W = 595
        private const val PAGE_H = 842
        private const val MARGIN = 46f
        private const val HEADER_BOTTOM = 118f
        private const val ROW_H = 34f
    }
}
