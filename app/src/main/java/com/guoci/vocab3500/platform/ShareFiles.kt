package com.guoci.vocab3500.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把文件交出去。
 *
 * 一律走 FileProvider + 系统分享面板：没有 WRITE_EXTERNAL_STORAGE 权限，不往公共
 * 目录写东西，用户想存到哪个文件夹、发给谁，都由系统面板决定。
 */
object ShareFiles {

    fun share(context: Context, file: File, mime: String, subject: String) {
        val uri = uriFor(context, file) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun uriFor(context: Context, file: File): Uri? = try {
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    } catch (_: Throwable) {
        // 文件不在 file_paths.xml 暴露的目录里时会抛，说明是代码错误而不是用户输入问题；
        // 返回 null 让界面去提示，而不是让 App 崩在分享这一步。
        null
    }

    /** 用户选了「保存到…」之后调用，把文本写进 SAF 给的 uri。 */
    fun writeText(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        true
    } catch (_: Throwable) {
        false
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
