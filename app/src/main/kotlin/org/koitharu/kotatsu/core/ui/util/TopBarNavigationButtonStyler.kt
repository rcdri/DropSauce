package org.koitharu.kotatsu.core.ui.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import com.google.android.material.R as materialR

/**
 * Applies the app's unified top-bar treatment to a [Toolbar]: a circular tonal navigation
 * (back/close) button on the start side and a tonal "pill" grouping the action icons on the end
 * side. Both share the same height, fill colour and icon tint so the bar reads as a single,
 * consistent design language regardless of which screen it belongs to.
 */
fun Toolbar.applyTonalTopBarStyle() {
	applyTonalNavigationButtonStyle()
	applyTonalActionMenuStyle()
}

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
 * Styles an arbitrary icon button (e.g. the contextual action-mode close "X") to match the
 * circular tonal navigation buttons used by the app's top bars.
 */
fun ImageView.applyTonalIconButtonStyle() {
	val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
	val startMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_start)
	val iconSize = resources.getDimensionPixelSize(R.dimen.top_bar_action_icon_size)
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

/**
 * Groups the action (menu) icons of a top bar into a single tonal pill that mirrors the circular
 * navigation button: identical height and fill colour, an end inset that matches the navigation
 * button's start margin, and one equally-sized circular ripple per icon with no gaps between them.
 *
 * Works for both a [Toolbar] and the contextual action-mode bar (`ActionBarContextView`), since
 * both host their items inside an [ActionMenuView]. The menu is populated asynchronously (and may
 * change at runtime), so styling is re-applied on every layout pass; it is idempotent and only
 * touches not-yet-styled views, so it converges without triggering relayout loops.
 */
fun ViewGroup.applyTonalActionMenuStyle() {
	if (getTag(R.id.tag_tonal_action_menu) == null) {
		setTag(R.id.tag_tonal_action_menu, true)
		addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
			(view as? ViewGroup)?.findActionMenuView()?.applyTonalPillStyle()
		}
	}
	post { findActionMenuView()?.applyTonalPillStyle() }
}

private fun ActionMenuView.applyTonalPillStyle() {
	val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
	val endMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_end)
	val iconSize = resources.getDimensionPixelSize(R.dimen.top_bar_action_icon_size)
	val padding = ((size - iconSize) / 2).coerceAtLeast(0)
	val iconTint = ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorOnSurfaceVariant))

	if (getTag(R.id.tag_tonal_action_pill) == null) {
		setTag(R.id.tag_tonal_action_pill, true)
		setPadding(0, 0, 0, 0)
		background = context.createTonalPillBackground(size)
		clipToOutline = true
	}
	// Constrain the menu container to the pill height and center it vertically (it otherwise fills
	// the whole bar), then mirror the navigation button's start margin on the end side.
	val lp = layoutParams ?: return
	var lpChanged = false
	if (lp.height != size) {
		lp.height = size
		lpChanged = true
	}
	val targetGravity = Gravity.END or Gravity.CENTER_VERTICAL
	if (lp is Toolbar.LayoutParams && lp.gravity != targetGravity) {
		lp.gravity = targetGravity
		lpChanged = true
	}
	if (lp is ViewGroup.MarginLayoutParams && lp.marginEnd != endMargin) {
		lp.marginEnd = endMargin
		lpChanged = true
	}
	if (lpChanged) {
		layoutParams = lp
	}
	for (child in children) {
		if (!child.isTonalActionItem() || child.getTag(R.id.tag_tonal_action_item) != null) {
			continue
		}
		child.setTag(R.id.tag_tonal_action_item, true)
		child.minimumWidth = 0
		child.minimumHeight = 0
		child.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			width = size
			height = size
			marginStart = 0
			marginEnd = 0
		}
		child.setPadding(padding, padding, padding, padding)
		child.background = context.createTonalActionItemBackground()
		child.applyTonalActionIconTint(iconTint)
	}
}

/** Only icon-only items belong in the pill; text actions keep their default (wider) layout. */
private fun View.isTonalActionItem(): Boolean = when (this) {
	is ImageView -> true // overflow button & image action views
	is TextView -> text.isNullOrEmpty() // icon-only ActionMenuItemView (a TextView subclass)
	else -> false
}

private fun View.applyTonalActionIconTint(tint: ColorStateList) {
	when (this) {
		is ImageView -> imageTintList = tint
		is TextView -> {
			// ActionMenuItemView holds its icon as an absolute compound drawable (set via
			// setCompoundDrawables), so tint those rather than the relative ones.
			val drawables = compoundDrawables
			var changed = false
			for (i in drawables.indices) {
				val drawable = drawables[i] ?: continue
				drawables[i] = drawable.mutate().apply { setTintList(tint) }
				changed = true
			}
			if (changed) {
				setCompoundDrawablesWithIntrinsicBounds(
					drawables[0], drawables[1], drawables[2], drawables[3],
				)
			}
		}
	}
}

private fun ViewGroup.findActionMenuView(): ActionMenuView? =
	children.filterIsInstance<ActionMenuView>().firstOrNull()

private fun Toolbar.findNavigationButton(): ImageButton? {
	navigationIcon ?: return null
	return children
		.filterIsInstance<ImageButton>()
		.firstOrNull()
}

private fun Context.createTonalNavigationBackground(): RippleDrawable {
	val shape = createCircleDrawable(getThemeColor(materialR.attr.colorSurfaceContainer))
	val mask = createCircleDrawable(Color.WHITE)
	return RippleDrawable(
		ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
		shape,
		mask,
	)
}

/**
 * Transparent-bodied circular ripple for an item inside the pill: the pill itself supplies the
 * fill, so only the (bounded, centered) press highlight is drawn here.
 */
private fun Context.createTonalActionItemBackground(): RippleDrawable {
	val mask = createCircleDrawable(Color.WHITE)
	return RippleDrawable(
		ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
		null,
		mask,
	)
}

private fun Context.createTonalPillBackground(heightPx: Int) = GradientDrawable().apply {
	shape = GradientDrawable.RECTANGLE
	cornerRadius = heightPx / 2f
	setColor(getThemeColor(materialR.attr.colorSurfaceContainer))
}

private fun createCircleDrawable(color: Int) = GradientDrawable().apply {
	shape = GradientDrawable.OVAL
	setColor(color)
}
