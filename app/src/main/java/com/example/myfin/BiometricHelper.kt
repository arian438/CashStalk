package com.example.myfin

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class BiometricHelper(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
    private val TAG = "BiometricHelper"

    companion object {
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_FACE_ENABLED = "face_enabled"
        private const val KEY_BIOMETRIC_AUTHENTICATED = "biometric_authenticated"
    }

    /**
     * Проверяет, поддерживается ли биометрия на устройстве (любая)
     */
    fun isAnyBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        Log.d(TAG, "Biometric availability: $result")
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Проверяет, есть ли биометрическое оборудование (даже если не настроено)
     */
    fun hasBiometricHardware(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
                result != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    /**
     * Проверяет, настроена ли биометрия в системе
     */
    fun isBiometricEnrolled(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Проверяет, включен ли отпечаток пальца
     */
    fun isFingerprintEnabled(): Boolean {
        return prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
    }

    /**
     * Устанавливает состояние отпечатка пальца
     */
    fun setFingerprintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply()
    }

    /**
     * Проверяет, включено ли распознавание лица
     */
    fun isFaceEnabled(): Boolean {
        return prefs.getBoolean(KEY_FACE_ENABLED, false)
    }

    /**
     * Устанавливает состояние распознавания лица
     */
    fun setFaceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FACE_ENABLED, enabled).apply()
    }

    /**
     * Проверяет, включена ли биометрия вообще
     */
    fun isBiometricEnabled(): Boolean {
        return isFingerprintEnabled() || isFaceEnabled()
    }

    /**
     * Проверяет, была ли уже пройдена аутентификация в текущей сессии
     */
    fun isAuthenticated(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_AUTHENTICATED, false)
    }

    /**
     * Устанавливает статус аутентификации
     */
    fun setAuthenticated(authenticated: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_AUTHENTICATED, authenticated).apply()
    }

    /**
     * Сбрасывает аутентификацию при выходе из приложения
     */
    fun resetAuthentication() {
        prefs.edit().putBoolean(KEY_BIOMETRIC_AUTHENTICATED, false).apply()
    }

    /**
     * Показывает диалог для отпечатка пальца
     */
    fun showFingerprintPrompt(
        fragment: Fragment,
        title: String,
        subtitle: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            fragment,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setAuthenticated(true)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(context.getString(R.string.cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Показывает диалог для распознавания лица
     */
    fun showFacePrompt(
        fragment: Fragment,
        title: String,
        subtitle: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(
            fragment,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    setAuthenticated(true)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        // Для лица используем специальные настройки
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(context.getString(R.string.cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}