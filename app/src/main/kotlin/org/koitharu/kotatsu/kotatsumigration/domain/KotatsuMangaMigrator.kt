package org.koitharu.kotatsu.kotatsumigration.domain

import androidx.room.withTransaction
import org.koitharu.kotatsu.bookmarks.data.BookmarkEntity
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.parser.MangaDataRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.history.data.HistoryEntity
import org.koitharu.kotatsu.history.data.toMangaHistory
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.tracker.data.TrackEntity
import javax.inject.Inject

/**
 * Re-keys every piece of user data attached to [oldManga] onto [newManga] (which lives on the
 * mapped Mihon source), then retires the old entry. A corrected fork of
 * [org.koitharu.kotatsu.alternatives.domain.MigrateUseCase]:
 *  - **bookmarks** and **stats** are migrated (the original silently dropped them),
 *  - reading-progress **percent is preserved** by recomputing it from the chapter we map to
 *    (the original reset it to zero),
 *  - chapters are matched by (volume, number) so history/bookmark positions survive.
 *
 * Because Kotatsu→Mihon migration stays on the *same website*, chapter lists line up closely and
 * the (volume, number) match is near-exact.
 */
class KotatsuMangaMigrator @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val mangaDataRepository: MangaDataRepository,
	private val database: MangaDatabase,
) {

	suspend operator fun invoke(oldManga: Manga, newManga: Manga) {
		val oldDetails = if (oldManga.chapters.isNullOrEmpty()) {
			runCatchingCancellable {
				mangaRepositoryFactory.create(oldManga.source).getDetails(oldManga)
			}.getOrDefault(oldManga)
		} else {
			oldManga
		}
		val newDetails = if (newManga.chapters.isNullOrEmpty()) {
			mangaRepositoryFactory.create(newManga.source).getDetails(newManga)
		} else {
			newManga
		}
		if (oldDetails.id == newDetails.id) {
			return // nothing to do — already the same entity
		}
		mangaDataRepository.storeManga(newDetails, replaceExisting = true)

		val oldChaptersById = oldDetails.chapters.orEmpty().associateBy { it.id }
		val newChapters = newDetails.chapters.orEmpty()

		database.withTransaction {
			// --- favourites (soft-delete leaves a sync tombstone, matching the app's convention) ---
			val favouritesDao = database.getFavouritesDao()
			val oldFavourites = favouritesDao.findAllRaw(oldDetails.id)
			if (oldFavourites.isNotEmpty()) {
				favouritesDao.delete(oldDetails.id)
				for (f in oldFavourites) {
					favouritesDao.upsert(f.copy(mangaId = newDetails.id))
				}
			}

			// --- history (progress preserved via recomputed percent) ---
			val historyDao = database.getHistoryDao()
			val oldHistory = historyDao.find(oldDetails.id)
			val hasNewHistory = oldHistory != null
			if (oldHistory != null) {
				val newHistory = makeNewHistory(oldDetails, newDetails, oldHistory)
				historyDao.delete(oldDetails.id)
				historyDao.upsert(newHistory)
			}

			// --- reading stats (FK -> history, so only when a history row now exists) ---
			val statsDao = database.getStatsDao()
			if (hasNewHistory) {
				val oldStats = statsDao.findAll(oldDetails.id)
				for (s in oldStats) {
					statsDao.upsert(s.copy(mangaId = newDetails.id))
				}
			}
			statsDao.deleteAll(oldDetails.id)

			// --- tracker (new-chapters watch) ---
			val tracksDao = database.getTracksDao()
			val oldTrack = tracksDao.find(oldDetails.id)
			if (oldTrack != null) {
				val lastChapter = newChapters.lastOrNull()
				tracksDao.delete(oldDetails.id)
				tracksDao.upsert(
					TrackEntity(
						mangaId = newDetails.id,
						lastChapterId = lastChapter?.id ?: 0L,
						newChapters = 0,
						lastCheckTime = System.currentTimeMillis(),
						lastChapterDate = lastChapter?.uploadDate ?: 0L,
						lastResult = TrackEntity.RESULT_EXTERNAL_MODIFICATION,
						lastError = null,
					),
				)
			}

			// --- bookmarks (chapter remapped by volume/number; page & scroll kept) ---
			val bookmarksDao = database.getBookmarksDao()
			val oldBookmarks = bookmarksDao.findAll(oldDetails.id)
			if (oldBookmarks.isNotEmpty()) {
				val remapped = oldBookmarks.map { bm ->
					BookmarkEntity(
						mangaId = newDetails.id,
						pageId = bm.pageId,
						chapterId = mapChapterId(bm.chapterId, oldChaptersById, newChapters),
						page = bm.page,
						scroll = bm.scroll,
						imageUrl = bm.imageUrl,
						createdAt = bm.createdAt,
						percent = bm.percent,
					)
				}
				bookmarksDao.upsert(remapped)
				for (bm in oldBookmarks) {
					bookmarksDao.delete(bm)
				}
			}

			// --- scrobbling links (no manga FK -> re-key the rows directly, preserving the link) ---
			val scrobblingDao = database.getScrobblingDao()
			val oldScrobblings = scrobblingDao.findAll(oldDetails.id)
			if (oldScrobblings.isNotEmpty()) {
				for (s in oldScrobblings) {
					scrobblingDao.upsert(
						org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity(
							scrobbler = s.scrobbler,
							id = s.id,
							mangaId = newDetails.id,
							targetId = s.targetId,
							status = s.status,
							chapter = s.chapter,
							comment = s.comment,
							rating = s.rating,
						),
					)
				}
				for (scrobbler in oldScrobblings.map { it.scrobbler }.distinct()) {
					scrobblingDao.delete(scrobbler, oldDetails.id)
				}
			}
		}
	}

	private fun makeNewHistory(oldManga: Manga, newManga: Manga, history: HistoryEntity): HistoryEntity {
		val newAll = newManga.chapters
		if (newAll.isNullOrEmpty()) {
			return HistoryEntity(
				mangaId = newManga.id,
				createdAt = history.createdAt,
				updatedAt = history.updatedAt,
				chapterId = history.chapterId,
				page = history.page,
				scroll = history.scroll,
				percent = history.percent,
				deletedAt = 0L,
				chaptersCount = history.chaptersCount,
			)
		}
		val oldChapters = oldManga.chapters
		if (oldChapters.isNullOrEmpty()) {
			// Old entry has no cached chapters: distribute by percent across the new list.
			val branch = newManga.getPreferredBranch(null)
			val list = newAll.filter { it.branch == branch }.ifEmpty { newAll }
			val index = if (history.percent in 0f..1f) (list.lastIndex * history.percent).toInt() else 0
			val current = list.getOrElse(index) { list.first() }
			return HistoryEntity(
				mangaId = newManga.id,
				createdAt = history.createdAt,
				updatedAt = history.updatedAt,
				chapterId = current.id,
				page = history.page,
				scroll = history.scroll,
				percent = percentForIndex(index, list.size),
				deletedAt = 0L,
				chaptersCount = list.size,
			)
		}
		val branch = oldManga.getPreferredBranch(history.toMangaHistory())
		val oldBranch = oldChapters.filter { it.branch == branch }.ifEmpty { oldChapters }
		var index = oldBranch.indexOfFirst { it.id == history.chapterId }
		if (index < 0) {
			index = if (history.percent in 0f..1f) (oldBranch.lastIndex * history.percent).toInt() else 0
		}
		val oldChapter = oldBranch.getOrElse(index) { oldBranch.first() }
		val newBranches = newAll.groupBy { it.branch }
		val newBranch = if (newBranches.containsKey(branch)) branch else newManga.getPreferredBranch(null)
		val newList = newBranches[newBranch] ?: newAll
		val newChapter = newList.findByNumber(oldChapter.volume, oldChapter.number)
			?: newList.getOrNull(index)
			?: newList.last()
		val newIndex = newList.indexOf(newChapter).coerceAtLeast(0)
		return HistoryEntity(
			mangaId = newManga.id,
			createdAt = history.createdAt,
			updatedAt = history.updatedAt,
			chapterId = newChapter.id,
			page = history.page,
			scroll = history.scroll,
			percent = percentForIndex(newIndex, newList.size),
			deletedAt = 0L,
			chaptersCount = newList.size,
		)
	}

	private fun mapChapterId(
		oldChapterId: Long,
		oldChaptersById: Map<Long, MangaChapter>,
		newChapters: List<MangaChapter>,
	): Long {
		val oldChapter = oldChaptersById[oldChapterId] ?: return oldChapterId
		val match = newChapters.findByNumber(oldChapter.volume, oldChapter.number)
			?: newChapters.firstOrNull { it.volume == oldChapter.volume && it.number == oldChapter.number }
		return match?.id ?: oldChapterId
	}

	private fun percentForIndex(index: Int, count: Int): Float =
		if (count <= 0) 0f else ((index + 1).toFloat() / count).coerceIn(0f, 1f)

	private fun List<MangaChapter>.findByNumber(volume: Int, number: Float): MangaChapter? =
		if (number <= 0f) null else firstOrNull { it.volume == volume && it.number == number }
}
