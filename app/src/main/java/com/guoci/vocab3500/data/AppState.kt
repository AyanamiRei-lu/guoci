package com.guoci.vocab3500.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.guoci.core.AnswerFeedback
import com.guoci.core.DailyQueue
import com.guoci.core.DailyRecord
import com.guoci.core.Exporter
import com.guoci.core.HintLevel
import com.guoci.core.Progress
import com.guoci.core.Scheduler
import com.guoci.core.Session
import com.guoci.core.SessionPhase
import com.guoci.core.SheetPlanner
import com.guoci.core.Streak
import com.guoci.core.StudySettings
import com.guoci.core.Word
import com.guoci.core.WordProgress
import com.guoci.core.WordStatus
import com.guoci.vocab3500.platform.SheetPdf
import java.time.LocalDate

enum class Phase { HOME, LEARN, QUIZ, DONE, WORDS, SETTINGS }

/** 默写单的取词范围。 */
enum class SheetSource(val label: String) {
    TODAY("今天的词"),
    WRONG("错词本"),
    STUDIED("学过的词"),
    ALL("全部词库"),
}

/**
 * 全 App 的状态与动作 —— 一层很薄的壳。
 *
 * 真正的判定逻辑（什么时候算学会、答错推到哪天、当天何时才算完成）在 :core 的
 * [Session] 里，那里有 JVM 单测覆盖。这里只做三件事：
 *   1. 把 Session 的结果搬进 Compose 的可观察状态；
 *   2. 每次变化后落盘（[Store] 原子写）；
 *   3. 提供界面需要的派生数据与设置项。
 *
 * 为什么不用 ViewModel：需要活过 Activity 重建的是"进度"，而进度每次作答后都已落盘。
 * 为了会话中间态再引一个 lifecycle 依赖不划算；旋转屏幕重启一次会话的代价可以接受。
 */
class AppState(context: Context) {

    private val appContext = context.applicationContext
    private val repo = WordRepo(context)
    private val store = Store(context)

    /** 整个会话共用一个"今天"，跨过 0 点也不改 —— 否则一次会话会把两天混在一起。 */
    val today: Long = LocalDate.now().toEpochDay()

    var words by mutableStateOf<List<Word>>(emptyList())
        private set
    private var index: Map<Int, Word> = emptyMap()

    var progress by mutableStateOf(Progress())
        private set
    var phase by mutableStateOf(Phase.HOME)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    private var session: Session? = null

    // ---- 会话的镜像状态（界面只读这些） ----
    var queue by mutableStateOf(DailyQueue(emptyList(), emptyList()))
        private set
    var learnList by mutableStateOf<List<Word>>(emptyList())
        private set
    var learnIndex by mutableStateOf(0)
        private set
    var quizList by mutableStateOf<List<Word>>(emptyList())
        private set
    var quizIndex by mutableStateOf(0)
        private set
    var round by mutableStateOf(0)
        private set
    var firstTryCorrect by mutableStateOf(0)
        private set
    var feedback by mutableStateOf<AnswerFeedback?>(null)
        private set

    fun load() {
        try {
            val all = repo.all()
            words = all
            index = all.associateBy { it.id }
            val loaded = store.load()
            progress = if (loaded.createdEpochDay == 0L) loaded.copy(createdEpochDay = today) else loaded
        } catch (t: Throwable) {
            loadError = "词库读取失败：${t.message ?: t.javaClass.simpleName}"
        }
    }

    // ---- 派生信息 ----

    val dayCompleted: Boolean get() = progress.days[today]?.completed == true

    fun queueForToday(): DailyQueue =
        Scheduler.buildQueue(words, progress.words, today, progress.settings.newPerDay)

    fun streakDays(): Int = Streak.current(progress.days, today)

    fun longestStreak(): Int = Streak.longest(progress.days)

    fun masteredCount(): Int = progress.graduatedCount()

    fun nextGoal(): Streak.Goal? = Streak.nextGoal(streakDays(), masteredCount())

    fun currentLearn(): Word? = learnList.getOrNull(learnIndex)

    fun currentQuiz(): Word? = quizList.getOrNull(quizIndex)

    fun hintFor(word: Word): String = com.guoci.core.Grader.hint(word.word, progress.settings.hintLevel)

    fun todayRecord(): DailyRecord? = progress.days[today]

    fun recentBadges(): List<String> = progress.badges.sorted()

    // ---- 导航 ----

    fun open(target: Phase) {
        phase = target
    }

    fun backHome() {
        phase = Phase.HOME
    }

    // ---- 一天的会话 ----

    fun startToday() {
        val q = queueForToday()
        queue = q
        session = Session(index, q, today, progress.words)
        syncSession()
    }

    fun markLearning(known: Boolean) {
        session?.markLearn(known)
        syncSession()
    }

