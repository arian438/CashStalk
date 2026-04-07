package com.example.myfin.data

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val type: String, // transaction, limit, reminder, category, report, update
    val timestamp: String,
    var isRead: Boolean,
    val category: String, // income, expense, system, reminder
    val transactionId: String? = null // ID транзакции, если уведомление связано с транзакцией
)