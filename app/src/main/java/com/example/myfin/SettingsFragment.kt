package com.example.myfin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myfin.data.User
import com.example.myfin.data.UserRepository
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private lateinit var currencySpinner: Spinner
    private lateinit var languageSpinner: Spinner
    private lateinit var currencyTextView: TextView
    private lateinit var languageTextView: TextView
    private lateinit var settingsWelcomeText: TextView
    private lateinit var settingsUserNameText: TextView
    private lateinit var notificationsSwitch: SwitchMaterial
    private lateinit var darkThemeSwitch: SwitchMaterial
    private lateinit var biometricSwitch: SwitchMaterial
    private lateinit var biometricStatusText: TextView
    private lateinit var biometryUnavailableText: TextView
    private lateinit var notificationBadge: TextView
    private lateinit var bellContainer: View
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private lateinit var userRepository: UserRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPrefs: SharedPreferences
    private var currentUser: User? = null
    private var isViewDestroyed = false
    private var isUpdatingFromCode = false
    private var isBiometricSwitchChanging = false

    // Добавляем недостающие переменные
    private var currentCurrencyPosition = 0
    private var currentLanguagePosition = 0

    // Для биометрии
    private val BIOMETRIC_PREFS = "biometric_prefs"
    private val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    // Для уведомлений
    private val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

    companion object {
        private const val TAG = "SettingsFragment"
        private const val EXTRA_OPEN_SETTINGS = "open_settings"
        private const val PREFS_NAME = "app_settings"

        // Ключи для SharedPreferences
        private const val KEY_SELECTED_CURRENCY_NAME = "selected_currency_name"
        private const val KEY_SELECTED_CURRENCY_SYMBOL = "selected_currency_symbol"
        private const val KEY_SELECTED_LANGUAGE_NAME = "selected_language_name"
        private const val KEY_SELECTED_LANGUAGE_CODE = "selected_language_code"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_CURRENCY_POSITION = "currency_position"
        private const val KEY_LANGUAGE_POSITION = "language_position"

        // Значения по умолчанию
        private const val DEFAULT_CURRENCY_NAME = "Российский рубль"
        private const val DEFAULT_CURRENCY_SYMBOL = "₽"
        private const val DEFAULT_LANGUAGE_NAME = "Русский"
        private const val DEFAULT_LANGUAGE_CODE = "ru"
        private const val DEFAULT_DARK_MODE = false
        private const val DEFAULT_NOTIFICATIONS = true
    }

    // BroadcastReceiver для обновления бейджа
    private val badgeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Badge update broadcast received")
            updateBadgeImmediately()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewDestroyed = false

        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())
        auth = FirebaseAuth.getInstance()
        userRepository = UserRepository()
        sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews(view)

        // Загружаем сохраненные настройки из локального хранилища
        loadLocalSettings()

        setupCurrencySpinner()
        setupLanguageSpinner()
        setupSwitches()
        setupBiometricSwitch()
        setupClickHandlers(view)
        loadUserData()
        checkBiometricAvailability()
        setupNotificationObserver()
        registerBadgeReceiver()
        updateBadgeImmediately()
    }

    /**
     * Загрузка всех настроек из локального SharedPreferences
     */
    private fun loadLocalSettings() {
        Log.d(TAG, "📦 Загрузка локальных настроек")

        // Загрузка позиций спиннеров
        currentCurrencyPosition = sharedPrefs.getInt(KEY_CURRENCY_POSITION, 0)
        currentLanguagePosition = sharedPrefs.getInt(KEY_LANGUAGE_POSITION, 0)

        // Загрузка состояния переключателей
        val darkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE)
        val notifications = sharedPrefs.getBoolean(KEY_NOTIFICATIONS, DEFAULT_NOTIFICATIONS)

        // Загрузка биометрии
        val biometricPrefs = requireContext().getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
        val biometricEnabled = biometricPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

        // Применяем тему
        val themeMode = if (darkMode) ThemeHelper.THEME_DARK else ThemeHelper.THEME_LIGHT
        ThemeHelper.setTheme(themeMode, requireContext())

        // Устанавливаем язык
        val languageCode = sharedPrefs.getString(KEY_SELECTED_LANGUAGE_CODE, DEFAULT_LANGUAGE_CODE) ?: DEFAULT_LANGUAGE_CODE
        if (LanguageHelper.getLanguage(requireContext()) != languageCode) {
            LanguageHelper.setLanguage(requireContext(), languageCode)
        }

        // Обновляем UI
        isUpdatingFromCode = true
        try {
            val currencyName = sharedPrefs.getString(KEY_SELECTED_CURRENCY_NAME, DEFAULT_CURRENCY_NAME) ?: DEFAULT_CURRENCY_NAME
            val languageName = sharedPrefs.getString(KEY_SELECTED_LANGUAGE_NAME, DEFAULT_LANGUAGE_NAME) ?: DEFAULT_LANGUAGE_NAME

            currencyTextView.text = currencyName
            languageTextView.text = languageName
            darkThemeSwitch.isChecked = darkMode
            notificationsSwitch.isChecked = notifications

            // Обновляем биометрию
            isBiometricSwitchChanging = true
            biometricSwitch.isChecked = biometricEnabled
            isBiometricSwitchChanging = false
            updateBiometricStatusText(biometricEnabled)

            Log.d(TAG, "✅ Локальные настройки загружены:")
            Log.d(TAG, "   Валюта: $currencyName")
            Log.d(TAG, "   Язык: $languageName")
            Log.d(TAG, "   Тёмная тема: $darkMode")
            Log.d(TAG, "   Уведомления: $notifications")
            Log.d(TAG, "   Биометрия: $biometricEnabled")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки локальных настроек: ${e.message}")
        } finally {
            isUpdatingFromCode = false
        }
    }

    /**
     * Сохранение всех настроек в локальное хранилище
     */
    private fun saveLocalSettings() {
        val editor = sharedPrefs.edit()

        editor.putInt(KEY_CURRENCY_POSITION, currentCurrencyPosition)
        editor.putInt(KEY_LANGUAGE_POSITION, currentLanguagePosition)
        editor.putBoolean(KEY_DARK_MODE, darkThemeSwitch.isChecked)
        editor.putBoolean(KEY_NOTIFICATIONS, notificationsSwitch.isChecked)

        // Сохраняем валюту
        val selectedCurrency = currencySpinner.selectedItem?.toString()
        if (selectedCurrency != null) {
            val currencyMap = getCurrencyMap()
            currencyMap[selectedCurrency]?.let { (currency, symbol) ->
                editor.putString(KEY_SELECTED_CURRENCY_NAME, currency)
                editor.putString(KEY_SELECTED_CURRENCY_SYMBOL, symbol)
            }
        }

        // Сохраняем язык
        val selectedLanguage = languageSpinner.selectedItem?.toString()
        if (selectedLanguage != null) {
            editor.putString(KEY_SELECTED_LANGUAGE_NAME, selectedLanguage)
            val languageCode = LanguageHelper.getLanguageCodeFromDisplayName(selectedLanguage)
            editor.putString(KEY_SELECTED_LANGUAGE_CODE, languageCode)
        }

        editor.apply()
        Log.d(TAG, "💾 Локальные настройки сохранены")
    }

    private fun registerBadgeReceiver() {
        val filter = IntentFilter(DashboardActivity.ACTION_UPDATE_BADGES)
        localBroadcastManager.registerReceiver(badgeUpdateReceiver, filter)
    }

    private fun updateBadgeImmediately() {
        val activity = activity as? DashboardActivity
        val unreadCount = activity?.getUnreadNotificationCount() ?: 0
        updateNotificationBadge(unreadCount)
    }

    private fun initViews(view: View) {
        settingsWelcomeText = view.findViewById(R.id.settingsWelcomeText)
        settingsUserNameText = view.findViewById(R.id.settingsUserNameText)
        currencySpinner = view.findViewById(R.id.currencySpinner)
        currencyTextView = view.findViewById(R.id.currencyTextView)
        languageSpinner = view.findViewById(R.id.languageSpinner)
        languageTextView = view.findViewById(R.id.languageTextView)
        notificationsSwitch = view.findViewById(R.id.notificationsSwitch)
        darkThemeSwitch = view.findViewById(R.id.darkThemeSwitch)
        biometricSwitch = view.findViewById(R.id.biometricSwitch)
        biometricStatusText = view.findViewById(R.id.biometricStatusText)
        biometryUnavailableText = view.findViewById(R.id.biometryUnavailableText)

        bellContainer = view.findViewById(R.id.bellContainer)
        notificationBadge = view.findViewById(R.id.notificationBadge)

        view.findViewById<View>(R.id.changePasswordCard)?.setOnClickListener {
            if (!isViewDestroyed) showChangePasswordDialog()
        }
    }

    private fun setupNotificationObserver() {
        val activity = activity as? DashboardActivity
        activity?.getNotificationRepository()?.notificationsLiveData?.observe(viewLifecycleOwner) { notifications ->
            val unreadCount = notifications.count { !it.isRead }
            updateNotificationBadge(unreadCount)
        }
    }

    private fun updateNotificationBadge(count: Int) {
        activity?.runOnUiThread {
            if (count > 0) {
                notificationBadge.visibility = View.VISIBLE
                notificationBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                notificationBadge.visibility = View.GONE
            }
        }
    }

    private fun checkBiometricAvailability() {
        val biometricManager = BiometricManager.from(requireContext())
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricSwitch.isEnabled = true
                biometryUnavailableText.visibility = View.GONE
                // Состояние уже загружено в loadLocalSettings
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                biometricSwitch.isEnabled = false
                biometryUnavailableText.visibility = View.VISIBLE
                biometryUnavailableText.text = "Биометрия не настроена в системе"
                // Отключаем биометрию в настройках, так как она недоступна
                val prefs = requireContext().getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply()
                biometricSwitch.isChecked = false
            }
            else -> {
                biometricSwitch.isEnabled = false
                biometryUnavailableText.visibility = View.VISIBLE
                biometryUnavailableText.text = getString(R.string.biometrics_not_available)
                val prefs = requireContext().getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply()
                biometricSwitch.isChecked = false
            }
        }
    }

    private fun saveBiometricState(enabled: Boolean) {
        val prefs = requireContext().getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        Log.d(TAG, "💾 Биометрия сохранена: $enabled")
    }

    private fun updateBiometricStatusText(enabled: Boolean) {
        biometricStatusText.text = if (enabled)
            "Биометрия включена" else getString(R.string.face_id_touch_id)
    }

    private fun setupBiometricSwitch() {
        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isViewDestroyed || isUpdatingFromCode || isBiometricSwitchChanging) {
                return@setOnCheckedChangeListener
            }

            val prefs = requireContext().getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            val currentlyEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

            if (isChecked == currentlyEnabled) {
                return@setOnCheckedChangeListener
            }

            if (!isChecked) {
                saveBiometricState(false)
                updateBiometricStatusText(false)
                Toast.makeText(
                    requireContext(),
                    "Биометрия отключена",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                val executor = ContextCompat.getMainExecutor(requireContext())

                val biometricPrompt = BiometricPrompt(
                    this@SettingsFragment,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            saveBiometricState(true)
                            updateBiometricStatusText(true)

                            isBiometricSwitchChanging = true
                            biometricSwitch.isChecked = true
                            isBiometricSwitchChanging = false

                            Toast.makeText(
                                requireContext(),
                                "Биометрия включена",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)

                            isBiometricSwitchChanging = true
                            biometricSwitch.isChecked = false
                            isBiometricSwitchChanging = false

                            saveBiometricState(false)
                            updateBiometricStatusText(false)

                            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_CANCELED) {
                                Toast.makeText(
                                    requireContext(),
                                    "Ошибка: $errString",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Подтверждение")
                    .setSubtitle("Вход в приложение")
                    .setDescription("Используйте отпечаток пальца или лицо")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .setNegativeButtonText(getString(R.string.cancel))
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    private fun loadUserData() {
        userRepository.getCurrentUserLiveData().observe(viewLifecycleOwner, Observer { user ->
            if (user != null && !isViewDestroyed) {
                currentUser = user
                updateUserName()
                // Не перезаписываем локальные настройки данными из БД
            }
        })
        userRepository.loadCurrentUser()
    }

    private fun updateUserName() {
        if (isViewDestroyed) return
        currentUser?.let { user ->
            val displayName = if (user.fio.isNotBlank()) user.fio else user.email.substringBefore("@")
            settingsUserNameText.text = "$displayName 👋"
            settingsWelcomeText.text = getString(R.string.welcome)
        }
    }

    private fun getCurrencyMap(): Map<String, Pair<String, String>> {
        val currencies = resources.getStringArray(R.array.currencies)
        return mapOf(
            currencies[0] to Pair(getString(R.string.russian_ruble), "₽"),
            currencies[1] to Pair(getString(R.string.us_dollar), "$"),
            currencies[2] to Pair(getString(R.string.euro), "€"),
            currencies[3] to Pair(getString(R.string.kazakhstani_tenge), "₸"),
            currencies[4] to Pair(getString(R.string.belarusian_ruble), "Br")
        )
    }

    private fun setupCurrencySpinner() {
        val currencies = resources.getStringArray(R.array.currencies)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        currencySpinner.adapter = adapter

        // Устанавливаем сохраненную позицию
        currencySpinner.setSelection(currentCurrencyPosition)

        currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isViewDestroyed || isUpdatingFromCode) return

                val selected = parent?.getItemAtPosition(position).toString()
                val currencyMap = getCurrencyMap()

                currencyMap[selected]?.let { (currency, symbol) ->
                    // Обновляем позицию и сохраняем локально
                    currentCurrencyPosition = position
                    currencyTextView.text = currency

                    // Сохраняем в локальное хранилище
                    saveLocalSettings()

                    // Обновляем в базе данных
                    updateUserSettings(currency = currency, currencySymbol = symbol)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupLanguageSpinner() {
        val languages = resources.getStringArray(R.array.languages)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        // Устанавливаем сохраненную позицию
        languageSpinner.setSelection(currentLanguagePosition)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isViewDestroyed || isUpdatingFromCode) return

                val selectedLanguageName = parent?.getItemAtPosition(position).toString()

                // Обновляем позицию и сохраняем локально
                currentLanguagePosition = position
                languageTextView.text = selectedLanguageName

                // Сохраняем в локальное хранилище
                saveLocalSettings()

                val languageCode = LanguageHelper.getLanguageCodeFromDisplayName(selectedLanguageName)

                // Обновляем в базе данных
                updateUserSettings(language = selectedLanguageName)

                // Если язык изменился, перезапускаем приложение
                if (LanguageHelper.getLanguage(requireContext()) != languageCode) {
                    LanguageHelper.setLanguage(requireContext(), languageCode)
                    LanguageHelper.setLanguageChanged(requireContext(), true)

                    Toast.makeText(requireContext(), getString(R.string.language_changed), Toast.LENGTH_SHORT).show()

                    val intent = Intent(requireActivity(), DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    intent.putExtra(EXTRA_OPEN_SETTINGS, true)
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwitches() {
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isViewDestroyed || isUpdatingFromCode) return@setOnCheckedChangeListener
            saveLocalSettings()
            updateUserSettings(notifications = isChecked)
        }

        darkThemeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isViewDestroyed || isUpdatingFromCode) return@setOnCheckedChangeListener

            // Сохраняем локально
            saveLocalSettings()

            // Обновляем в БД
            updateUserSettings(darkMode = isChecked)

            // Применяем тему
            val themeMode = if (isChecked) ThemeHelper.THEME_DARK else ThemeHelper.THEME_LIGHT
            ThemeHelper.setTheme(themeMode, requireContext())

            Toast.makeText(
                requireContext(),
                if (isChecked) getString(R.string.dark_theme_applied) else getString(R.string.light_theme_applied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupClickHandlers(view: View) {
        view.findViewById<View>(R.id.ic_back)?.setOnClickListener {
            if (!isViewDestroyed) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        bellContainer.setOnClickListener {
            (activity as? DashboardActivity)?.showFragment(NotificationsFragment())
        }
    }

    private fun showChangePasswordDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.change_password))
            .setView(R.layout.dialog_change_password)
            .setPositiveButton(getString(R.string.change)) { dialog, _ ->
                val dialogView = (dialog as AlertDialog).findViewById<TextInputLayout>(R.id.currentPasswordLayout)?.parent as? ViewGroup
                val currentPassword = dialogView?.findViewById<TextInputEditText>(R.id.currentPassword)?.text.toString()
                val newPassword = dialogView?.findViewById<TextInputEditText>(R.id.newPassword)?.text.toString()
                val confirmPassword = dialogView?.findViewById<TextInputEditText>(R.id.confirmPassword)?.text.toString()

                if (validatePasswordInput(currentPassword, newPassword, confirmPassword)) {
                    changePassword(currentPassword, newPassword)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun validatePasswordInput(currentPassword: String, newPassword: String, confirmPassword: String): Boolean {
        if (currentPassword.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.enter_current_password), Toast.LENGTH_SHORT).show()
            return false
        }
        if (newPassword.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.enter_new_password), Toast.LENGTH_SHORT).show()
            return false
        }
        if (newPassword.length < 6) {
            Toast.makeText(requireContext(), getString(R.string.password_min_length), Toast.LENGTH_SHORT).show()
            return false
        }
        if (newPassword != confirmPassword) {
            Toast.makeText(requireContext(), getString(R.string.passwords_do_not_match), Toast.LENGTH_SHORT).show()
            return false
        }
        if (currentPassword == newPassword) {
            Toast.makeText(requireContext(), getString(R.string.password_must_differ), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser
        val email = user?.email

        if (user == null || email == null) {
            Toast.makeText(requireContext(), getString(R.string.user_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.changing_password))
            .setCancelable(false)
            .create()
        progressDialog.show()

        val credential = EmailAuthProvider.getCredential(email, currentPassword)

        user.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    user.updatePassword(newPassword)
                        .addOnCompleteListener { updateTask ->
                            progressDialog.dismiss()
                            if (updateTask.isSuccessful) {
                                Toast.makeText(requireContext(), getString(R.string.password_changed_success), Toast.LENGTH_SHORT).show()
                                showReLoginDialog()
                            } else {
                                val error = updateTask.exception?.message ?: getString(R.string.unknown_error)
                                Toast.makeText(requireContext(), getString(R.string.password_change_error, error), Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    progressDialog.dismiss()
                    val error = reauthTask.exception?.message ?: getString(R.string.unknown_error)
                    if (error.contains("invalid credential", ignoreCase = true) || error.contains("wrong password", ignoreCase = true)) {
                        Toast.makeText(requireContext(), getString(R.string.invalid_current_password), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.auth_error, error), Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    private fun showReLoginDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.password_changed))
            .setMessage(getString(R.string.relogin_recommendation))
            .setPositiveButton(getString(R.string.logout)) { dialog, _ ->
                auth.signOut()
                startActivity(Intent(requireContext(), MainActivity::class.java))
                requireActivity().finish()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.stay)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateUserSettings(
        currency: String? = null,
        currencySymbol: String? = null,
        darkMode: Boolean? = null,
        language: String? = null,
        notifications: Boolean? = null
    ) {
        val user = currentUser ?: return

        val updatedUser = user.copy(
            currency = currency ?: user.currency,
            currencySymbol = currencySymbol ?: user.currencySymbol,
            darkMode = darkMode ?: user.darkMode,
            language = language ?: user.language,
            notifications = notifications ?: user.notifications
        )

        userRepository.updateUser(updatedUser) { success, message ->
            if (isViewDestroyed) return@updateUser

            if (success) {
                currentUser = updatedUser
                if (currencySymbol != null) {
                    (activity as? DashboardActivity)?.notifyCurrencyChanged(currencySymbol)
                    Toast.makeText(requireContext(), getString(R.string.currency_changed), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.settings_save_error, message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isViewDestroyed) {
            userRepository.loadCurrentUser()

            // Перезагружаем состояние биометрии из локального хранилища
            val prefs = requireContext().getSharedPreferences(BIOMETRIC_PREFS, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

            isBiometricSwitchChanging = true
            biometricSwitch.isChecked = isEnabled
            isBiometricSwitchChanging = false

            updateBiometricStatusText(isEnabled)

            val activity = activity as? DashboardActivity
            activity?.refreshNotificationBadge()
            updateBadgeImmediately()
        }
    }

    override fun onDestroyView() {
        try {
            localBroadcastManager.unregisterReceiver(badgeUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        isViewDestroyed = true
        super.onDestroyView()
    }
}