@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SManga : Serializable {
	var url: String
	var title: String
	var artist: String?
	var author: String?
	var description: String?

	@Deprecated("Use genres instead", replaceWith = ReplaceWith("genres"))
	var genre: String?

	/**
	 * List-based genres (extensions-lib 1.5+). Backed by the deprecated [genre] string
	 * so existing round-trip code continues to work.
	 */
	var genres: List<String>
		get() = genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() } ?: emptyList()
		set(value) { genre = value.joinToString(", ") }

	/** Alternative titles (extensions-lib 1.5+). */
	var altTitles: List<String>
		get() = emptyList()
		set(_) {}

	/** Banner image URL (extensions-lib 1.5+). */
	var banner: String?
		get() = null
		set(_) {}

	/** Content rating (extensions-lib 1.5+). */
	var contentRating: ContentRating
		get() = ContentRating.SAFE
		set(_) {}

	/** Percentile score 0–100, null if unavailable (extensions-lib 1.5+). */
	var score: Int?
		get() = null
		set(_) {}

	/** Preferred reading direction (extensions-lib 1.5+). */
	var readingMode: ReadingMode?
		get() = null
		set(_) {}

	/** Custom metadata map for namespaced extension data (extensions-lib 1.5+). */
	var memo: Map<String, String>
		get() = emptyMap()
		set(_) {}

	var status: Int
	var thumbnail_url: String?
	var update_strategy: UpdateStrategy
	var initialized: Boolean

	fun copy() = create().also {
		it.url = url
		it.title = title
		it.artist = artist
		it.author = author
		it.description = description
		it.genre = genre
		it.genres = genres
		it.altTitles = altTitles
		it.banner = banner
		it.contentRating = contentRating
		it.score = score
		it.readingMode = readingMode
		it.memo = memo
		it.status = status
		it.thumbnail_url = thumbnail_url
		it.update_strategy = update_strategy
		it.initialized = initialized
	}

	enum class ContentRating {
		SAFE, SUGGESTIVE, ADULT
	}

	enum class ReadingMode {
		RIGHT_TO_LEFT, LEFT_TO_RIGHT, LONG_STRIP
	}

	companion object {
		const val UNKNOWN = 0
		const val ONGOING = 1
		const val COMPLETED = 2
		const val LICENSED = 3
		const val PUBLISHING_FINISHED = 4
		const val CANCELLED = 5
		const val ON_HIATUS = 6

		fun create(): SManga {
			return SMangaImpl()
		}
	}
}
