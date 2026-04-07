package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myfin.data.AuthRepository
import com.example.myfin.data.PinManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var pinManager: PinManager
    private val repo by lazy { AuthRepository() }
    private val tag = "MainActivity"
    private val mainScope = MainScope()

    private lateinit var btnLogin: Button
    private lateinit var emailInput: EditText
    private lateinit var passInput: EditText

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Устанавливаем тему до создания view
        setTheme(R.style.Theme_MyFin)
        super.onCreate(savedInstanceState)

        // СНАЧАЛА проверяем авторизацию
        if (checkAuthAndNavigate()) {
            return // Если пользователь авторизован - завершаем onCreate, UI не показывается
        }

        // Только если пользователь НЕ авторизован - показываем UI
        setupLoginUI()
    }

    /**
     * Проверяет авторизацию и перенаправляет если нужно
     * @return true если пользователь авторизован (активность будет закрыта)
     */
    private fun checkAuthAndNavigate(): Boolean {
        try {
            // Быстрая инициализация Firebase
            auth = FirebaseAuth.getInstance()

            val currentUser = auth.currentUser

            if (currentUser != null) {
                // PinManager теперь быстрый (без шифрования)
                pinManager = PinManager(this)

                val hasPin = pinManager.hasPin(currentUser.uid)

                val intent = if (hasPin) {
                    Intent(this, BiometricPinLoginActivity::class.java)
                } else {
                    Intent(this, DashboardActivity::class.java)
                }

                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return true
            }
        } catch (e: Exception) {
            Log.e(tag, "Error checking auth", e)
        }

        return false
    }
    private fun setupLoginUI() {
        setContentView(R.layout.activity_main)

        // Инициализация view
        emailInput = findViewById(R.id.emailField)
        passInput = findViewById(R.id.passwordField)
        btnLogin = findViewById(R.id.btnLogin)
        val btnRegister = findViewById<TextView>(R.id.btnRegister)
        val forgotPassword = findViewById<TextView>(R.id.forgot)

        // Устанавливаем слушатели
        btnLogin.setOnClickListener { onLoginClick() }
        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Инициализируем остальное в фоне
        initNonCriticalComponents()
    }

    private fun initNonCriticalComponents() {
        mainScope.launch(Dispatchers.IO) {
            try {
                // Инициализируем PinManager в фоне
                if (!::pinManager.isInitialized) {
                    pinManager = PinManager(this@MainActivity)
                }

                // Прогреваем репозиторий
                repo.hashCode()

            } catch (e: Exception) {
                Log.e(tag, "Background init error", e)
            }
        }
    }

    private fun onLoginClick() {
        val email = emailInput.text.toString().trim()
        val pass = passInput.text.toString()

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, R.string.enter_email_and_password, Toast.LENGTH_SHORT).show()
            return
        }

        // Блокируем кнопку
        btnLogin.isEnabled = false

        repo.login(email, pass) { ok, err ->
            btnLogin.isEnabled = true

            if (ok) {
                Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()

                val user = auth.currentUser
                if (user != null) {
                    handlePostLogin(user.uid)
                } else {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            } else {
                Toast.makeText(this,
                    getString(R.string.error, err ?: getString(R.string.unknown_error)),
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePostLogin(userId: String) {
        mainScope.launch {
            val hasPin = withContext(Dispatchers.IO) {
                if (!::pinManager.isInitialized) {
                    pinManager = PinManager(this@MainActivity)
                }
                pinManager.hasPin(userId)
            }

            val intent = if (!hasPin) {
                Intent(this@MainActivity, CreatePinActivity::class.java)
            } else {
                Intent(this@MainActivity, BiometricPinLoginActivity::class.java)
            }

            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}