package com.paradox.browser.developer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.paradox.browser.databinding.ItemDevToolBinding

class DeveloperToolsAdapter(
    private val items: List<DeveloperSettingsActivity.DevTool>
) : RecyclerView.Adapter<DeveloperToolsAdapter.VH>() {

    class VH(val binding: ItemDevToolBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDevToolBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.itemTitle.text = item.title
        holder.binding.itemDesc.text = item.desc
        holder.binding.root.setOnClickListener { item.action() }
    }
}
