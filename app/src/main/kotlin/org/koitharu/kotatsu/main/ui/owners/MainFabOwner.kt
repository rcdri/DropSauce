package org.koitharu.kotatsu.main.ui.owners

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

interface MainFabOwner {

	@get:StringRes
	val mainFabTextRes: Int

	@get:DrawableRes
	val mainFabIconRes: Int

	val isMainFabEnabled: Boolean
		get() = true

	val isMainFabLoading: Boolean
		get() = false

	fun onMainFabClick()
}

interface MainFabInvalidator {

	fun invalidateMainFab()
}
