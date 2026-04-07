package com.example.myfin

import org.junit.Assert.*
import org.junit.Test

class EmailValidationTest {

    private fun isValidEmail(email: String): Boolean {
        // Простая валидация email
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return email.matches(emailRegex.toRegex())
    }

    @Test
    fun testValidEmails() {
        // Корректные email адреса
        assertTrue(isValidEmail("user@example.com"))
        assertTrue(isValidEmail("user.name@example.co.uk"))
        assertTrue(isValidEmail("user+tag@example.com"))
        assertTrue(isValidEmail("123@example.com"))
        assertTrue(isValidEmail("user@subdomain.example.com"))
    }

    @Test
    fun testInvalidEmails() {
        // Некорректные email адреса
        assertFalse(isValidEmail("invalid-email")) // нет @ и домена
        assertFalse(isValidEmail("user@")) // нет домена
        assertFalse(isValidEmail("@example.com")) // нет локальной части
        assertFalse(isValidEmail("user@.com")) // нет доменного имени
        assertFalse(isValidEmail("user@example.")) // нет доменной зоны
        assertFalse(isValidEmail("user name@example.com")) // пробел
        assertFalse(isValidEmail("")) // пустая строка
        assertFalse(isValidEmail("   ")) // пробелы
    }
}