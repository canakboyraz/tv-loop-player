package com.pion.tvloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Arka planda çalışır. TV bekleme modundan açıldığında (SCREEN_ON) oynatıcıyı öne getirir.
 * Çoğu TV "kapat" denince gerçekten kapanmaz, beklemeye geçer; bu yüzden BOOT_COMPLETED tek başına yetmez.
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Ekran açıldıktan sonra sistemin ana ekranı yüklemesini bekle, sonra öne gel
            handler.postDelayed({ launchPlayer(c) }, 3000)
            handler.postDelayed({ launchPlayer(c) }, 10000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DREAMING_STOPPED)
        }
        registerReceiver(screenReceiver, f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val chId = "tvloop"
        val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(chId, "TV Loop", NotificationManager.IMPORTANCE_MIN))
            Notification.Builder(this, chId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setContentTitle("TV Loop Player çalışıyor")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        fun start(c: Context) {
            val i = Intent(c, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (_: Exception) { }
        }

        fun launchPlayer(c: Context) {
            val i = Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try { c.startActivity(i) } catch (_: Exception) { }
        }
    }
}
