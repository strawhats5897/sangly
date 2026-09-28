package com.hangly.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hangly.app.R
import com.hangly.app.data.HanglyPreferences
import com.hangly.app.databinding.ItemPresetCharmBinding

data class CharmItem(
    val id: String,
    val name: String,
    val iconRes: Int
)

class CharmPresetAdapter(
    private var selectedId: String,
    private val onSelect: (CharmItem) -> Unit
) : RecyclerView.Adapter<CharmPresetAdapter.CharmViewHolder>() {

    private val items = listOf(
        CharmItem(HanglyPreferences.CHARM_SPIDERMAN, "Spider-Man", R.drawable.ic_spiderman),
        CharmItem(HanglyPreferences.CHARM_EVIL_EYE, "Evil Eye", R.drawable.ic_evil_eye),
        CharmItem(HanglyPreferences.CHARM_CAP_SHIELD, "Cap Shield", R.drawable.ic_cap_shield),
        CharmItem(HanglyPreferences.CHARM_LUCKY_CAT, "Lucky Cat", R.drawable.ic_lucky_cat),
        CharmItem(HanglyPreferences.CHARM_GHOST, "Ghost", R.drawable.ic_ghost),
        CharmItem(HanglyPreferences.CHARM_CUSTOM, "Custom", R.drawable.ic_launcher_foreground)
    )

    fun setSelected(id: String) {
        val oldIndex = items.indexOfFirst { it.id == selectedId }
        val newIndex = items.indexOfFirst { it.id == id }
        selectedId = id
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharmViewHolder {
        val binding = ItemPresetCharmBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CharmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharmViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CharmViewHolder(private val binding: ItemPresetCharmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CharmItem) {
            binding.txtPresetTitle.text = item.name
            binding.imgPreset.setImageResource(item.iconRes)

            val isSelected = item.id == selectedId
            binding.containerPreset.background = ContextCompat.getDrawable(
                binding.root.context,
                if (isSelected) R.drawable.bg_preset_selected else R.drawable.bg_preset_unselected
            )

            binding.root.setOnClickListener {
                setSelected(item.id)
                onSelect(item)
            }
        }
    }
}
