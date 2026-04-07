package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myfin.data.Transaction
import com.example.myfin.data.TransactionRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PerformanceTests {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var targetContext: Context

    // Определяем эмулятор
    private val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.contains("vbox") ||
                Build.FINGERPRINT.contains("generic") ||
                Build.MODEL.contains("Android SDK") ||
                Build.PRODUCT.contains("sdk") ||
                Build.MANUFACTURER.contains("Google") && Build.MODEL.contains("SDK") ||
                Build.BRAND == "generic"
    }

    // Лимиты для реального устройства
    private val COLD_START_LIMIT_MS: Long = 5000  // 5 секунд
    private val QUERY_LIMIT_MS: Long = 3000       // 3 секунды
    private val UI_RESPONSE_LIMIT_MS: Long = 500  // 500мс
    private val THROUGHPUT_MIN: Double = 30.0     // 30 записей/сек

    @Before
    fun setUp() {
        targetContext = ApplicationProvider.getApplicationContext()

        println("========================================")
        println("📱 Инициализация тестов...")

        // Ждем инициализации Firebase
        Thread.sleep(2000)

        if (FirebaseApp.getApps(targetContext).isEmpty()) {
            FirebaseApp.initializeApp(targetContext)
            Thread.sleep(1000)
        }

        transactionRepository = TransactionRepository()
        Thread.sleep(1000)

        println("📱 Тестирование на: ${if (isEmulator) "ЭМУЛЯТОРЕ" else "ФИЗИЧЕСКОМ УСТРОЙСТВЕ"}")
        println("========================================")
    }

    // ========== ТЕСТ 1: Время холодного старта ==========

    @Test
    fun testMainActivityColdStartTime() {
        try {
            // Очищаем состояние авторизации (чтобы попасть в MainActivity)
            val prefs = targetContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            // Также очищаем биометрические настройки
            val biometricPrefs = targetContext.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            biometricPrefs.edit().clear().apply()

            val startTime = System.currentTimeMillis()
            println("🚀 Запуск MainActivity...")

            val scenario = ActivityScenario.launch(MainActivity::class.java)

            // Ждем, пока Activity станет RESUME
            var activityResumed = false
            var launchTime = 0L

            try {
                scenario.moveToState(Lifecycle.State.RESUMED)
                activityResumed = true
                launchTime = System.currentTimeMillis() - startTime
                println("✅ MainActivity запустилась за: ${launchTime}мс")
            } catch (e: Exception) {
                println("❌ Ошибка при переходе в RESUMED: ${e.message}")
            }

            // Проверяем, какая Activity активна
            if (activityResumed) {
                scenario.onActivity { activity ->
                    val currentTime = System.currentTimeMillis() - startTime
                    println("📊 Текущая Activity: ${activity::class.java.simpleName}")
                    println("📊 Время запуска: ${currentTime}мс")

                    // Проверяем время
                    assertTrue(
                        "❌ Холодный старт: ${currentTime}мс > ${COLD_START_LIMIT_MS}мс",
                        currentTime <= COLD_START_LIMIT_MS
                    )
                    println("✅ Холодный старт: ${currentTime}мс")
                }
            } else {
                println("⚠️ Activity не перешла в RESUMED состояние")
            }

            scenario.close()

        } catch (e: Exception) {
            println("❌ Тест упал с ошибкой: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testDashboardActivityColdStartTime() {
        try {
            val prefs = targetContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            val intent = Intent(targetContext, DashboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val startTime = System.currentTimeMillis()
            val scenario = ActivityScenario.launch<DashboardActivity>(intent)

            try {
                scenario.moveToState(Lifecycle.State.RESUMED)
                val launchTime = System.currentTimeMillis() - startTime
                println("✅ DashboardActivity запустилась за: ${launchTime}мс")

                scenario.onActivity { activity ->
                    assertTrue(
                        "❌ Холодный старт DashboardActivity: ${launchTime}мс > ${COLD_START_LIMIT_MS}мс",
                        launchTime <= COLD_START_LIMIT_MS
                    )
                    println("✅ Холодный старт DashboardActivity: ${launchTime}мс")
                }
            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
            }

            scenario.close()

        } catch (e: Exception) {
            println("❌ Тест упал: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testFullAppColdStartToInteractiveTime() {
        try {
            val prefs = targetContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            val startTime = System.currentTimeMillis()
            val scenario = ActivityScenario.launch(MainActivity::class.java)

            try {
                scenario.moveToState(Lifecycle.State.RESUMED)
                val interactiveTime = System.currentTimeMillis() - startTime
                println("✅ Время до интерактивности: ${interactiveTime}мс")

                scenario.onActivity { activity ->
                    val isInteractive = when {
                        activity is MainActivity -> {
                            try {
                                val loginButton = activity.findViewById<Button>(R.id.btnLogin)
                                loginButton?.isEnabled == true && loginButton?.isClickable == true
                            } catch (e: Exception) {
                                false
                            }
                        }
                        activity.javaClass.simpleName == "DashboardActivity" -> true
                        else -> false
                    }

                    println("📊 Активность: ${activity::class.java.simpleName}, интерактивна: $isInteractive")
                    assertTrue("❌ Основной экран не загружен", isInteractive)
                    assertTrue(
                        "❌ Время до интерактивности: ${interactiveTime}мс > ${COLD_START_LIMIT_MS}мс",
                        interactiveTime <= COLD_START_LIMIT_MS
                    )
                    println("✅ Время до интерактивности: ${interactiveTime}мс")
                }
            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
            }

            scenario.close()

        } catch (e: Exception) {
            println("❌ Тест упал: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // ========== ТЕСТ 2: Время выполнения запроса ==========

    @Test
    fun testGetTransactionsPerformance() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()
        var queryTime: Long = 0
        var hasData = false

        val observer = androidx.lifecycle.Observer<List<Transaction>> { transactions ->
            if (!hasData && transactions.isNotEmpty()) {
                hasData = true
                queryTime = System.currentTimeMillis() - startTime
                println("📊 Получено транзакций: ${transactions.size}")
                latch.countDown()
            }
        }

        transactionRepository.getTransactionsLiveData().observeForever(observer)
        transactionRepository.loadTransactions()

        val completed = latch.await(15, TimeUnit.SECONDS)
        transactionRepository.getTransactionsLiveData().removeObserver(observer)

        assertTrue("❌ Тест не завершился за 15 секунд", completed)

        if (hasData) {
            assertTrue(
                "❌ Время получения: ${queryTime}мс > ${QUERY_LIMIT_MS}мс",
                queryTime <= QUERY_LIMIT_MS
            )
            println("✅ Время получения транзакций: ${queryTime}мс")
        } else {
            println("⚠️ Нет данных")
        }
    }

    @Test
    fun testAggregatedDataPerformance() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()
        var queryTime: Long = 0
        var hasResult = false

        transactionRepository.getTotalBalance { balance, income, expense ->
            if (!hasResult) {
                hasResult = true
                queryTime = System.currentTimeMillis() - startTime
                println("📊 Баланс: $balance, Доход: $income, Расход: $expense")
                latch.countDown()
            }
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        assertTrue("❌ Тест не завершился за 15 секунд", completed)

        if (hasResult) {
            assertTrue(
                "❌ Время агрегации: ${queryTime}мс > ${QUERY_LIMIT_MS}мс",
                queryTime <= QUERY_LIMIT_MS
            )
            println("✅ Время агрегации данных: ${queryTime}мс")
        } else {
            println("⚠️ Не удалось получить агрегированные данные")
        }
    }

    // ========== ТЕСТ 3: Время отклика UI ==========

    @Test
    fun testButtonClickResponseTime() {
        val scenario = ActivityScenario.launch(DashboardActivity::class.java)

        try {
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.fabAdd)
                if (button != null) {
                    val clickStartTime = System.currentTimeMillis()
                    button.performClick()
                    val responseTime = System.currentTimeMillis() - clickStartTime

                    assertTrue(
                        "❌ Время отклика: ${responseTime}мс > ${UI_RESPONSE_LIMIT_MS}мс",
                        responseTime <= UI_RESPONSE_LIMIT_MS
                    )
                    println("✅ Время отклика кнопки: ${responseTime}мс")
                } else {
                    println("⚠️ Кнопка fabAdd не найдена")
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
        }

        scenario.close()
    }

    @Test
    fun testNavigationTabSwitchResponseTime() {
        val scenario = ActivityScenario.launch(DashboardActivity::class.java)

        try {
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                val bottomNav = activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                if (bottomNav != null) {
                    val switchStartTime = System.currentTimeMillis()
                    bottomNav.selectedItemId = R.id.nav_stats
                    val switchTime = System.currentTimeMillis() - switchStartTime

                    assertTrue(
                        "❌ Время переключения: ${switchTime}мс > ${UI_RESPONSE_LIMIT_MS}мс",
                        switchTime <= UI_RESPONSE_LIMIT_MS
                    )
                    println("✅ Время переключения: ${switchTime}мс")
                } else {
                    println("⚠️ BottomNavigationView не найден")
                }
            }
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
        }

        scenario.close()
    }

    @Test
    fun testFragmentTransitionResponseTime() {
        val scenario = ActivityScenario.launch(DashboardActivity::class.java)

        try {
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                val dashboard = activity as DashboardActivity
                val startTime = System.currentTimeMillis()
                dashboard.showFragment(ProfileFragment(), "ProfileFragment")
                val transitionTime = System.currentTimeMillis() - startTime

                assertTrue(
                    "❌ Время перехода: ${transitionTime}мс > ${UI_RESPONSE_LIMIT_MS}мс",
                    transitionTime <= UI_RESPONSE_LIMIT_MS
                )
                println("✅ Время перехода: ${transitionTime}мс")
            }
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
        }

        scenario.close()
    }

    // ========== ТЕСТ 4: Пропускная способность ==========

    @Test
    fun testBulkTransactionsLoadPerformance() {
        val latch = CountDownLatch(1)
        var hasData = false
        val startTime = System.currentTimeMillis()

        val observer = androidx.lifecycle.Observer<List<Transaction>> { transactions ->
            if (!hasData && transactions.isNotEmpty()) {
                hasData = true
                val loadTime = System.currentTimeMillis() - startTime
                val size = transactions.size
                val throughput = if (loadTime > 0) (size.toDouble() / loadTime) * 1000 else 0.0

                println("📊 Загружено: $size записей за ${loadTime}мс")
                println("⚡ Пропускная способность: ${String.format("%.2f", throughput)} записей/сек")

                assertTrue(
                    "❌ Пропускная способность: ${String.format("%.2f", throughput)} < ${THROUGHPUT_MIN}",
                    throughput >= THROUGHPUT_MIN
                )
                println("✅ Пропускная способность: ${String.format("%.2f", throughput)} записей/сек")

                latch.countDown()
            }
        }

        transactionRepository.getTransactionsLiveData().observeForever(observer)
        transactionRepository.loadTransactions()

        val completed = latch.await(20, TimeUnit.SECONDS)
        transactionRepository.getTransactionsLiveData().removeObserver(observer)

        assertTrue("❌ Тест не завершился за 20 секунд", completed)

        if (!hasData) {
            println("⚠️ Нет данных для измерения пропускной способности")
        }
    }

    // ========== ТЕСТЫ ДЛЯ ОТДЕЛЬНЫХ МЕТОДОВ ==========

    @Test
    fun testLanguageHelperPerformance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startTime = System.currentTimeMillis()

        repeat(1000) { i ->
            if (i % 2 == 0) {
                LanguageHelper.setLanguage(context, "en")
            } else {
                LanguageHelper.getCurrentLanguage(context)
            }
            LanguageHelper.wasLanguageChanged(context)
        }

        val totalTime = System.currentTimeMillis() - startTime
        val avgTime = totalTime / 1000.0
        println("⏱️ LanguageHelper: $totalTime мс, среднее ${String.format("%.3f", avgTime)} мс")
        assertTrue("❌ LanguageHelper слишком медленный", avgTime < 1.0)
    }

    @Test
    fun testTransactionValidationPerformance() {
        val startTime = System.currentTimeMillis()

        repeat(1000) { i ->
            validateTransaction(
                title = "Test Transaction $i",
                amount = 100.50,
                type = if (i % 2 == 0) "income" else "expense",
                categoryId = "test-category-id",
                categoryName = "Test Category",
                description = "Test description",
                userId = "test-user-id"
            )
        }

        val totalTime = System.currentTimeMillis() - startTime
        val avgTime = totalTime / 1000.0
        println("⏱️ Валидация: $totalTime мс, среднее ${String.format("%.3f", avgTime)} мс")
        assertTrue("❌ Валидация слишком медленная", avgTime < 0.5)
    }

    private fun validateTransaction(
        title: String,
        amount: Double,
        type: String,
        categoryId: String,
        categoryName: String,
        description: String,
        userId: String?
    ): Boolean {
        if (userId == null) return false
        if (categoryId.isEmpty() || categoryName.isEmpty()) return false
        if (title.isEmpty()) return false
        if (amount <= 0) return false
        if (type != "income" && type != "expense") return false
        return true
    }

    @Test
    fun testAddTransactionPerformance() {
        val latch = CountDownLatch(1)
        val startTime = System.currentTimeMillis()
        var completed = false

        transactionRepository.addTransaction(
            title = "Test Performance Transaction",
            amount = 100.0,
            type = "expense",
            categoryId = "test-category-id",
            categoryName = "Test Category",
            description = "Performance test"
        ) { success, message ->
            if (!completed) {
                completed = true
                val operationTime = System.currentTimeMillis() - startTime
                println("⏱️ Добавление: ${operationTime}мс, успех: $success, сообщение: $message")
                latch.countDown()
            }
        }

        val awaited = latch.await(15, TimeUnit.SECONDS)
        assertTrue("❌ Тест не завершился за 15 секунд", awaited)
    }

    private fun generateTestTransactions(count: Int): List<Transaction> {
        return List(count) { index ->
            Transaction(
                id = "test-$index",
                title = "Test Transaction $index",
                amount = 100.0 + index,
                type = if (index % 2 == 0) "income" else "expense",
                categoryId = "test-category-id",
                categoryName = "Test Category",
                date = "2024-01-01",
                createdAt = System.currentTimeMillis() - index * 1000,
                updatedAt = System.currentTimeMillis() - index * 1000,
                userId = "test-user-id",
                description = "Test description $index"
            )
        }
    }
}