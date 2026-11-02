package com.teach.darbhanga.teacher

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmHelper.saveToken()
    }

    // App khuli ho tab yahan aata hai (background me system khud dikhata hai)
    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: "Tuition"
        val body = msg.notification?.body ?: msg.data["body"] ?: ""

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, LoginActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CrashApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}