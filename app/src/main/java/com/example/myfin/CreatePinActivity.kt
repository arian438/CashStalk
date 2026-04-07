package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText  // Изменено с TextInputEditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myfin.data.AuthRepository
import com.example.myfin.data.PinManager
import com.google.firebase.auth.FirebaseAuth

class CreatePinActivity : AppCompatActivity() {
    private lateinit var pinInputs: List<EditText>  // Изменено с TextInputEditText
    private lateinit var tvTitle: TextView
    private lateinit var tvError: TextView
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button

    private var firstPin = ""
    private var isConfirming = false
    private lateinit var auth: FirebaseAuth
    private val repo = AuthRepository()
    private lateinit var pinManager: PinManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pin_create)

        // Инициализируем FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Инициализируем PinManager
        pinManager = PinManager(this)

        // Исправляем метод установки отступов
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        pinInputs = listOf(
            findViewById(R.id.pinDigit1),
            findViewById(R.id.pinDigit2),
            findViewById(R.id.pinDigit3),
            findViewById(R.id.pinDigit4)
        )
        tvTitle = findViewById(R.id.tvTitle)
        tvError = findViewById(R.id.tvError)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnCancel = findViewById(R.id.btnCancel)

        // Если пользователь пришел с регистрации, скрываем кнопку отмены
        if (intent.getBooleanExtra("from_register", false)) {
            btnCancel.visibility = Button.GONE
        }
    }

    private fun setupListeners() {
        // Обработка ввода в поля PIN
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
                            // Последнее поле заполнено
                            btnSubmit.isEnabled = getEnteredPin().length == 4
                        }
                    } else if (s?.length == 0 && index > 0) {
                        // Возврат к предыдущему полю при удалении
                        pinInputs[index - 1].requestFocus()
                    }

                    // Обновляем состояние кнопки
                    btnSubmit.isEnabled = getEnteredPin().length == 4

                    // Скрываем ошибку при изменении
                    tvError.visibility = TextView.GONE
                }
            })
        }

        btnSubmit.setOnClickListener {
            val pin = getEnteredPin()
            if (pin.length == 4) {
                if (!isConfirming) {
                    // Первый ввод PIN
                    firstPin = pin
                    isConfirming = true
                    tvTitle.text = getString(R.string.confirm_pin)
                    clearPinInputs()
                    btnSubmit.isEnabled = false
                } else {
                    // Подтверждение PIN
                    if (pin == firstPin) {
                        // PIN-коды совпадают, сохраняем
                        savePinAndContinue(pin)
                    } else {
                        // PIN-коды не совпадают
                        tvError.text = getString(R.string.pins_do_not_match)
                        tvError.visibility = TextView.VISIBLE
                        clearPinInputs()
                        isConfirming = false
                        tvTitle.text = getString(R.string.create_pin)
                        btnSubmit.isEnabled = false
                    }
                }
            }
        }

        btnCancel.setOnClickListener {
            // Возврат на экран входа
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun getEnteredPin(): String {
        return pinInputs.joinToString("") { it.text.toString() }
    }

    private fun clearPinInputs() {
        pinInputs.forEach { it.text?.clear() }
        pinInputs[0].requestFocus()
    }

    private fun savePinAndContinue(pin: String) {
        // Получаем текущего пользователя через FirebaseAuth напрямую
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // Сохраняем PIN для пользователя
            pinManager.savePin(currentUser.uid, pin)

            Toast.makeText(this, R.string.pin_created_success, Toast.LENGTH_SHORT).show()

            // Переходим на главный экран
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        } else {
            // Если пользователь не авторизован, возвращаемся на вход
            Toast.makeText(this, R.string.error_user_not_found, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}