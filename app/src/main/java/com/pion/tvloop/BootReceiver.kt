package com.pion.tvloop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        SyncWorker.schedule(app)
        KeepAliveService.start(app)
        // Açılışta sistem launcher'ı yüklendikten sonra öne gelmek için birkaç kez dene
        val h = Handler(Looper.getMainLooper())
        listOf(2000L, 8000L, 20000L).forEach { d -> h.postDelayed({ KeepAliveService.launchPlayer(app) }, d) }
    }
}
