package com.example.myfin.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myfin.DashboardActivity
import com.example.myfin.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*

class NotificationRepository(private val context: Context) {

    companion object {
        private const val TAG = "NotificationRepository"
        private const val PREF_NAME = "notifications_prefs"
        private const val KEY_NOTIFICATIONS = "notifications_list"
        private const val MAX_NOTIFICATIONS = 100
        private const val CHANNEL_ID = "myfin_notifications_channel"
        private const val CHANNEL_NAME = "MyFin Уведомления"
        private const val NOTIFICATION_ID_BASE = 1000

        const val ACTION_NOTIFICATIONS_UPDATED = "com.example.myfin.NOTIFICATIONS_UPDATED"
        const val ACTION_NEW_NOTIFICATION = "com.example.myfin.NEW_NOTIFICATION"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _notificationsLiveData = MutableLiveData<List<Notification>>()
    val notificationsLiveData: LiveData<List<Notification>> = _notificationsLiveData
    private val localBroadcastManager = LocalBroadcastManager.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        createNotificationChannel()
        loadNotifications()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Канал для уведомлений MyFin"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 100, 200, 300)
                    enableLights(true)
                    lightColor = android.graphics.Color.GREEN
                    setShowBadge(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel", e)
            }
        }
    }

    fun addNotification(notification: Notification) {
        try {
            Log.d(TAG, "========== ДОБАВЛЕНИЕ УВЕДОМЛЕНИЯ ==========")
            Log.d(TAG, "Тип: ${notification.type}")
            Log.d(TAG, "Заголовок: ${notification.title}")
            Log.d(TAG, "Сообщение: ${notification.message}")

            // ✅ ДОБАВИТЬ ПРОВЕРКУ - включены ли уведомления в настройках
            val notificationsEnabled = areNotificationsEnabled()
            if (!notificationsEnabled) {
                Log.d(TAG, "⚠️ Уведомления отключены в настройках, пропускаем")
                return
            }

            val notifications = getAllNotifications().toMutableList()

            val finalNotification = if (notification.timestamp.isEmpty()) {
                notification.copy(timestamp = dateFormat.format(Date()))
            } else {
                notification
            }

            notifications.add(0, finalNotification)

            if (notifications.size > MAX_NOTIFICATIONS) {
                notifications.removeAt(notifications.size - 1)
            }

            saveNotifications(notifications)
            _notificationsLiveData.postValue(notifications)
            Log.d(TAG, "✅ Уведомление сохранено в SharedPreferences. Всего уведомлений: ${notifications.size}")

            if (hasNotificationPermission()) {
                showSystemNotification(finalNotification)
            } else {
                Log.d(TAG, "Notification permission not granted, skipping system notification")
            }

            sendNotificationBroadcast(ACTION_NEW_NOTIFICATION)
            sendNotificationBroadcast(ACTION_NOTIFICATIONS_UPDATED)
            sendNewNotificationDetailsBroadcast(finalNotification)

            Log.d(TAG, "✅ Уведомление успешно добавлено: ${finalNotification.title}")
            Log.d(TAG, "==========================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding notification", e)
        }
    }

    private fun showSystemNotification(notification: Notification) {
        try {
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("fragment", "NotificationsFragment")
                putExtra("notification_id", notification.id)
                putExtra("notification_type", notification.type)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notification.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(notification.title)
                .setContentText(notification.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setNumber(getUnreadCount())
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setVibrate(longArrayOf(0, 100, 200, 300))
                .setLights(android.graphics.Color.GREEN, 1000, 1000)

            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt()

            try {
                notificationManager.notify(notificationId, notificationBuilder.build())
                Log.d(TAG, "System notification shown: ${notification.title}")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException showing notification: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error building system notification", e)
        }
    }

    fun markAsRead(notificationId: String) {
        try {
            val notifications = getAllNotifications().toMutableList()
            val index = notifications.indexOfFirst { it.id == notificationId }
            if (index != -1) {
                notifications[index] = notifications[index].copy(isRead = true)
                saveNotifications(notifications)
                _notificationsLiveData.postValue(notifications)
                sendNotificationBroadcast(ACTION_NOTIFICATIONS_UPDATED)
                updateAppIconBadge()
                Log.d(TAG, "Notification marked as read: $notificationId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read", e)
        }
    }

    fun markAllAsRead() {
        try {
            val notifications = getAllNotifications().toMutableList()
            val updated = notifications.map { it.copy(isRead = true) }
            saveNotifications(updated)
            _notificationsLiveData.postValue(updated)
            sendNotificationBroadcast(ACTION_NOTIFICATIONS_UPDATED)
            updateAppIconBadge()
            Log.d(TAG, "All notifications marked as read")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all as read", e)
        }
    }

    fun deleteNotification(notificationId: String) {
        try {
            val notifications = getAllNotifications().toMutableList()
            notifications.removeAll { it.id == notificationId }
            saveNotifications(notifications)
            _notificationsLiveData.postValue(notifications)
            sendNotificationBroadcast(ACTION_NOTIFICATIONS_UPDATED)
            updateAppIconBadge()
            Log.d(TAG, "Notification deleted: $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification", e)
        }
    }

    fun clearAllNotifications() {
        try {
            saveNotifications(emptyList())
            _notificationsLiveData.postValue(emptyList())
            sendNotificationBroadcast(ACTION_NOTIFICATIONS_UPDATED)
            updateAppIconBadge()
            Log.d(TAG, "All notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all notifications", e)
        }
    }

    fun forceRefresh() {
        try {
            val notifications = getAllNotifications()
            _notificationsLiveData.postValue(notifications)
            Log.d(TAG, "Force refresh: ${notifications.size} notifications")
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing refresh", e)
        }
    }

    private fun updateAppIconBadge() {
        try {
            val unreadCount = getUnreadCount()
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE")
            intent.putExtra("badge_count", unreadCount)
            intent.putExtra("badge_count_package_name", context.packageName)
            intent.putExtra("badge_count_class_name", "${context.packageName}.SplashActivity")

            try {
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending badge broadcast", e)
            }

            Log.d(TAG, "App icon badge updated: $unreadCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app icon badge", e)
        }
    }

    fun getUnreadCount(): Int {
        return try {
            getAllNotifications().count { !it.isRead }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread count", e)
            0
        }
    }

    fun getAllNotifications(): List<Notification> {
        val json = sharedPreferences.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        val type: Type = object : TypeToken<List<Notification>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all notifications", e)
            emptyList()
        }
    }

    private fun saveNotifications(notifications: List<Notification>) {
        try {
            val json = gson.toJson(notifications)
            sharedPreferences.edit().putString(KEY_NOTIFICATIONS, json).apply()
            Log.d(TAG, "Saved ${notifications.size} notifications to SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notifications", e)
        }
    }

    private fun loadNotifications() {
        try {
            val notifications = getAllNotifications()
            _notificationsLiveData.postValue(notifications)
            Log.d(TAG, "Loaded ${notifications.size} notifications")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading notifications", e)
        }
    }

    private fun sendNotificationBroadcast(action: String) {
        try {
            val intent = Intent(action)
            localBroadcastManager.sendBroadcast(intent)
            Log.d(TAG, "Broadcast sent: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast: $action", e)
        }
    }

    private fun sendNewNotificationDetailsBroadcast(notification: Notification) {
        try {
            val intent = Intent(ACTION_NEW_NOTIFICATION).apply {
                putExtra("notification_id", notification.id)
                putExtra("notification_title", notification.title)
                putExtra("notification_message", notification.message)
                putExtra("notification_type", notification.type)
            }
            localBroadcastManager.sendBroadcast(intent)
            Log.d(TAG, "New notification broadcast sent: ${notification.type}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending new notification broadcast", e)
        }
    }

    private fun getCurrencySymbol(): String {
        val sharedPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val currencyCode = sharedPrefs.getString("currency", "RUB")
        return when (currencyCode) {
            "RUB" -> "₽"
            "USD" -> "$"
            "EUR" -> "€"
            "KZT" -> "₸"
            "BYN" -> "Br"
            else -> "₽"
        }
    }

    fun createTransactionNotification(
        title: String,
        message: String,
        transactionId: String,
        type: String
    ): Notification {
        return Notification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = "transaction",
            timestamp = dateFormat.format(Date()),
            isRead = false,
            category = type,
            transactionId = transactionId
        )
    }

    fun createCategoryNotification(
        title: String,
        message: String,
        categoryType: String
    ): Notification {
        return Notification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = "category",
            timestamp = dateFormat.format(Date()),
            isRead = false,
            category = categoryType,
            transactionId = null
        )
    }

    /**
     * Создает уведомление о превышении лимита
     */
    fun createLimitNotification(
        categoryName: String,
        spentAmount: Double,
        limitAmount: Double
    ): Notification {
        val percent = ((spentAmount / limitAmount) * 100).toInt()
        val exceededAmount = spentAmount - limitAmount
        val currencySymbol = getCurrencySymbol()

        val title = context.getString(R.string.notification_limit_exceeded_title)

        val message = if (spentAmount > limitAmount) {
            String.format(
                "Категория \"%s\": превышен лимит на %.2f %s (%d%% от лимита). Текущие расходы: %.2f %s из %.2f %s",
                categoryName,
                exceededAmount,
                currencySymbol,
                percent,
                spentAmount,
                currencySymbol,
                limitAmount,
                currencySymbol
            )
        } else {
            String.format(
                "Категория \"%s\": использовано %d%% лимита (%.2f %s из %.2f %s)",
                categoryName,
                percent,
                spentAmount,
                currencySymbol,
                limitAmount,
                currencySymbol
            )
        }

        Log.d(TAG, "📢 СОЗДАНО УВЕДОМЛЕНИЕ О ПРЕВЫШЕНИИ ЛИМИТА")
        Log.d(TAG, "Категория: $categoryName")
        Log.d(TAG, "Заголовок: $title")
        Log.d(TAG, "Сообщение: $message")

        return Notification(
            id = UUID.randomUUID().toString(),
            title = title,
            message = message,
            type = "limit",
            timestamp = dateFormat.format(Date()),
            isRead = false,
            category = "expense",
            transactionId = null
        )
    }

    fun createReminderNotification(message: String): Notification {
        return Notification(
            id = UUID.randomUUID().toString(),
            title = "⏰ Напоминание",
            message = message,
            type = "reminder",
            timestamp = dateFormat.format(Date()),
            isRead = false,
            category = "system",
            transactionId = null
        )
    }

    fun createReportNotification(
        period: String,
        totalIncome: Double,
        totalExpense: Double
    ): Notification {
        val balance = totalIncome - totalExpense
        val currencySymbol = getCurrencySymbol()
        val balanceText = if (balance >= 0) "+${formatAmount(balance)}" else formatAmount(balance)

        return Notification(
            id = UUID.randomUUID().toString(),
            title = "📊 Отчет за $period",
            message = "Доходы: ${formatAmount(totalIncome)} $currencySymbol | Расходы: ${formatAmount(totalExpense)} $currencySymbol | Баланс: $balanceText $currencySymbol",
            type = "report",
            timestamp = dateFormat.format(Date()),
            isRead = false,
            category = "system",
            transactionId = null
        )
    }

    /**
     * Обновляет состояние уведомлений
     */
    fun updateNotificationsEnabled(enabled: Boolean) {
        Log.d(TAG, "Notifications setting updated: $enabled")
        // Здесь можно сохранить в отдельный SharedPreferences или просто использовать глобальный
    }
    /**
     * Проверяет, включены ли уведомления в настройках пользователя
     */
    private fun areNotificationsEnabled(): Boolean {
        return try {
            val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val enabled = sharedPrefs.getBoolean("notifications", true)
            Log.d(TAG, "Notifications enabled in settings: $enabled")
            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notifications setting", e)
            true // по умолчанию true
        }
    }

    private fun formatAmount(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }

    fun getContext(): Context = context
}