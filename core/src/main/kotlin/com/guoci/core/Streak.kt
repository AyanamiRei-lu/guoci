package com.guoci.core

/**
 * 连续天数与徽章。
 *
 * 设计上只有一条硬规则：**断签不惩罚**。没有"你失去了 7 天连续"的弹窗，没有重置
 * 动画，当前天数从 0 重新数，但最长记录永远保留。背单词这件事上，羞辱只会换来卸载。
 *
 * 天数只在"当天队列 100% 过测验"时才算数（见 [DailyRecord.completed]），
 * 这样连续天数本身就代表"真的背好了多少天"，而不是"打开了多少次"。
 */
object Streak {

    /** 徽章定义：达到阈值即解锁，顺序即展示顺序。 */
    val STREAK_BADGES = intArrayOf(7, 14, 30, 60, 100)
    val WORD_BADGES = intArrayOf(100, 500, 1000, 2000, 3500)

    /**
     * 当前连续天数。
     *
     * 今天还没完成时从昨天往前数 —— 否则每天早上打开 App 都会看到"连续 0 天"，
     * 那是一种每天早上都被打击一次的设计。
     */
    fun current(days: Map<Long, DailyRecord>, today: Long): Int {
        var cursor = if (days[today]?.completed == true) today else today - 1
        var count = 0
        while (days[cursor]?.completed == true) {
            count++
            cursor--
            if (count > 100_000) break // 防御：损坏数据里的超长链不应把主线程卡死
        }
        return count
    }

    /** 历史最长连续。用于"最长 12 天"这种不催人的展示。 */
    fun longest(days: Map<Long, DailyRecord>): Int {
        val completed = days.filterValues { it.completed }.keys.sorted()
        if (completed.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until completed.size) {
            if (completed[i] == completed[i - 1] + 1) run++ else run = 1
            if (run > best) best = run
        }
        return best
    }

    fun earnedBadges(streak: Int, mastered: Int): Set<String> {
        val out = mutableSetOf<String>()
        for (b in STREAK_BADGES) if (streak >= b) out += badgeId("streak", b)
        for (b in WORD_BADGES) if (mastered >= b) out += badgeId("words", b)
        return out
    }

    fun badgeId(kind: String, threshold: Int): String = "$kind$threshold"

    fun badgeLabel(id: String): String {
        val kind = id.takeWhile { !it.isDigit() }
        val n = id.dropWhile { !it.isDigit() }.toIntOrNull() ?: 0
        return when (kind) {
            "streak" -> "连续 $n 天"
            "words" -> "掌握 $n 词"
            else -> id
        }
    }

    /**
     * 下一个目标。
     *
     * 只返回**一个**：完成页同时摆十个数字的结果是用户一个都不看。
     * 天数目标与词数目标各算一个"还差多少"，取相对更近的那个；这样刚开始背的前
     * 两周，用户看到的是"再 3 天拿 7 天徽章"，而不是"还差 3400 个词"。
     */
    fun nextGoal(streak: Int, mastered: Int): Goal? {
        val nextStreak = STREAK_BADGES.firstOrNull { it > streak }
        val nextWords = WORD_BADGES.firstOrNull { it > mastered }
        if (nextStreak == null && nextWords == null) return null

        val streakRemaining = nextStreak?.let { it - streak }
        val wordsRemaining = nextWords?.let { it - mastered }

        return when {
            streakRemaining != null && (wordsRemaining == null || streakRemaining <= wordsRemaining) ->
                Goal(badgeId("streak", nextStreak!!), "连续 $nextStreak 天", streakRemaining)
            wordsRemaining != null ->
                Goal(badgeId("words", nextWords!!), "掌握 $nextWords 词", wordsRemaining)
            else -> null
        }
    }

    data class Goal(val badgeId: String, val label: String, val remaining: Int)
}
