package com.guoci.vocab3500.data

import android.content.Context
import com.guoci.core.Word
import org.json.JSONObject

/**
 * 词库读取。
 *
 * 词库是随包只读的 assets/words.json（3500 条，由 tools/build-words.py 从 ECDICT
 * 生成）。打开一次、常驻内存：3500 个词条约 500 KB，比每次进测验都解析一遍 JSON
 * 划算得多，也不会出现"题库解析到一半"的中间状态。
 *
 * 解析失败必须抛出，不能吞掉 —— 词库坏掉时界面要明确告诉用户"词库读取失败"，
 * 而不是显示一个 0 词的首页让人以为 App 坏了。
 */
class WordRepo(private val context: Context) {

    @Volatile
    private var cache: List<Word>? = null

    fun all(): List<Word> = cache ?: synchronized(this) {
        cache ?: load().also { cache = it }
    }

    fun byId(id: Int): Word? = index()[id]

    private var idIndex: Map<Int, Word>? = null

    private fun index(): Map<Int, Word> = idIndex ?: synchronized(this) {
        idIndex ?: all().associateBy { it.id }.also { idIndex = it }
    }

    private fun load(): List<Word> {
        val text = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val arr = root.getJSONArray("words")
        val out = ArrayList<Word>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += Word(
                id = o.getInt("id"),
                rank = o.optInt("rank", o.getInt("id")),
                word = o.getString("word"),
                phonetic = o.optString("phonetic", ""),
                pos = o.optString("pos", ""),
                meaning = o.getString("meaning"),
            )
        }
        return out
    }

    companion object {
        const val ASSET = "words.json"
    }
}
