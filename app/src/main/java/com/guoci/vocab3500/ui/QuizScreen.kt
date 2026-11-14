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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.vocab3500.data.AppState

/**
 * 拼写测验 —— 这个 App 唯一的客观关卡。
 *
 * 题面只给中文释义 + 音标 + 提示（首字母/长度），要求把英文拼出来。只有这一关全对，
 * 当天的完成页才会出现。自评"我记住了"不算数，因为那恰恰是"背了就忘"的来源：
 * 感觉记住了，实际写不出来。
 */
@Composable
fun QuizScreen(state: AppState, onExit: () -> Unit) {
    val word = state.currentQuiz() ?: return
    val feedback = state.feedback
    var answer by remember(state.round) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.quizIndex, state.round) {
        answer = ""
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(word.id) {
        // 换题时清空输入；同一轮内重考同一词也重置
        answer = ""
    }

    val total = state.quizList.size
    val index = (state.quizIndex + 1).coerceAtMost(total)
    val hinted = state.hintFor(word)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
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
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (word.pos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(word.pos, color = Muted, fontSize = 13.sp)
            }
            if (word.phonetic.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("/${word.phonetic}/", style = phoneticStyle())
            }
            if (hinted.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    hinted,
                    fontSize = 20.sp,
                    letterSpacing = 6.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            enabled = feedback == null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, letterSpacing = 1.sp),
            placeholder = { Text("在这里拼出这个单词", fontSize = 16.sp, color = Muted) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (feedback == null && answer.isNotBlank()) {
                    keyboard?.hide()
                    state.submitAnswer(answer)
                }
            }),
        )

        if (feedback != null) {
            Spacer(Modifier.height(14.dp))
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
                                withStyle(
                                    SpanStyle(color = if (mark.matched) Success else Danger),
                                ) { append(mark.char) }
                            }
                        },
                        fontFamily = WordFont,
                        fontSize = 26.sp,
                    )
                    if (!feedback.correct) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "你写的是：${feedback.typed.ifBlank { "（空）" }}",
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

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (feedback == null) {
                Button(
                    onClick = {
                        keyboard?.hide()
                        state.submitAnswer(answer)
                    },
                    enabled = answer.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("提交", fontSize = 17.sp)
                }
            } else {
                Button(
                    onClick = { state.advance() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("继续", fontSize = 17.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
