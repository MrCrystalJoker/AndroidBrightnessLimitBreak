package com.mrcrystaljoker.brightness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class BrightnessService : Service() {

    companion object {
        const val CHANNEL_ID = "brightness_control"
        const val NOTIFY_ID = 1001
        const val ACTION_ADJUST = "com.mrcrystaljoker.brightness.ADJUST"
        const val ACTION_REFRESH = "com.mrcrystaljoker.brightness.REFRESH"
        const val EXTRA_DELTA = "delta"

        /** 进程内标记，供保活任务判断服务是否还活着 */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BrightnessService::class.java)
            context.startForegroundService(intent)
        }

        fun adjust(context: Context, delta: Int) {
            context.sendBroadcast(
                Intent(context, BrightnessActionReceiver::class.java)
                    .setAction(ACTION_ADJUST)
                    .putExtra(EXTRA_DELTA, delta)
            )
        }
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var mask: MaskOverlay
    private var autoBrightnessTaken = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                // 息屏后系统会重置亮度覆盖，亮屏必须重新下发
                BrightnessController.applyNow(BrightnessController.currentPct)
                updateNotification()
            }
        }
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notificationManager = getSystemService(NotificationManager::class.java)
        mask = MaskOverlay(this)
        BrightnessController.listener = { pct ->
            mask.update(BrightnessController.maskAlphaFor(pct))
            updateNotification()
        }
        createChannel()

        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, refreshReceiver,
            IntentFilter(ACTION_REFRESH).apply { addAction(ACTION_ADJUST) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        startForeground(NOTIFY_ID, buildNotification())
        BrightnessController.disableAutoBrightness()
        autoBrightnessTaken = true
        BrightnessController.applyNow(BrightnessController.currentPct)
        mask.update(BrightnessController.maskAlphaFor(BrightnessController.currentPct))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADJUST -> {
                val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                BrightnessController.applyNow(BrightnessController.currentPct + delta)
                updateNotification()
            }
            ACTION_REFRESH -> updateNotification()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(refreshReceiver) }
        BrightnessController.listener = null
        runCatching { mask.hide() }
        if (autoBrightnessTaken) {
            BrightnessController.enableAutoBrightness()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉 App 时，服务本身应该继续跑；
        // 如果系统顺手把它停了，这里补一次重启。
        if (!isRunning && BrightnessController.hasPermission()) {
            runCatching { BrightnessService.start(this@BrightnessService) }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "亮度控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "常驻亮度调节"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val collapsed = RemoteViews(packageName, R.layout.notification_collapsed)
        val expanded = RemoteViews(packageName, R.layout.notification_expanded)

        bindViews(collapsed, compact = true)
        bindViews(expanded, compact = false)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PanelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brightness)
            .setContentTitle("亮度")
            .setContentText("${BrightnessController.currentPct}%")
            .setContentIntent(contentIntent)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .build()
    }

    private fun bindViews(views: RemoteViews, compact: Boolean) {
        val pct = BrightnessController.currentPct
        views.setTextViewText(R.id.tv_value, "$pct%")
        views.setProgressBar(R.id.progress, 100, pct, false)

        if (compact) {
            views.setOnClickPendingIntent(R.id.btn_minus, adjustIntent(-5))
            views.setOnClickPendingIntent(R.id.btn_plus, adjustIntent(5))
        } else {
            views.setOnClickPendingIntent(R.id.btn_m10, adjustIntent(-10))
            views.setOnClickPendingIntent(R.id.btn_m5, adjustIntent(-5))
            views.setOnClickPendingIntent(R.id.btn_m1, adjustIntent(-1))
            views.setOnClickPendingIntent(R.id.btn_p1, adjustIntent(1))
            views.setOnClickPendingIntent(R.id.btn_p5, adjustIntent(5))
            views.setOnClickPendingIntent(R.id.btn_p10, adjustIntent(10))
        }
    }

    private fun adjustIntent(delta: Int): PendingIntent {
        val intent = Intent(this, BrightnessActionReceiver::class.java)
            .setAction(ACTION_ADJUST)
            .putExtra(EXTRA_DELTA, delta)
        return PendingIntent.getBroadcast(
            this,
            delta + 100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFY_ID, buildNotification())
    }
}
