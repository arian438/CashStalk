package com.example.myfin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.adapter.NotificationAdapter
import com.example.myfin.data.Notification
import com.example.myfin.data.NotificationRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class NotificationsFragment : Fragment() {

    private lateinit var notificationRepository: NotificationRepository

    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var emptyStateSubText: TextView
    private lateinit var notificationsWelcomeText: TextView
    private lateinit var notificationsUserNameText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var btnClearAll: MaterialButton
    private lateinit var btnMarkAllRead: MaterialButton
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private var allNotifications = mutableListOf<Notification>()
    private var filteredNotifications = mutableListOf<Notification>()
    private var isRefreshing = false
    private val handler = Handler(Looper.getMainLooper())
    private var refreshDelay = 0L

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "Broadcast received: $action")

            when (action) {
                DashboardActivity.ACTION_NEW_NOTIFICATION -> {
                    val notificationTitle = intent.getStringExtra("notification_title")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.new_notification_received_toast, notificationTitle ?: getString(R.string.new_notification)),
                        Toast.LENGTH_SHORT
                    ).show()
                    scheduleRefresh()
                }
                DashboardActivity.ACTION_NOTIFICATIONS_UPDATED -> {
                    Log.d(TAG, "Notifications updated broadcast received")
                    scheduleRefresh()
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationsFragment"
        private const val FILTER_ALL = "all"
        private const val FILTER_UNREAD = "unread"
        private const val REFRESH_DELAY_MS = 300L
    }

    private var currentFilter = FILTER_ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())

        try {
            notificationRepository = (activity as DashboardActivity).getNotificationRepository()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notification repository", e)
            notificationRepository = NotificationRepository(requireContext())
        }

        initViews(view)
        setupRecyclerView()
        setupNotificationsObserver()
        setupToggleGroup(view)
        setupClickHandlers(view)
        registerNotificationReceiver()
        updateUserName()

        // Загружаем уведомления один раз
        loadNotificationsOnce()
    }

    private fun initViews(view: View) {
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        emptyStateSubText = view.findViewById(R.id.emptyStateSubText)
        notificationsWelcomeText = view.findViewById(R.id.notificationsWelcomeText)
        notificationsUserNameText = view.findViewById(R.id.notificationsUserNameText)
        recyclerView = view.findViewById(R.id.recyclerViewNotifications)
        btnClearAll = view.findViewById(R.id.btnClearAll)
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead)
        toggleGroup = view.findViewById(R.id.toggleGroup)
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(
            notifications = emptyList(),
            onNotificationClicked = { notification ->
                onNotificationClicked(notification)
            },
            onNotificationDeleted = { notification ->
                showDeleteConfirmationDialog(notification)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = notificationAdapter
        recyclerView.setHasFixedSize(true)
    }

    private fun setupToggleGroup(view: View) {
        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnAll -> {
                        currentFilter = FILTER_ALL
                        filterNotifications()
                        showFilterToast(getString(R.string.showing_all_notifications))
                    }
                    R.id.btnUnread -> {
                        currentFilter = FILTER_UNREAD
                        filterNotifications()
                        showFilterToast(getString(R.string.showing_unread_notifications))
                    }
                }
            }
        }
    }

    private fun setupClickHandlers(view: View) {
        view.findViewById<View>(R.id.ic_back)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<View>(R.id.ic_bell)?.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.already_in_notifications), Toast.LENGTH_SHORT).show()
        }

        btnClearAll.setOnClickListener {
            showClearAllConfirmationDialog()
        }

        btnMarkAllRead.setOnClickListener {
            markAllAsRead()
        }
    }

    private fun setupNotificationsObserver() {
        notificationRepository.notificationsLiveData.observe(viewLifecycleOwner, Observer { notifications ->
            if (notifications != null) {
                Log.d(TAG, "LiveData updated, notifications count: ${notifications.size}")
                allNotifications = notifications.toMutableList()
                filterNotifications()
                (activity as? DashboardActivity)?.refreshNotificationBadge()
            }
        })
    }

    /**
     * Одноразовая загрузка уведомлений (без цикла)
     */
    private fun loadNotificationsOnce() {
        try {
            val freshNotifications = notificationRepository.getAllNotifications()
            Log.d(TAG, "Loaded ${freshNotifications.size} notifications")
            allNotifications = freshNotifications.toMutableList()
            filterNotifications()

            // Логируем типы уведомлений для отладки
            freshNotifications.forEach { notification ->
                Log.d(TAG, "Notification: type=${notification.type}, title=${notification.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading notifications", e)
        }
    }

    /**
     * Отложенное обновление (предотвращает бесконечный цикл)
     */
    private fun scheduleRefresh() {
        if (isRefreshing) return

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            refreshNotifications()
        }, REFRESH_DELAY_MS)
    }

    /**
     * Принудительное обновление уведомлений с защитой от рекурсии
     */
    private fun refreshNotifications() {
        if (isRefreshing) {
            Log.d(TAG, "Already refreshing, skipping")
            return
        }

        isRefreshing = true
        Log.d(TAG, "🔄 Refreshing notifications")

        try {
            val freshNotifications = notificationRepository.getAllNotifications()
            Log.d(TAG, "Got ${freshNotifications.size} notifications")

            // Обновляем только если данные изменились
            if (allNotifications.size != freshNotifications.size ||
                allNotifications.map { it.id } != freshNotifications.map { it.id }) {
                allNotifications = freshNotifications.toMutableList()
                filterNotifications()
            }

            (activity as? DashboardActivity)?.refreshNotificationBadge()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing notifications", e)
        } finally {
            handler.postDelayed({
                isRefreshing = false
            }, 500)
        }
    }

    private fun showDeleteConfirmationDialog(notification: Notification) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_notification))
            .setMessage(getString(R.string.delete_notification_confirmation))
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                deleteNotification(notification)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteNotification(notification: Notification) {
        try {
            notificationRepository.deleteNotification(notification.id)
            Toast.makeText(
                requireContext(),
                getString(R.string.notification_deleted),
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Notification deleted: ${notification.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification", e)
            Toast.makeText(
                requireContext(),
                getString(R.string.error, e.message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateUserName() {
        val user = (activity as? DashboardActivity)?.getCurrentUser()
        user?.let {
            val displayName = if (it.fio.isNotBlank()) {
                it.fio
            } else {
                it.email.substringBefore("@")
            }

            notificationsUserNameText.text = getString(R.string.welcome_user, displayName)
            notificationsWelcomeText.text = getString(R.string.welcome)
        }
    }

    private fun filterNotifications() {
        filteredNotifications.clear()

        when (currentFilter) {
            FILTER_ALL -> {
                filteredNotifications.addAll(allNotifications)
            }
            FILTER_UNREAD -> {
                filteredNotifications.addAll(allNotifications.filter { !it.isRead })
            }
        }

        filteredNotifications.sortByDescending { it.timestamp }

        notificationAdapter.updateData(filteredNotifications)
        updateEmptyState()
        updateButtons()

        Log.d(TAG, "Filtered ${filteredNotifications.size} notifications (filter: $currentFilter)")
    }

    private fun onNotificationClicked(notification: Notification) {
        notificationRepository.markAsRead(notification.id)

        when (notification.type) {
            "transaction" -> {
                notification.transactionId?.let { transactionId ->
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.opening_transaction)} #${transactionId.takeLast(8)}",
                        Toast.LENGTH_SHORT
                    ).show()
                } ?: run {
                    Toast.makeText(requireContext(), getString(R.string.opening_transaction), Toast.LENGTH_SHORT).show()
                }
            }
            "limit" -> {
                // Уведомление о превышении лимита - показываем статистику
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notification_limit_exceeded_title),
                    Toast.LENGTH_LONG
                ).show()
                // Можно открыть статистику или категории
                (activity as? DashboardActivity)?.showFragment(StatsFragment(), "StatsFragment")
            }
            "category" -> {
                Toast.makeText(requireContext(), getString(R.string.opening_category), Toast.LENGTH_SHORT).show()
                (activity as? DashboardActivity)?.showFragment(CategoriesFragment(), "CategoriesFragment")
            }
            "reminder" -> {
                Toast.makeText(requireContext(), getString(R.string.opening_add_transaction), Toast.LENGTH_SHORT).show()
                (activity as? DashboardActivity)?.showAddTransactionDialog()
            }
            "report" -> {
                Toast.makeText(requireContext(), getString(R.string.opening_transaction), Toast.LENGTH_SHORT).show()
                (activity as? DashboardActivity)?.showFragment(StatsFragment(), "StatsFragment")
            }
            else -> {
                Toast.makeText(requireContext(), getString(R.string.notification_marked_read), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyState() {
        if (filteredNotifications.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE

            when (currentFilter) {
                FILTER_ALL -> {
                    emptyStateText.text = getString(R.string.no_notifications)
                    emptyStateSubText.text = getString(R.string.no_notifications_subtitle)
                }
                FILTER_UNREAD -> {
                    emptyStateText.text = getString(R.string.no_unread_notifications)
                    emptyStateSubText.text = getString(R.string.all_notifications_read)
                }
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        }
    }

    private fun updateButtons() {
        btnClearAll.visibility = if (filteredNotifications.isNotEmpty()) View.VISIBLE else View.GONE

        val hasUnread = filteredNotifications.any { !it.isRead }
        btnMarkAllRead.visibility = if (hasUnread) View.VISIBLE else View.GONE
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_notifications))
            .setMessage(getString(R.string.clear_all_confirmation))
            .setPositiveButton(getString(R.string.clear)) { dialog, _ ->
                notificationRepository.clearAllNotifications()
                Toast.makeText(requireContext(), getString(R.string.all_notifications_deleted), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun markAllAsRead() {
        notificationRepository.markAllAsRead()
        Toast.makeText(requireContext(), getString(R.string.all_notifications_marked_read), Toast.LENGTH_SHORT).show()
    }

    private fun showFilterToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction(DashboardActivity.ACTION_NEW_NOTIFICATION)
            addAction(DashboardActivity.ACTION_NOTIFICATIONS_UPDATED)
        }
        localBroadcastManager.registerReceiver(notificationReceiver, filter)
        Log.d(TAG, "Notification receiver registered")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        try {
            localBroadcastManager.unregisterReceiver(notificationReceiver)
            Log.d(TAG, "Notification receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Receiver already unregistered")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        updateUserName()
        loadNotificationsOnce()
        (activity as? DashboardActivity)?.refreshNotificationBadge()
    }
}