package com.example.myfin.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.R
import com.example.myfin.data.Notification
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private var notifications: List<Notification>,
    private val onNotificationClicked: (Notification) -> Unit,
    private val onNotificationDeleted: (Notification) -> Unit  // Добавляем параметр
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.notificationIcon)
        val title: TextView = itemView.findViewById(R.id.notificationTitle)
        val message: TextView = itemView.findViewById(R.id.notificationMessage)
        val time: TextView = itemView.findViewById(R.id.notificationTime)
        val unreadIndicator: View = itemView.findViewById(R.id.unreadIndicator)
        val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)  // Добавляем кнопку
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        when (notification.type) {
            "transaction" -> {
                holder.icon.setImageResource(R.drawable.ic_transaction)
                holder.icon.setColorFilter(Color.parseColor("#4CAF50"))
            }
            "limit" -> {
                holder.icon.setImageResource(R.drawable.ic_warning)
                holder.icon.setColorFilter(Color.parseColor("#FF9800"))
            }
            "reminder" -> {
                holder.icon.setImageResource(R.drawable.ic_reminder)
                holder.icon.setColorFilter(Color.parseColor("#2196F3"))
            }
            "category" -> {
                holder.icon.setImageResource(R.drawable.ic_category)
                holder.icon.setColorFilter(Color.parseColor("#9C27B0"))
            }
            "report" -> {
                holder.icon.setImageResource(R.drawable.ic_report)
                holder.icon.setColorFilter(Color.parseColor("#607D8B"))
            }
            "update" -> {
                holder.icon.setImageResource(R.drawable.ic_update)
                holder.icon.setColorFilter(Color.parseColor("#795548"))
            }
            else -> {
                holder.icon.setImageResource(R.drawable.ic_bell)
                holder.icon.setColorFilter(Color.parseColor("#9E9E9E"))
            }
        }

        holder.title.text = notification.title
        holder.message.text = notification.message
        holder.time.text = formatTime(notification.timestamp)
        holder.unreadIndicator.visibility = if (notification.isRead) View.GONE else View.VISIBLE

        // Клик по уведомлению
        holder.itemView.setOnClickListener {
            onNotificationClicked(notification)
        }

        // Клик по кнопке удаления
        holder.deleteButton.setOnClickListener {
            onNotificationDeleted(notification)
        }
    }

    private fun formatTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM, HH:mm", Locale("ru", "RU"))
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp
        }
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newNotifications: List<Notification>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }
}