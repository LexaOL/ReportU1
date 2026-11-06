package com.example.reportu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.reportu.databinding.ItemHistoryBinding
import com.example.reportu.databinding.ItemNotificationBinding
import com.example.reportu.databinding.ItemRecentBinding

private object ReportDiff : DiffUtil.ItemCallback<Report>() {
    override fun areItemsTheSame(oldItem: Report, newItem: Report) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Report, newItem: Report) = oldItem == newItem
}

/** Filas compactas de "Reportes recientes" en Inicio (ícono de estado + título + píldora). */
class RecentAdapter(
    private val onClick: (Report) -> Unit
) : ListAdapter<Report, RecentAdapter.ViewHolder>(ReportDiff) {

    class ViewHolder(val binding: ItemRecentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRecentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = getItem(position)
        val b = holder.binding
        b.ivIcon.setImageResource(report.status.icon)
        b.tvTitle.text = report.title
        b.tvLocation.text = report.location
        b.tvStatus.bindStatus(report.status)
        b.root.setOnClickListener { onClick(report) }
    }
}

/**
 * Filas del Historial (con fecha) y del panel de mantenimiento (sin fecha):
 * folio + píldora, título con flecha y ubicación.
 */
class HistoryAdapter(
    private val showTime: Boolean,
    private val onClick: (Report) -> Unit
) : ListAdapter<Report, HistoryAdapter.ViewHolder>(ReportDiff) {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = getItem(position)
        val b = holder.binding
        b.tvId.text = report.folio
        b.tvStatus.bindStatus(report.status)
        b.tvTitle.text = report.title
        b.tvLocation.text = report.location
        b.rowTime.visibility = if (showTime) View.VISIBLE else View.GONE
        if (showTime) b.tvTime.text = Fmt.history(report.createdAt)
        b.root.setOnClickListener { onClick(report) }
    }
}

private object NotificationDiff : DiffUtil.ItemCallback<AppNotification>() {
    override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) =
        oldItem.reportId == newItem.reportId && oldItem.timestamp == newItem.timestamp

    override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) =
        oldItem == newItem
}

/** Lista de notificaciones: las nuevas (con punto) en negro, las leídas en gris. */
class NotificationAdapter(
    private val onClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.ViewHolder>(NotificationDiff) {

    class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val context = b.root.context

        b.tvText.text = item.text
        b.tvTime.text = Fmt.notification(item.timestamp)
        b.dotUnread.visibility = if (item.unread) View.VISIBLE else View.INVISIBLE

        val strong = ContextCompat.getColor(context, if (item.unread) R.color.black else R.color.ink_soft)
        val soft = ContextCompat.getColor(context, if (item.unread) R.color.muted else R.color.hint)
        b.tvText.setTextColor(strong)
        b.tvTime.setTextColor(soft)
        b.ivBell.setColorFilter(if (item.unread) strong else soft)
        b.ivBell.setBackgroundResource(R.drawable.bg_circle_outline_light)
        b.root.setOnClickListener { onClick(item) }
    }
}
