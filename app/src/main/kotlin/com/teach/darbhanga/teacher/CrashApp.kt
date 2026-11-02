package com.teach.darbhanga.teacher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class CrashApp : Application() {

    companion object {
        const val CHANNEL_ID = "tuition_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                var c: Throwable? = e
                var n = 0
                while (c != null && n < 5) {
                    sb.append(if (n == 0) "ERROR: " else "CAUSED BY: ")
                        .append(c.javaClass.name).append(": ").append(c.message).append("\n")
                    c.stackTrace.take(6).forEach { sb.append("   at ").append(it).append("\n") }
                    sb.append("\n")
                    c = c.cause
                    n++
                }
                getSharedPreferences("crash", MODE_PRIVATE)
                    .edit().putString("trace", sb.toString()).commit()
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Tuition Alerts", NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}