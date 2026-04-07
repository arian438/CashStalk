package com.example.myfin.data

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class DatabaseRepository(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
) {

    fun addTestData(userId: String, onComplete: (Boolean, String?) -> Unit) {
        val testData = hashMapOf<String, Any>(
            // Тестовые транзакции
            "transactions/$userId" to mapOf(
                "transaction_${System.currentTimeMillis()}_1" to mapOf(
                    "id" to "transaction_1",
                    "date" to "2023-11-01",
                    "title" to "Зарплата",
                    "amount" to 85000,
                    "type" to "income",
                    "category" to "Зарплата",
                    "description" to "Ежемесячная зарплата"
                ),
                "transaction_${System.currentTimeMillis()}_2" to mapOf(
                    "id" to "transaction_2",
                    "date" to "2023-11-03",
                    "title" to "Продукты",
                    "amount" to 3500,
                    "type" to "expense",
                    "category" to "Продукты",
                    "description" to "Покупка продуктов в супермаркете"
                ),
                "transaction_${System.currentTimeMillis()}_3" to mapOf(
                    "id" to "transaction_3",
                    "date" to "2023-11-05",
                    "title" to "Транспорт",
                    "amount" to 500,
                    "type" to "expense",
                    "category" to "Транспорт",
                    "description" to "Проезд на метро"
                )
            ),

            // Обновляем данные пользователя
            "users/$userId/currency" to "Рубль",
            "users/$userId/currencySymbol" to "₽",
            "users/$userId/fio" to "Иван",

            // Добавляем категории (если их нет)
            "categories" to mapOf(
                "expenses" to mapOf(
                    "Продукты" to mapOf(
                        "icon" to "🛒",
                        "color" to "#FF6B6B",
                        "monthlyLimit" to 15000
                    ),
                    "Транспорт" to mapOf(
                        "icon" to "🚗",
                        "color" to "#4ECDC4",
                        "monthlyLimit" to 5000
                    ),
                    "Кафе" to mapOf(
                        "icon" to "☕",
                        "color" to "#FFD166",
                        "monthlyLimit" to 8000
                    )
                ),
                "incomes" to mapOf(
                    "Зарплата" to mapOf(
                        "icon" to "💰",
                        "color" to "#118AB2"
                    ),
                    "Фриланс" to mapOf(
                        "icon" to "💻",
                        "color" to "#073B4C"
                    )
                )
            )
        )

        db.updateChildren(testData)
            .addOnSuccessListener {
                onComplete(true, "Данные успешно добавлены")
            }
            .addOnFailureListener { e ->
                onComplete(false, e.message)
            }
    }
}