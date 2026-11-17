package com.pion.tvloop

import android.content.Context
import android.content.Intent
import androidx.work.*
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Uzaktaki manifest.json'u okur: {"version": 3, "url": "https://.../video.mp4"}
 * version yereldekinden büyükse videoyu video.part'a indirir (kesilirse kaldığı yerden devam eder),
 * tamamlanınca video.mp4 ile atomik olarak değiştirir. İnternet yoksa mevcut yerel video oynamaya devam eder.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val c = applicationContext
        return try {
            val manifest = JSONObject(httpGetText(Prefs.manifestUrl(c)))
            val remoteVersion = manifest.getLong("version")
            val url = normalizeUrl(manifest.getString("url"))

            if (remoteVersion <= Prefs.localVersion(c) && Prefs.videoFile(c).exists()) {
                Prefs.setStatus(c, "Güncel (v$remoteVersion)")
                return Result.success()
            }

            // Farklı sürüme ait yarım indirme varsa sil
            val sp = c.getSharedPreferences("tvloop", Context.MODE_PRIVATE)
            if (sp.getLong("part_version", -1) != remoteVersion) {
                Prefs.partFile(c).delete()
                sp.edit().putLong("part_version", remoteVersion).apply()
            }

            download(url, remoteVersion)

            val part = Prefs.partFile(c)
            val target = Prefs.videoFile(c)
            target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("Dosya taşınamadı")
            Prefs.setLocalVersion(c, remoteVersion)
            Prefs.setStatus(c, "Yeni video yüklendi (v$remoteVersion)")
            c.sendBroadcast(Intent(ACTION_VIDEO_UPDATED).setPackage(c.packageName))
            Result.success()
        } catch (e: Exception) {
            Prefs.setStatus(applicationContext, "Hata: ${e.message}")
            Result.retry()
        }
    }

    private fun download(url: String, version: Long) {
        val part = Prefs.partFile(applicationContext)
        var existing = if (part.exists()) part.length() else 0L

        val conn = open(url)
        if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
        conn.connect()
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
            existing = 0 // sunucu Range desteklemiyor -> baştan
        } else if (code != HttpURLConnection.HTTP_PARTIAL) {
            if (code == 416 && existing > 0) return // zaten tamamı inmiş
            throw IllegalStateException("HTTP $code")
        }
        val ctype = conn.contentType ?: ""
        if (ctype.startsWith("text/html")) {
            throw IllegalStateException("Link video değil, web sayfası döndü (Drive paylaşımı 'Bağlantıya sahip herkes' olmalı)")
        }
        val total = conn.getHeaderField("Content-Length")?.toLongOrNull()?.plus(existing) ?: -1L

        conn.inputStream.use { input ->
            FileOutputStream(part, existing > 0).use { out ->
                val buf = ByteArray(256 * 1024)
                var done = existing
                var lastReport = 0L
                while (true) {
                    if (isStopped) throw InterruptedException("Durduruldu")
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - lastReport > 5_000_000) {
                        lastReport = done
                        val pct = if (total > 0) " %${done * 100 / total}" else ""
                        Prefs.setStatus(applicationContext, "İndiriliyor v$version:$pct (${done / 1_048_576} MB)")
                    }
                }
                out.fd.sync()
                if (total > 0 && done < total) throw IllegalStateException("Eksik indirme $done/$total")
            }
        }
    }

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        repeat(5) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "TvLoopPlayer")
            }
            val code = c.responseCode
            if (code in 301..308) {
                u = URL(u, c.getHeaderField("Location")); c.disconnect()
            } else {
                c.disconnect()
                return (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000; readTimeout = 60_000
                    setRequestProperty("User-Agent", "TvLoopPlayer")
                }
            }
        }
        throw IllegalStateException("Çok fazla yönlendirme")
    }

    private fun httpGetText(url: String): String {
        val sep = if (url.contains("?")) "&" else "?"
        val c = open("$url${sep}t=${System.currentTimeMillis()}") // önbelleği atla
        c.useCaches = false
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** Google Drive / Dropbox paylaşım linklerini doğrudan indirme linkine çevirir. */
    private fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        Regex("drive\\.google\\.com/(?:file/d/|open\\?id=|uc\\?(?:.*&)?id=)([A-Za-z0-9_-]+)").find(u)?.let {
            return "https://drive.usercontent.google.com/download?id=${it.groupValues[1]}&export=download&confirm=t"
        }
        if (u.contains("dropbox.com")) return u.replace("dl=0", "dl=1")
        return u
    }

    companion object {
        const val ACTION_VIDEO_UPDATED = "com.pion.tvloop.VIDEO_UPDATED"
        private const val PERIODIC = "tvloop_periodic"

        fun schedule(c: Context) {
            val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(net)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build()
            )
        }

        fun runNow(c: Context) {
            val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(c).enqueueUniqueWork(
                "tvloop_now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(net)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
