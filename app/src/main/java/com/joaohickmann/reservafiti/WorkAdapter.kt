package com.joaohickmann.reservafiti

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joaohickmann.reservafiti.databinding.ItemWorkinfoBinding
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

data class Work(
    val id: UUID,
    val atividadeAcademia: FitiApi.AtividadeAcademia,
    val dia: DayOfWeek,
    val hora: LocalTime
)

class WorkAdapter(
    private val onRemove: (Work) -> Unit
) : ListAdapter<Work, WorkAdapter.WorkInfoViewHolder>(WorkInfoItemCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = WorkInfoViewHolder(
        ItemWorkinfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: WorkInfoViewHolder, position: Int): Unit =
        holder.bind(getItem(position))

    private val horarioFormatter = DateTimeFormatter.ofPattern("HH:mm")

    inner class WorkInfoViewHolder(private val binding: ItemWorkinfoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private lateinit var workInfo: Work

        init {
            binding.btnExcluir.setOnClickListener { onRemove(workInfo) }
        }

        fun bind(item: Work) {
            workInfo = item
            binding.tvAtividade.text =
                "${item.atividadeAcademia.idAtividade} - ${item.atividadeAcademia.nomeAtividade}"
            binding.tvDia.text = item.dia.getDisplayName(TextStyle.FULL, Locale.getDefault())
            binding.tvHora.text = horarioFormatter.format(item.hora)
        }
    }
}

private class WorkInfoItemCallback : DiffUtil.ItemCallback<Work>() {
    override fun areItemsTheSame(oldItem: Work, newItem: Work): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Work, newItem: Work): Boolean =
        oldItem == newItem
}