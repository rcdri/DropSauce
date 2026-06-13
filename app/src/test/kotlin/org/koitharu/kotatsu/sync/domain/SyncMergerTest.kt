package org.koitharu.kotatsu.sync.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.backup.local.data.model.BookmarkBackup
import org.koitharu.kotatsu.backup.local.data.model.MangaBackup
import org.koitharu.kotatsu.backup.local.data.model.ScrobblingBackup
import org.koitharu.kotatsu.backup.local.data.model.StatsBackup
import org.koitharu.kotatsu.sync.data.model.SyncCategory
import org.koitharu.kotatsu.sync.data.model.SyncFavourite
import org.koitharu.kotatsu.sync.data.model.SyncHistory
import org.koitharu.kotatsu.sync.data.model.SyncTrack

/**
 * Verifies the per-record last-writer-wins merge that powers Google Drive sync. These guard against
 * silent data loss across devices: newer edits must win, soft-deletes must propagate, and a device
 * must never clobber its own state with an equally-old remote copy (local wins on a timestamp tie).
 */
class SyncMergerTest {

	private fun manga(id: Long) = MangaBackup(
		id = id,
		title = "Manga $id",
		url = "/manga/$id",
		publicUrl = "https://example.test/manga/$id",
		coverUrl = "https://example.test/manga/$id/cover.jpg",
		source = "TEST_SOURCE",
	)

	private fun category(id: Int, createdAt: Long, title: String = "C$id", deletedAt: Long = 0L) =
		SyncCategory(
			categoryId = id,
			createdAt = createdAt,
			sortKey = 0,
			title = title,
			order = "NEWEST",
			track = true,
			downloadNewChapters = false,
			isVisibleInLibrary = true,
			deletedAt = deletedAt,
		)

	private fun history(mangaId: Long, updatedAt: Long, page: Int = 0, deletedAt: Long = 0L) =
		SyncHistory(
			mangaId = mangaId,
			createdAt = 1L,
			updatedAt = updatedAt,
			chapterId = 1L,
			page = page,
			scroll = 0f,
			percent = 0f,
			deletedAt = deletedAt,
			chaptersCount = 1,
			manga = manga(mangaId),
		)

	private fun track(mangaId: Long, lastCheckTime: Long, newChapters: Int = 0) = SyncTrack(
		mangaId = mangaId,
		lastChapterId = 1L,
		newChapters = newChapters,
		lastCheckTime = lastCheckTime,
		lastChapterDate = 0L,
		lastResult = 0,
		lastError = null,
		manga = manga(mangaId),
	)

	private fun favourite(mangaId: Long, categoryId: Long, createdAt: Long, deletedAt: Long = 0L) =
		SyncFavourite(
			mangaId = mangaId,
			categoryId = categoryId,
			sortKey = 0,
			isPinned = false,
			createdAt = createdAt,
			deletedAt = deletedAt,
			manga = manga(mangaId),
		)

	@Test
	fun `categories - disjoint sets are unioned`() {
		val result = SyncMerger.mergeCategories(listOf(category(1, 100)), listOf(category(2, 100)))
		assertEquals(setOf(1, 2), result.map { it.categoryId }.toSet())
	}

	@Test
	fun `categories - newer remote wins`() {
		val result = SyncMerger.mergeCategories(
			local = listOf(category(1, createdAt = 100, title = "local")),
			remote = listOf(category(1, createdAt = 200, title = "remote")),
		)
		assertEquals(1, result.size)
		assertEquals("remote", result.single().title)
	}

	@Test
	fun `categories - older remote loses`() {
		val result = SyncMerger.mergeCategories(
			local = listOf(category(1, createdAt = 200, title = "local")),
			remote = listOf(category(1, createdAt = 100, title = "remote")),
		)
		assertEquals("local", result.single().title)
	}

	@Test
	fun `categories - timestamp tie keeps local`() {
		val result = SyncMerger.mergeCategories(
			local = listOf(category(1, createdAt = 100, title = "local")),
			remote = listOf(category(1, createdAt = 100, title = "remote")),
		)
		assertEquals("local", result.single().title)
	}

