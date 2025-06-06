package org.jellyfin.mobile.downloads

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.fallback
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.databinding.DownloadItemBinding
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

class DownloadsAdapter(
    private val apiClient: ApiClient,
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

            val thumbnailUrl = getThumbnailUrl(context, downloadEntity.mediaSource.itemId)
            binding.imageViewThumbnail.load(thumbnailUrl) {
                fallback(R.drawable.ic_local_movies_white_64)
                error(R.drawable.ic_local_movies_white_64)
            }
        }
    }

    fun getThumbnailUrl(context: Context, mediaSourceItemId: UUID): String? {
        val size = context.resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)

        return apiClient.imageApi.getItemImageUrl(
            itemId = mediaSourceItemId,
            imageType = ImageType.PRIMARY,
            maxWidth = size,
            maxHeight = size,
        )
    }
}
