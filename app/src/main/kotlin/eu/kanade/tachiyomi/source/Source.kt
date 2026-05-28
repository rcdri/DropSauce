package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

interface Source {
	val id: Long
	val name: String

	val lang: String
		get() = ""

	@Suppress("DEPRECATION")
	suspend fun getMangaDetails(manga: SManga): SManga {
		return fetchMangaDetails(manga).awaitSingle()
	}

	@Suppress("DEPRECATION")
	suspend fun getChapterList(manga: SManga): List<SChapter> {
		return fetchChapterList(manga).awaitSingle()
	}

	@Suppress("DEPRECATION")
	suspend fun getPageList(chapter: SChapter): List<Page> {
		return fetchPageList(chapter).awaitSingle()
	}

	/**
	 * Combined manga + chapter fetch (extensions-lib 1.5+).
	 * Host falls back to separate calls; sources may override for efficiency.
	 */
	suspend fun getMangaUpdate(
		manga: SManga,
		chapters: List<SChapter>,
		fetchDetails: Boolean,
		fetchChapters: Boolean,
	): SMangaUpdate {
		val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
		val updatedChapters = if (fetchChapters) getChapterList(manga) else chapters
		return SMangaUpdate(updatedManga, updatedChapters)
	}

	@Deprecated("Use the non-RxJava API instead", ReplaceWith("getMangaDetails"))
	fun fetchMangaDetails(manga: SManga): Observable<SManga> =
		throw IllegalStateException("Not used")

	@Deprecated("Use the non-RxJava API instead", ReplaceWith("getChapterList"))
	fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
		throw IllegalStateException("Not used")

	@Deprecated("Use the non-RxJava API instead", ReplaceWith("getPageList"))
	fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
		throw IllegalStateException("Not used")
}
