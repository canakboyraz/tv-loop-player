package com.pion.tvloop

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@SuppressLint("UnsafeOptInUsageError")
class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var info: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = startPlayback()
    }

    private val statusTick = object : Runnable {
        override fun run() {
            if (!Prefs.videoFile(this@MainActivity).exists()) {
                info.text = "Video bekleniyor…\n${Prefs.lastStatus(this@MainActivity)}\n\nAyarlar: MENU tuşu veya OK'a uzun bas"
                info.visibility = View.VISIBLE
            } else if (player?.isPlaying == true) {
                info.visibility = View.GONE
            }
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        info = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(playerView); addView(info)
        })
        hideSystemUi()

        SyncWorker.schedule(this)
        SyncWorker.runNow(this)
        KeepAliveService.start(this)
        askOverlayPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter(SyncWorker.ACTION_VIDEO_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, f, RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(updateReceiver, f)
        startPlayback()
        handler.post(statusTick)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(updateReceiver)
        handler.removeCallbacks(statusTick)
        player?.release(); player = null
    }

    private fun startPlayback() {
        val file = Prefs.videoFile(this)
        if (!file.exists()) return
        player?.release()
        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = 1f
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Bozuk dosya vb. durumda birkaç saniye sonra yeniden dene
                    handler.postDelayed({ startPlayback() }, 5000)
                }
            })
            p.prepare()
            p.playWhenReady = true
        }
        info.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSettings(); return true }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            showSettings(); return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun showSettings() {
        val input = EditText(this).apply { setText(Prefs.manifestUrl(this@MainActivity)); setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("Manifest adresi")
            .setMessage("Durum: ${Prefs.lastStatus(this)}\nYerel sürüm: ${Prefs.localVersion(this)}\n" +
                "Otomatik açılma izni: ${if (overlayGranted()) "VAR ✓" else "YOK ✗ (aşağıdaki 'İzin' butonu)"}")
            .setView(input)
            .setPositiveButton("Kaydet ve kontrol et") { _, _ ->
                Prefs.setManifestUrl(this, input.text.toString())
                SyncWorker.runNow(this)
            }
            .setNeutralButton(if (overlayGranted()) "Şimdi kontrol et" else "İzin") { _, _ ->
                if (overlayGranted()) SyncWorker.runNow(this) else openOverlaySettings()
            }
            .setNegativeButton("Kapat", null)
            .setOnDismissListener { hideSystemUi() }
            .show()
    }

    private fun overlayGranted() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun openOverlaySettings() {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        for (i in intents) {
            try { startActivity(i); return } catch (_: Exception) { }
        }
    }

    private fun askOverlayPermissionOnce() {
        if (Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(this)) return
        val sp = getSharedPreferences("tvloop", MODE_PRIVATE)
        if (sp.getBoolean("asked_overlay", false)) return
        sp.edit().putBoolean("asked_overlay", true).apply()
        openOverlaySettings()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}
