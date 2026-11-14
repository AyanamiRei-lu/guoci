package com.guoci.vocab3500.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.vocab3500.data.AppState

/**
 * 完成页。
 *
 * 这一屏的唯一职责是"给一个明确的结束感"：一个大勾、今天的结果、连续天数、
 * 再一个目标。没有"再学一组"、没有推荐、没有分享领奖励。用户在这里被放走，
 * 明天才会愿意再来 —— 这是"坚持不下来"最反直觉的解法。
 */
@Composable
fun DoneScreen(state: AppState, onHome: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    val record = state.todayRecord()
    val streak = state.streakDays()
    val goal = state.nextGoal()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(120.dp)
                .scale(scale.value)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✓",
                fontSize = 56.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("今天完成了", style = MaterialTheme.typography.headlineMedium)

        if (record != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (record.firstTryCorrect >= record.total && record.total > 0) {
                    "${record.total} 个词，全部一次过关"
                } else {
                    "${record.total} 个词 · 一次过关 ${record.firstTryCorrect}"
                },
                color = Muted,
                textAlign = TextAlign.Center,
            )
        }

        if (streak > 0) {
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Text("连续", color = Muted, fontSize = 14.sp)
                Text(
                    "$streak",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("天", color = Muted, fontSize = 14.sp)
            }
            val longest = state.longestStreak()
            if (longest > streak) {
                Spacer(Modifier.height(2.dp))
                Text("最长 $longest 天", color = Muted, fontSize = 12.sp)
            }
        }

        if (goal != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("下一个目标", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "再 ${goal.remaining} → ${goal.label}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "已解锁 ${state.progress.badges.size} 个徽章",
            color = Muted,
            fontSize = 12.sp,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("回到首页", fontSize = 17.sp)
        }
        Spacer(Modifier.height(28.dp))
    }
}
