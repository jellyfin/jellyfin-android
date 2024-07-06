package org.jellyfin.mobile.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.DownloadItemBinding

class DownloadsAdapter(private val onItemClick: (DownloadEntity) -> Unit, private val onItemHold: (DownloadEntity) -> Unit) : ListAdapter<DownloadEntity, DownloadsAdapter.DownloadViewHolder>(
    DownloadDiffCallback(),
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = DownloadItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(private val binding: DownloadItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(downloadEntity: DownloadEntity) {
            if (downloadEntity.thumbnail == null) {
                binding.unbind()
                onItemHold(downloadEntity)
                return
            }
            binding.downloadEntity = downloadEntity
            itemView.setOnClickListener {
                onItemClick(downloadEntity)
            }
            itemView.setOnLongClickListener {
                onItemHold(downloadEntity)
                true
            }

            binding.imageViewThumbnail.setImageBitmap(downloadEntity.thumbnail)

            binding.executePendingBindings()
        }
    }
}
