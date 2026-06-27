package org.koitharu.kotatsu.settings.sources.migration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.alternatives.ui.AutoFixService
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.ui.image.FaviconView
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import org.koitharu.kotatsu.settings.compose.BaseComposeSettingsFragment
import org.koitharu.kotatsu.settings.compose.DropSauceTheme

@AndroidEntryPoint
class BrokenSourcesMigrationFragment :
	BaseComposeSettingsFragment(R.string.migrate_broken_sources) {

	private val viewModel by viewModels<BrokenSourcesMigrationViewModel>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = ComposeView(requireContext()).apply {
		setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		setContent {
			DropSauceTheme {
				val state by viewModel.state.collectAsState()
				BrokenSourcesMigrationScreen(
					state = state,
					onToggle = viewModel::toggle,
					onFix = {
						if (AutoFixService.startForSources(requireContext(), state.selectedSources)) {
							Toast.makeText(
								requireContext(),
								R.string.broken_sources_migration_started,
								Toast.LENGTH_LONG,
							).show()
							viewModel.clearSelection()
						} else {
							Toast.makeText(requireContext(), R.string.error_occurred, Toast.LENGTH_SHORT).show()
						}
					},
				)
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(object : MenuProvider {
			override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
				menu.add(Menu.NONE, MENU_INFO, Menu.NONE, R.string.migration_information).apply {
					setIcon(R.drawable.ic_info_outline)
					setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
				}
			}

			override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
				if (menuItem.itemId != MENU_INFO) return false
				viewModel.toggleInfo()
				return true
			}
		})
	}

	private companion object {
		const val MENU_INFO = 0x4D4947
	}
}

@Composable
private fun BrokenSourcesMigrationScreen(
	state: BrokenSourcesMigrationState,
	onToggle: (String) -> Unit,
	onFix: () -> Unit,
) {
	val unavailableSources = state.sources.filter(LibrarySourceOption::isUnavailable)
	val mihonSources = state.sources.filterNot(LibrarySourceOption::isUnavailable)

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(top = 8.dp),
	) {
		AnimatedVisibility(visible = state.isInfoVisible) {
			Column {
				Column(
					modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(
						text = stringResource(R.string.migrate_broken_sources_description),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = stringResource(R.string.recommended),
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.primary,
					)
					Text(
						text = stringResource(R.string.unavailable_library_sources_summary),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
			}
		}

		when {
			state.isLoading -> Box(
				modifier = Modifier.fillMaxWidth().weight(1f),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator()
			}

			state.sources.isEmpty() -> Box(
				modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
				contentAlignment = Alignment.Center,
			) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					Text(
						text = stringResource(R.string.no_sources_to_migrate),
						style = MaterialTheme.typography.titleMedium,
					)
					Spacer(Modifier.height(8.dp))
					Text(
						text = stringResource(R.string.no_sources_to_migrate_summary),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
				if (unavailableSources.isNotEmpty()) {
					item {
						SourceSectionHeader(
							title = stringResource(R.string.recommended),
						)
					}
					items(unavailableSources, key = LibrarySourceOption::key) { source ->
						SourceCheckboxRow(
							source = source,
							isChecked = source.key in state.selectedSources,
							onToggle = { onToggle(source.key) },
						)
						SourceDivider()
					}
				}
				if (mihonSources.isNotEmpty()) {
					item {
						SourceSectionHeader(
							title = stringResource(R.string.mihon_sources),
						)
					}
					items(mihonSources, key = LibrarySourceOption::key) { source ->
						SourceCheckboxRow(
							source = source,
							isChecked = source.key in state.selectedSources,
							onToggle = { onToggle(source.key) },
						)
						SourceDivider()
					}
				}
				item { Spacer(Modifier.height(12.dp)) }
			}
		}

		Surface(
			tonalElevation = 3.dp,
			shadowElevation = 6.dp,
			color = MaterialTheme.colorScheme.surfaceContainer,
		) {
			Column {
				HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
				Button(
					onClick = onFix,
					enabled = state.selectedSources.isNotEmpty(),
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp)
						.height(52.dp),
				) {
					Text(
						if (state.selectedSources.isEmpty()) {
							stringResource(R.string.fix)
						} else {
							pluralStringResource(
								R.plurals.fix_selected_sources,
								state.selectedSources.size,
								state.selectedSources.size,
							)
						},
					)
				}
			}
		}
	}
}

@Composable
private fun SourceSectionHeader(title: String) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp),
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.primary,
		)
	}
}

@Composable
private fun SourceCheckboxRow(
	source: LibrarySourceOption,
	isChecked: Boolean,
	onToggle: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 64.dp)
			.clickable(onClick = onToggle)
			.padding(horizontal = 16.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		AndroidView(
			factory = { context ->
				FaviconView(context).apply {
					setIconStyle(R.style.FaviconDrawable_Small)
					applyExternalSourceStyle(true)
				}
			},
			update = { view ->
				val iconKey = source.iconUrl ?: source.iconSourceKey
				if (view.tag != iconKey) {
					view.tag = iconKey
					if (source.iconUrl != null) {
						view.setImageFromUrlAsync(source.iconUrl, source.title)
					} else {
						view.setImageAsync(MangaSource(source.iconSourceKey, source.title))
					}
				}
			},
			modifier = Modifier.width(40.dp).height(40.dp),
		)
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = source.title,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Text(
				text = pluralStringResource(R.plurals.manga_count, source.mangaCount, source.mangaCount),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Spacer(Modifier.width(12.dp))
		Checkbox(
			checked = isChecked,
			onCheckedChange = { onToggle() },
		)
	}
}

@Composable
private fun SourceDivider() {
	HorizontalDivider(
		modifier = Modifier.padding(start = 68.dp, end = 16.dp),
		thickness = 1.dp,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
}
