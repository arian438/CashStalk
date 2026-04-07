package com.example.myfin.adapter

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.R
import com.example.myfin.data.Transaction
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private var transactions: List<Transaction> = emptyList()
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    companion object {
        private const val TAG = "TransactionAdapter"
    }

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryIcon: TextView = itemView.findViewById(R.id.categoryIcon)
        val categoryName: TextView = itemView.findViewById(R.id.categoryName)
        val description: TextView = itemView.findViewById(R.id.description)
        val transactionDate: TextView = itemView.findViewById(R.id.transactionDate)
        val transactionAmount: TextView = itemView.findViewById(R.id.transactionAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        return try {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            TransactionViewHolder(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateViewHolder", e)
            throw e
        }
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        try {
            val transaction = transactions[position]

            // Безопасная обработка categoryName
            val categoryName = transaction.categoryName ?: "Другое"

            // Настраиваем иконку категории
            holder.categoryIcon.text = getCategoryIcon(categoryName)

            // Название категории
            holder.categoryName.text = categoryName

            // Описание (если есть)
            val descriptionText = if (transaction.description.isNotEmpty()) {
                transaction.description
            } else {
                transaction.title.ifEmpty { "Без описания" }
            }
            holder.description.text = descriptionText

            // Дата
            holder.transactionDate.text = formatDate(transaction.date)

            // Сумма с цветом в зависимости от типа
            val symbol = if (transaction.type == "income") "+" else "-"
            holder.transactionAmount.text = "$symbol${String.format("%.2f", transaction.amount)} ₽"

            val color = if (transaction.type == "income") {
                Color.parseColor("#4CAF50") // Зеленый для доходов
            } else {
                Color.parseColor("#F44336") // Красный для расходов
            }
            holder.transactionAmount.setTextColor(color)

            // Цвет фона иконки
            val iconColor = getColorForCategory(categoryName)
            holder.categoryIcon.setBackgroundColor(iconColor)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onBindViewHolder at position $position", e)
            // Показываем заглушку вместо краша
            holder.categoryIcon.text = "❌"
            holder.categoryName.text = "Ошибка"
            holder.description.text = "Ошибка загрузки"
            holder.transactionDate.text = ""
            holder.transactionAmount.text = "Error"
        }
    }

    override fun getItemCount() = transactions.size

    fun updateData(newTransactions: List<Transaction>) {
        try {
            this.transactions = newTransactions.sortedByDescending { it.createdAt }
            notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error in updateData", e)
        }
    }

    private fun getCategoryIcon(categoryName: String): String {
        return when (categoryName) {
            "Продукты" -> "🛒"
            "Транспорт" -> "🚗"
            "Кафе" -> "☕"
            "Развлечения" -> "🎬"
            "Здоровье" -> "🏥"
            "Одежда" -> "👕"
            "Зарплата" -> "💰"
            "Фриланс" -> "💻"
            "Инвестиции" -> "📈"
            "Подарки" -> "🎁"
            "Другое" -> "📌"
            else -> "💸"
        }
    }

    private fun getColorForCategory(categoryName: String): Int {
        val colorMap = mapOf(
            "Продукты" to Color.parseColor("#FF6B6B"),
            "Транспорт" to Color.parseColor("#4ECDC4"),
            "Кафе" to Color.parseColor("#FFD166"),
            "Развлечения" to Color.parseColor("#118AB2"),
            "Здоровье" to Color.parseColor("#EF476F"),
            "Одежда" to Color.parseColor("#7209B7"),
            "Зарплата" to Color.parseColor("#10B981"),
            "Фриланс" to Color.parseColor("#3B82F6"),
            "Инвестиции" to Color.parseColor("#8B5CF6"),
            "Подарки" to Color.parseColor("#F59E0B"),
            "Другое" to Color.parseColor("#9E9E9E")
        )
        return colorMap[categoryName] ?: Color.parseColor("#9E9E9E")
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting date: $dateString", e)
            dateString
        }
    }
}