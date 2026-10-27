package com.paradox.gamingtools.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.AppStorageInfo
import com.paradox.gamingtools.core.Fmt

class CacheAdapter(
    private val onManage: (AppStorageInfo) -> Unit
) : RecyclerView.Adapter<CacheAdapter.VH>() {

    private val items = ArrayList<AppStorageInfo>()

    fun submit(list: List<AppStorageInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.txtCacheLabel)
        val size: TextView = view.findViewById(R.id.txtCacheSize)
        val manage: TextView = view.findViewById(R.id.txtCacheManage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cache_app, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        holder.size.text = holder.itemView.context.getString(
            R.string.cache_row,
            Fmt.bytes(item.cacheBytes),
            Fmt.bytes(item.dataBytes)
        )
        holder.manage.setOnClickListener { onManage(item) }
        holder.itemView.setOnClickListener { onManage(item) }
    }
}
