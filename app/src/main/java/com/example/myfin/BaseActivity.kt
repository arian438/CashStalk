package com.example.myfin

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer

abstract class BaseFragment : Fragment() {

    protected fun setupNotificationBadge(badge: TextView?) {
        val activity = activity as? DashboardActivity
        activity?.getNotificationRepository()?.notificationsLiveData?.observe(viewLifecycleOwner) { notifications ->
            val unreadCount = notifications.count { !it.isRead }
            updateBadge(badge, unreadCount)
        }
    }

    private fun updateBadge(badge: TextView?, count: Int) {
        badge?.let {
            if (count > 0) {
                it.visibility = View.VISIBLE
                it.text = if (count > 99) "99+" else count.toString()
            } else {
                it.visibility = View.GONE
            }
        }
    }

    protected fun setupBellClickListener(bellContainer: View?) {
        bellContainer?.setOnClickListener {
            (activity as? DashboardActivity)?.showFragment(NotificationsFragment(), "NotificationsFragment")
        }
    }

    protected fun updateUserInfo() {
        val activity = activity as? DashboardActivity
        val user = activity?.getCurrentUser()

        user?.let {
            val displayName = if (it.fio.isNotBlank()) it.fio else it.email.substringBefore("@")

            view?.findViewById<TextView>(R.id.userNameText)?.text = displayName
            view?.findViewById<TextView>(R.id.profileUserNameText)?.text = displayName
            view?.findViewById<TextView>(R.id.settingsUserNameText)?.text = displayName

            view?.findViewById<TextView>(R.id.welcomeText)?.text = getString(R.string.welcome)
            view?.findViewById<TextView>(R.id.profileWelcomeText)?.text = getString(R.string.welcome)
            view?.findViewById<TextView>(R.id.settingsWelcomeText)?.text = getString(R.string.welcome)
        }
    }
}