package com.guoci.vocab3500.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guoci.vocab3500.data.Store

/**
 * 手机重启后把每日提醒重新挂上。
 *
 * AlarmManager 里挂的闹钟重启即全部丢失。如果"今天还没背单词"的提醒因为一次重启
 * 就永久消失，用户不会知道，App 也不会知道 —— 这是提醒功能最常见的静默失败。
 * 这里从落盘的设置里读回时间，重新安排。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = try {
            Store(context).load().settings
        } catch (_: Throwable) {
            return
        }
        val scheduler = ReminderScheduler(context)
        if (settings.reminderEnabled) {
            scheduler.schedule(settings.reminderHour, settings.reminderMinute)
        } else {
            scheduler.cancel()
        }
    }
}
