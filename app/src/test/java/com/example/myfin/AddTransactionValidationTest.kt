package com.example.myfin

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class AddTransactionValidationTest {

    private lateinit var validator: TransactionValidator

    @Before
    fun setUp() {
        validator = TransactionValidator()
    }

    @Test
    fun `addTransaction with valid data should succeed`() {
        // Arrange
        val title = "Обед в столовой"
        val amountText = "350.50"
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertTrue(validationResult.isValid)
        assertEquals(350.50, validationResult.amount, 0.001)
        assertEquals(title, validationResult.title)
        assertNull(validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with empty title should fail validation`() {
        // Arrange
        val title = ""
        val amountText = "350.50"
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Введите название", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with empty amount should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = ""
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Введите сумму", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with negative amount should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = "-100"
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Сумма должна быть положительной", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with zero amount should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = "0"
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Сумма должна быть положительной", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with amount containing comma should be valid`() {
        // Arrange
        val title = "Обед"
        val amountText = "350,50"
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertTrue(validationResult.isValid)
        assertEquals(350.50, validationResult.amount, 0.001)
    }

    @Test
    fun `addTransaction with invalid amount text should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = "abc"
        val categoryName = "Еда"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Некорректная сумма", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with null userId should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = "350.50"
        val categoryName = "Еда"
        val userId = null

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Ошибка авторизации", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with loading category should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = "350.50"
        val categoryName = "Загрузка категорий..."
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Выберите категорию", validationResult.errorMessage)
    }

    @Test
    fun `addTransaction with no categories should fail validation`() {
        // Arrange
        val title = "Обед"
        val amountText = "350.50"
        val categoryName = "Нет категорий"
        val userId = "test-user-id"

        // Act
        val validationResult = validator.validateTransaction(
            title = title,
            amountText = amountText,
            userId = userId,
            categoryName = categoryName
        )

        // Assert
        assertFalse(validationResult.isValid)
        assertEquals("Выберите категорию", validationResult.errorMessage)
    }
}

// Вспомогательный класс для валидации
class TransactionValidator {

    fun validateTransaction(
        title: String,
        amountText: String,
        userId: String?,
        categoryName: String
    ): ValidationResult {
        // Проверка userId
        if (userId == null) {
            return ValidationResult(false, errorMessage = "Ошибка авторизации")
        }

        // Проверка категории
        if (categoryName == "Загрузка категорий..." || categoryName == "Нет категорий") {
            return ValidationResult(false, errorMessage = "Выберите категорию")
        }

        // Проверка названия
        if (title.isEmpty()) {
            return ValidationResult(false, errorMessage = "Введите название")
        }

        // Проверка суммы
        if (amountText.isEmpty()) {
            return ValidationResult(false, errorMessage = "Введите сумму")
        }

        // Преобразование суммы
        val cleanAmount = amountText.replace(",", ".")
        val amount = cleanAmount.toDoubleOrNull()

        if (amount == null) {
            return ValidationResult(false, errorMessage = "Некорректная сумма")
        }

        if (amount <= 0) {
            return ValidationResult(false, errorMessage = "Сумма должна быть положительной")
        }

        return ValidationResult(true, title = title, amount = amount)
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val title: String = "",
    val amount: Double = 0.0,
    val errorMessage: String? = null
)