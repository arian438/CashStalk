package com.example.myfin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Минимальная тема - показывается МГНОВЕННО
        setTheme(R.style.Theme_MyFin_Splash)
        super.onCreate(savedInstanceState)

        // Даже layout не нужен - используем windowBackground
        setContentView(android.R.layout.simple_list_item_1) // Пустой layout

        // Перенаправляем через 100мс (почти мгновенно)
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNext()
        }, 100)
    }

    private fun navigateToNext() {
        val intent = try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser != null) {
                // Пользователь авторизован
                Intent(this, BiometricPinLoginActivity::class.java)
            } else {
                // Не авторизован
                Intent(this, MainActivity::class.java)
            }
        } catch (e: Exception) {
            Intent(this, MainActivity::class.java)
        }

        startActivity(intent)
        finish()
    }
}