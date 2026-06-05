package org.koitharu.kotatsu.core.ui.util

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.ActionBarContextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.getThemeDimensionPixelOffset
import com.google.android.material.R as materialR

class ActionModeDelegate : OnBackPressedCallback(false) {

	private var activeActionMode: ActionMode? = null
	private var exitingActionMode: ActionMode? = null
	private var exitingWindow: Window? = null
	private var listeners: MutableList<ActionModeListener>? = null
	private var defaultStatusBarColor = Color.TRANSPARENT
	private var actionModePreDraw: Pair<ActionBarContextView, ViewTreeObserver.OnPreDrawListener>? = null
	private var exitCompleteActions: MutableList<() -> Unit>? = null

	val isActionModeStarted: Boolean
		get() = activeActionMode != null

	override fun handleOnBackPressed() {
		finishActionMode()
	}

	fun onSupportActionModeStarted(mode: ActionMode, window: Window?) {
		val wasExiting = exitingActionMode != null
		exitingActionMode = null
		exitingWindow = null
		exitCompleteActions = null
		activeActionMode = mode
		isEnabled = true
		listeners?.forEach { it.onActionModeStarted(mode) }
		if (window != null) {
			val ctx = window.context
			val actionModeColor = ColorUtils.compositeColors(
				ContextCompat.getColor(ctx, materialR.color.m3_appbar_overlay_color),
				ctx.getThemeColor(materialR.attr.colorSurface),
			)
			@Suppress("DEPRECATION")
			if (!wasExiting) {
				defaultStatusBarColor = window.statusBarColor
			}
			@Suppress("DEPRECATION")
			window.statusBarColor = actionModeColor
			val statusBarHeight = ViewCompat.getRootWindowInsets(window.decorView)
				?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
			window.decorView.findViewById<ActionBarContextView?>(androidx.appcompat.R.id.action_mode_bar)?.apply {
				applyEdgeToEdgeActionMode(color = actionModeColor, statusBarHeight = statusBarHeight)
				keepActionModeEdgeToEdge(mode, color = actionModeColor, statusBarHeight = statusBarHeight)
			}
		}
	}

	fun onSupportActionModeFinished(mode: ActionMode, window: Window?) {
		activeActionMode = null
		exitingActionMode = mode
		exitingWindow = window
		isEnabled = false
		listeners?.forEach { it.onActionModeFinished(mode) }
		val actionModeView = actionModePreDraw?.first
		if (actionModeView == null) {
			completeActionModeExit()
		} else {
			actionModeView.postDelayed({
				if (exitingActionMode === mode) {
					completeActionModeExit()
				}
			}, ACTION_MODE_EXIT_CLEANUP_DELAY_MS)
		}
	}

	fun addListener(listener: ActionModeListener) {
		if (listeners == null) {
			listeners = ArrayList()
		}
		checkNotNull(listeners).add(listener)
	}

	fun removeListener(listener: ActionModeListener) {
		listeners?.remove(listener)
	}

	fun addListener(listener: ActionModeListener, owner: LifecycleOwner) {
		addListener(listener)
		owner.lifecycle.addObserver(ListenerLifecycleObserver(listener))
	}

	fun finishActionMode() {
		activeActionMode?.finish()
	}

	fun runAfterActionModeExit(action: () -> Unit) {
		if (exitingActionMode == null) {
			action()
		} else {
			if (exitCompleteActions == null) {
				exitCompleteActions = ArrayList()
			}
			exitCompleteActions?.add(action)
		}
	}

	private fun ActionBarContextView.applyEdgeToEdgeActionMode(color: Int, statusBarHeight: Int) {
		val actionBarHeight = context.getThemeDimensionPixelOffset(
			androidx.appcompat.R.attr.actionBarSize,
			contentHeight,
		)
		val totalHeight = actionBarHeight + statusBarHeight
		setBackgroundColor(color)
		setPadding(paddingLeft, statusBarHeight, paddingRight, paddingBottom)
		if (contentHeight != totalHeight) {
			setContentHeight(totalHeight)
			requestLayout()
		}
		if (minimumHeight != totalHeight) {
			minimumHeight = totalHeight
		}
		val params = layoutParams as? ViewGroup.MarginLayoutParams
		if (params != null && params.topMargin != 0) {
			params.topMargin = 0
			layoutParams = params
		}
		hideStatusGuard(statusBarHeight)
	}

	// AppCompat draws an anonymous status guard above overlaid action modes.
	private fun ActionBarContextView.hideStatusGuard(statusBarHeight: Int) {
		findStatusGuard(statusBarHeight)?.visibility = View.GONE
	}

	private fun ActionBarContextView.findStatusGuard(statusBarHeight: Int): View? {
		if (statusBarHeight == 0) {
			return null
		}
		val parentView = parent as? ViewGroup ?: return null
		return parentView.children.firstOrNull { child ->
			if (child === this || child.id != View.NO_ID) {
				return@firstOrNull false
			}
			val params = child.layoutParams as? ViewGroup.MarginLayoutParams ?: return@firstOrNull false
			params.height == statusBarHeight && params.topMargin == 0
		}
	}

	private fun ActionBarContextView.keepActionModeEdgeToEdge(
		mode: ActionMode,
		color: Int,
		statusBarHeight: Int,
	) {
		clearActionModePreDraw()
		val listener = ViewTreeObserver.OnPreDrawListener {
			when {
				activeActionMode === mode -> {
					applyEdgeToEdgeActionMode(color = color, statusBarHeight = statusBarHeight)
				}
				exitingActionMode === mode && visibility == View.VISIBLE -> {
					applyEdgeToEdgeActionMode(color = color, statusBarHeight = statusBarHeight)
				}
				else -> {
					if (exitingActionMode === mode) {
						completeActionModeExit()
					} else {
						clearActionModePreDraw()
					}
				}
			}
			true
		}
		viewTreeObserver.addOnPreDrawListener(listener)
		actionModePreDraw = this to listener
	}

	private fun clearActionModePreDraw() {
		val (view, listener) = actionModePreDraw ?: return
		if (view.viewTreeObserver.isAlive) {
			view.viewTreeObserver.removeOnPreDrawListener(listener)
		}
		actionModePreDraw = null
	}

	private fun completeActionModeExit() {
		restoreStatusBarColor()
		exitingActionMode = null
		exitingWindow = null
		clearActionModePreDraw()
		val actions = exitCompleteActions ?: return
		exitCompleteActions = null
		actions.forEach { it() }
	}

	private fun restoreStatusBarColor() {
		val window = exitingWindow ?: return
		@Suppress("DEPRECATION")
		window.statusBarColor = defaultStatusBarColor
	}

	private companion object {
		private const val ACTION_MODE_EXIT_CLEANUP_DELAY_MS = 500L
	}

	private inner class ListenerLifecycleObserver(
		private val listener: ActionModeListener,
	) : DefaultLifecycleObserver {

		override fun onDestroy(owner: LifecycleOwner) {
			super.onDestroy(owner)
			removeListener(listener)
		}
	}
}
