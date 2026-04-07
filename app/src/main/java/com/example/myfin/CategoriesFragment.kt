package com.example.myfin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.adapter.CategoryAdapter
import com.example.myfin.adapter.ColorAdapter
import com.example.myfin.adapter.IconAdapter
import com.example.myfin.data.Category
import com.example.myfin.data.CategoryRepository
import com.example.myfin.data.NotificationRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*

class CategoriesFragment : Fragment() {

    companion object {
        private const val TAG = "CategoriesFragment"
    }

    private lateinit var categoryRepository: CategoryRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnExpenses: TextView
    private lateinit var btnIncomes: TextView
    private lateinit var tabInfoText: TextView
    private lateinit var categoriesCountText: TextView
    private lateinit var emptyStateText: TextView
    private lateinit var addCategoryButton: MaterialButton
    private lateinit var notificationBadge: TextView
    private lateinit var bellContainer: View
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private var currentType: String = "expense"
    private var expenseCategoriesList = listOf<Category>()
    private var incomeCategoriesList = listOf<Category>()

    // BroadcastReceiver для обновления бейджа
    private val badgeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Badge update broadcast received")
            updateBadgeImmediately()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())

            // Инициализация репозиториев
            notificationRepository = NotificationRepository(requireContext())

            // Получаем репозиторий из активности или создаем новый
            categoryRepository = try {
                (activity as DashboardActivity).getCategoryRepository()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting category repository from activity", e)
                CategoryRepository(notificationRepository)
            }

            // Находим View из XML
            recyclerView = view.findViewById(R.id.recyclerViewCategories)
            btnExpenses = view.findViewById(R.id.btnExpenses)
            btnIncomes = view.findViewById(R.id.btnIncomes)
            tabInfoText = view.findViewById(R.id.tabInfoText)
            categoriesCountText = view.findViewById(R.id.categoriesCountText)
            emptyStateText = view.findViewById(R.id.emptyStateText)
            addCategoryButton = view.findViewById(R.id.addCategoryButton)

            // Инициализация бейджа уведомлений
            bellContainer = view.findViewById(R.id.bellContainer)
            notificationBadge = view.findViewById(R.id.notificationBadge)

            // Настройка RecyclerView
            setupRecyclerView()

            // Обработчики
            setupTabButtons()
            setupClickHandlers(view)
            setupAddCategoryButton()
            setupNotificationObserver()
            registerBadgeReceiver()

            // Наблюдатели с логированием
            setupCategoriesObservers()

            // Загружаем категории
            categoryRepository.loadAllCategories()

            // По умолчанию показываем расходы
            selectExpensesTab()

            // Немедленное обновление бейджа
            updateBadgeImmediately()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(requireContext(),
                getString(R.string.error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun registerBadgeReceiver() {
        val filter = IntentFilter(DashboardActivity.ACTION_UPDATE_BADGES)
        localBroadcastManager.registerReceiver(badgeUpdateReceiver, filter)
        Log.d(TAG, "Badge receiver registered")
    }

    private fun updateBadgeImmediately() {
        val activity = activity as? DashboardActivity
        val unreadCount = activity?.getUnreadNotificationCount() ?: 0
        updateNotificationBadge(unreadCount)
        Log.d(TAG, "Badge updated immediately: $unreadCount")
    }

    private fun setupNotificationObserver() {
        val activity = activity as? DashboardActivity
        activity?.getNotificationRepository()?.notificationsLiveData?.observe(viewLifecycleOwner) { notifications ->
            val unreadCount = notifications.count { !it.isRead }
            updateNotificationBadge(unreadCount)
            Log.d(TAG, "Badge updated via observer: $unreadCount")
        }
    }

    private fun updateNotificationBadge(count: Int) {
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

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(emptyList()) { category ->
            showCategoryOptions(category)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = categoryAdapter
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = false
    }

    private fun setupTabButtons() {
        btnExpenses.setOnClickListener { selectExpensesTab() }
        btnIncomes.setOnClickListener { selectIncomesTab() }
    }

    private fun setupClickHandlers(view: View) {
        // Кнопка назад
        view.findViewById<View>(R.id.ic_back)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Колокольчик
        bellContainer.setOnClickListener {
            Log.d(TAG, "Bell clicked, opening notifications")
            (activity as? DashboardActivity)?.showFragment(NotificationsFragment())
        }
    }

    private fun setupAddCategoryButton() {
        addCategoryButton.setOnClickListener {
            showAddOrEditCategoryDialog(null)
        }
    }

    private fun setupCategoriesObservers() {
        // Наблюдатель для категорий расходов
        categoryRepository.expenseCategories.observe(viewLifecycleOwner) { categories ->
            Log.d(TAG, "Expense categories updated: ${categories.size}")
            expenseCategoriesList = categories
            if (currentType == "expense") {
                updateCategoriesDisplay(categories)
            }
        }

        // Наблюдатель для категорий доходов
        categoryRepository.incomeCategories.observe(viewLifecycleOwner) { categories ->
            Log.d(TAG, "Income categories updated: ${categories.size}")
            incomeCategoriesList = categories
            if (currentType == "income") {
                updateCategoriesDisplay(categories)
            }
        }
    }

    private fun showAddOrEditCategoryDialog(existingCategory: Category?) {
        val isEditMode = existingCategory != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)

        val etCategoryName = dialogView.findViewById<AppCompatEditText>(R.id.etCategoryName)
        val etMonthlyLimit = dialogView.findViewById<AppCompatEditText>(R.id.etMonthlyLimit)
        val previewIcon = dialogView.findViewById<TextView>(R.id.previewIcon)
        val previewName = dialogView.findViewById<TextView>(R.id.previewName)
        val previewLayout = dialogView.findViewById<LinearLayout>(R.id.categoryPreview)
        val rvIcons = dialogView.findViewById<RecyclerView>(R.id.rvIcons)
        val rvColors = dialogView.findViewById<RecyclerView>(R.id.rvColors)
        val tvMonthlyLimitLabel = dialogView.findViewById<TextView>(R.id.tvMonthlyLimitLabel)

        tvMonthlyLimitLabel.text = getString(R.string.category_monthly_limit_hint)

        val icons = listOf(
            "💸", "💰", "💳", "🛒", "🍔", "☕", "🍕", "🚗", "⛽", "🏠",
            "💡", "📱", "💊", "👕", "🎬", "✈️", "🎁", "🏋️", "📚", "🎮",
            "💻", "📺", "🎵", "🎨", "⚽", "🎪", "🐶", "🌹", "🎓", "💍"
        )

        val colors = listOf(
            "#FF6B6B", "#4ECDC4", "#118AB2", "#073B4C", "#FFD166",
            "#06D6A0", "#EF476F", "#7209B7", "#3A86FF", "#FB5607",
            "#8338EC", "#3A86FF", "#FF006E", "#FB5607", "#8338EC"
        )

        var selectedIcon = if (isEditMode) existingCategory?.icon ?: "💸" else "💸"
        var selectedColor = if (isEditMode) existingCategory?.color ?: "#4ECDC4" else "#4ECDC4"

        val categoryTypeForDialog = if (isEditMode) existingCategory?.type ?: currentType else currentType

        val iconAdapter = IconAdapter(icons) { icon ->
            selectedIcon = icon
            updatePreview(selectedIcon, selectedColor, etCategoryName.text.toString(),
                previewIcon, previewName, previewLayout)
        }
        rvIcons.adapter = iconAdapter
        rvIcons.layoutManager = GridLayoutManager(requireContext(), 6)

        val colorAdapter = ColorAdapter(colors) { color ->
            selectedColor = color
            updatePreview(selectedIcon, selectedColor, etCategoryName.text.toString(),
                previewIcon, previewName, previewLayout)
        }
        rvColors.adapter = colorAdapter
        rvColors.layoutManager = GridLayoutManager(requireContext(), 6)

        if (isEditMode && existingCategory != null) {
            etCategoryName.setText(existingCategory.categoryName)
            if (existingCategory.type == "expense" && existingCategory.monthlyLimit > 0) {
                etMonthlyLimit.setText(existingCategory.monthlyLimit.toInt().toString())
                Log.d(TAG, "📝 Загружен лимит для редактирования: ${existingCategory.monthlyLimit}")
            }

            val iconIndex = icons.indexOf(existingCategory.icon)
            if (iconIndex != -1) iconAdapter.selectItem(iconIndex)
            else iconAdapter.selectItem(0)

            val colorIndex = colors.indexOf(existingCategory.color)
            if (colorIndex != -1) colorAdapter.selectItem(colorIndex)
            else colorAdapter.selectItem(1)
        } else {
            iconAdapter.selectItem(0)
            colorAdapter.selectItem(1)
        }

        // Управление видимостью поля лимита
        if (categoryTypeForDialog == "income") {
            tvMonthlyLimitLabel.visibility = View.GONE
            etMonthlyLimit.visibility = View.GONE
        } else {
            tvMonthlyLimitLabel.visibility = View.VISIBLE
            etMonthlyLimit.visibility = View.VISIBLE
        }

        etCategoryName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview(selectedIcon, selectedColor, s.toString(),
                    previewIcon, previewName, previewLayout)
            }
        })

        updatePreview(selectedIcon, selectedColor,
            etCategoryName.text.toString(),
            previewIcon, previewName, previewLayout)

        val dialogTitle = if (isEditMode) getString(R.string.edit_category) else getString(R.string.add_category)
        val buttonText = if (isEditMode) getString(R.string.save) else getString(R.string.add_button)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(buttonText) { dialog, which ->
                val name = etCategoryName.text.toString().trim()
                val limitText = etMonthlyLimit.text.toString().trim()
                val limit = if (limitText.isNotEmpty()) limitText.toDoubleOrNull() ?: 0.0 else 0.0

                Log.d(TAG, "💾 СОХРАНЕНИЕ КАТЕГОРИИ")
                Log.d(TAG, "   Имя: $name")
                Log.d(TAG, "   Лимит: $limit")
                Log.d(TAG, "   Тип: $categoryTypeForDialog")
                Log.d(TAG, "   Режим редактирования: $isEditMode")

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(),
                        getString(R.string.category_name_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (isEditMode && existingCategory != null) {
                    val updatedCategory = Category(
                        id = existingCategory.id,
                        categoryName = name,
                        icon = selectedIcon,
                        color = selectedColor,
                        monthlyLimit = if (existingCategory.type == "expense") limit else 0.0,
                        type = existingCategory.type,
                        isDefault = existingCategory.isDefault
                    )
                    Log.d(TAG, "   Обновляем категорию с лимитом: ${updatedCategory.monthlyLimit}")
                    categoryRepository.updateUserCategory(updatedCategory, existingCategory.type) { success, message ->
                        if (success) {
                            Toast.makeText(requireContext(),
                                getString(R.string.category_updated), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(),
                                getString(R.string.error, message), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    val newCategory = Category(
                        categoryName = name,
                        icon = selectedIcon,
                        color = selectedColor,
                        monthlyLimit = if (categoryTypeForDialog == "expense") limit else 0.0,
                        type = categoryTypeForDialog,
                        isDefault = false
                    )
                    Log.d(TAG, "   Добавляем категорию с лимитом: ${newCategory.monthlyLimit}")
                    categoryRepository.addUserCategory(newCategory, categoryTypeForDialog) { success, message ->
                        if (success) {
                            Toast.makeText(requireContext(),
                                getString(R.string.category_added), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(),
                                getString(R.string.error, message), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .apply {
                if (isEditMode && existingCategory != null && !existingCategory.isDefault) {
                    setNeutralButton(getString(R.string.delete)) { dialog, which ->
                        showDeleteCategoryDialog(existingCategory)
                    }
                }
            }
            .show()
    }

    private fun updatePreview(
        icon: String,
        color: String,
        name: String,
        previewIcon: TextView,
        previewName: TextView,
        previewLayout: LinearLayout
    ) {
        try {
            previewIcon.text = icon
            previewIcon.setBackgroundColor(Color.parseColor(color))
            previewName.text = if (name.isNotEmpty()) name else getString(R.string.category_preview_name)

            val bgColor = Color.parseColor(color)
            val lighterColor = ColorUtils.blendARGB(bgColor, Color.WHITE, 0.85f)
            previewLayout.setBackgroundColor(lighterColor)
        } catch (e: Exception) {
            previewIcon.setBackgroundColor(Color.parseColor("#4ECDC4"))
            previewLayout.setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
    }

    private fun showCategoryOptions(category: Category) {
        // Стандартные категории нельзя редактировать и удалять
        if (category.isDefault) {
            showCategoryDetails(category)
            return
        }

        val options = arrayOf(
            getString(R.string.edit),
            getString(R.string.view),
            getString(R.string.delete)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(category.categoryName)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showAddOrEditCategoryDialog(category)
                    1 -> showCategoryDetails(category)
                    2 -> showDeleteCategoryDialog(category)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteCategoryDialog(category: Category) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_category))
            .setMessage(getString(R.string.delete_category_confirmation, category.categoryName))
            .setPositiveButton(getString(R.string.delete)) { dialog, which ->
                categoryRepository.deleteUserCategory(category.id, category.type) { success, message ->
                    if (success) {
                        Toast.makeText(requireContext(),
                            getString(R.string.category_deleted), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(),
                            getString(R.string.error, message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun selectExpensesTab() {
        currentType = "expense"
        updateTabAppearance(btnExpenses, btnIncomes)
        tabInfoText.text = getString(R.string.expense_categories_title)
        addCategoryButton.text = getString(R.string.add_expense_category)

        updateCategoriesDisplay(expenseCategoriesList)
    }

    private fun selectIncomesTab() {
        currentType = "income"
        updateTabAppearance(btnIncomes, btnExpenses)
        tabInfoText.text = getString(R.string.income_categories_title)
        addCategoryButton.text = getString(R.string.add_income_category)

        updateCategoriesDisplay(incomeCategoriesList)
    }

    private fun updateTabAppearance(selectedTab: TextView, unselectedTab: TextView) {
        selectedTab.setBackgroundResource(R.drawable.bg_button)
        selectedTab.setTextColor(Color.WHITE)
        unselectedTab.setBackgroundResource(R.drawable.bg_input)
        unselectedTab.setTextColor(Color.parseColor("#666666"))
    }

    private fun updateCategoriesDisplay(categories: List<Category>) {
        Log.d(TAG, "Updating display for ${categories.size} categories of type $currentType")

        if (categories.isEmpty()) {
            showEmptyState()
            return
        }

        // Фильтруем категории по текущему типу и убираем дубликаты по ID
        val filteredCategories = categories
            .filter { it.type == currentType }
            .distinctBy { it.id }
            .sortedWith(
                compareBy(
                    { !it.isDefault },
                    { it.categoryName }
                )
            )

        val defaultCount = filteredCategories.count { it.isDefault }
        val userCount = filteredCategories.count { !it.isDefault }

        categoriesCountText.text = getString(R.string.categories_count_with_default,
            filteredCategories.size, defaultCount, userCount)

        Log.d(TAG, "Showing ${filteredCategories.size} categories (default: $defaultCount, user: $userCount)")

        filteredCategories.forEach {
            Log.d(TAG, "  - ${it.categoryName} (${it.id}, тип: ${it.type})")
        }

        categoryAdapter.updateData(filteredCategories)
        hideEmptyState()
    }

    private fun showCategoryDetails(category: Category) {
        val typeText = if (category.type == "expense")
            getString(R.string.expense)
        else
            getString(R.string.income)

        val categoryType = if (category.isDefault)
            getString(R.string.default_category)
        else
            getString(R.string.user_category)

        val limitText = if (category.monthlyLimit > 0 && category.type == "expense")
            "\n${getString(R.string.monthly_limit)}: ${formatAmount(category.monthlyLimit)} ₽"
        else ""

        val message = """
            $typeText: ${category.categoryName}
            $categoryType
            ${getString(R.string.color)}: ${category.color}
            ${getString(R.string.icon)}: ${category.icon}$limitText
        """.trimIndent()

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun formatAmount(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = when (currentType) {
            "expense" -> getString(R.string.no_expense_categories)
            "income" -> getString(R.string.no_income_categories_empty)
            else -> getString(R.string.no_categories_settings)
        }
        categoriesCountText.text = getString(R.string.categories_count, 0)
    }

    private fun hideEmptyState() {
        recyclerView.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        categoryRepository.loadAllCategories()

        val activity = activity as? DashboardActivity
        activity?.refreshNotificationBadge()
        updateBadgeImmediately()
    }

    override fun onDestroyView() {
        try {
            localBroadcastManager.unregisterReceiver(badgeUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        try {
            categoryRepository.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up repository", e)
        }

        super.onDestroyView()
    }
}