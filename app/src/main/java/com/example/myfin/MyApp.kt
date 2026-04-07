package com.example.myfin

import android.app.Application
import android.util.Log
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*

class MyApp : Application() {

    companion object {
        lateinit var instance: MyApp
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        val startTime = System.currentTimeMillis()
        super.onCreate()
        instance = this

        Log.d("Performance", "MyApp.onCreate start")

        // Применяем сохраненную тему (очень быстро)
        ThemeHelper.applySavedTheme(this)

        // Минимальная синхронная инициализация Firebase (обязательно)
        FirebaseApp.initializeApp(this)
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Может бросаться при повторной инициализации в редких процессах.
            Log.w("Performance", "Firebase persistence already configured: ${e.message}")
        }

        Log.d("Performance", "Firebase init: ${System.currentTimeMillis() - startTime}ms")

        // Вся остальная инициализация в фоне
        applicationScope.launch {
            initializeInBackground()
        }

        Log.d("Performance", "MyApp.onCreate total: ${System.currentTimeMillis() - startTime}ms")
    }

    private suspend fun initializeInBackground() {
        withContext(Dispatchers.IO) {
            try {
                // Прогреваем только необходимые классы Realtime Database
                Class.forName("com.google.firebase.database.FirebaseDatabase")
                Class.forName("com.google.firebase.database.DatabaseReference")

                // Прогреваем SharedPreferences (быстро)
                getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
                getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(base))
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}