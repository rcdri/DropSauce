package org.koitharu.kotatsu.list.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.core.prefs.ProgressIndicatorMode.CHAPTERS_READ

class ReadingProgressTest {

	@Test
	fun `chapter counts follow opened chapter progress`() {
		val progress = ReadingProgress(
			percent = 0.4f,
			totalChapters = 10,
			mode = CHAPTERS_READ,
		)

		assertEquals(4, progress.chapters)
		assertEquals(6, progress.chaptersLeft)
	}

	@Test
	fun `chapter counts are clamped at completion`() {
		val progress = ReadingProgress(
			percent = 1f,
			totalChapters = 10,
			mode = CHAPTERS_READ,
		)

		assertEquals(10, progress.chapters)
		assertEquals(0, progress.chaptersLeft)
	}
}
