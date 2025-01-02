package org.jellyfin.mobile.downloads

import androidx.recyclerview.widget.DiffUtil
import org.jellyfin.mobile.data.entity.DownloadEntity

class DownloadDiffCallback : DiffUtil.ItemCallback<DownloadEntity>() {
    override fun areItemsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
        return oldItem.itemId == newItem.itemId
    }

    override fun areContentsTheSame(oldItem: DownloadEntity, newItem: DownloadEntity): Boolean {
        return oldItem == newItem
    }
}
