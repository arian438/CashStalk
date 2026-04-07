package com.example.myfin

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LanguageHelper {

    const val PREFS_NAME = "language_prefs"
    const val KEY_LANGUAGE = "app_language"
    const val KEY_LANGUAGE_CHANGED = "language_changed"

    const val LANG_RUSSIAN = "ru"
    const val LANG_ENGLISH = "en"
    const val LANG_KAZAKH = "kk"
    const val LANG_BELARUSIAN = "be"

    // Сохраняем выбранный язык
    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LANGUAGE, languageCode)
            .putBoolean(KEY_LANGUAGE_CHANGED, true)
            .apply()
    }

    // Получаем сохраненный язык
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_RUSSIAN) ?: LANG_RUSSIAN
    }

    // Устанавливаем флаг, что язык был изменен
    fun setLanguageChanged(context: Context, changed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LANGUAGE_CHANGED, changed).apply()
    }

    // Проверяем, был ли изменен язык
    fun wasLanguageChanged(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LANGUAGE_CHANGED, false)
    }

    // Для совместимости с вашим кодом
    fun getCurrentLanguage(context: Context): String = getLanguage(context)

    // Создает контекст с нужной локалью
    fun wrapContext(context: Context): ContextWrapper {
        val languageCode = getLanguage(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }

        return ContextWrapper(context.createConfigurationContext(config))
    }

    // Для отображения в UI
    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            LANG_ENGLISH -> "English"
            LANG_KAZAKH -> "Қазақша"
            LANG_BELARUSIAN -> "Беларуская"
            else -> "Русский"
        }
    }

    // Получаем код языка из отображаемого имени
    fun getLanguageCodeFromDisplayName(displayName: String): String {
        return when (displayName) {
            "English" -> LANG_ENGLISH
            "Қазақша" -> LANG_KAZAKH
            "Беларуская" -> LANG_BELARUSIAN
            else -> LANG_RUSSIAN
        }
    }

    // Применить язык к контексту (для использования в активностях)
    fun applyLanguage(context: Context): Context {
        val languageCode = getLanguage(context)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}