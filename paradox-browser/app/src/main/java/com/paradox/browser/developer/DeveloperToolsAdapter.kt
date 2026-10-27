package com.paradox.browser.developer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.paradox.browser.databinding.ItemDevToolBinding

class DeveloperToolsAdapter(private val items: List<DeveloperSettingsActivity.DevTool>) :
    RecyclerView.Adapter<DeveloperToolsAdapter.VH>() {

    class VH(val binding: ItemDevToolBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDevToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.binding.itemTitle.text = it.title
        holder.binding.itemDesc.text = it.desc
        holder.binding.root.setOnClickListener { it.action() }
    }
}
