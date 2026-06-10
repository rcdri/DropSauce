package org.koitharu.kotatsu.sync.domain

import java.io.IOException

/** Thrown when the user must (re)authorize Google Drive access interactively. */
class SyncSignInRequiredException(
	message: String = "Google Drive authorization is required",
) : IOException(message)

/** Thrown for Drive REST API errors. */
class SyncApiException(
	val code: Int,
	message: String,
) : IOException(message)
