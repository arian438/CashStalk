package com.example.myfin.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myfin.R
import com.example.myfin.data.CategoryStat

class CategoryStatAdapter(
    private var stats: List<CategoryStat>
) : RecyclerView.Adapter<CategoryStatAdapter.StatViewHolder>() {

    class StatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorView: View = itemView.findViewById(R.id.statColor)
        val categoryName: TextView = itemView.findViewById(R.id.statCategoryName)
        val amount: TextView = itemView.findViewById(R.id.statAmount)
        val percentage: TextView = itemView.findViewById(R.id.statPercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_stat, parent, false)
        return StatViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatViewHolder, position: Int) {
        val stat = stats[position]

        try {
            holder.colorView.setBackgroundColor(Color.parseColor(stat.color))
        } catch (e: Exception) {
            holder.colorView.setBackgroundColor(Color.parseColor("#4ECDC4"))
        }

        holder.categoryName.text = stat.categoryName
        holder.amount.text = String.format("%,.0f %s", stat.amount, stat.currencySymbol)
        holder.percentage.text = String.format("%.1f%%", stat.percentage)
    }

    override fun getItemCount(): Int = stats.size

    fun updateData(newStats: List<CategoryStat>) {
        stats = newStats
        notifyDataSetChanged()
    }
}