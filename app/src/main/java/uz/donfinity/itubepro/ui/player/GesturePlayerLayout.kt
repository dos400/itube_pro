package uz.donfinity.itubepro.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * PlayerView plus fullscreen gestures, as a plain Android View.
 *
 * Gestures (same layout as ref/NewPipe/.../player/gesture/MainPlayerGestureListener.kt):
 * - double-tap left / right third → seek back / forward
 * - vertical drag on the right half → volume, on the left half → brightness
 *
 * Why a View and not Compose pointerInput? A Compose overlay would sit on top of PlayerView
 * and swallow the taps meant for its play/pause/seek-bar controls. Here [dispatchTouchEvent]
 * shows each touch to a GestureDetector first, but still passes it on to PlayerView, until
 * a gesture clearly belongs to us (a vertical drag or the second tap of a double-tap).
 */
@OptIn(UnstableApi::class)
@SuppressLint("ViewConstructor")
class GesturePlayerLayout(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onDoubleTapSeek(forward: Boolean)
        /** [delta] is the drag distance as a fraction of the view height; up is positive. */
        fun onVerticalDrag(rightSide: Boolean, delta: Float)
        fun onVerticalDragEnd()
    }

    val playerView = PlayerView(context).also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    var gesturesEnabled = false
    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** Touches starting this close to the top/bottom edge belong to the system bars. */
    private val edgeExclusionPx = 48 * resources.displayMetrics.density

    private var dragging = false
    private var dragRightSide = false
    private var consumingTap = false

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!gesturesEnabled) return false
            val third = width / 3f
            when {
                e.x < third -> listener?.onDoubleTapSeek(forward = false)
                e.x > 2 * third -> listener?.onDoubleTapSeek(forward = true)
                else -> return false // middle: leave it to PlayerView (show/hide controls)
            }
            consumingTap = true
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!gesturesEnabled || e1 == null) return false
            if (!dragging) {
                val dy = abs(e2.y - e1.y)
                val startsAtEdge = e1.y < edgeExclusionPx || e1.y > height - edgeExclusionPx
                // Mostly horizontal drags belong to the seek bar.
                if (dy < touchSlop * 2 || abs(distanceX) > abs(distanceY) || startsAtEdge) return false
                dragging = true
                dragRightSide = e1.x > width / 2f
            }
            // distanceY > 0 means the finger moved up.
            listener?.onVerticalDrag(dragRightSide, distanceY / height)
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val wasOurs = dragging || consumingTap
        detector.onTouchEvent(ev)
        val isOurs = dragging || consumingTap

        if (isOurs && !wasOurs) {
            // A gesture just started: tell PlayerView to forget the touch it has seen so far.
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (dragging) listener?.onVerticalDragEnd()
            dragging = false
            consumingTap = false
        }
        return if (isOurs) true else super.dispatchTouchEvent(ev)
    }
}
