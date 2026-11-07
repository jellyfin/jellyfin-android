package org.jellyfin.mobile.comments

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

/**
 * An EditText that tells the keyboard it can accept GIF/image content.
 *
 * Gboard disables its GIF tab for fields that do not advertise content MIME types. Wrapping the
 * input connection and handling commitContent is what makes the keyboard's GIFs usable here.
 */
class GifEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var onGifPicked: ((Uri, String) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        EditorInfoCompat.setContentMimeTypes(
            outAttrs,
            arrayOf("image/gif", "image/webp", "image/png", "image/jpeg"),
        )

        return InputConnectionCompat.createWrapper(
            connection,
            outAttrs,
            InputConnectionCompat.OnCommitContentListener { info, flags, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                    (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
                ) {
                    runCatching { info.requestPermission() }
                }

                val type = context.contentResolver.getType(info.contentUri) ?: "image/gif"
                onGifPicked?.invoke(info.contentUri, type)
                true
            },
        )
    }
}
