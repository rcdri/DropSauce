package org.koitharu.kotatsu.details.ui.pager

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.slider.TickVisibilityMode
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.util.ext.setValueRounded
import org.koitharu.kotatsu.core.util.progress.IntPercentLabelFormatter
import org.koitharu.kotatsu.details.ui.pager.ChaptersPagesSheet.Companion.TAB_BOOKMARKS
import org.koitharu.kotatsu.details.ui.pager.ChaptersPagesSheet.Companion.TAB_CHAPTERS
import org.koitharu.kotatsu.details.ui.pager.ChaptersPagesSheet.Companion.TAB_PAGES
import java.lang.ref.WeakReference

class ChapterPagesMenuProvider(
	private val sheet: BaseAdaptiveSheet<*>,
	private val pager: ViewPager2,
	private val settings: AppSettings,
) : OnBackPressedCallback(false), MenuProvider, MenuItem.OnActionExpandListener,
	Slider.OnChangeListener {

	private var expandedItemRef: WeakReference<MenuItem>? = null

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		val tab = getCurrentTab()
		when (tab) {
			// Chapter search now lives inline in the sheet toolbar, not in the menu.
			TAB_CHAPTERS -> Unit

			TAB_PAGES, TAB_BOOKMARKS -> {
				menuInflater.inflate(R.menu.opt_pages, menu)
				menu.findItem(R.id.action_grid_size)?.run {
					setOnActionExpandListener(this@ChapterPagesMenuProvider)
					(actionView as? Slider)?.setupPagesSizeSlider()
				}
			}
		}
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

	override fun handleOnBackPressed() {
		expandedItemRef?.get()?.collapseActionView()
	}

	override fun onMenuItemActionExpand(item: MenuItem): Boolean {
		expandedItemRef = WeakReference(item)
		sheet.expandAndLock()
		isEnabled = true
		return true
	}

	override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
		expandedItemRef = null
		isEnabled = false
		sheet.unlock()
		return true
	}

	override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
		if (fromUser) {
			settings.gridSizePages = value.toInt()
		}
	}

	private fun Slider.setupPagesSizeSlider() {
		valueFrom = 50f
		valueTo = 150f
		stepSize = 5f
		tickVisibilityMode = TickVisibilityMode.TICK_VISIBILITY_HIDDEN
		labelBehavior = LabelFormatter.LABEL_FLOATING
		setLabelFormatter(IntPercentLabelFormatter(context))
		setValueRounded(settings.gridSizePages.toFloat())
		addOnChangeListener(this@ChapterPagesMenuProvider)
	}

	private fun getCurrentTab(): Int {
		var page = pager.currentItem
		if (page > 0 && pager.adapter?.itemCount == 2) { // no Pages page
			page++ // shift
		}
		return page
	}
}
