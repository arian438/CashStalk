package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.myfin.data.PinManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executor

class BiometricPinLoginActivity : AppCompatActivity() {

    private lateinit var pinManager: PinManager
    private lateinit var auth: FirebaseAuth
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private lateinit var pinInputLayout: TextInputLayout
    private lateinit var pinInput: TextInputEditText
    private lateinit var btnLoginWithPin: Button
    private lateinit var btnLoginWithBiometric: Button
    private lateinit var tvUseAnotherMethod: TextView
    private lateinit var tvForgotPin: TextView

    private var userId: String? = null
    private var isBiometricAvailable = false
    private var isBiometricEnabled = false
    private var pinAttempts = 0
    private val MAX_PIN_ATTEMPTS = 5
    private var blockUntilTime: Long = 0

    companion object {
        private const val TAG = "BiometricPinLoginActivity"
        private const val PREFS_NAME = "biometric_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biometric_pin_login)

        pinManager = PinManager(this)
        auth = FirebaseAuth.getInstance()

        initViews()
        checkCurrentUser()
        setupBiometric()
        checkBiometricAvailability()
    }

    private fun initViews() {
        pinInputLayout = findViewById(R.id.pinInputLayout)
        pinInput = findViewById(R.id.pinInput)
        btnLoginWithPin = findViewById(R.id.btnLoginWithPin)
        btnLoginWithBiometric = findViewById(R.id.btnLoginWithBiometric)
        tvUseAnotherMethod = findViewById(R.id.tvUseAnotherMethod)
        tvForgotPin = findViewById(R.id.tvForgotPin)

        btnLoginWithPin.setOnClickListener { loginWithPin() }
        btnLoginWithBiometric.setOnClickListener { authenticateWithBiometric() }
        tvUseAnotherMethod.setOnClickListener { toggleLoginMethod() }
        tvForgotPin.setOnClickListener { handleForgotPin() }
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Нет авторизованного пользователя - возвращаемся на главную
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        userId = currentUser.uid

        // Проверяем, есть ли PIN у пользователя
        if (!pinManager.hasPin(userId!!)) {
            // Если PIN не установлен, отправляем на создание PIN
            startActivity(Intent(this, CreatePinActivity::class.java))
            finish()
        }
    }

    private fun checkBiometricAvailability() {
        // Проверяем, включена ли биометрия в настройках
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isBiometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

        // Проверяем доступность биометрии на устройстве
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        isBiometricAvailable = isBiometricEnabled && canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS

        updateUIForBiometricAvailability()
    }

    private fun updateUIForBiometricAvailability() {
        if (isBiometricAvailable) {
            // Биометрия доступна и включена - показываем оба метода входа
            // По умолчанию показываем биометрию
            showBiometricMethod()
        } else {
            // Биометрия недоступна или отключена - показываем только PIN
            showPinMethod()
            tvUseAnotherMethod.visibility = View.GONE // Скрываем кнопку переключения
        }
    }

    private fun showBiometricMethod() {
        btnLoginWithBiometric.visibility = View.VISIBLE
        pinInputLayout.visibility = View.GONE
        btnLoginWithPin.visibility = View.GONE
        tvUseAnotherMethod.visibility = View.VISIBLE
        tvUseAnotherMethod.text = getString(R.string.use_pin)
    }

    private fun showPinMethod() {
        btnLoginWithBiometric.visibility = View.GONE
        pinInputLayout.visibility = View.VISIBLE
        btnLoginWithPin.visibility = View.VISIBLE
        if (isBiometricAvailable) {
            tvUseAnotherMethod.visibility = View.VISIBLE
            tvUseAnotherMethod.text = getString(R.string.use_biometric)
        } else {
            tvUseAnotherMethod.visibility = View.GONE
        }
    }

    private fun toggleLoginMethod() {
        if (btnLoginWithBiometric.visibility == View.VISIBLE) {
            // Сейчас видна биометрия - переключаем на PIN
            showPinMethod()
        } else {
            // Сейчас виден PIN - переключаем на биометрию
            showBiometricMethod()
        }
    }

    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Биометрия успешна - вход в приложение
                    runOnUiThread {
                        Toast.makeText(
                            this@BiometricPinLoginActivity,
                            getString(R.string.biometrics_auth_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToDashboard()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        // Ошибка биометрии - показываем PIN для входа
                        runOnUiThread {
                            Toast.makeText(
                                this@BiometricPinLoginActivity,
                                getString(R.string.biometrics_auth_failed) + ": $errString",
                                Toast.LENGTH_LONG
                            ).show()
                            // Автоматически переключаем на PIN при ошибке
                            showPinMethod()
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    runOnUiThread {
                        Toast.makeText(
                            this@BiometricPinLoginActivity,
                            getString(R.string.biometrics_auth_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometrics_auth_title))
            .setSubtitle(getString(R.string.biometrics_auth_subtitle))
            .setDescription(getString(R.string.biometrics_auth_description))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(getString(R.string.cancel))
            .build()
    }

    private fun authenticateWithBiometric() {
        biometricPrompt.authenticate(promptInfo)
    }

    private fun loginWithPin() {
        // Проверяем блокировку
        if (System.currentTimeMillis() < blockUntilTime) {
            val secondsLeft = ((blockUntilTime - System.currentTimeMillis()) / 1000) + 1
            pinInputLayout.error = getString(R.string.too_many_attempts)
            Toast.makeText(
                this,
                getString(R.string.too_many_attempts) + " $secondsLeft ${getString(R.string.seconds)}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val enteredPin = pinInput.text.toString().trim()

        if (enteredPin.isEmpty()) {
            pinInputLayout.error = getString(R.string.enter_pin)
            return
        }

        userId?.let { uid ->
            // ИСПОЛЬЗУЕМ verifyPin ВМЕСТО getPin
            val isPinValid = pinManager.verifyPin(uid, enteredPin)

            if (isPinValid) {
                // PIN верный - вход в приложение
                pinAttempts = 0
                Toast.makeText(
                    this,
                    getString(R.string.login_success),
                    Toast.LENGTH_SHORT
                ).show()
                navigateToDashboard()
            } else {
                // Неверный PIN
                pinAttempts++

                if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                    // Блокируем на 30 секунд
                    blockUntilTime = System.currentTimeMillis() + 30000 // 30 секунд
                    pinInputLayout.error = getString(R.string.too_many_attempts)
                    pinInput.text?.clear()

                    Toast.makeText(
                        this,
                        getString(R.string.too_many_attempts),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val attemptsLeft = MAX_PIN_ATTEMPTS - pinAttempts
                    pinInputLayout.error = getString(R.string.invalid_pin_attempts, attemptsLeft)
                    pinInput.text?.clear()
                }
            }
        }
    }

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun handleForgotPin() {
        // Забыли PIN - выход из аккаунта и вход заново
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.forgot_pin))
            .setMessage(getString(R.string.forgot_pin_message))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}