import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import org.jellyfin.mobile.R

class ChapterMarkingView(private val context: Context) {
    fun createView(parent: ConstraintLayout, marginStart: Int): View {
        val view = View(context).apply {
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(
                (3 * context.resources.displayMetrics.density).toInt(),
                (15 * context.resources.displayMetrics.density).toInt(),
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                setMargins(marginStart, 0, 0, 0)
            }

            val chapterMarking = ContextCompat.getDrawable(context, R.drawable.chapter_marking)
            chapterMarking?.setTint(ContextCompat.getColor(context, R.color.unplayed))
            background = chapterMarking
        }

        parent.addView(view)
        return view
    }
}
