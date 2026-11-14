package com.guoci.core

/**
 * 间隔复习表。
 *
 * 为什么是 1/2/4/7/15/30 而不是 SM-2 那套 ease factor
 *
 * 艾宾浩斯曲线的原始实验就是按这几个时间点测遗忘率的，用户对这个节奏有直觉；
 * 而 SM-2 的"难度系数乘以前一间隔"会让间隔变成 3、7、17、42 这种数字，学生在
 * 界面上看到"下次复习：17 天后"既不理解也不相信。这里用固定表，答对升一档，
 * 答错降一档，行为可解释、可预测，也就能被测试逐条覆盖。
 *
 * 为什么答错只降一档、而且明天就再见面
 *
 * 清零会制造"这个词永远学不完"的绝望感（这是背单词 App 最常见的劝退点）。降一档
 * 保留了"你已经有基础"的暗示，而 due=today+1 保证错过的词一定在明天出现 ——
 * 用户能感到"它记得我"，这比算法理论上的最优更重要。
 */
object Scheduler {

    /** 每一档对应的间隔天数；索引即 stage。 */
    val INTERVALS = intArrayOf(1, 2, 4, 7, 15, 30)

    /** stage 达到这个值即"毕业"，不再排期。 */
    const val GRADUATED_STAGE = 6

    /** 一天最多排多少张复习卡：超过就不再学新词，避免缺勤回来后一次性被 300 张卡劝退。 */
    const val MAX_REVIEW_PER_DAY = 120

    /** 新词通过当日测验后：进入第 0 档，明天再见。 */
    fun onNewWordPassed(p: WordProgress, today: Long): WordProgress = p.copy(
        stage = 0,
        dueEpochDay = today + INTERVALS[0],
        status = WordStatus.REVIEW,
        streakCorrect = p.streakCorrect + 1,
        lastSeenEpochDay = today,
    )

    /**
     * 复习词的结果。
     *
     * 只有"答对"会推进档位；答错降一档、明天到期、lapses+1、连对清零。
     * 已经毕业的词不会再被排期，因此这里不处理 GRADUATED 的输入。
     */
    fun onReviewAnswered(p: WordProgress, today: Long, correct: Boolean): WordProgress =
        if (correct) {
            val next = p.stage + 1
            if (next >= GRADUATED_STAGE) {
                p.copy(
                    stage = GRADUATED_STAGE,
                    // 毕业词不再需要到期日。用 Long.MAX_VALUE 而不是 0，是为了让任何
                    // "due <= today" 的筛选都不会再选中它。
                    dueEpochDay = Long.MAX_VALUE,
                    status = WordStatus.GRADUATED,
                    streakCorrect = p.streakCorrect + 1,
                    lastSeenEpochDay = today,
                )
            } else {
                p.copy(
                    stage = next,
                    dueEpochDay = today + INTERVALS[next],
                    status = WordStatus.REVIEW,
                    streakCorrect = p.streakCorrect + 1,
                    lastSeenEpochDay = today,
                )
            }
        } else {
            val back = (p.stage - 1).coerceAtLeast(0)
            p.copy(
                stage = back,
                dueEpochDay = today + 1,
                status = WordStatus.REVIEW,
                streakCorrect = 0,
                lapses = p.lapses + 1,
                lastSeenEpochDay = today,
            )
        }

    /** 当天的队列：到期的复习词（含降档后重新到期的）+ 没考完的遗留词 + 新词。 */
    fun buildQueue(
        all: List<Word>,
        progress: Map<Int, WordProgress>,
        today: Long,
        newPerDay: Int,
    ): DailyQueue {
        val dueAll = all.filter { w ->
            val p = progress[w.id]
            p != null && p.status == WordStatus.REVIEW && p.dueEpochDay <= today
        }.sortedWith(compareBy({ progress[it.id]?.dueEpochDay ?: 0L }, { it.rank }))

        // 学过但还没通过测验的词（例如学到一半 App 被系统杀掉）。它们既不是新词也
        // 没排到期，如果不在这里捞回来，这些词会永远卡在 LEARNING 里消失掉。
        val pending = all.filter { w ->
            (progress[w.id] ?: WordProgress()).status == WordStatus.LEARNING
        }.sortedBy { it.rank }

        val capped = dueAll.size > MAX_REVIEW_PER_DAY
        val due = if (capped) dueAll.take(MAX_REVIEW_PER_DAY) else dueAll

        // 缺勤回归保护：复习量已经超载时不再加新词，并在首页说明原因。
        val newBudget = if (capped) 0 else newPerDay.coerceIn(0, 50)
        val newWords = all.asSequence()
            .filter { (progress[it.id] ?: WordProgress()).status == WordStatus.NEW }
            .sortedBy { it.rank }
            .take(newBudget)
            .toList()

        return DailyQueue(
            due = due,
            newWords = newWords,
            pending = pending,
            dueTotal = dueAll.size,
            newWordsCapped = capped,
        )
    }
}

data class DailyQueue(
    val due: List<Word>,
    val newWords: List<Word>,
    val pending: List<Word> = emptyList(),
    val dueTotal: Int = 0,
    val newWordsCapped: Boolean = false,
) {
    /** 学习阶段要过一遍的词：新词 + 上次没考完的。 */
    val toLearn: List<Word> get() = newWords + pending

    /** 测验阶段要考的全部词。 */
    val toQuiz: List<Word> get() = due + pending + newWords

    val total: Int get() = toQuiz.size
    val isEmpty: Boolean get() = total == 0
}
