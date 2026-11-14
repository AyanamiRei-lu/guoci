package com.guoci.vocab3500.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.vocab3500.data.AppState

/**
 * 学习卡（只过新词与上次没考完的词）。
 *
 * 一张卡一次只给一个词：正面英文 + 音标，点一下翻面看中文。
 * "不熟"不会立刻改排期 —— 排期只由后面的拼写测验决定；它只是把这词放回本轮末尾
 * 再看一眼。用户因此不会觉得"我随手点一下就把自己坑了"。
 */
@Composable
fun StudyScreen(
    state: AppState,
    onExit: () -> Unit,
    onSpeak: (String) -> Unit,
    canSpeak: Boolean,
) {
    val word = state.currentLearn() ?: return
    var flipped by remember(word.id) { mutableStateOf(false) }
    val total = state.learnList.size
    val index = (state.learnIndex + 1).coerceAtMost(total)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("学习 $index / $total", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("退出", color = Muted) }
        }
        Spacer(Modifier.height(8.dp))
        ThinProgress(index.toFloat() / total.coerceAtLeast(1))

        Spacer(Modifier.weight(1f))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clickable { flipped = !flipped },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!flipped) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            word.word,
                            fontFamily = WordFont,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (word.phonetic.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("/${word.phonetic}/", style = phoneticStyle())
                        }
                        if (canSpeak) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { onSpeak(word.word) }) {
                                Text("听读音", fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        if (word.pos.isNotEmpty()) {
                            Text(word.pos, color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            word.meaning,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (flipped) "记住了就往下，没记住也往下 —— 测验会再考它" else "点卡片翻面",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { state.markLearning(known = false) },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("不熟", fontSize = 16.sp)
            }
            Button(
                onClick = { state.markLearning(known = true) },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("记住了", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