    fun submitAnswer(text: String) {
        session?.submit(text)
        syncSession()
    }

    fun advance() {
        session?.advance()
        syncSession()
    }

    /**
     * 把 Session 的结果搬进界面状态并落盘。
     *
     * 落盘放在这里而不是"退出时"：用户可能在测验中途被系统杀掉进程，
     * 已排期的结果必须已经写进文件，否则那些词会原地复活。
     */
    private fun syncSession() {
        val s = session ?: return
        progress = progress.copy(words = s.progress)
        learnList = s.learnList
        learnIndex = s.learnIndex
        quizList = s.quizList
        quizIndex = s.quizIndex
        round = s.round
        firstTryCorrect = s.firstTryCorrect
        feedback = s.feedback

        val record = s.dayRecord
        if (s.phase == SessionPhase.DONE && record != null && progress.days[today]?.completed != true) {
            val days = progress.days + (today to record)
            // 连续天数必须用"包含今天"的新 map 重算，否则完成当天不算进连续天数。
            val streak = Streak.current(days, today)
            val badges = progress.badges + Streak.earnedBadges(streak, progress.graduatedCount())
            progress = progress.copy(days = days, badges = badges)
        }
        store.save(progress)

        phase = when (s.phase) {
            SessionPhase.LEARN -> Phase.LEARN
            SessionPhase.QUIZ -> Phase.QUIZ
            SessionPhase.DONE -> Phase.DONE
        }
    }

    // ---- 设置 ----

    fun updateSettings(transform: (StudySettings) -> StudySettings) {
        progress = progress.copy(settings = transform(progress.settings))
        store.save(progress)
    }

    fun setNewPerDay(n: Int) = updateSettings { it.copy(newPerDay = n.coerceIn(1, 50)) }
    fun setHintLevel(level: HintLevel) = updateSettings { it.copy(hintLevel = level) }
    fun setTts(enabled: Boolean) = updateSettings { it.copy(ttsEnabled = enabled) }
    fun setReminder(enabled: Boolean) = updateSettings { it.copy(reminderEnabled = enabled) }
    fun setReminderTime(hour: Int, minute: Int) =
        updateSettings { it.copy(reminderHour = hour, reminderMinute = minute) }

    fun resetProgress() {
        progress = Progress(createdEpochDay = today, settings = progress.settings)
        session = null
        store.save(progress)
        phase = Phase.HOME
    }

    // ---- 导出 ----

    fun wordById(id: Int): Word? = index[id]

    fun studiedWords(): List<Word> =
        words.filter { (progress.words[it.id]?.status ?: WordStatus.NEW) != WordStatus.NEW }

    fun wrongWords(): List<Word> = studiedWords()
        .filter { (progress.words[it.id]?.lapses ?: 0) > 0 }
        .sortedByDescending { progress.words[it.id]?.lapses ?: 0 }

    fun wordsFor(source: SheetSource): List<Word> = when (source) {
        // 还没开始今天的学习时 queue 是空的，但"今天的词"应该仍然可用：按当前进度
        // 现算一份，否则用户会看到"这个范围里还没有词"这种莫名其妙的拒绝。
        SheetSource.TODAY -> queue.toQuiz.ifEmpty { queueForToday().toQuiz }
        SheetSource.WRONG -> wrongWords()
        SheetSource.STUDIED -> studiedWords()
        SheetSource.ALL -> words
    }

    private fun rowsFor(list: List<Word>): List<Exporter.Row> = list.map { w ->
        val p: WordProgress = progress.of(w.id)
        Exporter.Row(
            word = w.word,
            phonetic = w.phonetic,
            pos = w.pos,
            meaning = w.meaning,
            lapses = p.lapses,
            lastWrongEpochDay = if (p.lapses > 0) p.lastSeenEpochDay else -1L,
        )
    }

    fun wrongBookCsv(): String = Exporter.toCsv(rowsFor(wrongWords()))
    fun wrongBookTxt(): String = Exporter.toTxt(rowsFor(wrongWords()))

    /** 写一个导出文件到 filesDir/export（FileProvider 暴露的就是这个目录）。 */
    fun writeExportFile(name: String, content: String): java.io.File {
        val dir = java.io.File(appContext.filesDir, "export").apply { mkdirs() }
        val file = java.io.File(dir, name)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /** 生成默写单 PDF。返回文件与排版结果，界面可以顺手显示"共 N 题"。 */
    fun makeSheet(source: SheetSource, options: SheetPlanner.Options): Pair<java.io.File, SheetPlanner.Plan> {
        val list = wordsFor(source)
        val plan = SheetPlanner.plan(list, options)
        val file = SheetPdf(appContext).write(plan, source.label)
        return file to plan
    }

    fun exportProgress(): java.io.File = store.exportSnapshot(progress)
}
