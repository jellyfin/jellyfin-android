package org.jellyfin.mobile.comments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.mobile.R

/**
 * Renders reply notifications: who replied to you, what they said, and where.
 *
 * Rows are read-only — the only action is opening the episode, which also marks the notification
 * read — so there are none of the like/reply/delete controls the comment rows carry.
 */
class NotificationsAdapter(
    private val onOpen: (CommentNotificationDto) -> Unit,
    private val onAvatar: (ImageView, CommentNotificationDto) -> Unit,
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    private var items: List<CommentNotificationDto> = emptyList()

    fun submit(newItems: List<CommentNotificationDto>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.notification_avatar)
        private val actor: TextView = itemView.findViewById(R.id.notification_actor)
        private val time: TextView = itemView.findViewById(R.id.notification_time)
        private val dot: View = itemView.findViewById(R.id.notification_dot)
        private val text: TextView = itemView.findViewById(R.id.notification_text)
        private val parent: TextView = itemView.findViewById(R.id.notification_parent)
        private val itemLabel: TextView = itemView.findViewById(R.id.notification_item_label)

        fun bind(item: CommentNotificationDto) {
            val actorName = item.actorName.ifEmpty { itemView.context.getString(R.string.comments_unknown_user) }
            val action = if (item.type == "mention") {
                itemView.context.getString(R.string.comments_mentioned_you)
            } else {
                itemView.context.getString(R.string.comments_replied_to_you)
            }
            actor.text = itemView.context.getString(R.string.comments_notification_actor, actorName, action)
            time.text = CommentsAdapter.formatRelativeTime(item.createdAt)
            text.setText(CommentsAdapter.highlightMentions(item.text), TextView.BufferType.SPANNABLE)
            text.isVisible = item.text.isNotBlank()

            parent.text = item.parentText?.takeIf { it.isNotBlank() }?.let {
                itemView.context.getString(R.string.comments_replies_you_wrote, it)
            }
            parent.isVisible = !item.parentText.isNullOrBlank()

            itemLabel.text = item.itemLabel
            itemLabel.isVisible = item.itemLabel.isNotBlank()

            dot.isVisible = !item.isRead

            avatar.tag = item.id
            onAvatar(avatar, item)

            itemView.setOnClickListener { onOpen(item) }
        }
    }
}
