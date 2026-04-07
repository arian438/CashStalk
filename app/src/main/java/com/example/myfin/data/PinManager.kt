package com.example.myfin.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class PinManager(context: Context) {

    private val appContext = context.applicationContext
    private val quickPrefs = appContext.getSharedPreferences("pin_check", Context.MODE_PRIVATE)
    private val pinCache = mutableMapOf<String, Boolean>()

    // Ленивая инициализация шифрованных preferences
    private val encryptedPrefs: SharedPreferences by lazy {
        Log.d("PinManager", "🔐 Initializing encrypted preferences...")
        val startTime = System.currentTimeMillis()
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_pin_prefs",
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also {
            Log.d("PinManager", "✅ Encrypted prefs ready in ${System.currentTimeMillis() - startTime}ms")
        }
    }

    // БЫСТРАЯ проверка наличия PIN (0-1 мс)
    fun hasPin(userId: String): Boolean {
        // Проверяем кэш
        pinCache[userId]?.let { return it }

        // Проверяем быстрое хранилище
        val result = quickPrefs.contains("has_pin_$userId")
        pinCache[userId] = result
        return result
    }

    // МЕДЛЕННАЯ проверка PIN (используем только при вводе)
    fun verifyPin(userId: String, enteredPin: String): Boolean {
        if (!hasPin(userId)) return false

        // Только здесь инициализируем шифрование
        val savedPin = encryptedPrefs.getString("pin_$userId", null)
        return savedPin == enteredPin
    }

    // Сохранение PIN
    fun savePin(userId: String, pin: String) {
        encryptedPrefs.edit().putString("pin_$userId", pin).apply()
        quickPrefs.edit().putBoolean("has_pin_$userId", true).apply()
        pinCache[userId] = true
    }

    // Удаление PIN
    fun deletePin(userId: String) {
        encryptedPrefs.edit().remove("pin_$userId").apply()
        quickPrefs.edit().remove("has_pin_$userId").apply()
        pinCache.remove(userId)
    }
}