package de.lennart.minilauncher.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import de.lennart.minilauncher.Swipe
import de.lennart.minilauncher.classifySwipe
import kotlin.math.abs
import kotlin.math.hypot

/** Hosts the home screen widgets; its views report long-presses and page swipes. */
class LauncherWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LongPressWidgetHostView(context)

    companion object {
        private const val HOST_ID = 0x4d4c // "ML"
    }
}

/**
 * Widget view that handles gestures on its own, because a widget fills most of the page and
 * Compose never sees touches the view system consumes:
 *  - a long-press anywhere on the widget, even over clickable or scrollable children;
 *  - swipes in [swipes] that start on the widget: left and right always, up only when no part
 *    of the widget can scroll up (otherwise the swipe scrolls it, e.g. a task list).
 * When one of these fires, the children get a cancel event.
 */
class LongPressWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    var onSwipe: ((Swipe) -> Unit)? = null
    var swipes: Set<Swipe> = emptySet()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDistance = 80 * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var swiping = false
    private var childCanScrollUp = false
    private var lastSize = 0 to 0

    private val longPressRunnable = Runnable {
        longPressed = true
        cancelChildTouches()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    /** Ends whatever the widget's children were tracking (pressed state, scrolling, clicks). */
    private fun cancelChildTouches() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                swiping = false
                downX = ev.x
                downY = ev.y
                childCanScrollUp = canScrollUp(this)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (hypot(dx, dy) > touchSlop) removeCallbacks(longPressRunnable)
                if (!swiping && !longPressed && startsSwipe(dx, dy)) {
                    swiping = true
                    cancelChildTouches()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (swiping) {
                    classifySwipe(ev.x - downX, ev.y - downY, swipeDistance)
                        ?.takeIf { it in swipes }
                        ?.let { onSwipe?.invoke(it) }
                }
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longPressRunnable)
        }
        // After a long-press or a swipe the rest of the gesture belongs to us, not the widget.
        if (longPressed || swiping) return true
        // Claim the gesture even over empty widget areas so it keeps being tracked.
        return super.dispatchTouchEvent(ev) || ev.actionMasked == MotionEvent.ACTION_DOWN
    }

    /** Whether the movement so far clearly is a swipe we handle instead of the widget. */
    private fun startsSwipe(dx: Float, dy: Float): Boolean = when {
        abs(dx) > 2 * touchSlop && abs(dx) > 2 * abs(dy) ->
            (if (dx > 0) Swipe.Right else Swipe.Left) in swipes
        -dy > 2 * touchSlop && -dy > 2 * abs(dx) -> Swipe.Up in swipes && !childCanScrollUp
        else -> false
    }

    /** True if [view] or one of its descendants can scroll to show content further down. */
    private fun canScrollUp(view: View): Boolean {
        if (view.canScrollVertically(1)) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) if (canScrollUp(view.getChildAt(i))) return true
        }
        return false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w to h) != lastSize) {
            lastSize = w to h
            post { reportSize(w, h) }
        }
    }

    /** Tells the provider how much space it has, so it can pick a fitting layout. */
    private fun reportSize(w: Int, h: Int) {
        val density = resources.displayMetrics.density
        val widthDp = w / density
        val heightDp = h / density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            updateAppWidgetSize(Bundle(), widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }
}
