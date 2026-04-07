package com.example.myfin.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.lifecycle.MutableLiveData
import java.util.*

class CategoryRepository(private val notificationRepo: NotificationRepository? = null) {

    companion object {
        private const val TAG = "CategoryRepository"
    }

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    val expenseCategories = MutableLiveData<List<Category>>(emptyList())
    val incomeCategories = MutableLiveData<List<Category>>(emptyList())

    fun loadAllCategories() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 НАЧАЛО ЗАГРУЗКИ КАТЕГОРИЙ")
        Log.d(TAG, "========================================")

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "❌ Пользователь не авторизован!")
            loadStandardCategoriesOnly()
            return
        }

        Log.d(TAG, "✅ ID пользователя: $userId")
        loadAllCategoriesFromFirebase(userId)
    }

    private fun loadAllCategoriesFromFirebase(userId: String) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "📡 ЗАГРУЗКА ИЗ FIREBASE")
        Log.d(TAG, "========================================")

        val expenseList = mutableListOf<Category>()
        val incomeList = mutableListOf<Category>()

        var loadedCount = 0
        val totalToLoad = 4

        fun checkComplete() {
            loadedCount++
            if (loadedCount == totalToLoad) {
                Log.d(TAG, "========================================")
                Log.d(TAG, "📊 ИТОГО ЗАГРУЖЕНО:")
                Log.d(TAG, "   Расходы: ${expenseList.size}")
                Log.d(TAG, "   Доходы: ${incomeList.size}")
                Log.d(TAG, "========================================")

                expenseList.forEach { Log.d(TAG, "   - ${it.categoryName} (${it.type}) лимит: ${it.monthlyLimit}") }

                if (expenseList.isEmpty() && incomeList.isEmpty()) {
                    loadStandardCategoriesOnly()
                } else {
                    expenseCategories.postValue(expenseList)
                    incomeCategories.postValue(incomeList)
                }
            }
        }

        // 1. ЗАГРУЗКА ПОЛЬЗОВАТЕЛЬСКИХ КАТЕГОРИЙ из users/{userId}/categories
        Log.d(TAG, "📌 1. Загрузка пользовательских категорий из users/$userId/categories")
        database.reference
            .child("users")
            .child(userId)
            .child("categories")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "   📥 Найдено ${snapshot.childrenCount} пользовательских категорий")
                        snapshot.children.forEach { child ->
                            val data = child.value as? Map<*, *>
                            if (data != null) {
                                try {
                                    val name = data["name"] as? String ?: ""
                                    val type = data["type"] as? String ?: "expense"
                                    val icon = data["icon"] as? String ?: if (type == "expense") "💸" else "💰"
                                    val color = data["color"] as? String ?: if (type == "expense") "#4ECDC4" else "#118AB2"
                                    val monthlyLimit = (data["monthlyLimit"] as? Number)?.toDouble() ?: 0.0

                                    Log.d(TAG, "      📊 Категория '$name' лимит: $monthlyLimit")

                                    val category = Category(
                                        id = child.key ?: "",
                                        categoryName = name,
                                        icon = icon,
                                        color = color,
                                        monthlyLimit = monthlyLimit,
                                        type = type,
                                        isDefault = false
                                    )

                                    if (type == "expense") expenseList.add(category)
                                    else incomeList.add(category)

                                    Log.d(TAG, "      ✅ Пользовательская: $name ($type)")
                                } catch (e: Exception) {
                                    Log.e(TAG, "      ❌ Ошибка: ${e.message}")
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "   📭 Пользовательских категорий нет")
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "   ❌ Ошибка загрузки пользовательских категорий", error.toException())
                    checkComplete()
                }
            })

        // 2. ЗАГРУЗКА СТАНДАРТНЫХ РАСХОДОВ из categories/expenses/default
        Log.d(TAG, "📌 2. Загрузка стандартных расходов из categories/expenses/default")
        database.reference
            .child("categories")
            .child("expenses")
            .child("default")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "   📥 Найдено ${snapshot.childrenCount} стандартных расходов")
                        snapshot.children.forEach { child ->
                            val data = child.value as? Map<*, *>
                            if (data != null) {
                                try {
                                    val name = child.key ?: ""
                                    val icon = data["icon"] as? String ?: "💸"
                                    val color = data["color"] as? String ?: "#4ECDC4"

                                    if (expenseList.none { it.categoryName == name }) {
                                        val category = Category(
                                            id = "default_${name.lowercase(Locale.ROOT)}",
                                            categoryName = name,
                                            icon = icon,
                                            color = color,
                                            monthlyLimit = 0.0,
                                            type = "expense",
                                            isDefault = true
                                        )
                                        expenseList.add(category)
                                        Log.d(TAG, "      ✅ Стандартный расход: $name")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "      ❌ Ошибка: ${e.message}")
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "   📭 Стандартных расходов нет")
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "   ❌ Ошибка загрузки стандартных расходов", error.toException())
                    checkComplete()
                }
            })

        // 3. ЗАГРУЗКА СТАНДАРТНЫХ ДОХОДОВ из categories/incomes/default
        Log.d(TAG, "📌 3. Загрузка стандартных доходов из categories/incomes/default")
        database.reference
            .child("categories")
            .child("incomes")
            .child("default")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "   📥 Найдено ${snapshot.childrenCount} стандартных доходов")
                        snapshot.children.forEach { child ->
                            val data = child.value as? Map<*, *>
                            if (data != null) {
                                try {
                                    val name = child.key ?: ""
                                    val icon = data["icon"] as? String ?: "💰"
                                    val color = data["color"] as? String ?: "#118AB2"

                                    if (incomeList.none { it.categoryName == name }) {
                                        val category = Category(
                                            id = "default_${name.lowercase(Locale.ROOT)}",
                                            categoryName = name,
                                            icon = icon,
                                            color = color,
                                            monthlyLimit = 0.0,
                                            type = "income",
                                            isDefault = true
                                        )
                                        incomeList.add(category)
                                        Log.d(TAG, "      ✅ Стандартный доход: $name")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "      ❌ Ошибка: ${e.message}")
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "   📭 Стандартных доходов нет")
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "   ❌ Ошибка загрузки стандартных доходов", error.toException())
                    checkComplete()
                }
            })

        // 4. ЗАГРУЗКА СТАРЫХ ПОЛЬЗОВАТЕЛЬСКИХ КАТЕГОРИЙ
        Log.d(TAG, "📌 4. Загрузка старых пользовательских категорий")

        database.reference
            .child("categories")
            .child("expenses")
            .child("user")
            .child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "   📥 Найдено ${snapshot.childrenCount} старых расходов")
                        snapshot.children.forEach { child ->
                            val data = child.value as? Map<*, *>
                            if (data != null) {
                                try {
                                    val name = data["categoryName"] as? String ?: ""
                                    val icon = data["icon"] as? String ?: "💸"
                                    val color = data["color"] as? String ?: "#4ECDC4"
                                    val monthlyLimit = (data["monthlyLimit"] as? Number)?.toDouble() ?: 0.0

                                    if (name.isNotEmpty() && expenseList.none { it.categoryName == name }) {
                                        val category = Category(
                                            id = child.key ?: "",
                                            categoryName = name,
                                            icon = icon,
                                            color = color,
                                            monthlyLimit = monthlyLimit,
                                            type = "expense",
                                            isDefault = false
                                        )
                                        expenseList.add(category)
                                        Log.d(TAG, "      ✅ Старая категория расходов: $name (лимит: $monthlyLimit)")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "      ❌ Ошибка: ${e.message}")
                                }
                            }
                        }
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "   ❌ Ошибка загрузки старых расходов", error.toException())
                    checkComplete()
                }
            })

        database.reference
            .child("categories")
            .child("incomes")
            .child("user")
            .child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "   📥 Найдено ${snapshot.childrenCount} старых доходов")
                        snapshot.children.forEach { child ->
                            val data = child.value as? Map<*, *>
                            if (data != null) {
                                try {
                                    val name = data["categoryName"] as? String ?: ""
                                    val icon = data["icon"] as? String ?: "💰"
                                    val color = data["color"] as? String ?: "#118AB2"
                                    val monthlyLimit = (data["monthlyLimit"] as? Number)?.toDouble() ?: 0.0

                                    if (name.isNotEmpty() && incomeList.none { it.categoryName == name }) {
                                        val category = Category(
                                            id = child.key ?: "",
                                            categoryName = name,
                                            icon = icon,
                                            color = color,
                                            monthlyLimit = monthlyLimit,
                                            type = "income",
                                            isDefault = false
                                        )
                                        incomeList.add(category)
                                        Log.d(TAG, "      ✅ Старая категория доходов: $name")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "      ❌ Ошибка: ${e.message}")
                                }
                            }
                        }
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "   ❌ Ошибка загрузки старых доходов", error.toException())
                    checkComplete()
                }
            })
    }

    private fun loadStandardCategoriesOnly() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "📋 ЗАГРУЗКА СТАНДАРТНЫХ КАТЕГОРИЙ (FALLBACK)")
        Log.d(TAG, "========================================")

        val defaultExpenses = listOf(
            Category("default_food", "Продукты", "🛒", "#FF6B6B", 0.0, "expense", true),
            Category("default_transport", "Транспорт", "🚗", "#4ECDC4", 0.0, "expense", true),
            Category("default_cafe", "Кафе", "☕", "#FFD166", 0.0, "expense", true),
            Category("default_entertainment", "Развлечения", "🎬", "#118AB2", 0.0, "expense", true),
            Category("default_health", "Здоровье", "🏥", "#EF476F", 0.0, "expense", true),
            Category("default_clothing", "Одежда", "👕", "#7209B7", 0.0, "expense", true)
        )

        val defaultIncomes = listOf(
            Category("default_salary", "Зарплата", "💰", "#10B981", 0.0, "income", true),
            Category("default_freelance", "Фриланс", "💻", "#3B82F6", 0.0, "income", true),
            Category("default_investments", "Инвестиции", "📈", "#8B5CF6", 0.0, "income", true),
            Category("default_gifts", "Подарки", "🎁", "#F59E0B", 0.0, "income", true)
        )

        expenseCategories.postValue(defaultExpenses)
        incomeCategories.postValue(defaultIncomes)

        Log.d(TAG, "✅ ЗАГРУЖЕНО СТАНДАРТНЫХ КАТЕГОРИЙ:")
        Log.d(TAG, "   Расходы: ${defaultExpenses.size}")
        Log.d(TAG, "   Доходы: ${defaultIncomes.size}")
    }

    fun addUserCategory(category: Category, type: String, callback: (Boolean, String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "❌ Пользователь не авторизован!")
            callback(false, "Пользователь не авторизован")
            return
        }

        val userId = currentUser.uid
        Log.e(TAG, "🔴🔴🔴 addUserCategory ВЫЗВАН!")
        Log.e(TAG, "   userId: $userId")
        Log.e(TAG, "   Имя: ${category.categoryName}")
        Log.e(TAG, "   Тип: $type")
        Log.e(TAG, "   Лимит: ${category.monthlyLimit}")

        val existingCategories = if (type == "expense") {
            expenseCategories.value ?: emptyList()
        } else {
            incomeCategories.value ?: emptyList()
        }

        if (existingCategories.any { it.categoryName.equals(category.categoryName, ignoreCase = true) }) {
            Log.e(TAG, "❌ Категория с таким названием уже существует")
            callback(false, "Категория с таким названием уже существует")
            return
        }

        // СОХРАНЯЕМ В НЕСКОЛЬКО МЕСТ ДЛЯ НАДЕЖНОСТИ

        val newCategoryId = UUID.randomUUID().toString()

        // Место 1: users/{userId}/categories/{id}
        val path1 = database.reference
            .child("users")
            .child(userId)
            .child("categories")
            .child(newCategoryId)

        // Место 2: categories/{type}/user/{userId}/{id}
        val path2 = database.reference
            .child("categories")
            .child(if (type == "expense") "expenses" else "incomes")
            .child("user")
            .child(userId)
            .child(newCategoryId)

        // Место 3: users/{userId}/customCategories/{id}
        val path3 = database.reference
            .child("users")
            .child(userId)
            .child("customCategories")
            .child(newCategoryId)

        val categoryMap = mapOf(
            "name" to category.categoryName,
            "type" to type,
            "icon" to category.icon,
            "color" to category.color,
            "monthlyLimit" to if (type == "expense") category.monthlyLimit else 0.0,
            "createdAt" to System.currentTimeMillis()
        )

        Log.e(TAG, "📦 СОХРАНЯЕМ ДАННЫЕ: $categoryMap")
        Log.e(TAG, "📁 ПУТЬ 1: users/$userId/categories/$newCategoryId")
        Log.e(TAG, "📁 ПУТЬ 2: categories/${type}/user/$userId/$newCategoryId")
        Log.e(TAG, "📁 ПУТЬ 3: users/$userId/customCategories/$newCategoryId")

        var successCount = 0
        var failCount = 0

        fun checkComplete() {
            if (successCount + failCount == 3) {
                if (successCount > 0) {
                    val newCategory = Category(
                        id = newCategoryId,
                        categoryName = category.categoryName,
                        icon = category.icon,
                        color = category.color,
                        monthlyLimit = if (type == "expense") category.monthlyLimit else 0.0,
                        type = type,
                        isDefault = false
                    )

                    if (type == "expense") {
                        val currentList = expenseCategories.value?.toMutableList() ?: mutableListOf()
                        currentList.add(newCategory)
                        expenseCategories.postValue(currentList)
                        Log.e(TAG, "✅ Категория расходов добавлена в локальный список, всего: ${currentList.size}")
                    } else {
                        val currentList = incomeCategories.value?.toMutableList() ?: mutableListOf()
                        currentList.add(newCategory)
                        incomeCategories.postValue(currentList)
                        Log.e(TAG, "✅ Категория доходов добавлена в локальный список, всего: ${currentList.size}")
                    }
                    callback(true, null)
                } else {
                    Log.e(TAG, "❌ Не удалось сохранить категорию ни в одно место!")
                    callback(false, "Не удалось сохранить категорию")
                }
            }
        }

        // Сохраняем в путь 1
        path1.setValue(categoryMap)
            .addOnSuccessListener {
                Log.e(TAG, "✅ СОХРАНЕНО В ПУТЬ 1: users/$userId/categories/$newCategoryId")
                successCount++
                checkComplete()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Ошибка сохранения в путь 1: ${e.message}")
                failCount++
                checkComplete()
            }

        // Сохраняем в путь 2
        path2.setValue(categoryMap)
            .addOnSuccessListener {
                Log.e(TAG, "✅ СОХРАНЕНО В ПУТЬ 2: categories/${type}/user/$userId/$newCategoryId")
                successCount++
                checkComplete()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Ошибка сохранения в путь 2: ${e.message}")
                failCount++
                checkComplete()
            }

        // Сохраняем в путь 3
        path3.setValue(categoryMap)
            .addOnSuccessListener {
                Log.e(TAG, "✅ СОХРАНЕНО В ПУТЬ 3: users/$userId/customCategories/$newCategoryId")
                successCount++
                checkComplete()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Ошибка сохранения в путь 3: ${e.message}")
                failCount++
                checkComplete()
            }
    }

    fun updateUserCategory(category: Category, type: String, callback: (Boolean, String?) -> Unit) {
        val currentUser = auth.currentUser ?: run {
            callback(false, "Пользователь не авторизован")
            return
        }

        if (category.isDefault) {
            callback(false, "Стандартную категорию нельзя редактировать")
            return
        }

        val userId = currentUser.uid

        Log.d(TAG, "🔄 ОБНОВЛЕНИЕ КАТЕГОРИИ")
        Log.d(TAG, "   ID: ${category.id}")
        Log.d(TAG, "   Имя: ${category.categoryName}")
        Log.d(TAG, "   Тип: ${category.type}")
        Log.d(TAG, "   Лимит: ${category.monthlyLimit}")

        val updates = mutableMapOf<String, Any>(
            "name" to category.categoryName,
            "icon" to category.icon,
            "color" to category.color
        )

        if (category.type == "expense") {
            updates["monthlyLimit"] = category.monthlyLimit
            Log.d(TAG, "   ✅ Лимит добавлен в обновление: ${category.monthlyLimit}")
        }

        // Обновляем во всех местах
        val path1 = database.reference
            .child("users")
            .child(userId)
            .child("categories")
            .child(category.id)

        val path2 = database.reference
            .child("categories")
            .child(if (type == "expense") "expenses" else "incomes")
            .child("user")
            .child(userId)
            .child(category.id)

        val path3 = database.reference
            .child("users")
            .child(userId)
            .child("customCategories")
            .child(category.id)

        path1.updateChildren(updates)
            .addOnSuccessListener { Log.d(TAG, "✅ Обновлено в пути 1") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Ошибка пути 1: ${e.message}") }

        path2.updateChildren(updates)
            .addOnSuccessListener { Log.d(TAG, "✅ Обновлено в пути 2") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Ошибка пути 2: ${e.message}") }

        path3.updateChildren(updates)
            .addOnSuccessListener { Log.d(TAG, "✅ Обновлено в пути 3") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Ошибка пути 3: ${e.message}") }
            .addOnCompleteListener {
                if (type == "expense") {
                    val currentList = expenseCategories.value?.toMutableList() ?: mutableListOf()
                    val index = currentList.indexOfFirst { it.id == category.id }
                    if (index != -1) {
                        currentList[index] = category
                        expenseCategories.postValue(currentList)
                    }
                } else {
                    val currentList = incomeCategories.value?.toMutableList() ?: mutableListOf()
                    val index = currentList.indexOfFirst { it.id == category.id }
                    if (index != -1) {
                        currentList[index] = category
                        incomeCategories.postValue(currentList)
                    }
                }
                callback(true, null)
            }
    }

    fun deleteUserCategory(categoryId: String, type: String, callback: (Boolean, String?) -> Unit) {
        val currentUser = auth.currentUser ?: run {
            callback(false, "Пользователь не авторизован")
            return
        }

        val userId = currentUser.uid

        Log.d(TAG, "🗑️ УДАЛЕНИЕ КАТЕГОРИИ ID: $categoryId, тип: $type")

        val path1 = database.reference
            .child("users")
            .child(userId)
            .child("categories")
            .child(categoryId)

        val path2 = database.reference
            .child("categories")
            .child(if (type == "expense") "expenses" else "incomes")
            .child("user")
            .child(userId)
            .child(categoryId)

        val path3 = database.reference
            .child("users")
            .child(userId)
            .child("customCategories")
            .child(categoryId)

        path1.removeValue()
        path2.removeValue()
        path3.removeValue()

        if (type == "expense") {
            val currentList = expenseCategories.value?.filter { it.id != categoryId } ?: emptyList()
            expenseCategories.postValue(currentList)
        } else {
            val currentList = incomeCategories.value?.filter { it.id != categoryId } ?: emptyList()
            incomeCategories.postValue(currentList)
        }

        callback(true, null)
    }

    fun cleanup() {
        Log.d(TAG, "🧹 Очистка репозитория")
    }
}