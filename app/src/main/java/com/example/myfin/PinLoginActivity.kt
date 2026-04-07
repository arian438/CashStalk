package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myfin.data.AuthRepository
import com.example.myfin.data.PinManager
import com.google.firebase.auth.FirebaseAuth

class PinLoginActivity : AppCompatActivity() {
    private lateinit var pinInputs: List<EditText>
    private lateinit var tvEmail: TextView
    private lateinit var tvError: TextView
    private lateinit var btnLoginWithPassword: TextView

    private var attempts = 0
    private val maxAttempts = 3
    private lateinit var pinManager: PinManager
    private lateinit var auth: FirebaseAuth
    private val repo = AuthRepository()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pin_login)

        // Инициализируем FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Инициализируем PinManager после создания Activity
        pinManager = PinManager(this)

        // Исправляем метод установки отступов
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()

        // Загружаем данные пользователя
        loadUserEmail()
    }

    private fun initViews() {
        pinInputs = listOf(
            findViewById(R.id.pinDigit1),
            findViewById(R.id.pinDigit2),
            findViewById(R.id.pinDigit3),
            findViewById(R.id.pinDigit4)
        )
        tvEmail = findViewById(R.id.tvEmail)
        tvError = findViewById(R.id.tvError)
        btnLoginWithPassword = findViewById(R.id.btnLoginWithPassword)
    }

    private fun loadUserEmail() {
        // Сначала показываем email из FirebaseAuth
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            tvEmail.text = firebaseUser.email ?: getString(R.string.user_not_found)
        } else {
            tvEmail.text = getString(R.string.user_not_found)
        }

        // Также загружаем полные данные пользователя через репозиторий (опционально)
        repo.getCurrentUser { user ->
            if (user != null) {
                // Если нужно отобразить имя вместо email
                // tvEmail.text = user.fio
            }
        }
    }

    private fun setupListeners() {
        pinInputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        // Переход к следующему полю
                        if (index < pinInputs.size - 1) {
                            pinInputs[index + 1].requestFocus()
                        } else {
                            // Последнее поле заполнено - проверяем PIN
                            val enteredPin = getEnteredPin()
                            if (enteredPin.length == 4) {
                                checkPin(enteredPin)
                            }
                        }
                    } else if (s?.length == 0 && index > 0) {
                        // Возврат к предыдущему полю при удалении
                        pinInputs[index - 1].requestFocus()
                    }

                    // Скрываем ошибку при изменении
                    tvError.visibility = TextView.GONE
                }
            })
        }

        btnLoginWithPassword.setOnClickListener {
            // Переход на экран входа с паролем
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun getEnteredPin(): String {
        return pinInputs.joinToString("") { it.text.toString() }
    }

    private fun checkPin(enteredPin: String) {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Toast.makeText(this, R.string.error_user_not_found, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // ИСПОЛЬЗУЕМ verifyPin ВМЕСТО getPin
        val isPinValid = pinManager.verifyPin(firebaseUser.uid, enteredPin)

        if (isPinValid) {
            // PIN верный
            Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        } else {
            // PIN неверный
            attempts++
            val remainingAttempts = maxAttempts - attempts

            if (remainingAttempts > 0) {
                tvError.text = getString(R.string.invalid_pin_attempts, remainingAttempts)
                tvError.visibility = TextView.VISIBLE
                clearPinInputs()
            } else {
                blockPinEntry()
            }
        }
    }

    private fun clearPinInputs() {
        pinInputs.forEach { it.text?.clear() }
        pinInputs[0].requestFocus()
    }

    private fun blockPinEntry() {
        tvError.text = getString(R.string.too_many_attempts)
        tvError.visibility = TextView.VISIBLE
        pinInputs.forEach { it.isEnabled = false }

        // Блокируем на 30 секунд
        Handler(Looper.getMainLooper()).postDelayed({
            pinInputs.forEach { it.isEnabled = true }
            attempts = 0
            tvError.visibility = TextView.GONE
            clearPinInputs()
        }, 30000)
    }
}