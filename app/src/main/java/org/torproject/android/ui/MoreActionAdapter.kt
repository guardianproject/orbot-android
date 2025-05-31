package org.torproject.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.ImageViewCompat

import androidx.recyclerview.widget.RecyclerView

import org.torproject.android.R

class MoreActionAdapter(
    private val items: List<OrbotMenuAction>
) : RecyclerView.Adapter<MoreActionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val label: TextView = view.findViewById(R.id.tvLabel)
        val card: CardView = view as CardView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_more_action, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        if (item.removeTint)
            ImageViewCompat.setImageTintList(holder.icon, null)
        if (item.backgroundColor != null) {
            holder.card.setCardBackgroundColor(item.backgroundColor!!)
        }
        holder.icon.setImageResource(item.imgId)
        holder.label.setText(item.textId)
        holder.itemView.setOnClickListener { item.action() }
    }
}