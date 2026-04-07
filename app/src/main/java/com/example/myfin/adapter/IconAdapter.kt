package com.example.myfin.adapter

import com.example.myfin.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class IconAdapter(
    private val icons: List<String>,
    private val onIconSelected: (String) -> Unit
) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

    private var selectedPosition = 0

    inner class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconText: TextView = itemView.findViewById(R.id.tvIcon)
        val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon, parent, false)
        return IconViewHolder(view)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val icon = icons[position]
        holder.iconText.text = icon

        // Показываем выделение выбранной иконки
        holder.selectionIndicator.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                selectedPosition = adapterPosition
                notifyDataSetChanged()
                onIconSelected(icons[adapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = icons.size

    fun getSelectedIcon(): String = if (icons.isNotEmpty()) icons[selectedPosition] else "💸"

    fun selectItem(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
        if (icons.isNotEmpty()) {
            onIconSelected(icons[position])
        }
    }
}