package org.jellyfin.mobile.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.serialization.json.Json
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.DownloadItemBinding
import org.jellyfin.mobile.player.source.JellyfinMediaSource

class DownloadsAdapter(private val onItemClick: (DownloadItem) -> Unit) : RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder>() {

    private var downloads: List<DownloadEntity> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = DownloadItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(downloads[position])
    }

    override fun getItemCount() = downloads.size

    inner class DownloadViewHolder(private val binding: DownloadItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(download: DownloadEntity) {
            val downloadItem = DownloadItem(Json.decodeFromString<JellyfinMediaSource>(download.mediaSource))
            binding.downloadItem = downloadItem
            itemView.setOnClickListener {
                onItemClick(downloadItem)
            }
            binding.executePendingBindings()
        }
    }

    fun setDownloads(downloads: List<DownloadEntity>) {
        this.downloads = downloads
        notifyDataSetChanged()
    }
}