	@Test
	fun `categories - remote soft-delete propagates over older local`() {
		val result = SyncMerger.mergeCategories(
			local = listOf(category(1, createdAt = 100, deletedAt = 0L)),
			remote = listOf(category(1, createdAt = 100, deletedAt = 300L)),
		)
		assertTrue("deletion should win via newer effective timestamp", result.single().deletedAt == 300L)
	}

	@Test
	fun `history - newer updatedAt wins`() {
		val result = SyncMerger.mergeHistory(
			local = listOf(history(1, updatedAt = 100, page = 5)),
			remote = listOf(history(1, updatedAt = 200, page = 9)),
		)
		assertEquals(9, result.single().page)
	}

	@Test
	fun `history - local kept when newer than remote`() {
		val result = SyncMerger.mergeHistory(
			local = listOf(history(1, updatedAt = 500, page = 5)),
			remote = listOf(history(1, updatedAt = 200, page = 9)),
		)
		assertEquals(5, result.single().page)
	}

	@Test
	fun `tracks - newer lastCheckTime wins`() {
		val result = SyncMerger.mergeTracks(
			local = listOf(track(1, lastCheckTime = 100, newChapters = 1)),
			remote = listOf(track(1, lastCheckTime = 200, newChapters = 7)),
		)
		assertEquals(7, result.single().newChapters)
	}

	@Test
	fun `favourites - keyed by manga and category`() {
		val result = SyncMerger.mergeFavourites(
			local = listOf(favourite(mangaId = 1, categoryId = 10, createdAt = 100)),
			remote = listOf(favourite(mangaId = 1, categoryId = 20, createdAt = 100)),
		)
		assertEquals(2, result.size)
	}

	@Test
	fun `stats - distinct sessions are unioned`() {
		val a = StatsBackup(mangaId = 1, startedAt = 1000, duration = 60, pages = 10)
		val b = StatsBackup(mangaId = 1, startedAt = 2000, duration = 30, pages = 5)
		val result = SyncMerger.mergeStats(listOf(a), listOf(b))
		assertEquals(2, result.size)
	}

	@Test
	fun `scrobblings - union by composite key, local wins on conflict`() {
		val local = ScrobblingBackup(scrobbler = 1, id = 1, mangaId = 1, targetId = 100, chapter = 5)
		val remote = ScrobblingBackup(scrobbler = 1, id = 1, mangaId = 1, targetId = 100, chapter = 9)
		val other = ScrobblingBackup(scrobbler = 2, id = 1, mangaId = 1, targetId = 200, chapter = 3)
		val result = SyncMerger.mergeScrobblings(listOf(local), listOf(remote, other))
		assertEquals(2, result.size)
		val merged = result.first { it.scrobbler == 1 }
		assertEquals("local should win on conflict", 5, merged.chapter)
	}

	@Test
	fun `bookmarks - groups unioned and items merged by page with local winning`() {
		val localItem = BookmarkBackup.Item(
			mangaId = 1, pageId = 10, chapterId = 1, page = 0, scroll = 1,
			imageUrl = "a", createdAt = 1, percent = 0f,
		)
		val remoteSamePage = BookmarkBackup.Item(
			mangaId = 1, pageId = 10, chapterId = 1, page = 0, scroll = 2,
			imageUrl = "b", createdAt = 2, percent = 0f,
		)
		val remoteNewPage = BookmarkBackup.Item(
			mangaId = 1, pageId = 11, chapterId = 1, page = 1, scroll = 0,
			imageUrl = "c", createdAt = 3, percent = 0f,
		)
		val result = SyncMerger.mergeBookmarks(
			local = listOf(BookmarkBackup(manga(1), listOf(localItem))),
			remote = listOf(BookmarkBackup(manga(1), listOf(remoteSamePage, remoteNewPage))),
		)
		val group = result.single()
		assertEquals(setOf(10L, 11L), group.bookmarks.map { it.pageId }.toSet())
		val page10 = group.bookmarks.first { it.pageId == 10L }
		assertEquals("local item should win for the same pageId", 1, page10.scroll)
	}

	@Test
	fun `empty inputs produce empty output`() {
		assertTrue(SyncMerger.mergeCategories(emptyList(), emptyList()).isEmpty())
		assertNull(SyncMerger.mergeCategories(emptyList(), emptyList()).firstOrNull())
	}
}
