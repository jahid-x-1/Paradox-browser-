package com.paradox.gamingtools.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.GameApp

class GameAdapter(
    private val onClick: (GameApp) -> Unit,
    private val onFavorite: (GameApp) -> Unit
) : RecyclerView.Adapter<GameAdapter.VH>() {

    private val items = ArrayList<GameApp>()
    private var favorites: Set<String> = emptySet()

    fun submit(apps: List<GameApp>, favoritePackages: Set<String>) {
        items.clear()
        items.addAll(apps)
        favorites = favoritePackages
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val label: TextView = view.findViewById(R.id.txtLabel)
        val packageName: TextView = view.findViewById(R.id.txtPackage)
        val star: TextView = view.findViewById(R.id.txtStar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.label.text = app.label
        holder.packageName.text = app.packageName
        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon)
        } else {
            holder.icon.setImageResource(R.drawable.ic_app_placeholder)
        }

        val favorite = favorites.contains(app.packageName)
        holder.star.text = if (favorite) "★" else "☆"
        holder.star.alpha = if (favorite) 1f else 0.35f

        holder.itemView.setOnClickListener { onClick(app) }
        holder.star.setOnClickListener { onFavorite(app) }
    }
}
