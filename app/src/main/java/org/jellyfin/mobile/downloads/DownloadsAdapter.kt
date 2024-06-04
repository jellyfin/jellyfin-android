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
            binding.downloadItem = downloadItem
            itemView.setOnClickListener {
                onItemClick(downloadItem)
            }
            itemView.setOnLongClickListener {
                onItemHold(downloadItem)
                true
            }

            // Prepare description
            var desc = ""
            if (downloadItem.mediaSource.item?.seriesName != null) {
                desc = "${downloadItem.mediaSource.item.seriesName} - ${downloadItem.mediaSource.item.seasonName ?: ""}"
            } else if (downloadItem.mediaSource.item?.productionYear != null) {
                desc = downloadItem.mediaSource.item.productionYear.toString()
            }
            binding.textViewDescription.text = desc

            binding.imageViewThumbnail.setImageBitmap(downloadItem.thumbnail)

            binding.executePendingBindings()
        }
    }
}
