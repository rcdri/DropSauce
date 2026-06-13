package org.koitharu.kotatsu.reader.ui.translate

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One recognized text block paired with its translation. */
data class TranslatedBlock(
	val original: String,
	val translated: String,
)

data class PageTranslation(
	val sourceLanguage: String,
	val targetLanguage: String,
	val blocks: List<TranslatedBlock>,
)

/**
 * On-device manga page translation: recognizes text on a page image with ML Kit (Japanese model,
 * which also covers Latin script) and translates each detected block into the device language using
 * ML Kit's offline translator. Everything runs locally — no network calls or API keys — and the
 * translation language model is downloaded on first use via Google Play Services.
 */
@Singleton
class PageTranslator @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	suspend fun translatePage(uri: Uri): PageTranslation = withContext(Dispatchers.Default) {
		val target = targetLanguage()
		val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
		try {
			val image = InputImage.fromFilePath(context, uri)
			val recognized = recognizer.process(image).await()
			val originals = recognized.textBlocks
				.map { it.text.trim() }
				.filter { it.isNotEmpty() }
			if (originals.isEmpty()) {
				return@withContext PageTranslation(TranslateLanguage.JAPANESE, target, emptyList())
			}
			val translator = Translation.getClient(
				TranslatorOptions.Builder()
					.setSourceLanguage(TranslateLanguage.JAPANESE)
					.setTargetLanguage(target)
					.build(),
			)
			try {
				translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
				val blocks = originals.map { original ->
					TranslatedBlock(original = original, translated = translator.translate(original).await())
				}
				PageTranslation(TranslateLanguage.JAPANESE, target, blocks)
			} finally {
				translator.close()
			}
		} finally {
			recognizer.close()
		}
	}

	private fun targetLanguage(): String {
		return TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH
	}

	private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
		addOnSuccessListener { cont.resume(it) }
		addOnFailureListener { cont.resumeWithException(it) }
		addOnCanceledListener { cont.cancel() }
	}
}
