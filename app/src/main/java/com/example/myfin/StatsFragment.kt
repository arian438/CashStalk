package com.example.myfin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.adapter.CategoryStatAdapter
import com.example.myfin.data.*
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.*

class StatsFragment : Fragment(), CurrencyUpdateListener {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var userRepository: UserRepository
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private var allTransactions: List<Transaction> = emptyList()
    private var expenseCategories: List<Category> = emptyList()
    private var incomeCategories: List<Category> = emptyList()
    private var currentPeriod = "month"
    private var currentChartType = "bar" // "bar" или "pie"
    private var currentPieType = "expense" // "expense" или "income"
    private var currentCurrencySymbol: String = "₽"
    private var currentUser: User? = null

    private lateinit var notificationBadge: TextView
    private lateinit var bellContainer: View
    private lateinit var totalIncomeValue: TextView
    private lateinit var totalExpenseValue: TextView
    private lateinit var statsTitle: TextView

    private lateinit var barChartCard: View
    private lateinit var pieChartCard: View
    private lateinit var btnPieExpenses: TextView
    private lateinit var btnPieIncomes: TextView
    private lateinit var categoriesStatsRecyclerView: RecyclerView
    private lateinit var emptyPieChartText: TextView
    private lateinit var pieChartView: PieChartView
    private lateinit var piePeriodSubtitle: TextView // Новая подпись для периода

    private lateinit var categoryStatAdapter: CategoryStatAdapter

    companion object {
        private const val TAG = "StatsFragment"
    }

