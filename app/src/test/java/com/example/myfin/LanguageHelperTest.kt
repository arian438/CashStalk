package com.example.myfin

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LanguageHelperTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        // Настройка моков для SharedPreferences
        `when`(mockContext.getSharedPreferences(LanguageHelper.PREFS_NAME, Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
    }

    @Test
    fun `setLanguage should save language code to preferences`() {
        // Arrange
        val languageCode = LanguageHelper.LANG_ENGLISH

        // Act
        LanguageHelper.setLanguage(mockContext, languageCode)

        // Assert
        verify(mockEditor).putString(LanguageHelper.KEY_LANGUAGE, languageCode)
        verify(mockEditor).apply()
    }

    @Test
    fun `getLanguage should return default Russian when no language saved`() {
        // Arrange
        `when`(mockSharedPreferences.getString(LanguageHelper.KEY_LANGUAGE, LanguageHelper.LANG_RUSSIAN))
            .thenReturn(LanguageHelper.LANG_RUSSIAN)

        // Act
        val result = LanguageHelper.getLanguage(mockContext)

        // Assert
        assertEquals(LanguageHelper.LANG_RUSSIAN, result)
    }

    @Test
    fun `getLanguage should return saved language when exists`() {
        // Arrange
        val savedLanguage = LanguageHelper.LANG_ENGLISH
        `when`(mockSharedPreferences.getString(LanguageHelper.KEY_LANGUAGE, LanguageHelper.LANG_RUSSIAN))
            .thenReturn(savedLanguage)

        // Act
        val result = LanguageHelper.getLanguage(mockContext)

        // Assert
        assertEquals(savedLanguage, result)
    }
}