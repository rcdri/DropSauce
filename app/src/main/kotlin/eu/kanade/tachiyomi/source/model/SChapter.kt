@file:Suppress("DEPRECATION", "PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SChapter : Serializable {
	var url: String
	var name: String
	var date_upload: Long

	@Deprecated("Use number instead", replaceWith = ReplaceWith("number"))
	var chapter_number: Float

	/** Chapter number as a string (extensions-lib 1.5+). */
	var number: String?
		get() = chapter_number.takeIf { it >= 0 }?.let { if (it == it.toLong().toFloat()) it.toLong().toString() else it.toString() }
		set(value) { chapter_number = value?.toFloatOrNull() ?: -1f }

	/** Volume number (extensions-lib 1.5+). */
	var volume: String?
		get() = null
		set(_) {}

	@Deprecated("Use scanlators instead", replaceWith = ReplaceWith("scanlators"))
	var scanlator: String?

	/** List of scanlators (extensions-lib 1.5+). Backed by the deprecated [scanlator] field. */
	var scanlators: List<String>
		get() = scanlator?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() } ?: emptyList()
		set(value) { scanlator = value.joinToString(", ").ifEmpty { null } }

	/** Optional display note (extensions-lib 1.5+). */
	var note: String?
		get() = null
		set(_) {}

	/** Custom metadata map for namespaced extension data (extensions-lib 1.5+). */
	var memo: Map<String, String>
		get() = emptyMap()
		set(_) {}

	fun copyFrom(other: SChapter) {
		name = other.name
		url = other.url
		date_upload = other.date_upload
		chapter_number = other.chapter_number
		scanlator = other.scanlator
	}

	companion object {
		fun create(): SChapter {
			return SChapterImpl()
		}
	}
}
