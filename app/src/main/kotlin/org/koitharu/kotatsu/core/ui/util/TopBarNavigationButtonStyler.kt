package org.koitharu.kotatsu.core.ui.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import com.google.android.material.R as materialR

fun Toolbar.applyTonalNavigationButtonStyle() {
	post {
		val navigationButton = findNavigationButton() ?: return@post
		val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
		val startMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_start)
		val titleInset = resources.getDimensionPixelSize(R.dimen.top_bar_title_inset_with_navigation)

		navigationButton.updateLayoutParams<Toolbar.LayoutParams> {
			width = size
			height = size
			gravity = Gravity.START or Gravity.CENTER_VERTICAL
			marginStart = startMargin
		}
		navigationButton.background = context.createTonalNavigationBackground()
		navigationButton.imageTintList = ColorStateList.valueOf(
			context.getThemeColor(materialR.attr.colorOnSurfaceVariant),
		)
		contentInsetStartWithNavigation = titleInset
	}
}

/**
 * Groups the toolbar's action (menu) items into a single tonal pill that mirrors the circular
 * navigation button: the pill height matches the navigation button size, shares its colour, and
 * every item sits in its own circular tap target of the same size. The result is a neatly aligned
 * cluster of icons on the end side of the top bar.
 */
fun Toolbar.applyTonalActionMenuStyle() {
	post {
		val menuView = children.filterIsInstance<ActionMenuView>().firstOrNull() ?: return@post
		val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
		val endMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_start)
		val iconSize = (24f * resources.displayMetrics.density).toInt()
		val padding = ((size - iconSize) / 2).coerceAtLeast(0)
		val tint = context.getThemeColor(materialR.attr.colorOnSurfaceVariant)

		menuView.background = context.createTonalMenuPillBackground(size)
		menuView.updateLayoutParams<Toolbar.LayoutParams> {
			height = size
			gravity = Gravity.END or Gravity.CENTER_VERTICAL
			marginEnd = endMargin
		}
		menuView.children.forEach { child ->
			child.minimumWidth = 0
			child.updateLayoutParams<ViewGroup.MarginLayoutParams> {
				width = size
				height = size
				marginStart = 0
				marginEnd = 0
			}
			child.setPadding(padding, padding, padding, padding)
			child.background = context.createTonalMenuItemBackground()
			if (child is ImageView) {
				child.scaleType = ImageView.ScaleType.FIT_CENTER
			}
		}
		// Tint the action and overflow icons to match the navigation button.
		val menu = menu
		for (i in 0 until menu.size()) {
			menu.getItem(i).icon?.tintCompat(tint)
		}
		overflowIcon?.tintCompat(tint)
	}
}

/**
 * Styles an arbitrary icon button (e.g. the contextual action-mode close "X") to match the
 * circular tonal navigation buttons used by the app's top bars.
 */
fun ImageView.applyTonalIconButtonStyle() {
	val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
	val startMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_start)
	val iconSize = (24f * resources.displayMetrics.density).toInt()
	val padding = ((size - iconSize) / 2).coerceAtLeast(0)
	minimumWidth = 0
	minimumHeight = 0
	updateLayoutParams<ViewGroup.MarginLayoutParams> {
		width = size
		height = size
		marginStart = startMargin
	}
	setPadding(padding, padding, padding, padding)
	scaleType = ImageView.ScaleType.FIT_CENTER
	background = context.createTonalNavigationBackground()
	imageTintList = ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorOnSurfaceVariant))
}

private fun Toolbar.findNavigationButton(): ImageButton? {
	navigationIcon ?: return null
	return children
		.filterIsInstance<ImageButton>()
		.firstOrNull()
}

private fun Drawable.tintCompat(color: Int) {
	DrawableCompat.setTint(mutate(), color)
}

private fun Context.createTonalNavigationBackground(): Drawable {
	val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
	val shape = createCircleDrawable(getThemeColor(materialR.attr.colorSurfaceContainer), size)
	val mask = createCircleDrawable(0xFFFFFFFF.toInt(), size)
	return RippleDrawable(
		ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
		shape,
		mask,
	)
}

/**
 * Transparent base with a circular ripple sized to the navigation button. The pill behind the
 * items provides the surface colour, so each item only needs to contribute its own circular
 * highlight when pressed.
 */
private fun Context.createTonalMenuItemBackground(): Drawable {
	val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
	val mask = createCircleDrawable(0xFFFFFFFF.toInt(), size)
	return RippleDrawable(
		ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
		null,
		mask,
	)
}

private fun Context.createTonalMenuPillBackground(sizePx: Int) = GradientDrawable().apply {
	shape = GradientDrawable.RECTANGLE
	cornerRadius = sizePx / 2f
	setColor(getThemeColor(materialR.attr.colorSurfaceContainer))
}

private fun createCircleDrawable(color: Int, sizePx: Int) = GradientDrawable().apply {
	shape = GradientDrawable.OVAL
	setColor(color)
	setSize(sizePx, sizePx)
}
