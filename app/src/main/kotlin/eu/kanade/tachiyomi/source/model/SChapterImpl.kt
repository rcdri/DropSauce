@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

class SChapterImpl : SChapter {
	override lateinit var url: String
	override lateinit var name: String
	override var date_upload: Long = 0

	@Deprecated("Use number instead", replaceWith = ReplaceWith("number"))
	override var chapter_number: Float = -1f

	@Deprecated("Use scanlators instead", replaceWith = ReplaceWith("scanlators"))
	override var scanlator: String? = null
}
