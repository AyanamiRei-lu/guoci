package com.guoci.vocab3500.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 视觉。
 *
 * 只有一个强调色（青绿），因为整个 App 只有一件事值得被强调："开始今天的词"。
 * 页面底色是暖白纸色而不是纯白，配深色模式；正文用系统中文字体（各厂商的中文字形
 * 质量都比任何内嵌字体好），英文单词单独用衬线体 —— 那是词典和试卷的样子，
 * 让人一眼知道"这是要背的那个词"。
 */

private val Paper = Color(0xFFFDFDF8)
private val Ink = Color(0xFF1E1B16)
private val Teal = Color(0xFF0F7A6B)
private val TealLight = Color(0xFF7FD8C6)
private val Night = Color(0xFF12100C)
private val NightSurface = Color(0xFF1C1913)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F0E8),
    onPrimaryContainer = Color(0xFF063E36),
    secondary = Color(0xFF5A5F52),
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EEE6),
    onSurfaceVariant = Color(0xFF57544C),
    outline = Color(0xFFB9B5A8),
    error = Color(0xFFC2452D),
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF06322B),
    primaryContainer = Color(0xFF0C4B42),
    onPrimaryContainer = Color(0xFFB8EFE1),
    secondary = Color(0xFFB9BEB0),
    onSecondary = Color(0xFF23271F),
    background = Night,
    onBackground = Color(0xFFEDEAE2),
    surface = NightSurface,
    onSurface = Color(0xFFEDEAE2),
    surfaceVariant = Color(0xFF26231B),
    onSurfaceVariant = Color(0xFFC7C3B8),
    outline = Color(0xFF77736A),
    error = Color(0xFFFF9B84),
    onError = Color(0xFF3B0A00),
)

@Composable
fun GuociTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}

/** 语义色：对/错/次要文字。深色下都提亮，保证对比度。 */
val Success: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF7FD8A6) else Color(0xFF2E7D5B)

val Danger: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF9B84) else Color(0xFFC2452D)

val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** 单词用的衬线体。系统都带 Noto Serif / Droid Serif，不需要内嵌字体。 */
val WordFont = FontFamily.Serif

/** 音标：等宽一点更像词典，用 SansSerif 也可以，这里靠字距拉开。 */
@Composable
fun phoneticStyle() = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 16.sp,
    color = Muted,
    letterSpacing = 0.5.sp,
)

/**
 * 细进度条。
 *
 * 手写而不是用 LinearProgressIndicator：Material3 的进度条 API 在小版本间改过签名
 * （progress 从 Float 变成 lambda），这个 App 不值得为一个 4dp 的条去赌版本。
 */
@Composable
fun ThinProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Int = 4,
) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(height.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(height.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(height.dp)),
        )
    }
}

/** 数字强调：完成页/首页的大数字用同一套字重。 */
@Composable
fun numberStyle(size: Int = 40) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground,
)
