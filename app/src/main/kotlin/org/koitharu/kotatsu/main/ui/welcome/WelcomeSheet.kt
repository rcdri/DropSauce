package org.koitharu.kotatsu.main.ui.welcome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backup.local.ui.restore.RestoreDialogFragment
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ItemColorSchemeBinding
import org.koitharu.kotatsu.databinding.SheetWelcomeBinding

@AndroidEntryPoint
class WelcomeSheet : BaseAdaptiveSheet<SheetWelcomeBinding>(), View.OnClickListener {

	private val viewModel by viewModels<WelcomeViewModel>()
	private var currentSlide = 0
	private var isAmoledSyncInProgress = false
	private val availableColorSchemes = ColorScheme.getAvailableList()

	private val restoreTachiyomiLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			viewModel.restoreBackup(uri)
		}
	}

	private val restoreDropSauceLauncher = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri ->
		if (uri != null) {
			RestoreDialogFragment.show(parentFragmentManager, uri)
		}
	}

	private val googleSignInLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { result ->
		viewModel.handleGoogleSignInResult(result.data)
	}

	private val installPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		updatePermissionButtons()
	}

	private val notificationsPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) {
		updatePermissionButtons()
	}

	private val batteryPermissionLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		updatePermissionButtons()
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetWelcomeBinding {
		return SheetWelcomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetWelcomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		disableFitToContents()
		binding.headerBar.isGone = true
		onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				handleBackNavigation()
			}
		})
		initColorThemeSelector(binding)
		binding.buttonNext.setOnClickListener(this)
		binding.buttonSelectDestination.setOnClickListener(this)
		binding.buttonPermissionInstall.setOnClickListener(this)
		binding.buttonPermissionNotifications.setOnClickListener(this)
		binding.buttonPermissionBattery.setOnClickListener(this)
		binding.buttonOpenGithub.setOnClickListener(this)
		binding.buttonOpenDiscord.setOnClickListener(this)
		binding.buttonSignInGoogle.setOnClickListener(this)
		binding.buttonRestoreDropsauce.setOnClickListener(this)
		binding.buttonRestoreTachiyomi.setOnClickListener(this)
		binding.buttonVisitWebsite.setOnClickListener(this)

		binding.groupTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
			if (!isChecked) {
				return@addOnButtonCheckedListener
			}
			val mode = when (checkedId) {
				R.id.button_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
				R.id.button_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
				else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
			}
			viewModel.setTheme(mode)
			AppCompatDelegate.setDefaultNightMode(mode)
		}

		binding.switchAmoled.setOnCheckedChangeListener { _, isChecked ->
			if (isAmoledSyncInProgress) {
				return@setOnCheckedChangeListener
			}
			viewModel.setAmoledTheme(isChecked)
			requireActivity().recreate()
		}

		viewModel.selectedTheme.observe(viewLifecycleOwner) { mode ->
			val target = when (mode) {
				AppCompatDelegate.MODE_NIGHT_NO -> R.id.button_theme_light
				AppCompatDelegate.MODE_NIGHT_YES -> R.id.button_theme_dark
				else -> R.id.button_theme_system
			}
			if (binding.groupTheme.checkedButtonId != target) {
				binding.groupTheme.check(target)
			}
			updateAmoledAvailability(mode)
		}
		viewModel.selectedColorScheme.observe(viewLifecycleOwner) { colorScheme ->
			renderColorThemeCards(colorScheme)
		}
		viewModel.isAmoledEnabled.observe(viewLifecycleOwner) {
			if (binding.switchAmoled.isChecked != it) {
				isAmoledSyncInProgress = true
				binding.switchAmoled.isChecked = it
				isAmoledSyncInProgress = false
			}
		}
		viewModel.storageSummary.observe(viewLifecycleOwner) {
			binding.textViewDownloadDestination.text = it ?: getString(R.string.onboarding_default_destination)
		}
		viewModel.onBackupRestored.observeEvent(viewLifecycleOwner) { result ->
			val text = if (result.error != null) {
				result.error.getDisplayMessage(resources)
			} else {
				getString(R.string.data_restored_success)
			}
			Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
		}
		viewModel.onGoogleSignInLaunch.observeEvent(viewLifecycleOwner) { intent ->
			googleSignInLauncher.launch(intent)
		}
		viewModel.onGoogleSignInCompleted.observeEvent(viewLifecycleOwner) { success ->
			val msgRes = if (success) R.string.sync_completed else R.string.sync_error
			Toast.makeText(requireContext(), msgRes, Toast.LENGTH_LONG).show()
		}
		viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
			binding.buttonSignInGoogle.isEnabled = !loading
			binding.buttonRestoreDropsauce.isEnabled = !loading
			binding.buttonRestoreTachiyomi.isEnabled = !loading
		}

		updateSlideUi()
		updatePermissionButtons()
	}

	private fun initColorThemeSelector(binding: SheetWelcomeBinding) {
		renderColorThemeCards(viewModel.selectedColorScheme.value)
	}

	private fun renderColorThemeCards(selected: ColorScheme) {
		val binding = viewBinding ?: return
		val stroke = resources.getDimensionPixelSize(com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width)
		binding.linearColorThemes.removeAllViews()
		for (theme in availableColorSchemes) {
			val themedContext = ContextThemeWrapper(requireContext(), theme.styleResId)
			val item = ItemColorSchemeBinding.inflate(LayoutInflater.from(themedContext), binding.linearColorThemes, false)
			item.textViewTitle.setText(theme.titleResId)
			val isSelected = theme == selected
			item.card.isChecked = isSelected
			item.card.strokeWidth = if (isSelected) stroke else 0
			item.imageViewCheck.isVisible = isSelected
			val click = View.OnClickListener {
				if (viewModel.selectedColorScheme.value != theme) {
					viewModel.setColorScheme(theme)
					requireActivity().recreate()
				}
			}
			item.root.setOnClickListener(click)
			item.card.setOnClickListener(click)
			binding.linearColorThemes.addView(item.root)
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.refreshStorageSummary()
		updateAmoledAvailability(viewModel.selectedTheme.value)
		updatePermissionButtons()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding?.scrollView?.updatePadding(
			top = barsInsets.top + resources.getDimensionPixelOffset(R.dimen.margin_small),
			bottom = barsInsets.bottom + resources.getDimensionPixelOffset(R.dimen.margin_normal) * 8,
		)
		viewBinding?.buttonNext?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			bottomMargin = resources.getDimensionPixelOffset(R.dimen.margin_normal) * 3 + barsInsets.bottom
		}
		viewBinding?.layoutPageIndicator?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			bottomMargin = resources.getDimensionPixelOffset(R.dimen.margin_normal) + barsInsets.bottom
		}
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_next -> onNextClick()
			R.id.button_select_destination -> router.showDirectorySelectDialog()
			R.id.button_permission_install -> requestInstallPermission()
			R.id.button_permission_notifications -> requestNotificationsPermission()
			R.id.button_permission_battery -> requestBatteryOptimizationPermission()
			R.id.button_open_github -> openExternalLink(getString(R.string.url_github), getString(R.string.source_code))
			R.id.button_open_discord -> openExternalLink(getString(R.string.url_discord_web), getString(R.string.discord))
			R.id.button_sign_in_google -> viewModel.launchGoogleSignIn()
			R.id.button_restore_dropsauce -> restoreDropSauceLauncher.launch(arrayOf("application/*", "*/*"))
			R.id.button_restore_tachiyomi -> restoreTachiyomiLauncher.launch(arrayOf("application/*", "*/*"))
			R.id.button_visit_website -> openExternalLink(getString(R.string.url_dropsauce_website), getString(R.string.onboarding_visit_website))
		}
	}

	private fun onNextClick() {
		if (currentSlide >= LAST_SLIDE_INDEX) {
			viewModel.completeOnboarding()
			dismiss()
			return
		}
		currentSlide++
		updateSlideUi()
	}

	private fun updateSlideUi() {
		val binding = viewBinding ?: return
		binding.flipperSlides.displayedChild = currentSlide
		val (titleRes, iconRes) = when (currentSlide) {
			0 -> R.string.welcome to R.drawable.ic_welcome
			1 -> R.string.onboarding_storage_permissions_title to R.drawable.ic_storage
			2 -> R.string.onboarding_sync_title to R.drawable.ic_sync
			else -> R.string.onboarding_finish_title to R.drawable.ic_save_ok
		}
		binding.textWelcomeTitle.setText(titleRes)
		binding.imageWelcomeIcon.setImageResource(iconRes)
		updateIndicator()
		if (currentSlide >= LAST_SLIDE_INDEX) {
			binding.buttonNext.setImageResource(R.drawable.ic_check)
			binding.buttonNext.contentDescription = getString(R.string.confirm)
		} else {
			binding.buttonNext.setImageResource(R.drawable.ic_arrow_forward)
			binding.buttonNext.contentDescription = getString(R.string.next)
		}
	}

	private fun updateIndicator() {
		val binding = viewBinding ?: return
		val dots = arrayOf(binding.dotPage1, binding.dotPage2, binding.dotPage3, binding.dotPage4)
		dots.forEachIndexed { index, view ->
			val isSelected = index == currentSlide
			view.setBackgroundResource(if (isSelected) R.drawable.bg_onboarding_dot_selected else R.drawable.bg_onboarding_dot)
			view.animate().cancel()
			view.animate()
				.scaleX(if (isSelected) DOT_SELECTED_SCALE else DOT_NORMAL_SCALE)
				.scaleY(if (isSelected) DOT_SELECTED_SCALE else DOT_NORMAL_SCALE)
				.alpha(if (isSelected) DOT_SELECTED_ALPHA else DOT_NORMAL_ALPHA)
				.setDuration(DOT_ANIM_DURATION)
				.start()
		}
	}

	private fun updateAmoledAvailability(themeMode: Int) {
		val binding = viewBinding ?: return
		val isDark = when (themeMode) {
			AppCompatDelegate.MODE_NIGHT_YES -> true
			AppCompatDelegate.MODE_NIGHT_NO -> false
			else -> {
				(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
			}
		}
		binding.switchAmoled.isEnabled = isDark
		binding.switchAmoled.alpha = if (isDark) 1f else 0.5f
		if (!isDark && binding.switchAmoled.isChecked) {
			isAmoledSyncInProgress = true
			binding.switchAmoled.isChecked = false
			isAmoledSyncInProgress = false
			viewModel.setAmoledTheme(false)
		}
	}

	private fun handleBackNavigation() {
		if (currentSlide > 0) {
			currentSlide--
			updateSlideUi()
		} else {
			dismiss()
		}
	}

	private fun updatePermissionButtons() {
		val binding = viewBinding ?: return
		bindPermissionButton(
			button = binding.buttonPermissionInstall,
			titleRes = R.string.onboarding_permission_install,
			isGranted = hasInstallPermission(),
		)
		bindPermissionButton(
			button = binding.buttonPermissionNotifications,
			titleRes = R.string.onboarding_permission_notifications,
			isGranted = hasNotificationPermission(),
		)
		bindPermissionButton(
			button = binding.buttonPermissionBattery,
			titleRes = R.string.onboarding_permission_battery,
			isGranted = isIgnoringBatteryOptimizations(),
		)
	}

	private fun bindPermissionButton(button: MaterialButton, titleRes: Int, isGranted: Boolean) {
		button.text = getString(
			R.string.onboarding_permission_title_status,
			getString(titleRes),
			getString(if (isGranted) R.string.enabled else R.string.disabled),
		)
		button.isEnabled = !isGranted
	}

	private fun hasInstallPermission(): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return true
		}
		return requireContext().packageManager.canRequestPackageInstalls()
	}

	private fun hasNotificationPermission(): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return true
		}
		return ContextCompat.checkSelfPermission(
			requireContext(),
			Manifest.permission.POST_NOTIFICATIONS,
		) == PackageManager.PERMISSION_GRANTED
	}

	private fun isIgnoringBatteryOptimizations(): Boolean {
		val powerManager = requireContext().getSystemService(PowerManager::class.java) ?: return false
		return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
	}

	private fun requestInstallPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return
		}
		val intent = Intent(
			Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
			Uri.parse("package:${requireContext().packageName}"),
		)
		runCatching {
			installPermissionLauncher.launch(intent)
		}.onFailure {
			Snackbar.make(requireView(), R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	private fun requestNotificationsPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission()) {
			return
		}
		notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
	}

	private fun requestBatteryOptimizationPermission() {
		if (isIgnoringBatteryOptimizations()) {
			return
		}
		val intent = Intent(
			Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
			Uri.parse("package:${requireContext().packageName}"),
		)
		runCatching {
			batteryPermissionLauncher.launch(intent)
		}.onFailure {
			Snackbar.make(requireView(), R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	private fun openExternalLink(url: String, title: String) {
		if (!router.openExternalBrowser(url, title)) {
			Snackbar.make(requireView(), R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	private companion object {
		const val SLIDES_COUNT = 4
		const val LAST_SLIDE_INDEX = SLIDES_COUNT - 1
		const val DOT_ANIM_DURATION = 140L
		const val DOT_SELECTED_SCALE = 1.15f
		const val DOT_NORMAL_SCALE = 1f
		const val DOT_SELECTED_ALPHA = 1f
		const val DOT_NORMAL_ALPHA = 0.75f
	}
}
