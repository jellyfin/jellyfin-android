package org.jellyfin.mobile.comments

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsMentionHighlightTest {

    @Test
    fun mentionIsBoldAndVelvetColoured() {
        val text = CommentsAdapter.highlightMentions("@kasia look at this") as Spanned

        val style = text.getSpans(0, text.length, StyleSpan::class.java).single()
        assertEquals(Typeface.BOLD, style.style)

        val colour = text.getSpans(0, text.length, ForegroundColorSpan::class.java).single()
        assertEquals(0xFFFE2C55.toInt(), colour.foregroundColor)

        assertTrue(text.getSpanStart(style) == 0)
        assertEquals("@kasia".length, text.getSpanEnd(style))
    }

    @Test
    fun emailAddressIsNotAMention() {
        val text = CommentsAdapter.highlightMentions("mail me at foo@bar.com") as Spanned
        assertEquals(0, text.getSpans(0, text.length, StyleSpan::class.java).size)
    }
}
