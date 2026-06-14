package org.koitharu.kotatsu.core.ui.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.setTooltipCompat
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
 * Replaces the toolbar's default action-menu rendering with a custom tonal pill that mirrors the
 * circular navigation button. The pill is built from scratch (rather than restyling the framework
 * [ActionMenuView], whose cell sizing we cannot control): every action item — and the overflow
 * button — becomes an identical circular tap target the same size as the navigation button, the
 * items are flush and evenly spaced inside one capsule, and the pill sits the same distance from the
 * end edge as the navigation button does from the start edge.
 *
 * Menus that host a custom action view (e.g. a SearchView or a Slider that expands across the bar)
 * keep the framework rendering, since those are not simple icon rows.
 */
fun Toolbar.applyTonalActionMenuStyle() {
	post { renderTonalActionMenu() }
}

private fun Toolbar.renderTonalActionMenu() {
	val menu = menu
	val tint = context.getThemeColor(materialR.attr.colorOnSurfaceVariant)
	for (i in 0 until menu.size()) {
		menu.getItem(i).icon?.tintCompat(tint)
	}
	overflowIcon?.tintCompat(tint)

	val nativeMenuView = children.filterIsInstance<ActionMenuView>().firstOrNull()
	val existingPill = findViewById<View>(R.id.top_bar_actions_pill)

	// Leave menus that expand a custom action view (SearchView, Slider, …) to the framework.
	val hasActionView = (0 until menu.size()).any { menu.getItem(it).hasActionViewCompat() }
	if (hasActionView) {
		existingPill?.let(::removeView)
		nativeMenuView?.isVisible = true
		return
	}

	val actionItems = ArrayList<MenuItem>()
	val overflowItems = ArrayList<MenuItem>()
	for (i in 0 until menu.size()) {
		val item = menu.getItem(i)
		if (!item.isVisible) continue
		if (item.icon != null && item.showsAsAction()) {
			actionItems += item
		} else {
			overflowItems += item
		}
	}

	existingPill?.let(::removeView)
	// The custom pill renders the actions, so the framework menu view must stay hidden.
	nativeMenuView?.isVisible = false
	if (actionItems.isEmpty() && overflowItems.isEmpty()) {
		return
	}

	val size = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_size)
	val endMargin = resources.getDimensionPixelSize(R.dimen.top_bar_navigation_button_margin_start)
	val pill = buildActionsPill(size, ColorStateList.valueOf(tint), actionItems, overflowItems)
	addView(
		pill,
		Toolbar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, size).apply {
			gravity = Gravity.END or Gravity.CENTER_VERTICAL
			marginEnd = endMargin
		},
	)
	// Pin the pill the same distance from the end edge as the navigation button is from the start.
	setContentInsetsRelative(contentInsetStart, 0)
	contentInsetEndWithActions = 0
}

private fun Toolbar.buildActionsPill(
	size: Int,
	tint: ColorStateList,
	actionItems: List<MenuItem>,
	overflowItems: List<MenuItem>,
): LinearLayout {
	val pill = LinearLayout(context).apply {
		id = R.id.top_bar_actions_pill
		orientation = LinearLayout.HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		background = context.createTonalPillBackground(size)
	}
	for (item in actionItems) {
		pill.addView(
			createActionButton(size, item.icon, item.title, item.isEnabled, tint) {
				menu.performIdentifierAction(item.itemId, 0)
			},
		)
	}
	if (overflowItems.isNotEmpty()) {
		val button = createActionButton(
			size = size,
			icon = overflowIcon,
			title = context.getString(R.string.show_menu),
			enabled = true,
			tint = tint,
			onClick = {},
		)
		button.setOnClickListener { showOverflowPopup(button, overflowItems) }
		pill.addView(button)
	}
	return pill
}

private fun Toolbar.createActionButton(
	size: Int,
	icon: Drawable?,
	title: CharSequence?,
	enabled: Boolean,
	tint: ColorStateList,
	onClick: () -> Unit,
): AppCompatImageButton = AppCompatImageButton(context).apply {
	layoutParams = LinearLayout.LayoutParams(size, size)
	minimumWidth = 0
	minimumHeight = 0
	setPadding(0, 0, 0, 0)
	background = context.createCircularRipple(size)
	setImageDrawable(icon)
	imageTintList = tint
	scaleType = ImageView.ScaleType.CENTER
	isEnabled = enabled
	title?.let {
		contentDescription = it
		setTooltipCompat(it)
	}
	setOnClickListener { onClick() }
}

private fun Toolbar.showOverflowPopup(anchor: View, overflowItems: List<MenuItem>) {
	val popup = PopupMenu(context, anchor, Gravity.END)
	overflowItems.forEach { copyMenuItem(it, popup.menu) }
	popup.setForceShowIcon(true)
	popup.setOnMenuItemClickListener { clicked ->
		menu.performIdentifierAction(clicked.itemId, 0)
		true
	}
	popup.show()
}

private fun copyMenuItem(src: MenuItem, dst: Menu) {
	if (!src.isVisible) return
	if (src.hasSubMenu()) {
		val sub = dst.addSubMenu(src.groupId, src.itemId, src.order, src.title)
		sub.item.icon = src.icon
		sub.item.isEnabled = src.isEnabled
		val srcSub = src.subMenu ?: return
		for (i in 0 until srcSub.size()) {
			copyMenuItem(srcSub.getItem(i), sub)
		}
	} else {
		dst.add(src.groupId, src.itemId, src.order, src.title).also {
			it.icon = src.icon
			it.isEnabled = src.isEnabled
			it.isCheckable = src.isCheckable
			it.isChecked = src.isChecked
		}
	}
}

/**
 * Whether the item carries a custom action view (e.g. a SearchView or Slider). Uses
 * `hasCollapsibleActionView()` from the framework implementation so collapse-action-view items are
 * detected even before their view is instantiated.
 */
private fun MenuItem.hasActionViewCompat(): Boolean {
	if (actionView != null || isActionViewExpanded) return true
	return runCatching {
		javaClass.getMethod("hasCollapsibleActionView").invoke(this) as? Boolean
	}.getOrDefault(false) == true
}

/**
 * Reads the (otherwise hidden) `showAsAction` flags from the framework menu-item implementation via
 * reflection — there is no public getter — to tell whether an item is meant to sit on the bar or in
 * the overflow. Falls back to "has icon" if the implementation differs.
 */
private fun MenuItem.showsAsAction(): Boolean = runCatching {
	val always = javaClass.getMethod("requiresActionButton").invoke(this) as? Boolean
	val ifRoom = javaClass.getMethod("requestsActionButton").invoke(this) as? Boolean
	always == true || ifRoom == true
}.getOrDefault(icon != null)

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
 * Transparent base with a circular ripple sized to the button. The pill behind the buttons provides
 * the surface colour, so each button only contributes its own circular highlight when pressed.
 */
private fun Context.createCircularRipple(sizePx: Int): Drawable {
	val mask = createCircleDrawable(0xFFFFFFFF.toInt(), sizePx)
	return RippleDrawable(
		ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
		null,
		mask,
	)
}

private fun Context.createTonalPillBackground(sizePx: Int) = GradientDrawable().apply {
	shape = GradientDrawable.RECTANGLE
	cornerRadius = sizePx / 2f
	setColor(getThemeColor(materialR.attr.colorSurfaceContainer))
}

private fun createCircleDrawable(color: Int, sizePx: Int) = GradientDrawable().apply {
	shape = GradientDrawable.OVAL
	setColor(color)
	setSize(sizePx, sizePx)
}
