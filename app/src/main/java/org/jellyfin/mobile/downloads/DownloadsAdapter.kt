package org.jellyfin.mobile.downloads

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.DownloadItemBinding
import org.jellyfin.mobile.player.source.JellyfinMediaSource

class DownloadsAdapter(private val onItemClick: (DownloadItem) -> Unit, private val onItemHold: (DownloadItem) -> Unit) : ListAdapter<DownloadEntity, DownloadsAdapter.DownloadViewHolder>(DownloadDiffCallback())  {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = DownloadItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(private val binding: DownloadItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(download: DownloadEntity) {
            val downloadItem = DownloadItem(Json.decodeFromString<JellyfinMediaSource>(download.mediaSource))
            binding.downloadItem = downloadItem
            itemView.setOnClickListener {
                onItemClick(downloadItem)
            }
            itemView.setOnLongClickListener {
                onItemHold(downloadItem)
                true
            }
            binding.executePendingBindings()
        }
    }
}
