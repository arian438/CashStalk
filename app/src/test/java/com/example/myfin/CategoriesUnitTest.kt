package com.example.myfin

import com.example.myfin.data.Category
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class CategoriesUnitTest {

    private lateinit var categoryValidator: CategoryValidator
    private lateinit var categoryFormatter: CategoryFormatter

    @Before
    fun setUp() {
        categoryValidator = CategoryValidator()
        categoryFormatter = CategoryFormatter()
    }

    @Test
    fun `validateCategoryName should return true for non-empty name`() {
        // Проверка корректных названий
        assertTrue(categoryValidator.isValidName("Еда"))
        assertTrue(categoryValidator.isValidName("Транспорт"))
        assertTrue(categoryValidator.isValidName("Мои расходы"))
    }

    @Test
    fun `validateCategoryName should return false for empty or blank name`() {
        // Проверка некорректных названий
        assertFalse(categoryValidator.isValidName(""))
        assertFalse(categoryValidator.isValidName("   "))
        assertFalse(categoryValidator.isValidName("\n"))
    }

    @Test
    fun `validateMonthlyLimit should accept positive numbers and zero`() {
        // Проверка допустимых значений лимита
        assertTrue(categoryValidator.isValidMonthlyLimit(1000.0))
        assertTrue(categoryValidator.isValidMonthlyLimit(0.0))
        assertTrue(categoryValidator.isValidMonthlyLimit(500.50))
    }

    @Test
    fun `validateMonthlyLimit should reject negative numbers`() {
        // Проверка недопустимых отрицательных значений
        assertFalse(categoryValidator.isValidMonthlyLimit(-100.0))
        assertFalse(categoryValidator.isValidMonthlyLimit(-0.01))
    }

    @Test
    fun `filterByType should return only expense categories`() {
        // Подготовка смешанного списка категорий
        val categories = listOf(
            Category(type = "expense", categoryName = "Еда"),
            Category(type = "income", categoryName = "Зарплата"),
            Category(type = "expense", categoryName = "Транспорт"),
            Category(type = "income", categoryName = "Фриланс")
        )

        // Фильтрация категорий расходов
        val expenseCategories = categories.filter { it.type == "expense" }

        // Проверка результатов фильтрации
        assertEquals(2, expenseCategories.size)
        assertTrue(expenseCategories.all { it.type == "expense" })
        assertEquals("Еда", expenseCategories[0].categoryName)
        assertEquals("Транспорт", expenseCategories[1].categoryName)
    }

    @Test
    fun `filterByType should return only income categories`() {
        // Подготовка смешанного списка категорий
        val categories = listOf(
            Category(type = "expense", categoryName = "Еда"),
            Category(type = "income", categoryName = "Зарплата"),
            Category(type = "expense", categoryName = "Транспорт"),
            Category(type = "income", categoryName = "Фриланс")
        )

        // Фильтрация категорий доходов
        val incomeCategories = categories.filter { it.type == "income" }

        // Проверка результатов фильтрации
        assertEquals(2, incomeCategories.size)
        assertTrue(incomeCategories.all { it.type == "income" })
        assertEquals("Зарплата", incomeCategories[0].categoryName)
        assertEquals("Фриланс", incomeCategories[1].categoryName)
    }
}

// Вспомогательные классы для тестов
class CategoryValidator {
    fun isValidName(name: String): Boolean {
        return name.isNotBlank()
    }

    fun isValidMonthlyLimit(limit: Double): Boolean {
        return limit >= 0
    }
}

class CategoryFormatter {
    fun formatAmountWithDot(amount: Double): String {
        return String.format(Locale.US, "%.2f", amount)
    }

    fun formatAmountWithLocale(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }
}