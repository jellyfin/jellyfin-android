package org.jellyfin.mobile.comments

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.mobile.R
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Renders comments as a flat list: a top-level comment, then its replies indented underneath.
 */
class CommentsAdapter(
    private val onLike: (CommentDto) -> Unit,
    private val onReply: (CommentDto) -> Unit,
    private val onEdit: (CommentDto) -> Unit,
    private val onDelete: (CommentDto) -> Unit,
    private val onAvatar: (ImageView, CommentDto) -> Unit,
    private val onMedia: (ImageView, CommentDto) -> Unit,
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    private var items: List<CommentItem> = emptyList()

    fun submit(newItems: List<CommentItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.comment_avatar)
        private val username: TextView = itemView.findViewById(R.id.comment_username)
        private val time: TextView = itemView.findViewById(R.id.comment_time)
        private val text: TextView = itemView.findViewById(R.id.comment_text)
        private val media: ImageView = itemView.findViewById(R.id.comment_media)
        private val likeButton: ImageButton = itemView.findViewById(R.id.comment_like_button)
        private val likeCount: TextView = itemView.findViewById(R.id.comment_like_count)
        private val replyButton: TextView = itemView.findViewById(R.id.comment_reply_button)
        private val editButton: TextView = itemView.findViewById(R.id.comment_edit_button)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.comment_delete_button)

        fun bind(item: CommentItem) {
            val comment = item.comment

            val author = comment.username.ifEmpty { itemView.context.getString(R.string.comments_unknown_user) }
            // A reply that answers another reply says who ("me > them"), as TikTok does; both sit
            // at the same level, so the flat thread is unchanged.
            val answered = comment.replyToUsername
            val answersSibling = answered != null &&
                comment.replyToCommentId != null &&
                comment.replyToCommentId != comment.parentId
            username.text = if (answersSibling) {
                itemView.context.getString(R.string.comments_reply_to, author, answered)
            } else {
                author
            }
            time.text = formatRelativeTime(comment.createdAt).let { relative ->
                if (comment.editedAt != null) "$relative · ${itemView.context.getString(R.string.comments_edited)}" else relative
            }
            text.setText(highlightMentions(comment.text), TextView.BufferType.SPANNABLE)

            val mediaUrl = comment.mediaUrl
            if (mediaUrl != null) {
                media.isVisible = true
                onMedia(media, comment)
            } else {
                media.isVisible = false
                media.setImageDrawable(null)
            }

            // Replies are indented under their parent.
            val base = itemView.resources.getDimensionPixelSize(R.dimen.comments_item_padding)
            val indent = itemView.resources.getDimensionPixelSize(R.dimen.comments_reply_indent)
            itemView.setPaddingRelative(
                base + if (item.isReply) indent else 0,
                itemView.paddingTop,
                base,
                itemView.paddingBottom,
            )

            avatar.tag = comment.id
            onAvatar(avatar, comment)

            val liked = comment.userLiked
            likeButton.setImageResource(
                if (liked) R.drawable.ic_favorite_white_24dp else R.drawable.ic_favorite_border_white_24dp,
            )
            likeButton.imageTintList = ColorStateList.valueOf(
                if (liked) Color.parseColor("#FE2C55") else Color.parseColor("#B3FFFFFF"),
            )
            likeCount.text = if (comment.likeCount > 0) comment.likeCount.toString() else ""
            likeButton.setOnClickListener { onLike(comment) }

                // Replies can be replied to as well. The server attaches a reply to a reply to the same
            // top-level thread, so the conversation continues without nesting any deeper.
            replyButton.isVisible = true
            replyButton.setOnClickListener { onReply(comment) }

            editButton.isVisible = comment.mayEdit
            editButton.setOnClickListener { onEdit(comment) }

            deleteButton.isVisible = comment.mayDelete
            deleteButton.setOnClickListener { onDelete(comment) }
        }
    }

    companion object {
        private val mentionRegex = Regex("(?<![\\w@])@[A-Za-z0-9._-]+")
        private val mentionColor = 0xFFFE2C55.toInt()

        /**
         * Draws @handles in the velvet accent and bold, matching the web panel.
         */
        fun highlightMentions(value: String): CharSequence {
            val text = SpannableString(value)
            mentionRegex.findAll(value).forEach { match ->
                text.setSpan(
                    StyleSpan(Typeface.BOLD),
                    match.range.first,
                    match.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                text.setSpan(
                    ForegroundColorSpan(mentionColor),
                    match.range.first,
                    match.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            return text
        }

        /**
         * "just now", "5m", "3h", "2d" — the same compact style the web client uses.
         */
        fun formatRelativeTime(value: String?, nowMillis: Long = System.currentTimeMillis()): String {
            if (value.isNullOrEmpty()) return ""
            val then = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() ?: return ""
            val seconds = ((nowMillis - then) / 1000).coerceAtLeast(0)
            if (seconds < 45) return "just now"
            val units = listOf(
                "y" to ChronoUnit.YEARS.duration.seconds,
                "mo" to ChronoUnit.MONTHS.duration.seconds,
                "w" to ChronoUnit.WEEKS.duration.seconds,
                "d" to ChronoUnit.DAYS.duration.seconds,
                "h" to ChronoUnit.HOURS.duration.seconds,
                "m" to ChronoUnit.MINUTES.duration.seconds,
            )
            for ((suffix, unitSeconds) in units) {
                val amount = seconds / unitSeconds
                if (amount >= 1) return "$amount$suffix"
            }
            return "just now"
        }
    }
}
