package com.example.myfin.data

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class TransactionRepository(private val notificationRepo: NotificationRepository? = null) {
    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val transactionsLiveData = MutableLiveData<List<Transaction>>()

    private var transactionsListener: ValueEventListener? = null
    private var isListenerAttached = false

    // Хранилище для отслеживания уже отправленных уведомлений (защита от дублей)
    private val sentNotificationsCache = mutableMapOf<String, Long>()
    private val NOTIFICATION_COOLDOWN_MS = 60000L // 60 секунд

    companion object {
        private const val TAG = "TransactionRepository"
    }

    fun getTransactionsLiveData() = transactionsLiveData

    fun loadTransactions() {
        val userId = auth.currentUser?.uid ?: return

        if (isListenerAttached) {
            removeTransactionsListener()
        }

        transactionsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val transactions = mutableListOf<Transaction>()
                snapshot.children.forEach { child ->
                    child.getValue(Transaction::class.java)?.let {
                        transactions.add(it)
                    }
                }
                transactions.sortByDescending { it.date }
                Log.d(TAG, "Загружено транзакций: ${transactions.size}")
                transactionsLiveData.postValue(transactions)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Ошибка загрузки транзакций: ${error.message}")
            }
        }

        db.child("transactions").child(userId)
            .orderByChild("date")
            .addValueEventListener(transactionsListener!!)

        isListenerAttached = true
        Log.d(TAG, "Слушатель транзакций прикреплен")
    }

    fun forceRefresh() {
        Log.d(TAG, "Force refresh called")
        loadTransactions()
    }

    private fun removeTransactionsListener() {
        val userId = auth.currentUser?.uid ?: return
        if (transactionsListener != null && isListenerAttached) {
            db.child("transactions").child(userId).removeEventListener(transactionsListener!!)
            transactionsListener = null
            isListenerAttached = false
            Log.d(TAG, "Слушатель транзакций удален")
        }
    }

    fun addTransaction(
        title: String,
        amount: Double,
        type: String,
        categoryId: String,
        categoryName: String,
        description: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(false, "Пользователь не авторизован")
            return
        }

        val transactionId = generateTransactionId()
        val currentDate = getCurrentDate()
        val now = System.currentTimeMillis()

        val transaction = Transaction(
            id = transactionId,
            title = title,
            amount = amount,
            type = type,
            categoryId = categoryId,
            categoryName = categoryName,
            description = description,
            date = currentDate,
            createdAt = now,
            updatedAt = now,
            userId = userId
        )

        Log.e(TAG, "========== ДОБАВЛЕНИЕ ТРАНЗАКЦИИ ==========")
        Log.e(TAG, "userId: $userId")
        Log.e(TAG, "transactionId: $transactionId")
        Log.e(TAG, "categoryId: $categoryId")
        Log.e(TAG, "categoryName: $categoryName")
        Log.e(TAG, "type: $type")
        Log.e(TAG, "amount: $amount")

        db.child("transactions").child(userId).child(transactionId)
            .setValue(transaction)
            .addOnSuccessListener {
                Log.e(TAG, "✅ Транзакция успешно добавлена")

                notificationRepo?.let { repo ->
                    val notificationTitle = if (type == "income") "💰 Доход добавлен" else "💸 Расход добавлен"
                    val notificationMessage = if (type == "income") {
                        "Добавлен доход: $title на сумму ${formatAmount(amount)} ₽"
                    } else {
                        "Добавлен расход: $title на сумму ${formatAmount(amount)} ₽"
                    }

                    val notification = repo.createTransactionNotification(
                        title = notificationTitle,
                        message = notificationMessage,
                        transactionId = transactionId,
                        type = type
                    )
                    repo.addNotification(notification)

                    if (type == "expense") {
                        Log.e(TAG, "🔴🔴🔴 ЭТО РАСХОД! Запускаем проверку лимита...")
                        checkCategoryLimit(userId, categoryId, categoryName, repo)
                    } else {
                        Log.e(TAG, "🔴🔴🔴 ЭТО ДОХОД, лимит не проверяем")
                    }
                }

                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Ошибка добавления транзакции", e)
                onResult(false, e.message ?: "Неизвестная ошибка")
            }
    }

    fun updateTransaction(
        transactionId: String,
        title: String,
        amount: Double,
        type: String,
        categoryId: String,
        categoryName: String,
        description: String,
        date: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(false, "Пользователь не авторизован")
            return
        }

        val now = System.currentTimeMillis()

        val updates = mapOf(
            "title" to title,
            "amount" to amount,
            "type" to type,
            "categoryId" to categoryId,
            "categoryName" to categoryName,
            "description" to description,
            "date" to date,
            "updatedAt" to now
        )

        db.child("transactions").child(userId).child(transactionId)
            .updateChildren(updates)
            .addOnSuccessListener {
                notificationRepo?.let { repo ->
                    if (type == "expense") {
                        Log.e(TAG, "🔴🔴🔴 Запускаем проверку лимита при обновлении")
                        checkCategoryLimit(userId, categoryId, categoryName, repo)
                    }
                }
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                onResult(false, e.message)
            }
    }

    private fun checkCategoryLimit(
        userId: String,
        categoryId: String,
        categoryName: String,
        notificationRepo: NotificationRepository
    ) {
        Log.e(TAG, "=========================================")
        Log.e(TAG, "🔴🔴🔴 НАЧАЛО ПРОВЕРКИ ЛИМИТА")
        Log.e(TAG, "🔴 userId: $userId")
        Log.e(TAG, "🔴 categoryId: '$categoryId'")
        Log.e(TAG, "🔴 categoryName: '$categoryName'")
        Log.e(TAG, "=========================================")

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val monthStart = "$currentYear-${String.format("%02d", currentMonth)}-01"
        val monthEnd = "$currentYear-${String.format("%02d", currentMonth)}-${calendar.getActualMaximum(Calendar.DAY_OF_MONTH)}"

        Log.e(TAG, "📅 Период проверки: $monthStart - $monthEnd")

        findCategoryAndCheckLimit(userId, categoryId, categoryName, monthStart, monthEnd, notificationRepo)
    }

    private fun findCategoryAndCheckLimit(
        userId: String,
        categoryId: String,
        categoryName: String,
        monthStart: String,
        monthEnd: String,
        notificationRepo: NotificationRepository
    ) {
        val pathsToCheck = listOf(
            "users/$userId/categories/$categoryId",
            "users/$userId/customCategories/$categoryId",
            "categories/expenses/user/$userId/$categoryId",
            "categories/incomes/user/$userId/$categoryId"
        )

        Log.e(TAG, "🔍 Ищем категорию по путям:")
        pathsToCheck.forEach { Log.e(TAG, "   - $it") }

        var found = false

        for (path in pathsToCheck) {
            if (found) break
            Log.e(TAG, "🔍 Проверяем путь: $path")

            db.child(path).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists() && !found) {
                        found = true
                        Log.e(TAG, "✅ КАТЕГОРИЯ НАЙДЕНА в пути: $path")

                        val data = snapshot.value as? Map<*, *>
                        if (data != null) {
                            val name = data["name"] as? String ?: categoryName
                            val limit = (data["monthlyLimit"] as? Number)?.toDouble() ?: 0.0
                            val type = data["type"] as? String ?: "expense"

                            Log.e(TAG, "📊 ДАННЫЕ КАТЕГОРИИ:")
                            Log.e(TAG, "   Имя: $name")
                            Log.e(TAG, "   Лимит: $limit")
                            Log.e(TAG, "   Тип: $type")

                            if (limit > 0 && type == "expense") {
                                calculateAndNotify(userId, name, limit, monthStart, monthEnd, notificationRepo)
                            } else {
                                Log.e(TAG, "⚠️ Лимит не установлен (limit=$limit) или категория не расход")
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Ошибка при проверке пути $path: ${e.message}")
                }
        }

        if (!found) {
            Log.e(TAG, "⚠️ Категория не найдена по ID, ищем по имени: $categoryName")
            findCategoryByName(userId, categoryName, monthStart, monthEnd, notificationRepo)
        }
    }

    private fun findCategoryByName(
        userId: String,
        categoryName: String,
        monthStart: String,
        monthEnd: String,
        notificationRepo: NotificationRepository
    ) {
        Log.e(TAG, "🔍 Поиск категории по имени: $categoryName")

        val searchPaths = listOf(
            "users/$userId/categories",
            "users/$userId/customCategories",
            "categories/expenses/user/$userId",
            "categories/incomes/user/$userId"
        )

        for (path in searchPaths) {
            db.child(path).get()
                .addOnSuccessListener { snapshot ->
                    snapshot.children.forEach { child ->
                        val data = child.value as? Map<*, *>
                        if (data != null) {
                            val name = data["name"] as? String ?: ""
                            if (name.equals(categoryName, ignoreCase = true)) {
                                val limit = (data["monthlyLimit"] as? Number)?.toDouble() ?: 0.0
                                val type = data["type"] as? String ?: "expense"

                                Log.e(TAG, "✅ Найдена категория по имени: $name, лимит: $limit")

                                if (limit > 0 && type == "expense") {
                                    calculateAndNotify(userId, name, limit, monthStart, monthEnd, notificationRepo)
                                }
                                return@addOnSuccessListener
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Ошибка поиска по пути $path: ${e.message}")
                }
        }
    }

    // Генерируем уникальный ключ для уведомления
    private fun getNotificationKey(categoryName: String, monthStart: String): String {
        return "${categoryName}_${monthStart}"
    }

    // Проверяем, можно ли отправлять уведомление (защита от дублей)
    private fun canSendNotification(key: String): Boolean {
        val lastSentTime = sentNotificationsCache[key]
        val now = System.currentTimeMillis()

        return if (lastSentTime == null || (now - lastSentTime) > NOTIFICATION_COOLDOWN_MS) {
            sentNotificationsCache[key] = now
            true
        } else {
            Log.e(TAG, "⚠️ Уведомление для '$key' уже отправлено ${(now - lastSentTime) / 1000} секунд назад. Пропускаем дубль.")
            false
        }
    }

    private fun calculateAndNotify(
        userId: String,
        categoryName: String,
        limit: Double,
        monthStart: String,
        monthEnd: String,
        notificationRepo: NotificationRepository
    ) {
        Log.e(TAG, "🧮 НАЧИНАЕМ ПОДСЧЕТ РАСХОДОВ за месяц")
        Log.e(TAG, "   Категория: $categoryName")
        Log.e(TAG, "   Лимит: $limit")
        Log.e(TAG, "   Период: $monthStart - $monthEnd")

        db.child("transactions").child(userId)
            .orderByChild("date")
            .startAt(monthStart)
            .endAt(monthEnd)
            .get()
            .addOnSuccessListener { snapshot ->
                var totalSpent = 0.0
                var matchingTransactions = 0

                snapshot.children.forEach { child ->
                    val transaction = child.getValue(Transaction::class.java)
                    transaction?.let {
                        if (it.type == "expense" && it.categoryName == categoryName) {
                            totalSpent += it.amount
                            matchingTransactions++
                            Log.e(TAG, "   📝 Найдена транзакция: ${it.title} - ${it.amount} ₽")
                        }
                    }
                }

                Log.e(TAG, "=========================================")
                Log.e(TAG, "📊 РЕЗУЛЬТАТ ПОДСЧЕТА:")
                Log.e(TAG, "   Категория: $categoryName")
                Log.e(TAG, "   Найдено транзакций: $matchingTransactions")
                Log.e(TAG, "   Потрачено всего: $totalSpent ₽")
                Log.e(TAG, "   Лимит: $limit ₽")

                if (totalSpent > limit) {
                    // Генерируем ключ для проверки дублей
                    val notificationKey = getNotificationKey(categoryName, monthStart)

                    if (canSendNotification(notificationKey)) {
                        Log.e(TAG, "🔴🔴🔴 ЛИМИТ ПРЕВЫШЕН! Создаем уведомление...")
                        val notification = notificationRepo.createLimitNotification(
                            categoryName,
                            totalSpent,
                            limit
                        )
                        notificationRepo.addNotification(notification)
                        Log.e(TAG, "✅ УВЕДОМЛЕНИЕ СОЗДАНО И ДОБАВЛЕНО!")
                    } else {
                        Log.e(TAG, "⚠️ Уведомление о превышении лимита уже было отправлено, пропускаем дубль")
                    }
                } else {
                    Log.e(TAG, "✅ Лимит НЕ превышен: $totalSpent <= $limit")
                }
                Log.e(TAG, "=========================================")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Ошибка при подсчете расходов: ${e.message}")
            }
    }

    fun deleteTransaction(
        transactionId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(false, "Пользователь не авторизован")
            return
        }

        db.child("transactions").child(userId).child(transactionId)
            .removeValue()
            .addOnSuccessListener {
                forceRefresh()
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                onResult(false, e.message ?: "Ошибка удаления")
            }
    }

    fun getTotalBalance(onResult: (Double, Double, Double) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            onResult(0.0, 0.0, 0.0)
            return
        }

        db.child("transactions").child(userId)
            .get()
            .addOnSuccessListener { snapshot ->
                var totalIncome = 0.0
                var totalExpense = 0.0

                snapshot.children.forEach { child ->
                    val transaction = child.getValue(Transaction::class.java)
                    transaction?.let {
                        if (it.type == "income") {
                            totalIncome += it.amount
                        } else {
                            totalExpense += it.amount
                        }
                    }
                }

                val balance = totalIncome - totalExpense
                onResult(balance, totalIncome, totalExpense)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Ошибка получения баланса", e)
                onResult(0.0, 0.0, 0.0)
            }
    }

    fun cleanup() {
        removeTransactionsListener()
        sentNotificationsCache.clear() // Очищаем кэш уведомлений
        Log.d(TAG, "TransactionRepository очищен")
    }

    private fun generateTransactionId(): String {
        return "trans_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun formatAmount(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }
}