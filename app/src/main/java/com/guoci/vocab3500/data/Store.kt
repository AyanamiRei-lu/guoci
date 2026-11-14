package com.guoci.vocab3500.data

import android.content.Context
import com.guoci.core.DailyRecord
import com.guoci.core.HintLevel
import com.guoci.core.Progress
import com.guoci.core.StudySettings
import com.guoci.core.WordProgress
import com.guoci.core.WordStatus
import org.json.JSONObject
import java.io.File

/**
 * 进度的落盘与读取。
 *
 * 为什么是单个 JSON 文件而不是 Room/SQLite
 *
 * 这个 App 的数据量是"3500 个词条各一行状态"，峰值不过 200 KB；一次全量写盘只要
 * 几毫秒。为此引入 Room + KSP 换来的是：多两个构建插件、多一套 schema 迁移、以及
 * 一个只在 Release 包里才会出现的注解处理问题。这里选择手写 JSON，代价是 100 行
 * 序列化代码，收益是零依赖和"任何时刻文件里都是一个完整的状态"。
 *
 * 原子性：先写 progress.json.tmp，再 rename 覆盖。断电最坏情况是丢掉最后一次写入，
 * 绝不会留下半个 JSON —— 半个 JSON 会让 App 在下次启动时丢掉全部进度。
 */
class Store(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, NAME)

    /** 读取失败时把坏文件挪走并返回空进度，绝不让 App 起不来。 */
    fun load(): Progress {
        if (!file.exists()) return Progress()
        return try {
            parse(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (t: Throwable) {
            quarantine()
            Progress()
        }
    }

    fun save(progress: Progress) {
        val json = serialize(progress).toString()
        val tmp = File(appContext.filesDir, "$NAME.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            // 某些设备上 rename 跨目录会失败，退化为覆盖写。
            file.writeText(json, Charsets.UTF_8)
            tmp.delete()
        }
    }

    /** 导出备份：把当前进度原样写成可读 JSON，交给系统分享面板。 */
    fun exportSnapshot(progress: Progress): File {
        val dir = File(appContext.filesDir, "export").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.CHINA)
            .format(java.util.Date())
        val out = File(dir, "guoci-progress-$stamp.json")
        out.writeText(serialize(progress).toString(2), Charsets.UTF_8)
        return out
    }

    private fun quarantine() {
        try {
            val broken = File(appContext.filesDir, "$NAME.broken-${System.currentTimeMillis()}.json")
            file.renameTo(broken)
        } catch (_: Throwable) {
            file.delete()
        }
    }

    private fun serialize(p: Progress): JSONObject {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("createdEpochDay", p.createdEpochDay)

        val settings = JSONObject()
        settings.put("newPerDay", p.settings.newPerDay)
        settings.put("hintLevel", p.settings.hintLevel.name)
        settings.put("ttsEnabled", p.settings.ttsEnabled)
        settings.put("reminderEnabled", p.settings.reminderEnabled)
        settings.put("reminderHour", p.settings.reminderHour)
        settings.put("reminderMinute", p.settings.reminderMinute)
        root.put("settings", settings)

        val badges = org.json.JSONArray()
        p.badges.forEach { badges.put(it) }
        root.put("badges", badges)

        val words = JSONObject()
        for ((id, w) in p.words) {
            val o = JSONObject()
            o.put("stage", w.stage)
            o.put("due", w.dueEpochDay)
            o.put("lapses", w.lapses)
            o.put("streakCorrect", w.streakCorrect)
            o.put("lastSeen", w.lastSeenEpochDay)
            o.put("status", w.status.name)
            words.put(id.toString(), o)
        }
        root.put("words", words)

        val days = JSONObject()
        for ((day, r) in p.days) {
            val o = JSONObject()
            o.put("completed", r.completed)
            o.put("newLearned", r.newLearned)
            o.put("reviewed", r.reviewed)
            o.put("firstTry", r.firstTryCorrect)
            o.put("total", r.total)
            days.put(day.toString(), o)
        }
        root.put("days", days)
        return root
    }

    private fun parse(root: JSONObject): Progress {
        val settingsJson = root.optJSONObject("settings")
        val settings = if (settingsJson == null) StudySettings() else StudySettings(
            newPerDay = settingsJson.optInt("newPerDay", 10).coerceIn(1, 50),
            hintLevel = runCatching {
                HintLevel.valueOf(settingsJson.optString("hintLevel", HintLevel.INITIAL.name))
            }.getOrDefault(HintLevel.INITIAL),
            ttsEnabled = settingsJson.optBoolean("ttsEnabled", true),
            reminderEnabled = settingsJson.optBoolean("reminderEnabled", false),
            reminderHour = settingsJson.optInt("reminderHour", 20).coerceIn(0, 23),
            reminderMinute = settingsJson.optInt("reminderMinute", 0).coerceIn(0, 59),
        )

        val badgesJson = root.optJSONArray("badges")
        val badges = buildSet {
            if (badgesJson != null) for (i in 0 until badgesJson.length()) add(badgesJson.getString(i))
        }

        val wordsJson = root.optJSONObject("words")
        val words = if (wordsJson == null) emptyMap() else buildMap {
            for (key in wordsJson.keys()) {
                val o = wordsJson.getJSONObject(key)
                val id = key.toIntOrNull() ?: continue
                put(id, WordProgress(
                    stage = o.optInt("stage", 0).coerceIn(0, 6),
                    dueEpochDay = o.optLong("due", 0L),
                    lapses = o.optInt("lapses", 0),
                    streakCorrect = o.optInt("streakCorrect", 0),
                    lastSeenEpochDay = o.optLong("lastSeen", -1L),
                    status = runCatching {
                        WordStatus.valueOf(o.optString("status", WordStatus.NEW.name))
                    }.getOrDefault(WordStatus.NEW),
                ))
            }
        }

        val daysJson = root.optJSONObject("days")
        val days = if (daysJson == null) emptyMap() else buildMap {
            for (key in daysJson.keys()) {
                val day = key.toLongOrNull() ?: continue
                val o = daysJson.getJSONObject(key)
                put(day, DailyRecord(
                    completed = o.optBoolean("completed", false),
                    newLearned = o.optInt("newLearned", 0),
                    reviewed = o.optInt("reviewed", 0),
                    firstTryCorrect = o.optInt("firstTry", 0),
                    total = o.optInt("total", 0),
                ))
            }
        }

        return Progress(
            createdEpochDay = root.optLong("createdEpochDay", 0L),
            settings = settings,
            words = words,
            days = days,
            badges = badges,
        )
    }

    companion object {
        private const val NAME = "progress.json"
        private const val FORMAT_VERSION = 1
    }
}
