package org.jellyfin.mobile.utils

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyEventInterceptorTest {

    @Test
    fun `default onInterceptKeyEvent returns false`() {
        val interceptor = object : KeyEventInterceptor {}
        val keyEvent = mockk<KeyEvent>()

        assertFalse(interceptor.onInterceptKeyEvent(keyEvent))
    }

    @Test
    fun `keyboard seek time constant is 5 seconds`() {
        assertEquals(5000L, Constants.KEYBOARD_SEEK_TIME_MS)
    }

    @Test
    fun `custom interceptor consumes handled key events`() {
        val interceptor = object : KeyEventInterceptor {
            override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
        }

        val spaceEvent = mockk<KeyEvent> { every { keyCode } returns KeyEvent.KEYCODE_SPACE }
        val leftEvent = mockk<KeyEvent> { every { keyCode } returns KeyEvent.KEYCODE_DPAD_LEFT }
        val rightEvent = mockk<KeyEvent> { every { keyCode } returns KeyEvent.KEYCODE_DPAD_RIGHT }
        val otherEvent = mockk<KeyEvent> { every { keyCode } returns KeyEvent.KEYCODE_A }

        assertTrue(interceptor.onInterceptKeyEvent(spaceEvent))
        assertTrue(interceptor.onInterceptKeyEvent(leftEvent))
        assertTrue(interceptor.onInterceptKeyEvent(rightEvent))
        assertFalse(interceptor.onInterceptKeyEvent(otherEvent))
    }
}
