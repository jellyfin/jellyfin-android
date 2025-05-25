import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import org.jellyfin.mobile.R

class ChapterMarking(private val context: Context, parent: ConstraintLayout, bias: Float) {
    private val _chapterMarkingDrawingWidth =
        context.resources.getDimensionPixelSize(R.dimen.exo_chapter_marking_width)
    private val _chapterMarkingDrawingHeight =
        context.resources.getDimensionPixelSize(R.dimen.exo_chapter_marking_height)

    private val view: View = View(context).apply {
        id = View.generateViewId()
        layoutParams = ConstraintLayout.LayoutParams(
            _chapterMarkingDrawingWidth,
            _chapterMarkingDrawingHeight,
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            horizontalBias = bias
        }
        background = ContextCompat.getDrawable(context, R.drawable.chapter_marking)
    }

    init {
        parent.addView(view)
    }

    fun setColor(id: Int) {
        view.setBackgroundColor(ContextCompat.getColor(context, id))
    }
}
