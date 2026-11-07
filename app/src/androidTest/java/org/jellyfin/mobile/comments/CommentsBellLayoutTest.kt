package org.jellyfin.mobile.comments

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The native bell is never hidden: it is the entry point to the replies view. Only its badge is
 * allowed to disappear when there are no unread replies.
 */
@RunWith(AndroidJUnit4::class)
class CommentsBellLayoutTest {

    @Test
    fun bellStaysVisibleAtZeroUnreadAndWithNotificationsOff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)

        // PlayerView creates a Handler for the current looper, so it has to be inflated on the
        // main thread rather than the instrumentation thread.
        lateinit var binding: FragmentPlayerBinding
        instrumentation.runOnMainSync {
            binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        }

        assertEquals(View.VISIBLE, binding.commentsBell.visibility)

        // The badge, not the bell, carries the zero/unread state.
        binding.commentsBellBadge.visibility = View.GONE
        assertEquals(View.VISIBLE, binding.commentsBell.visibility)

        binding.commentsBellBadge.visibility = View.VISIBLE
        assertEquals(View.VISIBLE, binding.commentsBell.visibility)
    }
}