    private val badgeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Badge update broadcast received")
            updateBadgeImmediately()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? DashboardActivity)?.addCurrencyUpdateListener(this)
    }

    override fun onDetach() {
        super.onDetach()
        (activity as? DashboardActivity)?.removeCurrencyUpdateListener(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_stats, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())

        transactionRepository = TransactionRepository()
        categoryRepository = try {
            (activity as DashboardActivity).getCategoryRepository()
        } catch (e: Exception) {
            CategoryRepository()
        }
        userRepository = UserRepository()

        totalIncomeValue = view.findViewById(R.id.totalIncomeValue)
        totalExpenseValue = view.findViewById(R.id.totalExpenseValue)
        statsTitle = view.findViewById(R.id.statsTitle)

        bellContainer = view.findViewById(R.id.bellContainer)
        notificationBadge = view.findViewById(R.id.notificationBadge)

        barChartCard = view.findViewById(R.id.barChartCard)
        pieChartCard = view.findViewById(R.id.pieChartCard)
        btnPieExpenses = view.findViewById(R.id.btnPieExpenses)
        btnPieIncomes = view.findViewById(R.id.btnPieIncomes)
        categoriesStatsRecyclerView = view.findViewById(R.id.categoriesStatsRecyclerView)
        emptyPieChartText = view.findViewById(R.id.emptyPieChartText)
        pieChartView = view.findViewById(R.id.pieChartView)
        piePeriodSubtitle = view.findViewById(R.id.piePeriodSubtitle) // Инициализация новой подписи

        // Настройка RecyclerView для категорий
        categoryStatAdapter = CategoryStatAdapter(emptyList())
        categoriesStatsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        categoriesStatsRecyclerView.adapter = categoryStatAdapter

        view.findViewById<View>(R.id.ic_menu_stats)?.setOnClickListener {
            (activity as? DashboardActivity)?.openDrawer()
        }

        bellContainer.setOnClickListener {
            (activity as? DashboardActivity)?.showFragment(NotificationsFragment())
        }

        // Переключатель периодов (неделя/месяц/год)
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)
        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentPeriod = when (checkedId) {
                    R.id.btnWeek -> "week"
                    R.id.btnMonth -> "month"
                    R.id.btnYear -> "year"
                    else -> "month"
                }
                loadStats()
                // Обновляем подпись периода при смене периода
                updatePiePeriodSubtitle()
            }
        }

        // Переключатель типа диаграммы (линейная/круговая)
        val chartToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.chartToggleGroup)
        chartToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentChartType = when (checkedId) {
                    R.id.btnBarChart -> "bar"
                    R.id.btnPieChart -> "pie"
                    else -> "bar"
                }
                updateChartVisibility()
                if (currentChartType == "pie") {
                    loadPieChartData()
                    updatePiePeriodSubtitle()
                }
            }
        }

        // Переключатель доходы/расходы для круговой диаграммы
        btnPieExpenses.setOnClickListener {
            updatePieTabAppearance(btnPieExpenses, btnPieIncomes)
            currentPieType = "expense"
            statsTitle.text = getString(R.string.expenses_by_category)
            loadPieChartData()
            updatePiePeriodSubtitle()
        }

        btnPieIncomes.setOnClickListener {
            updatePieTabAppearance(btnPieIncomes, btnPieExpenses)
            currentPieType = "income"
            statsTitle.text = getString(R.string.incomes_by_category)
            loadPieChartData()
            updatePiePeriodSubtitle()
        }

        setupUserObserver()
        loadTransactions()
        setupCategoriesObservers()
        setupNotificationObserver()
        registerBadgeReceiver()
        updateBadgeImmediately()
    }

    private fun updatePieTabAppearance(selectedTab: TextView, unselectedTab: TextView) {
        selectedTab.setBackgroundResource(R.drawable.bg_button)
        selectedTab.setTextColor(Color.WHITE)
        unselectedTab.setBackgroundResource(R.drawable.bg_input)
        unselectedTab.setTextColor(Color.parseColor("#666666"))
    }

    private fun updateChartVisibility() {
        if (currentChartType == "bar") {
            barChartCard.visibility = View.VISIBLE
            pieChartCard.visibility = View.GONE
            statsTitle.text = getString(R.string.statistics_period_month)
        } else {
            barChartCard.visibility = View.GONE
            pieChartCard.visibility = View.VISIBLE
            statsTitle.text = if (currentPieType == "expense")
                getString(R.string.expenses_by_category)
            else
                getString(R.string.incomes_by_category)
            updatePiePeriodSubtitle()
        }
    }

    /**
     * Обновляет подпись периода для круговой диаграммы
     */
    private fun updatePiePeriodSubtitle() {
        if (!::piePeriodSubtitle.isInitialized) return

        val periodText = when (currentPeriod) {
            "week" -> getString(R.string.statistics_period_week)
            "month" -> getString(R.string.statistics_period_month)
            "year" -> getString(R.string.statistics_period_year)
            else -> getString(R.string.statistics_period_month)
        }

        val dateRangeText = getCurrentPeriodDateRange()
        piePeriodSubtitle.text = "$periodText • $dateRangeText"
    }

    /**
     * Возвращает диапазон дат для текущего периода
     */
    private fun getCurrentPeriodDateRange(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

        return when (currentPeriod) {
            "week" -> {
                calendar.firstDayOfWeek = Calendar.MONDAY
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val startOfWeek = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val endOfWeek = calendar.time
                "${dateFormat.format(startOfWeek)} - ${dateFormat.format(endOfWeek)}"
            }
            "month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val startOfMonth = calendar.time
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                val endOfMonth = calendar.time
                "${dateFormat.format(startOfMonth)} - ${dateFormat.format(endOfMonth)}"
            }
            "year" -> {
                val year = yearFormat.format(Calendar.getInstance().time)
                getString(R.string.year_full, year)
            }
            else -> ""
        }
    }

    private fun registerBadgeReceiver() {
        val filter = IntentFilter(DashboardActivity.ACTION_UPDATE_BADGES)
        localBroadcastManager.registerReceiver(badgeUpdateReceiver, filter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            localBroadcastManager.unregisterReceiver(badgeUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    private fun updateBadgeImmediately() {
        val activity = activity as? DashboardActivity
        val unreadCount = activity?.getUnreadNotificationCount() ?: 0
        updateNotificationBadge(unreadCount)
    }

    private fun setupNotificationObserver() {
        val activity = activity as? DashboardActivity
        activity?.getNotificationRepository()?.notificationsLiveData?.observe(viewLifecycleOwner) { notifications ->
            val unreadCount = notifications.count { !it.isRead }
            updateNotificationBadge(unreadCount)
        }
    }

    private fun updateNotificationBadge(count: Int) {
        activity?.runOnUiThread {
            if (count > 0) {
                notificationBadge.visibility = View.VISIBLE
                notificationBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                notificationBadge.visibility = View.GONE
            }
        }
    }

    override fun onCurrencyChanged(currencySymbol: String) {
        currentCurrencySymbol = currencySymbol
        activity?.runOnUiThread {
            loadStats()
        }
    }

    private fun setupUserObserver() {
        userRepository.getCurrentUserLiveData().observe(viewLifecycleOwner, Observer { user ->
            if (user != null) {
                currentUser = user
                currentCurrencySymbol = user.currencySymbol
                loadStats()
            }
        })
        userRepository.loadCurrentUser()
    }

    private fun setupCategoriesObservers() {
        categoryRepository.expenseCategories.observe(viewLifecycleOwner) { categories ->
            expenseCategories = categories
            if (currentChartType == "pie" && currentPieType == "expense") {
                loadPieChartData()
            }
        }

        categoryRepository.incomeCategories.observe(viewLifecycleOwner) { categories ->
            incomeCategories = categories
            if (currentChartType == "pie" && currentPieType == "income") {
                loadPieChartData()
            }
        }

        categoryRepository.loadAllCategories()
    }

    private fun loadTransactions() {
        transactionRepository.getTransactionsLiveData().observe(viewLifecycleOwner, Observer { transactions ->
            allTransactions = transactions ?: emptyList()
            loadStats()
        })
        transactionRepository.loadTransactions()
    }

    private fun loadStats() {
        val filteredTransactions = when (currentPeriod) {
            "week" -> getThisWeekTransactions()
            "month" -> getThisMonthTransactions()
            "year" -> getThisYearTransactions()
            else -> getThisMonthTransactions()
        }

        val stats = calculateBarStats(filteredTransactions)
        displayBarStats(stats)

        if (currentChartType == "pie") {
            loadPieChartData()
        }
    }

    private fun loadPieChartData() {
        val filteredTransactions = when (currentPeriod) {
            "week" -> getThisWeekTransactions()
            "month" -> getThisMonthTransactions()
            "year" -> getThisYearTransactions()
            else -> getThisMonthTransactions()
        }

        val categoryStats = calculatePieStats(filteredTransactions, currentPieType)
        displayPieStats(categoryStats)
    }

    private fun calculatePieStats(transactions: List<Transaction>, type: String): List<CategoryStat> {
        val filteredTransactions = transactions.filter { it.type == type }
        val total = filteredTransactions.sumOf { it.amount }

        if (total == 0.0) return emptyList()

        val categoryMap = mutableMapOf<String, Triple<Double, String, String>>()
        val categories = if (type == "expense") expenseCategories else incomeCategories

        filteredTransactions.forEach { transaction ->
            val category = categories.find {
                it.id == transaction.categoryId || it.categoryName == transaction.categoryName
            }
            val categoryName = category?.categoryName ?: transaction.categoryName ?: "Другое"
            val color = category?.color ?: if (type == "expense") "#EF4444" else "#0FB572"

            val currentAmount = categoryMap[categoryName]?.first ?: 0.0
            categoryMap[categoryName] = Triple(currentAmount + transaction.amount, color, categoryName)
        }

        return categoryMap.map { (_, triple) ->
            val (amount, color, name) = triple
            val percentage = (amount / total) * 100
            CategoryStat(
                categoryName = name,
                amount = amount,
                percentage = percentage,
                color = color,
                currencySymbol = currentCurrencySymbol
            )
        }.sortedByDescending { it.amount }
    }

    private fun displayPieStats(stats: List<CategoryStat>) {
        if (stats.isEmpty()) {
            emptyPieChartText.visibility = View.VISIBLE
            categoriesStatsRecyclerView.visibility = View.GONE
            pieChartView.visibility = View.GONE
        } else {
            emptyPieChartText.visibility = View.GONE
            categoriesStatsRecyclerView.visibility = View.VISIBLE
            pieChartView.visibility = View.VISIBLE

            // Обновляем данные в RecyclerView
            categoryStatAdapter.updateData(stats)

            // Обновляем круговую диаграмму
            pieChartView.setData(stats)
        }
    }

    private fun getThisWeekTransactions(): List<Transaction> {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis

        return allTransactions.filter {
            it.createdAt in startOfWeek..endOfWeek
        }
    }

    private fun getThisMonthTransactions(): List<Transaction> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.timeInMillis

        return allTransactions.filter {
            it.createdAt in startOfMonth..endOfMonth
        }
    }

    private fun getThisYearTransactions(): List<Transaction> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfYear = calendar.timeInMillis

        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfYear = calendar.timeInMillis

        return allTransactions.filter {
            it.createdAt in startOfYear..endOfYear
        }
    }

    private fun calculateBarStats(transactions: List<Transaction>): List<MonthStats> {
        val statsMap = mutableMapOf<String, MonthStatsData>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())

        transactions.forEach { transaction ->
            try {
                val date = dateFormat.parse(transaction.date) ?: Date()
                val monthKey = monthFormat.format(date)

                val data = statsMap.getOrPut(monthKey) { MonthStatsData(0.0, 0.0) }

                if (transaction.type == "income") {
                    data.income += transaction.amount
                } else {
                    data.expense += transaction.amount
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing date: ${transaction.date}", e)
            }
        }

        val sortedMonths = statsMap.keys.sortedBy {
            try {
                SimpleDateFormat("MMM", Locale.getDefault()).parse(it)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        return sortedMonths.map { monthName ->
            val data = statsMap[monthName]!!
            val total = data.income + data.expense
            val incomeWeight = if (total > 0) (data.income / total).toFloat() else 0f
            val expenseWeight = if (total > 0) (data.expense / total).toFloat() else 0f

            MonthStats(
                monthName = monthName,
                income = data.income,
                expense = data.expense,
                incomeWeight = incomeWeight,
                expenseWeight = expenseWeight
            )
        }
    }

    private fun displayBarStats(stats: List<MonthStats>) {
        val view = view ?: return

        val rows = listOf(
            R.id.rowJun, R.id.rowJul, R.id.rowAug,
            R.id.rowSep, R.id.rowOct, R.id.rowNov
        )

        val lastSixMonths = stats.takeLast(6)

        rows.forEachIndexed { index, rowId ->
            if (index < lastSixMonths.size) {
                bindBarRow(view, rowId, lastSixMonths[index])
            } else {
                view.findViewById<View>(rowId)?.visibility = View.GONE
            }
        }

        val periodTitleRes = when (currentPeriod) {
            "week" -> R.string.statistics_period_week
            "month" -> R.string.statistics_period_month
            "year" -> R.string.statistics_period_year
            else -> R.string.statistics
        }

        if (currentChartType == "bar") {
            statsTitle.text = getString(periodTitleRes)
        }

        val totalIncome = stats.sumOf { it.income }
        val totalExpense = stats.sumOf { it.expense }

        totalIncomeValue.text = String.format("%,.0f %s", totalIncome, currentCurrencySymbol)
        totalExpenseValue.text = String.format("%,.0f %s", totalExpense, currentCurrencySymbol)
    }

    private fun bindBarRow(root: View, rowId: Int, stats: MonthStats) {
        val container = root.findViewById<View>(rowId) ?: return
        container.visibility = View.VISIBLE

        container.findViewById<TextView>(R.id.monthTitle)?.text = stats.monthName
        container.findViewById<TextView>(R.id.incomeValue)?.text =
            String.format("%,.0f %s", stats.income, currentCurrencySymbol)
        container.findViewById<TextView>(R.id.expenseValue)?.text =
            String.format("%,.0f %s", stats.expense, currentCurrencySymbol)

        setWeight(container.findViewById(R.id.barIncome), stats.incomeWeight)
        setWeight(container.findViewById(R.id.barExpense), stats.expenseWeight)
    }

    private fun setWeight(view: View?, weight: Float) {
        val params = view?.layoutParams as? LinearLayout.LayoutParams ?: return
        params.weight = weight
        view.layoutParams = params
    }

    private data class MonthStats(
        val monthName: String,
        val income: Double,
        val expense: Double,
        val incomeWeight: Float,
        val expenseWeight: Float
    )

    private data class MonthStatsData(
        var income: Double,
        var expense: Double
    )

    override fun onResume() {
        super.onResume()
        transactionRepository.loadTransactions()
        userRepository.loadCurrentUser()
        categoryRepository.loadAllCategories()

        val activity = activity as? DashboardActivity
        activity?.refreshNotificationBadge()
        updateBadgeImmediately()
    }
}