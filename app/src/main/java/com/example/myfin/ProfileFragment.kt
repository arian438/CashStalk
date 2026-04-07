package com.example.myfin

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myfin.data.TransactionRepository
import com.example.myfin.data.User
import com.example.myfin.data.UserRepository
import com.example.myfin.utils.DataExporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment(), CurrencyUpdateListener {

    private lateinit var userRepository: UserRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var dataExporter: DataExporter
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private lateinit var profileWelcomeText: TextView
    private lateinit var profileUserNameText: TextView
    private lateinit var profileName: TextView
    private lateinit var profileEmail: TextView
    private lateinit var profileInitials: TextView
    private lateinit var notificationBadge: TextView
    private lateinit var bellContainer: View

    private lateinit var rowSettingsTitle: TextView
    private lateinit var rowCategoriesTitle: TextView
    private lateinit var rowExportTitle: TextView
    private lateinit var rowAboutTitle: TextView

    private var currentUser: User? = null
    private var allTransactions: List<com.example.myfin.data.Transaction> = emptyList()
    private var currentCurrencySymbol: String = "₽"

    companion object {
        private const val REQUEST_WRITE_STORAGE = 100
        private const val TAG = "ProfileFragment"
    }

    private val badgeUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Badge update broadcast received")
            updateBadgeImmediately()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? DashboardActivity)?.addCurrencyUpdateListener(this)
    }

    override fun onDetach() {
        super.onDetach()
        (activity as? DashboardActivity)?.removeCurrencyUpdateListener(this)
    }

    override fun onCurrencyChanged(currencySymbol: String) {
        currentCurrencySymbol = currencySymbol
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())

        userRepository = UserRepository()
        transactionRepository = TransactionRepository()
        dataExporter = DataExporter(requireContext())

        profileWelcomeText = view.findViewById(R.id.profileWelcomeText)
        profileUserNameText = view.findViewById(R.id.profileUserNameText)
        profileName = view.findViewById(R.id.profileName)
        profileEmail = view.findViewById(R.id.profileEmail)
        profileInitials = view.findViewById(R.id.profileInitials)

        bellContainer = view.findViewById(R.id.bellContainer)
        notificationBadge = view.findViewById(R.id.notificationBadge)

        rowSettingsTitle = view.findViewById<View>(R.id.rowSettings).findViewById(R.id.rowTitle)
        rowCategoriesTitle = view.findViewById<View>(R.id.rowCategories).findViewById(R.id.rowTitle)
        rowExportTitle = view.findViewById<View>(R.id.rowExport).findViewById(R.id.rowTitle)
        rowAboutTitle = view.findViewById<View>(R.id.rowAbout).findViewById(R.id.rowTitle)

        setupUserObserver()
        setupTransactionsObserver()
        setupNotificationObserver()
        registerBadgeReceiver()

        view.findViewById<View>(R.id.ic_menu_profile)?.setOnClickListener {
            (activity as? DashboardActivity)?.openDrawer()
        }

        bellContainer.setOnClickListener {
            (activity as? DashboardActivity)?.showFragment(NotificationsFragment())
        }

        updateRowTitles()
        setupRowClickListeners(view)
        updateBadgeImmediately()
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

    private fun updateRowTitles() {
        rowSettingsTitle.text = getString(R.string.settings)
        rowCategoriesTitle.text = getString(R.string.categories)
        rowExportTitle.text = getString(R.string.export_data)
        rowAboutTitle.text = getString(R.string.about_app)
    }

    private fun setupUserObserver() {
        userRepository.getCurrentUserLiveData().observe(viewLifecycleOwner, Observer { user ->
            currentUser = user
            if (user != null) {
                currentCurrencySymbol = user.currencySymbol
            }
            updateUserProfile()
        })
        userRepository.loadCurrentUser()
    }

    private fun setupTransactionsObserver() {
        transactionRepository.getTransactionsLiveData().observe(viewLifecycleOwner, Observer { transactions ->
            allTransactions = transactions
            Log.d(TAG, "Транзакции обновлены: ${transactions.size} элементов")
        })
        transactionRepository.loadTransactions()
    }

    private fun updateUserProfile() {
        currentUser?.let { user ->
            val displayName = if (user.fio.isNotBlank()) {
                user.fio
            } else {
                user.email.substringBefore("@")
            }

            profileUserNameText.text = getString(R.string.welcome_user, displayName)
            profileWelcomeText.text = getString(R.string.welcome)
            profileName.text = displayName
            profileEmail.text = user.email

            val initials = generateInitials(displayName)
            profileInitials.text = initials
        }
    }

    private fun generateInitials(fullName: String): String {
        return try {
            val parts = fullName.trim().split("\\s+".toRegex())
            when {
                parts.size >= 2 -> {
                    val firstInitial = parts[0].first().uppercaseChar()
                    val lastInitial = parts.last().first().uppercaseChar()
                    "$firstInitial$lastInitial"
                }
                parts.size == 1 && parts[0].isNotEmpty() -> {
                    parts[0].take(2).uppercase()
                }
                else -> getString(R.string.default_initials)
            }
        } catch (e: Exception) {
            getString(R.string.default_initials)
        }
    }

    private fun setupRowClickListeners(view: View) {
        view.findViewById<View>(R.id.rowSettings)?.setOnClickListener {
            (activity as? DashboardActivity)?.showFragment(SettingsFragment())
        }

        view.findViewById<View>(R.id.rowCategories)?.setOnClickListener {
            (activity as? DashboardActivity)?.showFragment(CategoriesFragment())
        }

        view.findViewById<View>(R.id.rowExport)?.setOnClickListener {
            handleExportClick()
        }

        view.findViewById<View>(R.id.rowAbout)?.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun handleExportClick() {
        if (currentUser == null) {
            Toast.makeText(requireContext(), getString(R.string.load_user_data), Toast.LENGTH_SHORT).show()
            return
        }

        if (allTransactions.isEmpty()) {
            showNoDataDialog()
            return
        }

        if (checkStoragePermission()) {
            showExportDialog()
        }
    }

    private fun showExportDialog() {
        dataExporter.showExportFormatDialog(
            transactions = allTransactions,
            user = currentUser!!,
            onExportComplete = { filePath, format ->
                showExportSuccessDialog(filePath, format)
            }
        )
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true
        }

        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
            false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showExportDialog()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.storage_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showExportSuccessDialog(filePath: String, format: String) {
        val readablePath = dataExporter.getReadableFilePath(filePath)
        val formatName = when (format) {
            DataExporter.FORMAT_CSV -> "CSV"
            DataExporter.FORMAT_EXCEL -> "Excel"
            DataExporter.FORMAT_TEXT -> getString(R.string.text_report)
            else -> format
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.export_completed))
            .setMessage(getString(R.string.export_success_message, formatName, readablePath))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.share)) { dialog, _ ->
                shareExportedFile(filePath, formatName)
                dialog.dismiss()
            }
            .show()
    }

    private fun shareExportedFile(filePath: String, format: String) {
        try {
            val uri = Uri.parse(filePath)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (format) {
                    "CSV" -> "text/csv"
                    "Excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    else -> "text/plain"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.my_finances_export))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.export_file_attached))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.share_error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showNoDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.no_data_title))
            .setMessage(getString(R.string.no_data_message))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    /**
     * Показывает диалог "О приложении" с информацией о конфиденциальности
     */
    private fun showAboutDialog() {
        try {
            val appName = getString(R.string.app_name)
            val versionName = try {
                requireContext().packageManager
                    .getPackageInfo(requireContext().packageName, 0)
                    .versionName
            } catch (e: PackageManager.NameNotFoundException) {
                "1.0.0"
            }

            val currentYear = SimpleDateFormat("yyyy", Locale.getDefault())
                .format(Date())

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.about_app))
                .setMessage(
                    getString(R.string.about_message, appName, versionName, currentYear)
                )
                .setPositiveButton(getString(R.string.got_it)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton(getString(R.string.privacy)) { dialog, _ ->
                    showPrivacyDialog()
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия диалога о приложении", e)
            Toast.makeText(requireContext(), getString(R.string.about_error), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Показывает диалог с информацией о конфиденциальности
     */
    private fun showPrivacyDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.privacy))
            .setMessage(getString(R.string.privacy_message))
            .setPositiveButton(getString(R.string.got_it), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        userRepository.loadCurrentUser()
        transactionRepository.loadTransactions()
        updateRowTitles()

        val activity = activity as? DashboardActivity
        activity?.refreshNotificationBadge()
        updateBadgeImmediately()
    }

    override fun onDestroyView() {
        // Сначала отписываемся от receiver
        try {
            localBroadcastManager.unregisterReceiver(badgeUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        // Удаляем наблюдателей
        transactionRepository.getTransactionsLiveData().removeObservers(viewLifecycleOwner)

        // Вызываем super в конце
        super.onDestroyView()
    }
}