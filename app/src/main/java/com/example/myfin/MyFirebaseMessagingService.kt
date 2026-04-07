package com.example.myfin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myfin.data.Notification
import com.example.myfin.data.NotificationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.text.SimpleDateFormat
import java.util.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMsgService"
        const val CHANNEL_ID = "myfin_notifications"
        private const val CHANNEL_NAME = "MyFin Notifications"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // Сохраняем токен в SharedPreferences
        saveTokenToPrefs(token)
    }

    private fun saveTokenToPrefs(token: String) {
        val sharedPref = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("fcm_token", token).apply()
        Log.d(TAG, "Token saved to SharedPreferences: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Проверяем, содержит ли сообщение данные
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val title = remoteMessage.data["title"] ?: getDefaultTitle()
            val message = remoteMessage.data["message"] ?: getDefaultMessage()
            val type = remoteMessage.data["type"] ?: "general"
            val category = remoteMessage.data["category"] ?: "system"
            val transactionId = remoteMessage.data["transactionId"]

            // Сохраняем уведомление в локальную базу
            saveNotification(title, message, type, category, transactionId)

            // Показываем уведомление в системном трее
            sendNotification(title, message)
        }

        // Проверяем, содержит ли сообщение уведомление (для консоли Firebase)
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            val title = it.title ?: getDefaultTitle()
            val message = it.body ?: getDefaultMessage()

            // Сохраняем уведомление
            saveNotification(title, message, "general", "system", null)

            // Показываем уведомление
            sendNotification(title, message)
        }
    }

    private fun getDefaultTitle(): String {
        return try {
            getString(R.string.notification)
        } catch (e: Exception) {
            "MyFin Уведомление"
        }
    }

    private fun getDefaultMessage(): String {
        return try {
            getString(R.string.new_notification)
        } catch (e: Exception) {
            "Новое уведомление"
        }
    }

    private fun saveNotification(
        title: String,
        message: String,
        type: String,
        category: String,
        transactionId: String? = null
    ) {
        try {
            val notification = com.example.myfin.data.Notification(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                type = type,
                timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                isRead = false,
                category = category,
                transactionId = transactionId
            )

            // Сохраняем в локальную базу
            val notificationRepo = NotificationRepository(applicationContext)
            notificationRepo.addNotification(notification)

            Log.d(TAG, "Notification saved to repository: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification", e)
        }
    }

    private fun sendNotification(title: String, message: String) {
        try {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("fragment", "NotificationsFragment")
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = CHANNEL_ID
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Создаем канал для Android Oreo и выше
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Канал для уведомлений MyFin"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 100, 200, 300)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationId = (System.currentTimeMillis() % 10000).toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())

            Log.d(TAG, "System notification sent: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }
}