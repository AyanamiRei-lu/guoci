package com.guoci.vocab3500.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guoci.core.SheetPlanner
import com.guoci.vocab3500.data.SheetSource

enum class ExportFormat(val label: String) { CSV("导出 CSV"), TXT("导出 TXT"), CLIPBOARD("复制到剪贴板") }

/** 小胶囊按钮，全 App 复用（避免为了 FilterChip 去赌 M3 的稳定性）。 */
@Composable
fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * 默写单参数。
 *
 * 默认值就是对高中生最有用的那组：中写英、20 题、按词表顺序、带答案页。
 * 乱序默认关闭 —— 按今天的顺序默写，和当天学过的内容对得上，更容易坚持。
 */
@Composable
fun SheetOptionsDialog(
    onDismiss: () -> Unit,
    initialSource: SheetSource = SheetSource.TODAY,
    onGenerate: (SheetSource, SheetPlanner.Options) -> Unit,
) {
    var source by remember { mutableStateOf(initialSource) }
    var kind by remember { mutableStateOf(SheetPlanner.Kind.ZH_TO_EN) }
    var count by remember { mutableStateOf(20) }
    var shuffle by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成默写单") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("取词范围", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSource.entries.take(2).forEach { s ->
                        ToggleChip(s.label, source == s) { source = s }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSource.entries.drop(2).forEach { s ->
                        ToggleChip(s.label, source == s) { source = s }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("题型", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip("中文写英文", kind == SheetPlanner.Kind.ZH_TO_EN) {
                        kind = SheetPlanner.Kind.ZH_TO_EN
                    }
                    ToggleChip("英文写中文", kind == SheetPlanner.Kind.EN_TO_ZH) {
                        kind = SheetPlanner.Kind.EN_TO_ZH
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("数量", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { n ->
                        ToggleChip("$n 题", count == n) { count = n }
                    }
                }

                Spacer(Modifier.height(14.dp))
                SwitchRow("打乱顺序", shuffle) { shuffle = it }
                SwitchRow("附答案页", answers) { answers = it }
                SwitchRow("给首字母提示", hint) { hint = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onGenerate(
                    source,
                    SheetPlanner.Options(
                        kind = kind,
                        count = count,
                        shuffle = shuffle,
                        seed = System.currentTimeMillis(),
                        hintFirstLetter = hint,
                        includeAnswers = answers,
                        title = "默写单",
                    ),
                )
            }) { Text("生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ExportDialog(onDismiss: () -> Unit, onPick: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出错词本") },
        text = {
            Column {
                Text(
                    "导出的是所有答错过的词，按错误次数排序。CSV 可以直接用 Excel 打开。",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                ExportFormat.entries.forEach { f ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onPick(f) },
                    ) {
                        Text(f.label, Modifier.padding(14.dp), fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, color = Muted, fontSize = 14.sp) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
