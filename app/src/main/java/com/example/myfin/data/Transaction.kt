package com.example.myfin.data

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class Transaction(
    var id: String = "",
    var title: String = "",
    var amount: Double = 0.0,
    var type: String = "", // "income" или "expense"
    var categoryId: String = "",
    var categoryName: String = "",
    var description: String = "",
    var date: String = "", // Формат: "yyyy-MM-dd"
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var userId: String = ""
) : Serializable {

    @Exclude
    fun hasIncomeType(): Boolean = type == "income"

    @Exclude
    fun hasExpenseType(): Boolean = type == "expense"

    @Exclude
    fun formatDisplayAmount(): String {
        val sign = if (hasIncomeType()) "+" else "-"
        return "$sign${String.format("%.2f", amount)} ₽"
    }
}