package org.jellyfin.mobile.downloads

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.DownloadItemBinding
import org.jellyfin.sdk.model.api.BaseItemDto

class DownloadsAdapter(
    private val onItemClick: (DownloadEntity) -> Unit,
    private val onItemHold: (DownloadEntity) -> Unit,
) : ListAdapter<DownloadEntity, DownloadsAdapter.DownloadViewHolder>(
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
                onItemHold(downloadEntity)
                return
            }

            val context = itemView.context

            val mediaItem: BaseItemDto? = downloadEntity.mediaSource.item
            binding.textViewName.text = downloadEntity.mediaSource.name
            binding.textViewDescription.text = when {
                mediaItem?.seriesName != null -> context.getString(
                    R.string.tv_show_desc,
                    mediaItem.seriesName,
                    mediaItem.parentIndexNumber,
                    mediaItem.indexNumber,
                )
                mediaItem?.productionYear != null -> mediaItem.productionYear.toString()
                else -> downloadEntity.mediaSource.id
            }
            binding.textViewFileSize.text = downloadEntity.fileSize

            itemView.setOnClickListener {
                onItemClick(downloadEntity)
            }
            itemView.setOnLongClickListener {
                onItemHold(downloadEntity)
                true
            }

            binding.imageViewThumbnail.setImageBitmap(downloadEntity.thumbnail)
        }
    }
}
