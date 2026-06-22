package org.koitharu.kotatsu.kotatsumigration.domain

import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.kotatsumigration.data.KotatsuSourceMap
import org.koitharu.kotatsu.kotatsumigration.data.MihonTarget
import org.koitharu.kotatsu.mihon.MihonExtensionManager
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Drives migration of restored Kotatsu library entries (built-in sources) onto installed Mihon
 * extensions. [scan] finds candidates; [migrate] resolves the predefined mapping, finds the same
 * manga on the mapped Mihon source by title (same website → the title is present), and re-keys all
 * user data via [KotatsuMangaMigrator].
 */
class KotatsuMigrationUseCase @Inject constructor(
	private val database: MangaDatabase,
	private val sourceMap: KotatsuSourceMap,
	private val mihonExtensionManager: MihonExtensionManager,
	private val mangaDataRepository: MangaDataRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val migrator: KotatsuMangaMigrator,
) {

	/** Restored manga on a built-in (non-Mihon) source that still carry user data. */
	suspend fun scan(): List<LegacyManga> {
		return database.getMangaDao().findLegacyMangaWithUserData().map {
			val manga = it.toManga()
			LegacyManga(
				id = manga.id,
				title = manga.title,
				sourceName = it.manga.source,
				sourceTitle = it.manga.sourceTitle,
			)
		}
	}

	suspend fun migrate(legacy: LegacyManga): Outcome {
		val target = sourceMap.resolve(legacy.sourceName) ?: return Outcome.NoMapping
		mihonExtensionManager.ensureReady()
		val mihonSource = mihonExtensionManager.getMihonMangaSourceById(target.sourceId)
			?: return Outcome.ExtensionNotInstalled(target)
		val oldManga = mangaDataRepository.findMangaById(legacy.id, withChapters = true)
			?: return Outcome.Failed(target, "Manga ${legacy.id} not found")
		val repository = mangaRepositoryFactory.create(mihonSource)
		val candidates = runCatchingCancellable {
			repository.getList(0, SortOrder.RELEVANCE, MangaListFilter(query = oldManga.title))
		}.getOrElse { return Outcome.Failed(target, it.message) }
		val match = pickBestMatch(oldManga, candidates) ?: return Outcome.NotFound(target)
		return runCatchingCancellable {
			val newDetails = repository.getDetails(match)
			migrator(oldManga, newDetails)
			Outcome.Migrated(target, newDetails) as Outcome
		}.getOrElse { Outcome.Failed(target, it.message) }
	}

	private fun pickBestMatch(old: Manga, candidates: List<Manga>): Manga? {
		if (candidates.isEmpty()) return null
		val target = normalize(old.title)
		if (target.isEmpty()) return null
		// 1. exact normalized-title match (overwhelmingly the case on the same website)
		candidates.firstOrNull { normalize(it.title) == target }?.let { return it }
		// 2. otherwise the best token-overlap candidate, if confident enough
		val targetTokens = tokens(old.title)
		var best: Manga? = null
		var bestScore = 0.0
		for (candidate in candidates) {
			val score = jaccard(targetTokens, tokens(candidate.title))
			if (score > bestScore) {
				bestScore = score
				best = candidate
			}
		}
		return best?.takeIf { bestScore >= MIN_SIMILARITY }
	}

	private fun normalize(value: String): String =
		value.lowercase().replace(NON_ALNUM, "")

	private fun tokens(value: String): Set<String> =
		value.lowercase().split(NON_ALNUM_SPLIT).filter { it.isNotBlank() }.toSet()

	private fun jaccard(a: Set<String>, b: Set<String>): Double {
		if (a.isEmpty() || b.isEmpty()) return 0.0
		val intersection = a.count { it in b }
		val union = a.size + b.size - intersection
		return if (union == 0) 0.0 else intersection.toDouble() / union
	}

	data class LegacyManga(
		val id: Long,
		val title: String,
		val sourceName: String,
		val sourceTitle: String?,
	)

	sealed interface Outcome {
		/** Migrated successfully onto [target]. */
		data class Migrated(val target: MihonTarget, val manga: Manga) : Outcome

		/** A mapping exists but its extension isn't installed. */
		data class ExtensionNotInstalled(val target: MihonTarget) : Outcome

		/** Extension installed, but the title wasn't found on the source. */
		data class NotFound(val target: MihonTarget) : Outcome

		/** No predefined Mihon equivalent for this Kotatsu source. */
		data object NoMapping : Outcome

		/** Migration threw (network etc.). */
		data class Failed(val target: MihonTarget?, val message: String?) : Outcome
	}

	private companion object {
		val NON_ALNUM = Regex("[^a-z0-9]")
		val NON_ALNUM_SPLIT = Regex("[^a-z0-9]+")
		const val MIN_SIMILARITY = 0.6
	}
}
