package org.koitharu.kotatsu.settings.sources.migration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.getStoredTitleOrNull
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.kotatsumigration.data.KotatsuSourceMap
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.settings.sources.catalog.ExternalExtensionRepoRepository
import javax.inject.Inject

@HiltViewModel
class BrokenSourcesMigrationViewModel @Inject constructor(
	private val database: MangaDatabase,
	private val settings: AppSettings,
	private val extensionManager: MihonExtensionManager,
	private val extensionRepoRepository: ExternalExtensionRepoRepository,
	private val kotatsuSourceMap: KotatsuSourceMap,
	@ApplicationContext private val context: Context,
) : ViewModel() {

	private val _state = MutableStateFlow(BrokenSourcesMigrationState(isLoading = true))
	val state: StateFlow<BrokenSourcesMigrationState> = _state.asStateFlow()

	init {
		refresh()
	}

	fun refresh() {
		viewModelScope.launch {
			_state.update { it.copy(isLoading = true) }
			val sources = withContext(Dispatchers.IO) {
				extensionManager.ensureReady()
				val installedIds = extensionManager.getMihonMangaSources()
					.mapTo(hashSetOf()) { it.sourceId }
				val repoUrl = settings.externalExtensionsRepoUrl
				val repositoryExtensions = repoUrl
					?.let { runCatching { extensionRepoRepository.getExtensions(it) }.getOrDefault(emptyList()) }
					.orEmpty()
				val repositoryIcons = buildMap {
					for (extension in repositoryExtensions) {
						val iconUrl = repoUrl?.let {
							extensionRepoRepository.resolveIconUrl(it, extension.packageName)
						}
						for (source in extension.sources) {
							val id = source.id.toLongOrNull() ?: continue
							if (iconUrl != null) putIfAbsent(id, iconUrl)
						}
					}
				}
				database.getMangaDao().findLibrarySourceUsage().map { usage ->
					val source = MangaSource(usage.source, usage.sourceTitle)
					val mihonId = usage.source
						.takeIf { it.startsWith(MIHON_PREFIX) }
						?.removePrefix(MIHON_PREFIX)
						?.substringBefore(':')
						?.toLongOrNull()
					val mappedLegacySource = if (mihonId == null) {
						kotatsuSourceMap.resolve(usage.source)
					} else {
						null
					}
					val representedInExtensionManager = when (mihonId) {
						null -> mappedLegacySource != null
						else -> mihonId in installedIds ||
							mihonId in repositoryIcons ||
							kotatsuSourceMap.resolveById(mihonId) != null
					}
					val representedSourceId = mihonId ?: mappedLegacySource?.sourceId
					val iconSourceKey = when {
						mihonId != null -> usage.source
						mappedLegacySource != null ->
							"MIHON_${mappedLegacySource.sourceId}:${mappedLegacySource.sourceName}"
						else -> usage.source
					}
					LibrarySourceOption(
						key = usage.source,
						title = usage.sourceTitle
							?.takeIf(String::isNotBlank)
							?: mappedLegacySource?.sourceName?.takeIf(String::isNotBlank)
							?: source.getStoredTitleOrNull()
							?: source.getTitle(context).toReadableSourceName(),
						mangaCount = usage.mangaCount,
						isUnavailable = !representedInExtensionManager,
						iconSourceKey = iconSourceKey,
						iconUrl = representedSourceId
							?.takeUnless { it in installedIds }
							?.let(repositoryIcons::get),
					)
				}
			}
			_state.update { current ->
				current.copy(
					isLoading = false,
					sources = sources,
					selectedSources = current.selectedSources.intersect(sources.mapTo(hashSetOf()) { it.key }),
				)
			}
		}
	}

	fun toggle(source: String) {
		_state.update { current ->
			current.copy(
				selectedSources = current.selectedSources.toMutableSet().apply {
					if (!add(source)) remove(source)
				},
			)
		}
	}

	fun clearSelection() {
		_state.update { it.copy(selectedSources = emptySet()) }
	}

	fun toggleInfo() {
		_state.update { it.copy(isInfoVisible = !it.isInfoVisible) }
	}

	companion object {
		private const val MIHON_PREFIX = "MIHON_"
	}
}

private fun String.toReadableSourceName(): String {
	if ('_' !in this || startsWith("MIHON_")) return this
	return lowercase()
		.split('_')
		.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}

data class BrokenSourcesMigrationState(
	val isLoading: Boolean = false,
	val isInfoVisible: Boolean = false,
	val sources: List<LibrarySourceOption> = emptyList(),
	val selectedSources: Set<String> = emptySet(),
)

data class LibrarySourceOption(
	val key: String,
	val title: String,
	val mangaCount: Int,
	val isUnavailable: Boolean,
	val iconSourceKey: String,
	val iconUrl: String?,
)
