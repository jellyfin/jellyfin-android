package org.jellyfin.mobile.utils

import android.text.Html

@Suppress("DEPRECATION")
fun stripHtmlChars(content: String?): String {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString()
    } else {
        Html.fromHtml(content).toString()
    }
}
