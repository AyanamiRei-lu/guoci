package com.guoci.core

/** 会话阶段：学 → 测 → 完成。 */
enum class SessionPhase { LEARN, QUIZ, DONE }

data class AnswerFeedback(
    val correct: Boolean,
    val expected: String,
    val typed: String,
    val marks: List<CharMark>,
    val word: Word,
    val wasReview: Boolean,
)

/**
 * 一天的学习会话。
 *
 * 为什么这段逻辑要放在 :core 而不是 Activity 里
 *
 * "背了忘"的解药是这个 App 里最难自证的部分：一个词到底在什么时候被判为记住、
 * 答错之后它被推到哪天、什么时候当天才算真的完成。这些规则如果散落在界面的点击
 * 回调里，就只能靠人在手机上点一遍来验证 —— 而人不会把"答错两次再答对"这种路径
 * 特意点一遍。放在这里，它可以在每次构建时被 JVM 上的测试穷举。
 *
 * 不变量（全部由测试守着）：
 *  1. 一个词在一次会话里最多排期一次（scheduled / lapsed 两个集合各管一边）。
 *  2. 答错不会零成本地混过去：复习词立刻降档到明天，新词留在 LEARNING 明天再考，
 *     并且都会被本轮末尾重考一遍，直到答对。
 *  3. 只有一轮考完、且没有任何错词时，当天才完成（[dayRecord] 非空）。
 *  4. firstTryCorrect 只统计第一轮答对的词 —— "一次过关"不能被重考次数美化。
 */
class Session(
    private val all: Map<Int, Word>,
    val queue: DailyQueue,
    val today: Long,
    initialProgress: Map<Int, WordProgress>,
) {

    var progress: Map<Int, WordProgress> = initialProgress
        private set

    var phase: SessionPhase =
        if (queue.toLearn.isEmpty()) SessionPhase.QUIZ else SessionPhase.LEARN
        private set

    var learnList: List<Word> = queue.toLearn
        private set
    var learnIndex: Int = 0
        private set

    var quizList: List<Word> = queue.toQuiz
        private set
    var quizIndex: Int = 0
        private set

    var round: Int = 0
        private set
    var firstTryCorrect: Int = 0
        private set
    var feedback: AnswerFeedback? = null
        private set

    /** 当天完成时的记录；未完成时为 null。 */
    var dayRecord: DailyRecord? = null
        private set

    private val wrongThisRound = linkedSetOf<Int>()
    private val scheduled = mutableSetOf<Int>()
    private val lapsed = mutableSetOf<Int>()
    private val markedUnknown = mutableSetOf<Int>()

    init {
        // 队列本来就是空的（例如 3500 词全部毕业）：直接算完成，不要停在测验页面上
        // 让用户对着一张不存在的题发呆。
        if (queue.toQuiz.isEmpty()) completeDay()
    }

    val currentLearn: Word? get() = learnList.getOrNull(learnIndex)
    val currentQuiz: Word? get() = quizList.getOrNull(quizIndex)
    val total: Int get() = queue.total

    private fun of(id: Int): WordProgress = progress[id] ?: WordProgress()

    private fun put(id: Int, p: WordProgress) {
        progress = progress + (id to p)
    }

    /** 学习阶段的"不熟 / 记住了"。只推进界面，不改排期。 */
    fun markLearn(known: Boolean) {
        if (phase != SessionPhase.LEARN) return
        val w = currentLearn ?: return
        val p = of(w.id)
        if (p.status == WordStatus.NEW) {
            put(w.id, p.copy(status = WordStatus.LEARNING, lastSeenEpochDay = today))
        }
        if (!known && markedUnknown.add(w.id)) {
            learnList = learnList + w // 不熟的词本轮再看一遍，但只加一次
        }
        learnIndex++
        if (learnIndex >= learnList.size) phase = SessionPhase.QUIZ
    }

    /** 测验阶段提交一次拼写。重复提交（反馈未确认）直接忽略。 */
    fun submit(text: String): AnswerFeedback? {
        if (phase != SessionPhase.QUIZ || feedback != null) return feedback
        val w = currentQuiz ?: return null
        val wasReview = of(w.id).status == WordStatus.REVIEW
        val correct = Grader.isCorrect(text, w.word)
        if (round == 0 && correct) firstTryCorrect++

        if (correct) {
            if (scheduled.add(w.id)) scheduleCorrect(w)
        } else {
            wrongThisRound += w.id
            scheduleWrong(w)
        }

        val fb = AnswerFeedback(
            correct = correct,
            expected = w.word,
            typed = text,
            marks = Grader.markDiff(w.word, text),
            word = w,
            wasReview = wasReview,
        )
        feedback = fb
        return fb
    }

    /** 确认反馈、进入下一题；一轮结束且全对时结算当天。 */
    fun advance() {
        if (phase != SessionPhase.QUIZ || feedback == null) return
        feedback = null
        quizIndex++
        if (quizIndex < quizList.size) return

        if (wrongThisRound.isEmpty()) {
            completeDay()
            return
        }
        quizList = wrongThisRound.mapNotNull { all[it] }
        quizIndex = 0
        wrongThisRound.clear()
        round++
    }

    private fun scheduleCorrect(w: Word) {
        val p = of(w.id)
        val np = if (p.status == WordStatus.REVIEW) {
            Scheduler.onReviewAnswered(p, today, correct = true)
        } else {
            Scheduler.onNewWordPassed(p, today)
        }
        put(w.id, np)
    }

    private fun scheduleWrong(w: Word) {
        val p = of(w.id)
        val np = when {
            p.status == WordStatus.REVIEW && scheduled.add(w.id) ->
                Scheduler.onReviewAnswered(p, today, correct = false)

            p.status != WordStatus.REVIEW && lapsed.add(w.id) ->
                p.copy(status = WordStatus.LEARNING, lastSeenEpochDay = today, lapses = p.lapses + 1)

            else -> return
        }
        put(w.id, np)
    }

    private fun completeDay() {
        val record = DailyRecord(
            completed = true,
            newLearned = queue.newWords.size,
            reviewed = queue.due.size,
            firstTryCorrect = firstTryCorrect,
            total = queue.total.coerceAtLeast(quizList.size),
        )
        dayRecord = record
        phase = SessionPhase.DONE
    }
}
