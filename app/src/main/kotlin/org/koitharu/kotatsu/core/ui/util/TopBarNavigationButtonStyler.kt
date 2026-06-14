package org.koitharu.kotatsu.core.ui.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.view.menu.ActionMenuItemView
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
 * navigation button: the pill height matches the navigation button size, shares its colour, sits
 * the same distance from the end edge as the navigation button does from the start edge, and every
 * item gets its own centered circular tap target of exactly the navigation-button size.
 *
 * The item sizing is intentionally left to [ActionMenuView] (so every cell stays a uniform width
 * and the items are evenly spaced); we only swap in a fixed-size, centered circular ripple so the
 * highlight never stretches to the cell bounds.
 */
fun Toolbar.applyTonalActionMenuStyle() {
	post {
		val menuView = children.filterIsInstance<ActionMenuView>().firstOrNull() ?: return@post
		val menu = menu
		val tint = context.getThemeColor(materialR.attr.colorOnSurfaceVariant)
		// Always tint the action and overflow icons to match the navigation button.
		for (i in 0 until menu.size()) {
			menu.getItem(i).icon?.tintCompat(tint)
		}
		overflowIcon?.tintCompat(tint)

		// While an action view is expanded (e.g. a SearchView or a Slider takes over the bar),
		// drop the pill so the action view keeps its natural layout.
		val hasExpandedActionView = (0 until menu.size()).any { menu.getItem(it).isActionViewExpanded }
		if (hasExpandedActionView) {
			menuView.background = null
			return@post
		}

		val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
		val endMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_start)

		// Pin the pill the same distance from the end edge as the navigation button is from the
		// start edge: cancel the toolbar's own end inset and apply the margin ourselves.
		contentInsetEndWithActions = 0
		menuView.background = context.createTonalMenuPillBackground(size)
		menuView.updateLayoutParams<Toolbar.LayoutParams> {
			height = size
			gravity = Gravity.END or Gravity.CENTER_VERTICAL
			marginEnd = endMargin
		}
		menuView.children.forEach { child ->
			// Only the regular action buttons and the overflow button get the circular tap target;
			// custom action views keep their own layout.
			if (child is ActionMenuItemView || child.javaClass.simpleName == "OverflowMenuButton") {
				child.background = context.createCenteredCircleRipple(size)
			}
		}
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
 * Transparent base with a fixed-size circular ripple centered in the host view. The pill behind the
 * items provides the surface colour; each item only contributes its own circular highlight when
 * pressed. [LayerDrawable.setLayerGravity]/[LayerDrawable.setLayerSize] keep the circle centered and
 * exactly [sizePx] wide regardless of how wide [ActionMenuView] makes the cell.
 */
private fun Context.createCenteredCircleRipple(sizePx: Int): Drawable {
	val mask = centeredCircleDrawable(0xFFFFFFFF.toInt(), sizePx)
	return RippleDrawable(
		ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
		null,
		mask,
	)
}

private fun centeredCircleDrawable(color: Int, sizePx: Int): Drawable {
	val circle = createCircleDrawable(color, sizePx)
	return LayerDrawable(arrayOf(circle)).apply {
		setLayerGravity(0, Gravity.CENTER)
		setLayerSize(0, sizePx, sizePx)
	}
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
