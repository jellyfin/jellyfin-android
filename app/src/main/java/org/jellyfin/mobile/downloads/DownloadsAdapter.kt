package org.jellyfin.mobile.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.DownloadItemBinding

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
            val downloadItem = DownloadItem(download)
            if (downloadItem.thumbnail == null) {
                binding.unbind()
                onItemHold(downloadItem)
                return
            }
            binding.downloadItem = downloadItem
            itemView.setOnClickListener {
                onItemClick(downloadItem)
            }
            itemView.setOnLongClickListener {
                onItemHold(downloadItem)
                true
            }

            binding.imageViewThumbnail.setImageBitmap(downloadItem.thumbnail)

            binding.executePendingBindings()
        }
    }
}
