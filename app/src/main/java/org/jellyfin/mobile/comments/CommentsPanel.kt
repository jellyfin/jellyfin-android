package org.jellyfin.mobile.comments

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.target.ImageViewTarget
import coil3.transform.CircleCropTransformation
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.ui.PlayerFragment
import org.jellyfin.mobile.utils.toast
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The TikTok-style comments sheet drawn over the playing video.
 *
 * It lives inside the player layout (not a DialogFragment) so it works in the app's fullscreen
 * mode without a Material bottom-sheet theme, and so playback is never interrupted.
 */
class CommentsPanel(
    private val fragment: PlayerFragment,
    private val binding: FragmentPlayerBinding,
    private val onCountChanged: (Int) -> Unit = {},
) : KoinComponent {
    private companion object {
        /** Share of the available height the sheet takes. */
        const val SHEET_FRACTION = 0.5f

        /** Never shrink below something you can still read a couple of comments in. */
        const val MIN_SHEET_DP = 240
    }

    private val repository: CommentsRepository by inject()
    private val imageLoader: ImageLoader by inject()

    private val context get() = binding.root.context
    private val container: View = binding.commentsContainer
    private val sheet: View = binding.commentsSheet
    private val scrim: View = binding.commentsScrim
    private val list = binding.commentsList
    private val loading = binding.commentsLoading
    private val empty = binding.commentsEmpty
    private val title: TextView = binding.commentsTitle
    private val sortButton: TextView = binding.commentsSort
    private val closeButton: ImageButton = binding.commentsClose
    private val input: TextView = binding.commentsInput
    private val sendButton: TextView = binding.commentsSend
    private val replyingRow: View = binding.commentsReplying
    private val replyingText: TextView = binding.commentsReplyingText
    private val replyingCancel: TextView = binding.commentsReplyingCancel

    private val adapter = CommentsAdapter(
        onLike = ::onLike,
        onReply = ::onReply,
        onDelete = ::onDelete,
        onAvatar = ::loadAvatar,
    )

    private var itemId: String? = null
    private var replyingTo: CommentDto? = null
    private var rows: List<CommentItem> = emptyList()
    private var sort: String = CommentsRepository.SORT_NEWEST
    private var loadJob: Job? = null

    val isVisible: Boolean get() = container.isVisible

    /** Whether the sheet is meant to be on screen (as opposed to mid-close). */
    private var isOpen = false

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter

        scrim.setOnClickListener { hide() }
        closeButton.setOnClickListener { hide() }
        sortButton.setOnClickListener {
            sort = if (sort == CommentsRepository.SORT_NEWEST) CommentsRepository.SORT_TOP else CommentsRepository.SORT_NEWEST
            updateSortLabel()
            load(reset = true)
        }

        input.doAfterTextChanged {
            val enabled = it?.isNotBlank() == true
            sendButton.isEnabled = enabled
            sendButton.alpha = if (enabled) 1f else 0.45f
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                postComment()
                true
            } else {
                false
            }
        }
        sendButton.setOnClickListener { postComment() }
        replyingCancel.setOnClickListener { setReplying(null) }

        updateSortLabel()

        // The IME inset is applied as padding by the player, which changes the container's
        // height without anyone telling this panel. Re-measure on every layout change so the
        // sheet always takes half of the space that is left.
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // Only while it is actually open: during the closing animation the sheet is on its
            // way out and must not be snapped back into place.
            if (!isOpen) {
                return@addOnLayoutChangeListener
            }
            applySheetHeight()
            sheet.translationY = 0f
        }
    }

    fun toggle(itemId: String) {
        if (isVisible) hide() else show(itemId)
    }

    fun show(itemId: String) {
        this.itemId = itemId
        setReplying(null)
        input.text = null

        isOpen = true
        container.isVisible = true
        container.alpha = 0f
        sheet.translationY = sheet.height.toFloat().takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels.toFloat()

        fragment.suppressControllerAutoHide(true)
        sizeSheet()

        container.animate().alpha(1f).setDuration(180).start()
        sheet.animate().translationY(0f).setDuration(220).start()

        load(reset = true)
    }

    fun hide() {
        isOpen = false
        fragment.suppressControllerAutoHide(false)
        setReplying(null)
        input.text = null
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(180).start()
        container.animate().alpha(0f).setDuration(180).withEndAction {
            if (!isVisible) return@withEndAction
            container.isVisible = false
        }.start()
    }

    fun dismissIfVisible() {
        if (isVisible) hide()
    }

    /**
     * Half of whatever room is actually left, so opening the keyboard (which shrinks the
     * container by the IME inset) does not leave the sheet overflowing off the top.
     */
    private fun applySheetHeight(): Int {
        if (container.height <= 0) {
            return sheet.height
        }

        val available = container.height - container.paddingTop - container.paddingBottom
        if (available <= 0) {
            return sheet.height
        }

        val minimum = (MIN_SHEET_DP * container.resources.displayMetrics.density).toInt()
        val target = (available * SHEET_FRACTION).toInt().coerceAtLeast(minimum)
        val lp = sheet.layoutParams
        if (lp.height != target) {
            lp.height = target
            sheet.layoutParams = lp
        }
        return target
    }

    private fun sizeSheet() {
        val height = applySheetHeight()
        sheet.translationY = height.toFloat()
        sheet.animate().translationY(0f).setDuration(220).start()
    }

    private fun updateSortLabel() {
        sortButton.setText(if (sort == CommentsRepository.SORT_NEWEST) R.string.comments_sort_newest else R.string.comments_sort_top)
    }

    private fun load(reset: Boolean) {
        val id = itemId ?: return
        loadJob?.cancel()
        loadJob = fragment.lifecycleScope.launch {
            loading.isVisible = true
            if (reset) {
                empty.isVisible = false
                rows = emptyList()
                adapter.submit(rows)
            }
            runCatching { repository.getComments(id, sort = sort) }
                .onSuccess { page ->
                    rows = page.items.flatMap { comment ->
                        listOf(CommentItem(comment, isReply = false)) +
                            comment.replies.map { CommentItem(it, isReply = true) }
                    }
                    adapter.submit(rows)
                    title.text = context.getString(R.string.comments_title_count, page.totalCount)
                    onCountChanged(page.totalCount)
                    empty.isVisible = rows.isEmpty()
                }
                .onFailure {
                    empty.isVisible = rows.isEmpty()
                    context.toast(context.getString(R.string.comments_error))
                }
            loading.isVisible = false
        }
    }

    private fun onLike(comment: CommentDto) {
        fragment.lifecycleScope.launch {
            runCatching { repository.toggleLike(comment.id) }
                .onSuccess { result ->
                    rows = rows.map { item ->
                        if (item.comment.id == comment.id) {
                            item.copy(comment = item.comment.copy(likeCount = result.likeCount, userLiked = result.userLiked))
                        } else {
                            item
                        }
                    }
                    adapter.submit(rows)
                }
                .onFailure { context.toast(context.getString(R.string.comments_error)) }
        }
    }

    private fun onReply(comment: CommentDto) {
        setReplying(comment)
    }

    private fun onDelete(comment: CommentDto) {
        AlertDialog.Builder(context)
            .setMessage(R.string.comments_delete_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                fragment.lifecycleScope.launch {
                    runCatching { repository.deleteComment(comment.id) }
                        .onSuccess { load(reset = true) }
                        .onFailure { context.toast(context.getString(R.string.comments_delete_error)) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun postComment() {
        val id = itemId ?: return
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        sendButton.isEnabled = false
        val parentId = replyingTo?.id
        fragment.lifecycleScope.launch {
            runCatching { repository.postComment(id, text, parentId) }
                .onSuccess {
                    input.text = null
                    setReplying(null)
                    load(reset = true)
                }
                .onFailure { context.toast(context.getString(R.string.comments_post_error)) }
            sendButton.isEnabled = input.text?.isNotBlank() == true
            sendButton.alpha = if (sendButton.isEnabled) 1f else 0.45f
        }
    }

    private fun setReplying(comment: CommentDto?) {
        replyingTo = comment
        replyingRow.isVisible = comment != null
        if (comment != null) {
            replyingText.text = context.getString(R.string.comments_replying_to, comment.username)
            input.requestFocus()
        }
    }

    private fun loadAvatar(imageView: ImageView, comment: CommentDto) {
        val path = comment.avatarUrl ?: "/Users/${comment.userId}/Images/Primary?height=64"
        val request = ImageRequest.Builder(context)
            .data(repository.absoluteUrl(path))
            .transformations(CircleCropTransformation())
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("Authorization", repository.buildAuthorizationHeader())
                    .build(),
            )
            .target(ImageViewTarget(imageView))
            .build()
        imageLoader.enqueue(request)
    }
}
