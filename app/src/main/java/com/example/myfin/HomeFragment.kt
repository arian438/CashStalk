package com.example.myfin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myfin.data.*
import com.google.firebase.auth.FirebaseAuth
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment(),
    AddTransactionDialogFragment.OnTransactionAddedListener,
    CurrencyUpdateListener {

    private var isShowingAllTransactions = false
    private var lastUpdateTime = 0L
    private val UPDATE_COOLDOWN_MS = 2000L

    private lateinit var backButton: ImageView
    private lateinit var transactionsTitle: TextView
    private lateinit var viewAllTextView: TextView
    private lateinit var transactionsTitleContainer: LinearLayout
    private lateinit var notificationBadge: TextView
    private lateinit var bellContainer: View
    private lateinit var localBroadcastManager: LocalBroadcastManager

    companion object {
        private const val TAG = "HomeFragment"
    }

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var userRepository: UserRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var totalBalanceText: TextView
    private lateinit var totalIncomeText: TextView
    private lateinit var totalExpenseText: TextView
    private lateinit var welcomeText: TextView
    private lateinit var userNameText: TextView
    private lateinit var transactionsContainer: LinearLayout
    private lateinit var emptyStateView: View

    private var transactions: List<Transaction> = emptyList()
    private var expenseCategories: List<Category> = emptyList()
    private var incomeCategories: List<Category> = emptyList()
    private var currentUser: User? = null
    private val handler = Handler(Looper.getMainLooper())
    private var balanceLoadPending = false
    private var isFragmentActive = true
    private var isUpdating = false

    private val badgeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isFragmentActive && isAdded) {
                Log.d(TAG, "Badge update broadcast received")
                updateBadgeImmediately()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        isFragmentActive = true
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        try {
            localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())

            auth = FirebaseAuth.getInstance()

            if (auth.currentUser == null) {
                Log.e(TAG, "User not authenticated")
                if (isAdded && isFragmentActive) {
                    Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), MainActivity::class.java))
                    requireActivity().finish()
                }
                return
            }

            notificationRepository = NotificationRepository(requireContext())
            transactionRepository = TransactionRepository(notificationRepository)
            userRepository = UserRepository()
            categoryRepository = CategoryRepository(notificationRepository)

            totalBalanceText = view.findViewById(R.id.totalBalance)
            totalIncomeText = view.findViewById(R.id.totalIncome)
            totalExpenseText = view.findViewById(R.id.totalExpense)

            val textLayout = view.findViewById<LinearLayout>(R.id.textLayout)
            welcomeText = textLayout.findViewById(R.id.welcomeText)
            userNameText = textLayout.findViewById(R.id.userNameText)

            backButton = view.findViewById(R.id.backButton)
            transactionsTitle = view.findViewById(R.id.transactionsTitle)
            viewAllTextView = view.findViewById(R.id.viewAllTextView)
            transactionsTitleContainer = view.findViewById(R.id.transactionsTitleContainer)

            transactionsContainer = view.findViewById(R.id.transactionsContainer)

            bellContainer = view.findViewById(R.id.bellContainer)
            notificationBadge = view.findViewById(R.id.notificationBadge)

            createEmptyStateView(view)
            setupClickHandlers(view)
            setupTransactionsObserver()
            setupUserObserver()
            setupCategoriesObservers()
            setupNotificationObserver()
            registerBadgeReceiver()

            transactionRepository.loadTransactions()
            loadBalance()

            (activity as? DashboardActivity)?.addCurrencyUpdateListener(this)

            updateBadgeImmediately()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            if (isAdded && isFragmentActive) {
                Toast.makeText(requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerBadgeReceiver() {
        try {
            val filter = IntentFilter(DashboardActivity.ACTION_UPDATE_BADGES)
            localBroadcastManager.registerReceiver(badgeUpdateReceiver, filter)
            Log.d(TAG, "Badge receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering badge receiver", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        isUpdating = false
        try {
            localBroadcastManager.unregisterReceiver(badgeUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        (activity as? DashboardActivity)?.removeCurrencyUpdateListener(this)
        transactionRepository.cleanup()
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateBadgeImmediately() {
        if (!isFragmentActive || !isAdded) return

        try {
            val unreadCount = notificationRepository.getUnreadCount()
            updateNotificationBadge(unreadCount)
            Log.d(TAG, "Badge updated immediately: $unreadCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating badge immediately", e)
        }
    }

    private fun setupNotificationObserver() {
        try {
            notificationRepository.notificationsLiveData.observe(viewLifecycleOwner) { notifications ->
                if (isFragmentActive && isAdded) {
                    val unreadCount = notifications.count { !it.isRead }
                    updateNotificationBadge(unreadCount)
                    Log.d(TAG, "Badge updated via observer: $unreadCount")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up notification observer", e)
        }
    }

    private fun updateNotificationBadge(count: Int) {
        if (!isFragmentActive || !isAdded) return

        activity?.runOnUiThread {
            try {
                if (count > 0) {
                    notificationBadge.visibility = View.VISIBLE
                    notificationBadge.text = if (count > 99) "99+" else count.toString()
                } else {
                    notificationBadge.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating badge UI", e)
            }
        }
    }

    override fun onCurrencyChanged(currencySymbol: String) {
        if (!isFragmentActive || !isAdded) return

        Log.d(TAG, "Currency changed to: $currencySymbol")
        loadBalance()
        updateTransactionsDisplay()
    }

    private fun createEmptyStateView(view: View) {
        try {
            val inflater = LayoutInflater.from(requireContext())
            emptyStateView = inflater.inflate(R.layout.layout_empty_state, transactionsContainer, false)
            emptyStateView.findViewById<TextView>(R.id.emptyStateAction).setOnClickListener {
                if (isFragmentActive && isAdded) {
                    (activity as? DashboardActivity)?.showAddTransactionDialog()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating empty state view", e)
        }
    }

    private fun setupClickHandlers(view: View) {
        try {
            view.findViewById<ImageView>(R.id.ic_menu)?.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    (activity as? DashboardActivity)?.openDrawer()
                }
            }

            bellContainer.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    (activity as? DashboardActivity)?.showFragment(NotificationsFragment())
                }
            }

            view.findViewById<View>(R.id.fabAdd)?.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    (activity as? DashboardActivity)?.showAddTransactionDialog()
                }
            }

            viewAllTextView.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    toggleTransactionsView()
                }
            }

            backButton.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    showRecentTransactions()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click handlers", e)
        }
    }

    override fun onTransactionAdded() {
        if (!isFragmentActive || !isAdded) return

        Log.d(TAG, "Transaction added, refreshing data")
        refreshData()
    }

    private fun handleTransactionUpdated() {
        if (!isFragmentActive || !isAdded || isUpdating) {
            Log.d(TAG, "Fragment not active or already updating, skipping")
            return
        }

        Log.d(TAG, "Transaction updated callback received")

        val now = System.currentTimeMillis()

        if (now - lastUpdateTime < UPDATE_COOLDOWN_MS) {
            Log.d(TAG, "Skipping: too frequent (cooldown)")
            return
        }

        isUpdating = true
        lastUpdateTime = now

        try {
            transactionRepository.forceRefresh()

            handler.postDelayed({
                if (isFragmentActive && isAdded) {
                    loadBalance()
                    updateBadgeImmediately()

                    if (isShowingAllTransactions) {
                        showAllTransactions()
                    } else {
                        showRecentTransactions()
                    }

                    isUpdating = false
                    Log.d(TAG, "Data refresh completed")
                } else {
                    isUpdating = false
                }
            }, 500)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleTransactionUpdated", e)
            isUpdating = false
        }
    }

    private fun refreshData() {
        if (!isFragmentActive || !isAdded) return

        Log.d(TAG, "REFRESHING DATA - reloading transactions")

        transactionRepository.forceRefresh()

        handler.postDelayed({
            if (isFragmentActive && isAdded) {
                loadBalance()
                updateBadgeImmediately()
                if (isShowingAllTransactions) {
                    showAllTransactions()
                } else {
                    showRecentTransactions()
                }
                Log.d(TAG, "Data refresh COMPLETED")
            }
        }, 500)
    }

    private fun toggleTransactionsView() {
        if (!isFragmentActive || !isAdded) return

        if (isShowingAllTransactions) {
            showRecentTransactions()
        } else {
            showAllTransactions()
        }
    }

    private fun getCurrentMonthTransactions(): List<Transaction> {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return transactions.filter { transaction ->
            try {
                val date = dateFormat.parse(transaction.date)
                calendar.time = date
                val transactionYear = calendar.get(Calendar.YEAR)
                val transactionMonth = calendar.get(Calendar.MONTH) + 1

                transactionYear == currentYear && transactionMonth == currentMonth
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing date: ${transaction.date}", e)
                false
            }
        }.sortedByDescending { it.date }
    }

    private fun showRecentTransactions() {
        if (!isFragmentActive || !isAdded) return

        isShowingAllTransactions = false

        backButton.visibility = View.GONE
        transactionsTitle.text = getString(R.string.recent_transactions)
        viewAllTextView.text = getString(R.string.view_all)
        viewAllTextView.visibility = View.VISIBLE

        val currentMonthTransactions = getCurrentMonthTransactions()

        if (currentMonthTransactions.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
            displayTransactions(currentMonthTransactions)
        }
    }

    private fun showAllTransactions() {
        if (!isFragmentActive || !isAdded) return

        isShowingAllTransactions = true

        backButton.visibility = View.VISIBLE
        transactionsTitle.text = getString(R.string.all_transactions)
        viewAllTextView.visibility = View.GONE

        val allTransactions = transactions.sortedByDescending { it.date }
        if (allTransactions.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
            displayTransactions(allTransactions)
        }
    }

    private fun setupUserObserver() {
        try {
            userRepository.getCurrentUserLiveData().observe(viewLifecycleOwner, Observer { user ->
                if (isFragmentActive && isAdded) {
                    currentUser = user
                    updateUserName()
                    loadBalance()
                }
            })
            userRepository.loadCurrentUser()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up user observer", e)
        }
    }

    private fun setupCategoriesObservers() {
        try {
            categoryRepository.expenseCategories.observe(viewLifecycleOwner, Observer { categories ->
                if (isFragmentActive && isAdded) {
                    expenseCategories = categories
                    Log.d(TAG, "Loaded ${categories.size} expense categories")
                    if (transactions.isNotEmpty()) {
                        updateTransactionsDisplay()
                    }
                }
            })

            categoryRepository.incomeCategories.observe(viewLifecycleOwner, Observer { categories ->
                if (isFragmentActive && isAdded) {
                    incomeCategories = categories
                    Log.d(TAG, "Loaded ${categories.size} income categories")
                    if (transactions.isNotEmpty()) {
                        updateTransactionsDisplay()
                    }
                }
            })

            categoryRepository.loadAllCategories()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up categories observers", e)
        }
    }

    private fun findCategoryForTransaction(transaction: Transaction): Category? {
        transaction.categoryId?.let { categoryId ->
            if (categoryId.isNotEmpty()) {
                expenseCategories.find { it.id == categoryId }?.let { return it }
                incomeCategories.find { it.id == categoryId }?.let { return it }
            }
        }

        val categoryName = transaction.categoryName ?: ""
        if (categoryName.isNotEmpty()) {
            expenseCategories.find { it.categoryName == categoryName }?.let { return it }
            incomeCategories.find { it.categoryName == categoryName }?.let { return it }
        }

        return null
    }

    private fun updateTransactionsDisplay() {
        if (!isFragmentActive || !isAdded) return

        if (isShowingAllTransactions) {
            showAllTransactions()
        } else {
            showRecentTransactions()
        }
    }

    private fun updateUserName() {
        if (!isFragmentActive || !isAdded) return

        try {
            currentUser?.let { user ->
                val displayName = if (user.fio.isNotBlank()) {
                    user.fio
                } else {
                    user.email.substringBefore("@")
                }

                userNameText.text = "$displayName 👋"
                welcomeText.text = getString(R.string.welcome)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user name", e)
        }
    }

    private fun setupTransactionsObserver() {
        try {
            transactionRepository.getTransactionsLiveData().observe(viewLifecycleOwner, Observer { transactionsList ->
                if (isFragmentActive && isAdded) {
                    Log.d(TAG, "Transactions loaded: ${transactionsList.size} items")

                    this.transactions = transactionsList

                    if (isShowingAllTransactions) {
                        showAllTransactions()
                    } else {
                        showRecentTransactions()
                    }

                    loadBalance()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up transactions observer", e)
        }
    }

    private fun loadBalance() {
        if (!isFragmentActive || !isAdded) return

        if (balanceLoadPending) {
            Log.d(TAG, "Balance load already pending")
            return
        }

        balanceLoadPending = true

        try {
            transactionRepository.getTotalBalance { balance, income, expense ->
                handler.post {
                    if (isFragmentActive && isAdded) {
                        try {
                            val format = NumberFormat.getNumberInstance(Locale.getDefault())
                            val currencySymbol = currentUser?.currencySymbol ?: "₽"
                            totalBalanceText.text = "${format.format(balance)} $currencySymbol"
                            totalIncomeText.text = "+${format.format(income)} $currencySymbol"
                            totalExpenseText.text = "-${format.format(expense)} $currencySymbol"

                            Log.d(TAG, "Balance updated: balance=$balance, income=$income, expense=$expense")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating balance UI", e)
                        } finally {
                            balanceLoadPending = false
                        }
                    } else {
                        balanceLoadPending = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading balance", e)
            balanceLoadPending = false
        }
    }

    private fun displayTransactions(transactions: List<Transaction>) {
        if (!isFragmentActive || !isAdded) return

        try {
            transactionsContainer.removeAllViews()

            if (transactions.isEmpty()) {
                showEmptyState()
                return
            }

            val inflater = LayoutInflater.from(requireContext())

            transactions.forEach { transaction ->
                val transactionView = inflater.inflate(
                    R.layout.item_transaction,
                    transactionsContainer,
                    false
                )

                setupTransactionView(transactionView, transaction)
                transactionsContainer.addView(transactionView)

                if (transaction != transactions.last()) {
                    val divider = View(requireContext())
                    divider.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(16, 8, 16, 8)
                    }
                    divider.setBackgroundColor(Color.parseColor("#E0E0E0"))
                    transactionsContainer.addView(divider)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying transactions", e)
        }
    }

    private fun setupTransactionView(view: View, transaction: Transaction) {
        if (!isFragmentActive || !isAdded) return

        try {
            Log.d(TAG, "Setting up transaction view for: ${transaction.title}")

            val categoryIcon = view.findViewById<TextView>(R.id.categoryIcon)
            val categoryName = view.findViewById<TextView>(R.id.categoryName)
            val description = view.findViewById<TextView>(R.id.description)
            val transactionDate = view.findViewById<TextView>(R.id.transactionDate)
            val transactionAmount = view.findViewById<TextView>(R.id.transactionAmount)
            val optionsButton: ImageView? = view.findViewById(R.id.optionsButton)

            val safeCategoryName = transaction.categoryName ?: "Другое"

            val category = findCategoryForTransaction(transaction)

            if (category != null) {
                categoryIcon.text = category.icon
                try {
                    categoryIcon.setBackgroundColor(Color.parseColor(category.color))
                } catch (e: Exception) {
                    categoryIcon.setBackgroundColor(getDefaultColorForCategory(safeCategoryName))
                }
                categoryName.text = category.categoryName
            } else {
                categoryIcon.text = getCategoryIcon(safeCategoryName)
                categoryIcon.setBackgroundColor(getDefaultColorForCategory(safeCategoryName))
                categoryName.text = safeCategoryName
            }

            val displayText = if (transaction.description.isNotEmpty()) {
                transaction.description
            } else {
                transaction.title.ifEmpty { "Без описания" }
            }
            description.text = displayText

            transactionDate.text = formatDate(transaction.date)

            val symbol = if (transaction.type == "income") "+" else "-"
            val amountText = "$symbol${String.format("%.2f", transaction.amount)} ${currentUser?.currencySymbol ?: "₽"}"
            transactionAmount.text = amountText

            val textColor = if (transaction.type == "income") {
                Color.parseColor("#4CAF50")
            } else {
                Color.parseColor("#F44336")
            }
            transactionAmount.setTextColor(textColor)

            optionsButton?.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    showOptionsMenu(it, transaction)
                }
            }

            view.setOnClickListener {
                if (isFragmentActive && isAdded) {
                    showEditTransactionDialog(transaction)
                }
            }

            Log.d(TAG, "Transaction view setup completed for: ${transaction.title}")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up transaction view", e)
        }
    }

    private fun getDefaultColorForCategory(categoryName: String): Int {
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
            "Подарки" to Color.parseColor("#F59E0B")
        )
        return colorMap[categoryName] ?: Color.parseColor("#9E9E9E")
    }

    private fun showOptionsMenu(view: View, transaction: Transaction) {
        if (!isFragmentActive || !isAdded) return

        try {
            val popup = PopupMenu(requireContext(), view)
            popup.menu.apply {
                add(0, 1, 0, getString(R.string.edit))
                add(0, 2, 1, getString(R.string.delete))
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        if (isFragmentActive && isAdded) {
                            showEditTransactionDialog(transaction)
                        }
                        true
                    }
                    2 -> {
                        if (isFragmentActive && isAdded) {
                            showDeleteConfirmationDialog(transaction)
                        }
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing options menu", e)
        }
    }

    private fun showEditTransactionDialog(transaction: Transaction) {
        if (!isFragmentActive || !isAdded) return

        try {
            val dialog = EditTransactionDialogFragment.newInstance(transaction)
            dialog.setTransactionUpdatedListener(object : EditTransactionDialogFragment.OnTransactionUpdatedListener {
                override fun onTransactionUpdated() {
                    Log.d(TAG, "Transaction updated callback received from dialog")
                    handler.post {
                        try {
                            if (isFragmentActive && isAdded) {
                                refreshData()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onTransactionUpdated", e)
                        }
                    }
                }

                override fun onTransactionDeleted() {
                    Log.d(TAG, "Transaction DELETED callback received from dialog")
                    handler.post {
                        try {
                            if (isFragmentActive && isAdded) {
                                // Принудительно обновляем данные
                                transactionRepository.forceRefresh()
                                refreshData()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onTransactionDeleted", e)
                        }
                    }
                }
            })
            dialog.show(parentFragmentManager, "EditTransactionDialog")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing edit dialog", e)
            if (isFragmentActive && isAdded) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmationDialog(transaction: Transaction) {
        if (!isFragmentActive || !isAdded) return

        try {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_transaction))
                .setMessage(getString(R.string.delete_confirmation, transaction.title))
                .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                    deleteTransaction(transaction)
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing delete dialog", e)
        }
    }

    private fun deleteTransaction(transaction: Transaction) {
        if (!isFragmentActive || !isAdded) return

        try {
            transactionRepository.deleteTransaction(transaction.id) { success, message ->
                handler.post {
                    if (isFragmentActive && isAdded) {
                        try {
                            if (success) {
                                Toast.makeText(requireContext(),
                                    getString(R.string.transaction_deleted),
                                    Toast.LENGTH_SHORT).show()
                                refreshData()
                            } else {
                                Toast.makeText(requireContext(),
                                    getString(R.string.error, message ?: "Ошибка"),
                                    Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in delete callback", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction", e)
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

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun showEmptyState() {
        if (!isFragmentActive || !isAdded) return

        try {
            transactionsContainer.removeAllViews()
            if (::emptyStateView.isInitialized) {
                transactionsContainer.addView(emptyStateView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing empty state", e)
        }
    }

    private fun hideEmptyState() {
        if (!isFragmentActive || !isAdded) return

        try {
            if (::emptyStateView.isInitialized && transactionsContainer.indexOfChild(emptyStateView) >= 0) {
                transactionsContainer.removeView(emptyStateView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding empty state", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isFragmentActive = true
        try {
            if (auth.currentUser != null) {
                transactionRepository.loadTransactions()
                loadBalance()
                userRepository.loadCurrentUser()
                categoryRepository.loadAllCategories()

                (activity as? DashboardActivity)?.refreshNotificationBadge()
                updateBadgeImmediately()

                handler.postDelayed({
                    if (isFragmentActive && isAdded) {
                        updateBadgeImmediately()
                    }
                }, 100)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }

    override fun onPause() {
        super.onPause()
        isFragmentActive = false
        handler.removeCallbacksAndMessages(null)
        balanceLoadPending = false
        isUpdating = false
    }
}