package com.example.myfin

import com.example.myfin.data.User
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SettingsUnitTest {

    private lateinit var validator: SettingsValidator
    private lateinit var currencyHelper: CurrencyHelper

    @Before
    fun setUp() {
        validator = SettingsValidator()
        currencyHelper = CurrencyHelper()
    }

    @Test
    fun `validate password change with valid inputs returns true`() {
        // Arrange
        val currentPassword = "oldPassword123"
        val newPassword = "newPassword123"
        val confirmPassword = "newPassword123"

        // Act
        val result = validator.validatePasswordChange(
            currentPassword, newPassword, confirmPassword
        )

        // Assert
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validate password change with empty current password returns error`() {
        // Arrange
        val currentPassword = ""
        val newPassword = "newPassword123"
        val confirmPassword = "newPassword123"

        // Act
        val result = validator.validatePasswordChange(
            currentPassword, newPassword, confirmPassword
        )

        // Assert
        assertFalse(result.isValid)
        assertEquals("Введите текущий пароль", result.errorMessage)
    }

    @Test
    fun `validate password change with empty new password returns error`() {
        // Arrange
        val currentPassword = "oldPassword123"
        val newPassword = ""
        val confirmPassword = ""

        // Act
        val result = validator.validatePasswordChange(
            currentPassword, newPassword, confirmPassword
        )

        // Assert
        assertFalse(result.isValid)
        assertEquals("Введите новый пароль", result.errorMessage)
    }

    @Test
    fun `validate password change with short new password returns error`() {
        // Arrange
        val currentPassword = "oldPassword123"
        val newPassword = "12345"
        val confirmPassword = "12345"

        // Act
        val result = validator.validatePasswordChange(
            currentPassword, newPassword, confirmPassword
        )

        // Assert
        assertFalse(result.isValid)
        assertEquals("Пароль должен быть не менее 6 символов", result.errorMessage)
    }

    @Test
    fun `validate password change with mismatched passwords returns error`() {
        // Arrange
        val currentPassword = "oldPassword123"
        val newPassword = "newPassword123"
        val confirmPassword = "differentPassword123"

        // Act
        val result = validator.validatePasswordChange(
            currentPassword, newPassword, confirmPassword
        )

        // Assert
        assertFalse(result.isValid)
        assertEquals("Пароли не совпадают", result.errorMessage)
    }

    @Test
    fun `validate password change with same current and new password returns error`() {
        // Arrange
        val currentPassword = "samePassword123"
        val newPassword = "samePassword123"
        val confirmPassword = "samePassword123"

        // Act
        val result = validator.validatePasswordChange(
            currentPassword, newPassword, confirmPassword
        )

        // Assert
        assertFalse(result.isValid)
        assertEquals("Новый пароль должен отличаться от текущего", result.errorMessage)
    }

    @Test
    fun `currency map returns correct symbol for each currency`() {
        // Act & Assert
        assertEquals("₽", currencyHelper.getSymbolForCurrency("Российский рубль"))
        assertEquals("$", currencyHelper.getSymbolForCurrency("Доллар США"))
        assertEquals("€", currencyHelper.getSymbolForCurrency("Евро"))
        assertEquals("₸", currencyHelper.getSymbolForCurrency("Казахстанский тенге"))
        assertEquals("Br", currencyHelper.getSymbolForCurrency("Белорусский рубль"))
        assertEquals("₽", currencyHelper.getSymbolForCurrency("Неизвестная валюта")) // default
    }

    @Test
    fun `user settings update should create updated user object`() {
        // Arrange - используем правильные поля из класса User
        val originalUser = User(
            id = "test-id",
            fio = "Тестовый Пользователь",
            email = "test@example.com",
            currency = "Российский рубль",
            currencySymbol = "₽",
            language = "Русский",
            darkMode = false,
            notifications = true,
            isDefault = false,
            orderIndex = 0
        )

        // Act
        val updatedUser = originalUser.copy(
            currency = "Доллар США",
            currencySymbol = "$",
            darkMode = true,
            notifications = false
        )

        // Assert
        assertEquals("Доллар США", updatedUser.currency)
        assertEquals("$", updatedUser.currencySymbol)
        assertTrue(updatedUser.darkMode)
        assertFalse(updatedUser.notifications)
        assertEquals("Русский", updatedUser.language) // не изменилось
        assertEquals("test-id", updatedUser.id) // не изменилось
        assertEquals("Тестовый Пользователь", updatedUser.fio) // не изменилось
        assertEquals("test@example.com", updatedUser.email) // не изменилось
    }

    @Test
    fun `user settings update should preserve unchanged fields`() {
        // Arrange
        val originalUser = User(
            id = "test-id",
            fio = "Тестовый Пользователь",
            email = "test@example.com",
            currency = "Российский рубль",
            currencySymbol = "₽",
            language = "Русский",
            darkMode = false,
            notifications = true,
            isDefault = false,
            orderIndex = 5
        )

        // Act - меняем только валюту
        val updatedUser = originalUser.copy(
            currency = "Евро",
            currencySymbol = "€"
        )

        // Assert - проверяем, что изменилось только то, что нужно
        assertEquals("Евро", updatedUser.currency)
        assertEquals("€", updatedUser.currencySymbol)

        // Остальные поля не должны измениться
        assertEquals("test-id", updatedUser.id)
        assertEquals("Тестовый Пользователь", updatedUser.fio)
        assertEquals("test@example.com", updatedUser.email)
        assertEquals("Русский", updatedUser.language)
        assertFalse(updatedUser.darkMode) // не изменилось
        assertTrue(updatedUser.notifications) // не изменилось
        assertFalse(updatedUser.isDefault) // не изменилось
        assertEquals(5, updatedUser.orderIndex) // не изменилось
    }
}

// Вспомогательные классы для тестов
class SettingsValidator {
    // Внутренний класс для результата валидации
    data class PasswordValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    fun validatePasswordChange(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): PasswordValidationResult {
        if (currentPassword.isEmpty()) {
            return PasswordValidationResult(false, "Введите текущий пароль")
        }
        if (newPassword.isEmpty()) {
            return PasswordValidationResult(false, "Введите новый пароль")
        }
        if (newPassword.length < 6) {
            return PasswordValidationResult(false, "Пароль должен быть не менее 6 символов")
        }
        if (newPassword != confirmPassword) {
            return PasswordValidationResult(false, "Пароли не совпадают")
        }
        if (currentPassword == newPassword) {
            return PasswordValidationResult(false, "Новый пароль должен отличаться от текущего")
        }
        return PasswordValidationResult(true)
    }
}

class CurrencyHelper {
    private val currencyMap = mapOf(
        "Российский рубль" to "₽",
        "Доллар США" to "$",
        "Евро" to "€",
        "Казахстанский тенге" to "₸",
        "Белорусский рубль" to "Br"
    )

    fun getSymbolForCurrency(currencyName: String): String {
        return currencyMap[currencyName] ?: "₽"
    }
}