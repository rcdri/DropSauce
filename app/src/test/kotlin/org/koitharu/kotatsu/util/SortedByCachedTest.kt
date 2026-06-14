package org.koitharu.kotatsu.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.core.util.ext.sortedByCached

class SortedByCachedTest {

	@Test
	fun matchesSortedBy() {
		val input = listOf("banana", "apple", "cherry", "date", "fig", "elderberry")
		val expected = input.sortedBy { it.length }
		val actual = input.sortedByCached { it.length }
		assertEquals(expected, actual)
	}

	@Test
	fun preservesStableOrderForEqualKeys() {
		// Items with equal keys must keep their original relative order, like sortedBy.
		val input = listOf("bb", "aa", "cc", "dd")
		val expected = input.sortedBy { it.length }
		val actual = input.sortedByCached { it.length }
		assertEquals(expected, actual)
	}

	@Test
	fun emptyInput() {
		assertEquals(emptyList<Int>(), emptyList<Int>().sortedByCached { it })
	}

	@Test
	fun evaluatesSelectorExactlyOncePerElement() {
		// The whole point of the optimization: the (potentially expensive) selector must run
		// exactly N times, not the O(N log N) times that sortedBy/compareBy would trigger.
		val input = (1..16).shuffled()
		var invocations = 0
		input.sortedByCached {
			invocations++
			it
		}
		assertEquals(input.size, invocations)
	}
}
