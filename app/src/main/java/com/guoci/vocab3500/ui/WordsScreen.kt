package com.guoci.vocab3500.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.guoci.core.Word
import com.guoci.core.WordStatus
import com.guoci.vocab3500.data.AppState
import com.guoci.vocab3500.data.SheetSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class WordFilter(val label: String) {
    ALL("全部"),
    REVIEW("复习中"),
    WRONG("错词"),
    MASTERED("已掌握"),
}

/**
 * 词库 / 错词本 / 导出 / 默写单。
 *
 * 这一屏承担了 App 里所有"看一眼、拿出去"的需求，所以它才没有把导出、默写单
 * 各自做成一屏 —— 用户想拿东西出去的时候，不会希望先想清楚该点哪个入口。
 */
@Composable
fun WordsScreen(
    state: AppState,
    onBack: () -> Unit,
    onShareFile: (File, String, String) -> Unit,
    onToast: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WordFilter.ALL) }
    var showExport by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val allWords = state.words
    val shown = remember(query, filter, state.progress, allWords) {
        val base = when (filter) {
            WordFilter.ALL -> allWords
            WordFilter.REVIEW -> allWords.filter { (state.progress.words[it.id]?.status) == WordStatus.REVIEW }
            WordFilter.WRONG -> allWords.filter { (state.progress.words[it.id]?.lapses ?: 0) > 0 }
            WordFilter.MASTERED -> allWords.filter { (state.progress.words[it.id]?.status) == WordStatus.GRADUATED }
        }
        if (query.isBlank()) base else base.filter {
            it.word.contains(query.trim(), ignoreCase = true) || it.meaning.contains(query.trim())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回", color = Muted) }
            Spacer(Modifier.weight(1f))
            Text("${shown.size} 个词", color = Muted, fontSize = 13.sp)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("搜单词或释义", fontSize = 15.sp, color = Muted) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WordFilter.entries.forEach { f ->
                ToggleChip(f.label, filter == f) { filter = f }
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            items(shown, key = { it.id }) { word ->
                WordRow(word, state)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { showExport = true },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("导出错词", fontSize = 14.sp) }
            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("生成默写单", fontSize = 14.sp) }
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
                    ExportFormat.CSV -> {
                        val f = state.writeExportFile("错词本-$stamp.csv", state.wrongBookCsv())
                        onShareFile(f, "text/csv", "错词本")
                    }
                    ExportFormat.TXT -> {
                        val f = state.writeExportFile("错词本-$stamp.txt", state.wrongBookTxt())
                        onShareFile(f, "text/plain", "错词本")
                    }
                    ExportFormat.CLIPBOARD -> onToast("已复制 ${state.wrongWords().size} 个词到剪贴板")
                }
                if (format == ExportFormat.CLIPBOARD) {
                    com.guoci.vocab3500.platform.ShareFiles.copy(ctx, "错词本", state.wrongBookTxt())
                }
            }
        }
    }

    if (showSheet) {
        SheetOptionsDialog(
            onDismiss = { showSheet = false },
            initialSource = SheetSource.TODAY,
        ) { source, options ->
            showSheet = false
            val list = state.wordsFor(source)
            if (list.isEmpty()) {
                onToast("这个范围里还没有词")
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
}

@Composable
private fun WordRow(word: Word, state: AppState) {
    val p = state.progress.words[word.id]
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                word.word,
                fontFamily = WordFont,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            if (word.phonetic.isNotEmpty()) {
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("/${word.phonetic}/", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            val lapses = p?.lapses ?: 0
            if (lapses > 0) {
                Text(
                    "错 $lapses",
                    color = Danger,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            (if (word.pos.isNotEmpty()) "${word.pos} " else "") + word.meaning,
            color = Muted,
            fontSize = 14.sp,
        )
    }
}
