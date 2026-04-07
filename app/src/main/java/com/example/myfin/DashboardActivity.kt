package com.example.myfin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myfin.data.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var userRepository: UserRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private var currentUser: User? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private var currentFragmentTag: String? = null
    private var selectedNavItemId: Int = R.id.nav_home
    private var lastBadgeUpdateTime = 0L
    private val BADGE_UPDATE_COOLDOWN = 500L

    private val currencyUpdateListeners = mutableListOf<CurrencyUpdateListener>()

    private val PERMISSION_REQUEST_NOTIFICATIONS = 1001

    // BroadcastReceiver для обновления бейджа при получении нового уведомления
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NotificationRepository.ACTION_NEW_NOTIFICATION -> {
                    Log.d(TAG, "Broadcast received: NEW_NOTIFICATION")
                    val now = System.currentTimeMillis()
                    if (now - lastBadgeUpdateTime > BADGE_UPDATE_COOLDOWN) {
                        lastBadgeUpdateTime = now
                        refreshNotificationBadge()
                        sendBroadcastToFragments()

                        val title = intent.getStringExtra("notification_title") ?: getString(R.string.new_notification)
                        Toast.makeText(
                            this@DashboardActivity,
                            getString(R.string.new_notification_received_toast, title),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                NotificationRepository.ACTION_NOTIFICATIONS_UPDATED -> {
                    Log.d(TAG, "Broadcast received: NOTIFICATIONS_UPDATED")
                    refreshNotificationBadge()
                    sendBroadcastToFragments()
                }
            }
        }
    }

    companion object {
        private const val TAG = "DashboardActivity"
        private const val KEY_CURRENT_FRAGMENT = "current_fragment"
        private const val KEY_SELECTED_NAV_ITEM = "selected_nav_item"
        private const val EXTRA_OPEN_SETTINGS = "open_settings"

        const val ACTION_NEW_NOTIFICATION = "com.example.myfin.NEW_NOTIFICATION"
        const val ACTION_NOTIFICATIONS_UPDATED = "com.example.myfin.NOTIFICATIONS_UPDATED"
        const val ACTION_UPDATE_BADGES = "com.example.myfin.UPDATE_BADGES"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applySavedTheme(this)
        super.onCreate(savedInstanceState)

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (savedInstanceState != null) {
            currentFragmentTag = savedInstanceState.getString(KEY_CURRENT_FRAGMENT)
            selectedNavItemId = savedInstanceState.getInt(KEY_SELECTED_NAV_ITEM, R.id.nav_home)
        }

        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)

        try {
            notificationRepository = NotificationRepository(this)
            userRepository = UserRepository()
            transactionRepository = TransactionRepository(notificationRepository)
            categoryRepository = CategoryRepository(notificationRepository)

            setContentView(R.layout.activity_dashboard)

            drawerLayout = findViewById(R.id.drawerLayout)
            navView = findViewById(R.id.navView)
            bottomNav = findViewById(R.id.bottomNav)

            setupBackPressedHandler()
            navView.setNavigationItemSelectedListener(this)

            setupFirebaseMessaging()
            setupNavigationHeader()
            setupBottomNavigation()
            setupUserObserver()
            setupNotificationsObserver()

            registerNotificationReceiver()
            requestNotificationPermission()

            if (openSettings) {
                Log.d(TAG, "Opening SettingsFragment")
                showFragment(SettingsFragment(), "SettingsFragment", false)
                resetBottomNavigation()
                selectedNavItemId = -1
            } else if (LanguageHelper.wasLanguageChanged(this)) {
                LanguageHelper.setLanguageChanged(this, false)
                showFragment(SettingsFragment(), "SettingsFragment", false)
                resetBottomNavigation()
                selectedNavItemId = -1
            } else if (savedInstanceState == null) {
                showFragment(HomeFragment(), "HomeFragment", false)
                bottomNav.selectedItemId = R.id.nav_home
                selectedNavItemId = R.id.nav_home
            } else {
                restoreCurrentFragment()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Toast.makeText(this, getString(R.string.loading_error, e.message), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun sendBroadcastToFragments() {
        try {
            val intent = Intent(ACTION_UPDATE_BADGES)
            localBroadcastManager.sendBroadcast(intent)
            Log.d(TAG, "Broadcast sent to fragments: $ACTION_UPDATE_BADGES")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast to fragments", e)
        }
    }

    fun getUnreadNotificationCount(): Int {
        return if (::notificationRepository.isInitialized) {
            notificationRepository.getUnreadCount()
        } else {
            0
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.notifications_permission_title)
                        .setMessage(R.string.notifications_permission_rationale)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                PERMISSION_REQUEST_NOTIFICATIONS
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_REQUEST_NOTIFICATIONS
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.notifications_enabled, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.notifications_disabled_long, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction(NotificationRepository.ACTION_NEW_NOTIFICATION)
            addAction(NotificationRepository.ACTION_NOTIFICATIONS_UPDATED)
        }
        localBroadcastManager.registerReceiver(notificationReceiver, filter)
        Log.d(TAG, "Notification receiver registered")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            localBroadcastManager.unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Receiver already unregistered")
        }

        if (::transactionRepository.isInitialized) {
            transactionRepository.cleanup()
        }
        if (::categoryRepository.isInitialized) {
            categoryRepository.cleanup()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationBadge()
        sendBroadcastToFragments()
    }

    private fun restoreCurrentFragment() {
        when (currentFragmentTag) {
            "HomeFragment" -> {
                showFragment(HomeFragment(), "HomeFragment", false)
                bottomNav.selectedItemId = R.id.nav_home
                selectedNavItemId = R.id.nav_home
            }
            "StatsFragment" -> {
                showFragment(StatsFragment(), "StatsFragment", false)
                bottomNav.selectedItemId = R.id.nav_stats
                selectedNavItemId = R.id.nav_stats
            }
            "ProfileFragment" -> {
                showFragment(ProfileFragment(), "ProfileFragment", false)
                bottomNav.selectedItemId = R.id.nav_profile
                selectedNavItemId = R.id.nav_profile
            }
            "NotificationsFragment" -> {
                showFragment(NotificationsFragment(), "NotificationsFragment", false)
                bottomNav.selectedItemId = R.id.nav_notifications
                selectedNavItemId = R.id.nav_notifications
            }
            "SettingsFragment" -> {
                showFragment(SettingsFragment(), "SettingsFragment", false)
                resetBottomNavigation()
                selectedNavItemId = -1
            }
            "CategoriesFragment" -> {
                showFragment(CategoriesFragment(), "CategoriesFragment", false)
                resetBottomNavigation()
                selectedNavItemId = -1
            }
            else -> {
                showFragment(HomeFragment(), "HomeFragment", false)
                bottomNav.selectedItemId = R.id.nav_home
                selectedNavItemId = R.id.nav_home
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateUIForThemeChange()
    }

    private fun updateUIForThemeChange() {
        navView.invalidate()
        bottomNav.invalidate()
        updateNavigationHeader(currentUser)

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment != null) {
            when (currentFragmentTag) {
                "HomeFragment" -> showFragment(HomeFragment(), "HomeFragment", false)
                "StatsFragment" -> showFragment(StatsFragment(), "StatsFragment", false)
                "ProfileFragment" -> showFragment(ProfileFragment(), "ProfileFragment", false)
                "NotificationsFragment" -> showFragment(NotificationsFragment(), "NotificationsFragment", false)
                "SettingsFragment" -> showFragment(SettingsFragment(), "SettingsFragment", false)
                "CategoriesFragment" -> showFragment(CategoriesFragment(), "CategoriesFragment", false)
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(HomeFragment(), "HomeFragment")
                    selectedNavItemId = R.id.nav_home
                    true
                }
                R.id.nav_stats -> {
                    showFragment(StatsFragment(), "StatsFragment")
                    selectedNavItemId = R.id.nav_stats
                    true
                }
                R.id.nav_profile -> {
                    showFragment(ProfileFragment(), "ProfileFragment")
                    selectedNavItemId = R.id.nav_profile
                    true
                }
                R.id.nav_settings -> {
                    showFragment(SettingsFragment(), "SettingsFragment")
                    resetBottomNavigation()
                    selectedNavItemId = -1
                    true
                }
                else -> false
            }
        }
    }

    private fun setupFirebaseMessaging() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                saveFCMToken(task.result)
            }
        }
    }

    private fun saveFCMToken(token: String) {
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
        Log.d(TAG, "FCM token saved: $token")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_FRAGMENT, currentFragmentTag)
        outState.putInt(KEY_SELECTED_NAV_ITEM, selectedNavItemId)
    }

    private fun setupUserObserver() {
        userRepository.getCurrentUserLiveData().observe(this, Observer { user ->
            if (user != null) {
                currentUser = user
                updateNavigationHeader(user)
            }
        })
        userRepository.loadCurrentUser()
    }

    private fun setupNotificationsObserver() {
        notificationRepository.notificationsLiveData.observe(this) { notifications ->
            Log.d(TAG, "Notifications updated, count: ${notifications.size}")
            refreshNotificationBadge()
            sendBroadcastToFragments()
        }
    }

    private fun setupNavigationHeader() {
        val headerView = navView.getHeaderView(0)

        headerView.findViewById<View>(R.id.userInfoLayout)?.setOnClickListener {
            showFragment(ProfileFragment(), "ProfileFragment")
            bottomNav.selectedItemId = R.id.nav_profile
            selectedNavItemId = R.id.nav_profile
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        headerView.findViewById<View>(R.id.ic_bell_header)?.setOnClickListener {
            showFragment(NotificationsFragment(), "NotificationsFragment")
            bottomNav.selectedItemId = R.id.nav_notifications
            selectedNavItemId = R.id.nav_notifications
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun updateNavigationHeader(user: User?) {
        user?.let {
            try {
                val headerView = navView.getHeaderView(0)
                val navUserAvatar = headerView.findViewById<TextView>(R.id.navUserAvatar)
                val navUserName = headerView.findViewById<TextView>(R.id.navUserName)
                val navUserEmail = headerView.findViewById<TextView>(R.id.navUserEmail)

                val displayName = if (it.fio.isNotBlank()) it.fio else it.email.substringBefore("@")

                navUserName?.text = displayName
                navUserEmail?.text = it.email
                navUserAvatar?.text = generateInitials(displayName)

                refreshNotificationBadge()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating header: ${e.message}")
            }
        }
    }

    fun refreshNotificationBadge() {
        try {
            val unreadCount = getUnreadNotificationCount()
            val headerView = navView.getHeaderView(0)
            val badge = headerView.findViewById<TextView>(R.id.notificationBadge)

            if (badge != null) {
                if (unreadCount > 0) {
                    badge.visibility = View.VISIBLE
                    badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                    Log.d(TAG, "Notification badge updated: $unreadCount unread")
                } else {
                    badge.visibility = View.GONE
                    Log.d(TAG, "Notification badge hidden")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification badge", e)
        }
    }

    private fun generateInitials(fullName: String): String {
        return try {
            val parts = fullName.trim().split("\\s+".toRegex())
            when {
                parts.size >= 2 -> "${parts[0].first()}${parts.last().first()}".uppercase()
                parts.size == 1 && parts[0].isNotEmpty() -> parts[0].take(2).uppercase()
                else -> getString(R.string.default_initials)
            }
        } catch (e: Exception) {
            getString(R.string.default_initials)
        }
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else if (supportFragmentManager.backStackEntryCount > 1) {
                    supportFragmentManager.popBackStack()
                    updateNavigationAfterBack()
                } else {
                    finishAffinity()
                }
            }
        })
    }

    private fun updateNavigationAfterBack() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        currentFragmentTag = fragment?.tag ?: "HomeFragment"

        when (fragment) {
            is HomeFragment -> {
                bottomNav.selectedItemId = R.id.nav_home
                selectedNavItemId = R.id.nav_home
            }
            is StatsFragment -> {
                bottomNav.selectedItemId = R.id.nav_stats
                selectedNavItemId = R.id.nav_stats
            }
            is ProfileFragment -> {
                bottomNav.selectedItemId = R.id.nav_profile
                selectedNavItemId = R.id.nav_profile
            }
            is NotificationsFragment -> {
                bottomNav.selectedItemId = R.id.nav_notifications
                selectedNavItemId = R.id.nav_notifications
            }
            else -> resetBottomNavigation()
        }

        refreshNotificationBadge()
        sendBroadcastToFragments()
    }

    fun openDrawer() {
        try {
            if (::drawerLayout.isInitialized) {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening drawer", e)
        }
    }

    fun showFragment(fragment: Fragment, tag: String? = null, addToBackStack: Boolean = true) {
        try {
            val fragmentTag = tag ?: fragment::class.java.simpleName
            currentFragmentTag = fragmentTag

            val transaction = supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment, fragmentTag)

            if (addToBackStack) {
                transaction.addToBackStack(fragmentTag)
            }

            transaction.commit()

            when (fragment) {
                is SettingsFragment, is CategoriesFragment -> resetBottomNavigation()
                else -> bottomNav.menu.setGroupCheckable(0, true, true)
            }

            // Если это фрагмент уведомлений, принудительно обновляем
            if (fragment is NotificationsFragment) {
                notificationRepository.forceRefresh()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing fragment", e)
        }
    }

    fun showAddTransactionDialog() {
        try {
            val dialog = AddTransactionDialogFragment()
            dialog.show(supportFragmentManager, "AddTransactionDialog")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing dialog", e)
            Toast.makeText(this, getString(R.string.dialog_error), Toast.LENGTH_SHORT).show()
        }
    }

    fun getCurrentUser(): User? = currentUser

    fun getNotificationRepository(): NotificationRepository = notificationRepository
    fun getTransactionRepository(): TransactionRepository = transactionRepository
    fun getCategoryRepository(): CategoryRepository = categoryRepository

    fun updateUserSettings(user: User) {
        try {
            currentUser = user
            userRepository.updateUser(user) { success, message ->
                if (!success) {
                    Toast.makeText(this, getString(R.string.settings_save_error, message), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user settings", e)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                showFragment(HomeFragment(), "HomeFragment")
                selectedNavItemId = R.id.nav_home
                bottomNav.selectedItemId = R.id.nav_home
            }
            R.id.nav_notifications -> {
                showFragment(NotificationsFragment(), "NotificationsFragment")
                selectedNavItemId = R.id.nav_notifications
                bottomNav.selectedItemId = R.id.nav_notifications
            }
            R.id.nav_stats -> {
                showFragment(StatsFragment(), "StatsFragment")
                selectedNavItemId = R.id.nav_stats
                bottomNav.selectedItemId = R.id.nav_stats
            }
            R.id.nav_profile -> {
                showFragment(ProfileFragment(), "ProfileFragment")
                selectedNavItemId = R.id.nav_profile
                bottomNav.selectedItemId = R.id.nav_profile
            }
            R.id.nav_categories -> {
                showFragment(CategoriesFragment(), "CategoriesFragment")
                resetBottomNavigation()
            }
            R.id.nav_settings -> {
                showFragment(SettingsFragment(), "SettingsFragment")
                resetBottomNavigation()
            }
            R.id.nav_about -> showAboutDialog()
            R.id.nav_logout -> logout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun resetBottomNavigation() {
        bottomNav.menu.setGroupCheckable(0, true, false)
        for (i in 0 until bottomNav.menu.size()) {
            bottomNav.menu.getItem(i).isChecked = false
        }
        bottomNav.menu.setGroupCheckable(0, true, true)
    }

    private fun logout() {
        try {
            transactionRepository.cleanup()
            categoryRepository.cleanup()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out", e)
            Toast.makeText(this, getString(R.string.logout_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        try {
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                "1.0.0"
            }
            val currentYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_app))
                .setMessage(getString(R.string.about_message, getString(R.string.app_name), versionName, currentYear))
                .setPositiveButton(getString(R.string.got_it)) { dialog, _ -> dialog.dismiss() }
                .setNeutralButton(getString(R.string.privacy)) { dialog, _ ->
                    showPrivacyDialog()
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing about dialog", e)
        }
    }

    private fun showPrivacyDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.privacy))
            .setMessage(getString(R.string.privacy_message))
            .setPositiveButton(getString(R.string.got_it), null)
            .show()
    }

    fun addCurrencyUpdateListener(listener: CurrencyUpdateListener) {
        if (!currencyUpdateListeners.contains(listener)) {
            currencyUpdateListeners.add(listener)
        }
    }

    fun removeCurrencyUpdateListener(listener: CurrencyUpdateListener) {
        currencyUpdateListeners.remove(listener)
    }

    fun notifyCurrencyChanged(currencySymbol: String) {
        currencyUpdateListeners.forEach { listener ->
            try {
                listener.onCurrencyChanged(currencySymbol)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }
}