package org.koitharu.kotatsu.core.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.loadingindicator.LoadingIndicator

/**
 * A [SwipeRefreshLayout] that hides the legacy spinner and follows the pull gesture with a
 * Material 3 Expressive [LoadingIndicator] instead.
 *
 * SwipeRefreshLayout keeps driving the gesture, nested scrolling and the refresh trigger via its
 * own circle view; we keep that circle fully invisible and mirror its vertical position onto our
 * indicator using alpha (not visibility) so we never call requestLayout() during a draw pass,
 * which was causing the indicator to get stuck visible after a cancelled drag.
 */
class LoadingIndicatorSwipeRefreshLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

	private val indicator = LoadingIndicator(
		context,
		null,
		com.google.android.material.R.attr.loadingIndicatorStyle,
	).apply {
		// Keep the view VISIBLE in the hierarchy at all times; hide via alpha instead.
		// Toggling visibility calls requestLayout() which is unsafe inside dispatchDraw().
		alpha = 0f
	}

	private val nativeCircle: ImageView?
		get() {
			for (i in 0 until childCount) {
				val child = getChildAt(i)
				if (child is ImageView) {
					return child
				}
			}
			return null
		}

	// After the user releases without triggering refresh, force-hide once the SwipeRefreshLayout
	// cancel animation has had time to finish (~200 ms). Guards against the native circle being
	// left VISIBLE at y ≈ 0, which would otherwise leave the indicator spinning at the top.
	private val forceHideRunnable = Runnable {
		if (!isRefreshing) indicator.alpha = 0f
	}

	override fun onFinishInflate() {
		super.onFinishInflate()
		// Added after the content child so SwipeRefreshLayout still resolves the scrollable target.
		addView(indicator, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		measureChild(indicator, widthMeasureSpec, heightMeasureSpec)
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		val w = indicator.measuredWidth
		val h = indicator.measuredHeight
		val l = (width - w) / 2
		indicator.layout(l, 0, l + w, h)
		syncIndicator()
	}

	override fun dispatchDraw(canvas: Canvas) {
		syncIndicator()
		super.dispatchDraw(canvas)
	}

	override fun onStopNestedScroll(target: android.view.View, type: Int) {
		super.onStopNestedScroll(target, type)
		if (!isRefreshing) {
			// Cancel animation runs for ~200 ms. Schedule a hard hide 300 ms out so any
			// edge-case where the native circle stays VISIBLE doesn't leave us spinning.
			removeCallbacks(forceHideRunnable)
			postDelayed(forceHideRunnable, 300L)
		}
	}

	override fun onStartNestedScroll(child: android.view.View, target: android.view.View, axes: Int, type: Int): Boolean {
		// User started a new drag — cancel any pending force-hide.
		removeCallbacks(forceHideRunnable)
		return super.onStartNestedScroll(child, target, axes, type)
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		removeCallbacks(forceHideRunnable)
	}

	private fun syncIndicator() {
		val circle = nativeCircle ?: return
		// Always keep the built-in arrow spinner invisible; we draw our own indicator.
		if (circle.alpha != 0f) {
			circle.alpha = 0f
		}
		// Use alpha (not visibility) so this is safe to call inside dispatchDraw().
		// Visibility changes trigger requestLayout(), which is not allowed mid-draw and
		// can silently be dropped, leaving the indicator stuck visible.
		val shouldShow = isRefreshing || circle.isVisible
		indicator.alpha = if (shouldShow) 1f else 0f
		if (shouldShow) {
			// circle.y is its current top (layout offset + drag translation); follow it.
			indicator.translationY = circle.y + (circle.height - indicator.height) / 2f
		}
	}
}
