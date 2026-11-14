package com.guoci.vocab3500.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guoci.core.HintLevel
import com.guoci.vocab3500.data.AppState
import com.guoci.vocab3500.data.SheetSource
import com.guoci.vocab3500.platform.ReminderScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置。
 *
 * 只有两类东西：会影响"每天多少事"的（新词数、提醒），以及"把东西拿出去"的
 * （默写单、导出）。提示难度放在这里是因为它改变的是测验的难易，而不是界面。
 */
@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
    onShareFile: (File, String, String) -> Unit,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scheduler = remember { ReminderScheduler(ctx) }
    var showSheet by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) onToast("没有通知权限，提醒不会弹出来")
    }

    val settings = state.progress.settings

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回", color = Muted) }
            Spacer(Modifier.weight(1f))
            Text("设置", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionTitle("每天背多少")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每天新词", fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.setNewPerDay(settings.newPerDay - 5) }) {
                    Text("－", fontSize = 20.sp)
                }
                Text("${settings.newPerDay}", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                TextButton(onClick = { state.setNewPerDay(settings.newPerDay + 5) }) {
                    Text("＋", fontSize = 20.sp)
                }
            }
            Text(
                "3500 词按每天 ${settings.newPerDay} 个，约 ${(3500 + settings.newPerDay - 1) / settings.newPerDay} 天背完；加上复习，每天大约 5~8 分钟。",
                color = Muted,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(22.dp))
            SectionTitle("测验提示")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("首字母", settings.hintLevel == HintLevel.INITIAL) {
                    state.setHintLevel(HintLevel.INITIAL)
                }
                ToggleChip("只给长度", settings.hintLevel == HintLevel.LENGTH) {
                    state.setHintLevel(HintLevel.LENGTH)
                }
                ToggleChip("无提示", settings.hintLevel == HintLevel.NONE) {
                    state.setHintLevel(HintLevel.NONE)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("提示越少，测验越难，也越接近默写的真实难度。", color = Muted, fontSize = 12.sp)

            Spacer(Modifier.height(22.dp))
            SectionTitle("提醒与声音")
            SwitchRow("单词发音", settings.ttsEnabled) { state.setTts(it) }
            SwitchRow("每天提醒我", settings.reminderEnabled) { on ->
                state.setReminder(on)
                if (on) {
                    scheduler.schedule(settings.reminderHour, settings.reminderMinute)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    scheduler.cancel()
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        TimePickerDialog(
                            ctx,
                            { _, h, m ->
                                state.setReminderTime(h, m)
                                if (settings.reminderEnabled) scheduler.schedule(h, m)
                            },
                            settings.reminderHour,
                            settings.reminderMinute,
                            true,
                        ).show()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("提醒时间", fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                    color = Muted,
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("拿出去")
            ActionRow("生成默写单", "可打印的 A4 默写单 PDF") { showSheet = true }
            ActionRow("导出错词本", "CSV / TXT / 复制到剪贴板") { showExport = true }
            ActionRow("导出进度备份", "一个 JSON，换手机时可以留着") {
                runCatching { state.exportProgress() }
                    .onSuccess { onShareFile(it, "application/json", "过词进度备份") }
                    .onFailure { onToast("导出失败") }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("数据")
            ActionRow("重置全部进度", "词库不动，清空所有记忆记录", danger = true) {
                showReset = true
            }

            Spacer(Modifier.height(26.dp))
            SectionTitle("关于")
            Text("过词 · 高中 3500", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "词库 3500 条（高考 gk 标记），来源 ECDICT（MIT 许可）。" +
                    "学习顺序按词性加权：先学名词、动词、形容词和较复杂的介词（per/upon/beyond 等），" +
                    "the/a/and 这类小学虚词与最常用的基础词排在最后。" +
                    "间隔复习为 1/2/4/7/15/30 天，答错降一档、明天再见。" +
                    "完全离线：不联网、没有账号、不收集任何数据，进度只存在这台手机里。",
                color = Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text("版本 1.2.0", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showSheet) {
        SheetOptionsDialog(onDismiss = { showSheet = false }) { source, options ->
            showSheet = false
            if (state.wordsFor(source).isEmpty()) {
                onToast(if (source == SheetSource.TODAY) "今天还没有词" else "这个范围里还没有词")
            } else {
                runCatching { state.makeSheet(source, options) }
                    .onSuccess { (file, plan) ->
                        onShareFile(file, "application/pdf", "默写单")
                        onToast("已生成 ${plan.items.size} 题")
                    }
                    .onFailure { onToast("生成失败：${it.message ?: "未知错误"}") }
            }
        }
    }

    if (showExport) {
        ExportDialog(onDismiss = { showExport = false }) { format ->
            showExport = false
            if (state.wrongWords().isEmpty()) {
                onToast("还没有错词")
            } else {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
                when (format) {
                    ExportFormat.CSV -> onShareFile(
                        state.writeExportFile("错词本-$stamp.csv", state.wrongBookCsv()),
                        "text/csv",
                        "错词本",
                    )
                    ExportFormat.TXT -> onShareFile(
                        state.writeExportFile("错词本-$stamp.txt", state.wrongBookTxt()),
                        "text/plain",
                        "错词本",
                    )
                    ExportFormat.CLIPBOARD -> {
                        com.guoci.vocab3500.platform.ShareFiles.copy(ctx, "错词本", state.wrongBookTxt())
                        onToast("已复制 ${state.wrongWords().size} 个词")
                    }
                }
            }
        }
    }

    if (showReset) {
        ConfirmDialog(
            title = "重置全部进度？",
            message = "所有记忆记录、连续天数和徽章都会清空，词库不受影响。这一步不能撤销。",
            confirmLabel = "确认重置",
            onConfirm = {
                showReset = false
                state.resetProgress()
                onToast("已重置")
            },
            onDismiss = { showReset = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                title,
                fontSize = 15.sp,
                color = if (danger) Danger else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("›", color = Muted, fontSize = 20.sp)
    }
}
