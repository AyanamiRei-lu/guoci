package com.guoci.core

/**
 * 一个词条的不可变数据。
 *
 * 这里不出现任何 Android 类型，也不出现 org.json —— 这个文件的全部内容都要能在
 * JVM 上被单测直接构造和断言。
 */
data class Word(
    val id: Int,
    val rank: Int,
    val word: String,
    val phonetic: String,
    val pos: String,
    val meaning: String,
)

/**
 * 一个词的记忆状态。
 *
 * stage 是"当前处于间隔表的第几档"，而不是"复习了几次"。分开表达是因为答错会降档，
 * 升降都不该影响"这个词一共错过几次"（lapses），后者是错词本的唯一依据。
 *
 * dueEpochDay 用 LocalDate.toEpochDay() 而不是毫秒时间戳：这个 App 的一切判断都以
 * "哪一天"为单位，用毫秒会让"晚上 23:59 学到 00:01"变成两次到期。
 */
data class WordProgress(
    val stage: Int = 0,
    val dueEpochDay: Long = 0L,
    val lapses: Int = 0,
    val streakCorrect: Int = 0,
    val lastSeenEpochDay: Long = -1L,
    val status: WordStatus = WordStatus.NEW,
)

enum class WordStatus { NEW, LEARNING, REVIEW, GRADUATED }

/** 一天的结果。completed 只有在当日队列 100% 通过拼写测验后才为 true。 */
data class DailyRecord(
    val completed: Boolean = false,
    val newLearned: Int = 0,
    val reviewed: Int = 0,
    val firstTryCorrect: Int = 0,
    val total: Int = 0,
)

/** 提示强度：默认给首字母和长度，够用又不至于直接送答案。 */
enum class HintLevel { INITIAL, LENGTH, NONE }

data class StudySettings(
    val newPerDay: Int = 10,
    val hintLevel: HintLevel = HintLevel.INITIAL,
    val ttsEnabled: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
)

/**
 * 全部持久化状态。
 *
 * words 用懒初始化：3500 个词不必在第一天就写 3500 条记录，没出现的 id 就是 NEW。
 */
data class Progress(
    val createdEpochDay: Long = 0L,
    val settings: StudySettings = StudySettings(),
    val words: Map<Int, WordProgress> = emptyMap(),
    val days: Map<Long, DailyRecord> = emptyMap(),
    val badges: Set<String> = emptySet(),
) {
    fun of(wordId: Int): WordProgress = words[wordId] ?: WordProgress()

    fun with(wordId: Int, p: WordProgress): Progress = copy(words = words + (wordId to p))

    fun graduatedCount(): Int = words.values.count { it.status == WordStatus.GRADUATED }

    fun learningCount(): Int = words.values.count { it.status == WordStatus.LEARNING }

    fun reviewCount(): Int = words.values.count { it.status == WordStatus.REVIEW }

    fun wrongCount(): Int = words.values.count { it.lapses > 0 && it.status != WordStatus.GRADUATED }
}
