package com.example.mediaprojectionex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mediaprojectionex.MainService.Companion.NOTIFICATION_ID

class MainApplication : Application() {
    companion object {
        lateinit var instance: MainApplication
            private set

        fun context(): Context {
            return instance.applicationContext
        }

        fun updateNotification(context: Context, vararg args: String) {

            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "update"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    channelId,
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val pendingIntent = Intent(context(), MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(context(), 0, notificationIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

            var title = ""
            var content = ""

            if (args.size > 1) {
                title = args[0]
                content = args[1]
            }
            if (args.isNotEmpty()) {
                title = args[0]
            }

            val notification = NotificationCompat.Builder(context(), channelId)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}