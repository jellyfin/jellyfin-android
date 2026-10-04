package org.jellyfin.mobile.player.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View.MeasureSpec
import android.widget.FrameLayout

enum class StereoscopicLayoutMode {
    MONO,
    SIDE_BY_SIDE,
    TOP_AND_BOTTOM,
}

class StereoscopicFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    var stereoscopicLayoutMode: StereoscopicLayoutMode = StereoscopicLayoutMode.MONO
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (stereoscopicLayoutMode == StereoscopicLayoutMode.MONO) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val fullWidth = MeasureSpec.getSize(widthMeasureSpec)
        val fullHeight = MeasureSpec.getSize(heightMeasureSpec)
        val eyeWidth = if (stereoscopicLayoutMode == StereoscopicLayoutMode.SIDE_BY_SIDE) fullWidth / 2 else fullWidth
        val eyeHeight = if (stereoscopicLayoutMode == StereoscopicLayoutMode.TOP_AND_BOTTOM) fullHeight / 2 else fullHeight
        val eyeWidthSpec = MeasureSpec.makeMeasureSpec(eyeWidth, MeasureSpec.EXACTLY)
        val eyeHeightSpec = MeasureSpec.makeMeasureSpec(eyeHeight, MeasureSpec.EXACTLY)

        super.onMeasure(eyeWidthSpec, eyeHeightSpec)

        val widthState = measuredWidthAndState and MEASURED_STATE_MASK
        val heightState = measuredHeightAndState and MEASURED_STATE_MASK
        setMeasuredDimension(
            resolveSizeAndState(fullWidth, widthMeasureSpec, widthState),
            resolveSizeAndState(fullHeight, heightMeasureSpec, heightState),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (stereoscopicLayoutMode == StereoscopicLayoutMode.MONO) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        val width = right - left
        val height = bottom - top
        val eyeRight = if (stereoscopicLayoutMode == StereoscopicLayoutMode.SIDE_BY_SIDE) {
            left + width / 2
        } else {
            right
        }
        val eyeBottom = if (stereoscopicLayoutMode == StereoscopicLayoutMode.TOP_AND_BOTTOM) {
            top + height / 2
        } else {
            bottom
        }
        super.onLayout(changed, left, top, eyeRight, eyeBottom)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (stereoscopicLayoutMode == StereoscopicLayoutMode.MONO) {
            super.dispatchDraw(canvas)
            return
        }

        val isSideBySide = stereoscopicLayoutMode == StereoscopicLayoutMode.SIDE_BY_SIDE
        val eyeWidth = if (isSideBySide) width / 2 else width
        val eyeHeight = if (isSideBySide) height else height / 2
        for (eye in 0..1) {
            val offsetX = if (isSideBySide) eye * eyeWidth else 0
            val offsetY = if (isSideBySide) 0 else eye * eyeHeight
            val saveCount = canvas.save()
            canvas.translate(offsetX.toFloat(), offsetY.toFloat())
            canvas.clipRect(
                0,
                0,
                if (isSideBySide && eye == 1) width - eyeWidth else eyeWidth,
                if (!isSideBySide && eye == 1) height - eyeHeight else eyeHeight,
            )
            super.dispatchDraw(canvas)
            canvas.restoreToCount(saveCount)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            touchOffsetX = 0f
            touchOffsetY = 0f
            when (stereoscopicLayoutMode) {
                StereoscopicLayoutMode.SIDE_BY_SIDE -> {
                    if (event.x >= width / 2f) touchOffsetX = (width / 2).toFloat()
                }
                StereoscopicLayoutMode.TOP_AND_BOTTOM -> {
                    if (event.y >= height / 2f) touchOffsetY = (height / 2).toFloat()
                }
                else -> Unit
            }
        }

        event.offsetLocation(-touchOffsetX, -touchOffsetY)
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            event.offsetLocation(touchOffsetX, touchOffsetY)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchOffsetX = 0f
                touchOffsetY = 0f
            }
        }
    }
}
