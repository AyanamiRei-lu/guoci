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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.core.AnswerFeedback
import com.guoci.vocab3500.data.AppState

/** 最长单词 15 个字母（straightforward），留一点余量但不允许无限输入。 */
private const val MAX_ANSWER = 20

/**
 * 拼写测验 —— 这个 App 唯一的客观关卡。
 *
 * 题面只给中文释义 + 音标 + 提示（首字母/长度），要求把英文拼出来。
 *
 * 输入用[内置键盘][SpellKeyboard]，不用系统输入法：系统输入法会纠错、联想、补全，
 * 那样测出来的是输入法的词库而不是用户的记忆。内置键盘没有联想、没有粘贴、没有
 * 自动改正，敲错只能退格。
 */
@Composable
fun QuizScreen(state: AppState, onExit: () -> Unit) {
    val word = state.currentQuiz() ?: return
    val feedback = state.feedback
    var answer by remember(state.round) { mutableStateOf("") }

    LaunchedEffect(state.quizIndex, state.round) {
        answer = ""
    }

    val total = state.quizList.size
    val index = (state.quizIndex + 1).coerceAtMost(total)
    val hinted = state.hintFor(word)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("测验 $index / $total", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("退出", color = Muted) }
        }
        Spacer(Modifier.height(8.dp))
        ThinProgress(index.toFloat() / total.coerceAtLeast(1))

        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                word.meaning,
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                // 最长的释义接近 60 字，不限制行数会在小屏上把键盘挤出屏幕。
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (word.pos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(word.pos, color = Muted, fontSize = 13.sp)
            }
            if (word.phonetic.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("/${word.phonetic}/", style = phoneticStyle())
            }
            if (hinted.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    hinted,
                    fontSize = 18.sp,
                    letterSpacing = 5.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (feedback == null) {
            AnswerDisplay(answer)
            Spacer(Modifier.height(16.dp))
            SpellKeyboard(
                onKey = { ch -> if (answer.length < MAX_ANSWER) answer += ch },
                onBackspace = { if (answer.isNotEmpty()) answer = answer.dropLast(1) },
                onSubmit = { if (answer.isNotBlank()) state.submitAnswer(answer) },
                submitEnabled = answer.isNotBlank(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "内置键盘：不联想、不纠错、不经过系统输入法",
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        } else {
            FeedbackCard(feedback)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { state.advance() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("继续", fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FeedbackCard(feedback: AnswerFeedback) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (feedback.correct) "拼对了" else "正确的拼写",
                color = if (feedback.correct) Success else Danger,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    feedback.marks.forEach { mark ->
                        withStyle(SpanStyle(color = if (mark.matched) Success else Danger)) {
                            append(mark.char)
                        }
                    }
                },
                fontFamily = WordFont,
                fontSize = 26.sp,
            )
            if (!feedback.correct) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "你拼的是：${feedback.typed.ifBlank { "（空）" }}",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (feedback.wasReview) "这个词明天还会再考你一次" else "这个词稍后会在本轮再考一次",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
