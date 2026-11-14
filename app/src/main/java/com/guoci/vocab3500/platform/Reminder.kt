package com.guoci.vocab3500.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.guoci.vocab3500.MainActivity
import com.guoci.vocab3500.R
import java.util.Calendar

/**
 * 每天一条提醒。
 *
 * 为什么用 AlarmManager 而不是 WorkManager：这里只有一件事 —— 到点提醒一次。
 * WorkManager 会为一个闹钟拖进 4 个构件和一套 JobScheduler 行为差异；而
 * setAndAllowWhileIdle 在 Doze 下也能唤醒，正好够用。代价是通知时间可能有几分钟
 * 偏差，对"提醒你背单词"来说完全可以接受。
 *
 * 提醒文案刻意只有一条、不递增、不撒娇：每天最多一句"今天的 10 个词还等着你"。
 * 一个会催命的 App 是被卸载得最快的 App。
 */
class ReminderScheduler(private val context: Context) {

    fun schedule(hour: Int, minute: Int) {
        ensureChannel()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = nextTriggerMillis(hour, minute)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent())
    }

    fun cancel() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "每日提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "每天一个时间提醒你背今天的词"
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "daily"
        const val ACTION_REMIND = "com.guoci.vocab3500.REMIND"
        private const val REQUEST_CODE = 1001
    }
}

/** 到点弹通知，并把下一次挂上（AlarmManager 的单次闹钟不会自己重复）。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return

        val store = com.guoci.vocab3500.data.Store(context)
        val settings = store.load().settings
        val scheduler = ReminderScheduler(context)
        scheduler.ensureChannel()

        if (settings.reminderEnabled && canNotify(context)) {
            show(context)
        }
        // 无论这次是否弹了通知，都把明天挂上：权限被关掉时用户下次打开设置再开就有。
        if (settings.reminderEnabled) {
            scheduler.schedule(settings.reminderHour, settings.reminderMinute)
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun show(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("今天的词还等着你")
            .setContentText("两分钟，10 个词，做完就结束。")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
