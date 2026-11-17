package com.pion.tvloop

import android.content.Context
import java.io.File

object Prefs {
    private const val NAME = "tvloop"
    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun manifestUrl(c: Context): String =
        sp(c).getString("manifest_url", null) ?: BuildConfig.DEFAULT_MANIFEST_URL
    fun setManifestUrl(c: Context, v: String) = sp(c).edit().putString("manifest_url", v.trim()).apply()

    fun localVersion(c: Context): Long = sp(c).getLong("local_version", -1)
    fun setLocalVersion(c: Context, v: Long) = sp(c).edit().putLong("local_version", v).apply()

    fun lastStatus(c: Context): String = sp(c).getString("status", "") ?: ""
    fun setStatus(c: Context, s: String) = sp(c).edit().putString("status", s).apply()

    fun videoFile(c: Context) = File(c.filesDir, "video.mp4")
    fun partFile(c: Context) = File(c.filesDir, "video.part")
}
