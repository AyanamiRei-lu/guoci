package com.guoci.vocab3500.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.core.WordStatus
import com.guoci.vocab3500.data.AppState
import kotlin.math.roundToInt

/**
 * 首页。
 *
 * 这一屏只有一件事可做。没有底部导航、没有四个 tab、没有"今日推荐"——
 * 一个背单词 App 让人第三天就卸载，通常不是因为功能少，而是因为打开它以后
 * 有太多可以点但不知道该点哪的东西。
 */
@Composable
fun HomeScreen(
    state: AppState,
    onStart: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val queue = remember(state.progress, state.words) { state.queueForToday() }
    val done = state.dayCompleted
    val streak = state.streakDays()
    val goal = state.nextGoal()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dateLabel(state.today), style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("设置", color = Muted) }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (done) {
                Text("✓", style = numberStyle(64), color = Success)
                Spacer(Modifier.height(8.dp))
                Text("今天完成了", style = MaterialTheme.typography.headlineMedium)
                val rec = state.todayRecord()
                if (rec != null) {
                    Spacer(Modifier.height(12.dp))
                    val praise = if (rec.firstTryCorrect >= rec.total && rec.total > 0) {
                        "${rec.total} 个词，全部一次过关"
                    } else {
                        "${rec.total} 个词 · 一次过关 ${rec.firstTryCorrect}"
                    }
                    Text(praise, color = Muted, textAlign = TextAlign.Center)
                }
                if (streak > 0) {
                    Spacer(Modifier.height(16.dp))
                    Text("连续 $streak 天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                goal?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("再 ${it.remaining} → ${it.label}", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text("明天：${tomorrowPreview(state)}", color = Muted, fontSize = 13.sp)
            } else if (queue.isEmpty) {
                Text("3500 个词都掌握了", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("可以去词库手动复习，或生成一张默写单。", color = Muted, textAlign = TextAlign.Center, fontSize = 13.sp)
            } else {
                Text("今天", style = MaterialTheme.typography.titleMedium, color = Muted)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${queue.newWords.size} 个新词 · ${queue.due.size} 个复习",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text("约 ${minutesFor(queue.total)} 分钟", color = Muted, fontSize = 13.sp)
                if (queue.newWordsCapped) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "复习积压了 ${queue.dueTotal} 个，今天先不学新词",
                        color = Danger,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (streak > 0) {
                    Spacer(Modifier.height(16.dp))
                    Text("连续 $streak 天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!done && !queue.isEmpty) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("开始今天的 ${queue.total} 个", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onOpenWords) {
                Text("词库 · 错词本 · 默写单", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

private fun minutesFor(total: Int): Int = (total * 0.2f).roundToInt().coerceAtLeast(1)

private fun dateLabel(epochDay: Long): String {
    val date = java.time.LocalDate.ofEpochDay(epochDay)
    val week = when (date.dayOfWeek.value) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"; 5 -> "周五"; 6 -> "周六"; else -> "周日"
    }
    return "${date.monthValue} 月 ${date.dayOfMonth} 日 · $week"
}

/** 明天大概有多少事要做：到期的复习词 + 每日新词。用来消除"明天会不会爆炸"的焦虑。 */
private fun tomorrowPreview(state: AppState): String {
    val tomorrow = state.today + 1
    val dueTomorrow = state.progress.words.values.count {
        it.status == WordStatus.REVIEW && it.dueEpochDay <= tomorrow
    }
    val newCount = state.progress.settings.newPerDay
    return "${dueTomorrow.coerceAtLeast(0)} 个复习 + $newCount 个新词"
}
