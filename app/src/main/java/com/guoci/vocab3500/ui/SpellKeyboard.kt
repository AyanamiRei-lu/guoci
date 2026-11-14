package com.guoci.vocab3500.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 内置拼写键盘。
 *
 * 为什么不复用系统输入法
 *
 * 系统输入法会替你纠正拼写、联想整词、把刚打错的字母改成对的 —— 那样"拼写测验"
 * 测的是输入法的词库，不是你的记忆。这个键盘只有一个字母一个字母地敲，敲错了只有
 * 退格能改，没有任何联想。同时它也彻底解决了"长按粘贴"这条路径：界面里没有可聚焦
 * 的文本输入框，系统输入法根本不会被唤起。
 *
 * 布局就是 QWERTY 三排 + 退格 + 提交，键高 48dp（够大，单手握得住）；
 * 词库里全是纯字母（最长 15 个字母），所以不需要空格、连字符、大写键。
 */
@Composable
fun SpellKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    submitEnabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        KeyRow("qwertyuiop", onKey, horizontalPadding = 0.dp)
        Spacer(Modifier.height(6.dp))
        KeyRow("asdfghjkl", onKey, horizontalPadding = 16.dp)
        Spacer(Modifier.height(6.dp))
        KeyRow("zxcvbnm", onKey, horizontalPadding = 44.dp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyCap(
                label = "⌫",
                onClick = onBackspace,
                modifier = Modifier.weight(1.6f),
                emphasized = false,
                fontSize = 20,
            )
            KeyCap(
                label = "提交",
                onClick = onSubmit,
                modifier = Modifier.weight(2.4f),
                emphasized = true,
                enabled = submitEnabled,
                fontSize = 16,
            )
        }
    }
}

@Composable
private fun KeyRow(
    letters: String,
    onKey: (Char) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (ch in letters) {
            KeyCap(
                label = ch.toString(),
                onClick = { onKey(ch) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    fontSize: Int = 20,
) {
    val bg = when {
        emphasized && enabled -> MaterialTheme.colorScheme.primary
        emphasized -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        emphasized && enabled -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> Muted
    }
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = fontSize.sp,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 输入框的替代品：只显示已经敲出来的字母，光标是一条竖线。不可聚焦，因此不会唤起输入法。 */
@Composable
fun AnswerDisplay(answer: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (answer.isEmpty()) {
            Text("按下面的字母键拼写", color = Muted, fontSize = 15.sp)
        } else {
            // 长词（straightforward 有 15 个字母）在窄屏上会超出宽度，按长度缩字号。
            val size = if (answer.length > 11) 22.sp else 30.sp
            Text(
                answer,
                fontFamily = WordFont,
                fontSize = size,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .width(2.dp)
                    .height(size.value.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
