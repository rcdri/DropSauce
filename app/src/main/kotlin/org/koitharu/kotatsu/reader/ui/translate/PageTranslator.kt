package org.koitharu.kotatsu.reader.ui.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of translating a page: a cache file holding the page image with translations drawn on it. */
data class PageTranslation(
	val imageUri: Uri,
	val blockCount: Int,
)

private class RecognizedBlock(val box: Rect, val text: String)

/**
 * On-device manga page translation. Recognizes text on a page image with ML Kit (Japanese model,
 * which also covers Latin), translates each detected text block into the device language with ML
 * Kit's offline translator, then draws each translation back **onto the page image** in place of the
 * original text. The composited page is written to the cache and returned as a Uri so it can be
 * shown in the full-screen image viewer. Everything runs locally — no network calls or API keys —
 * apart from a one-time language-model download via Google Play Services.
 */
@Singleton
class PageTranslator @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	suspend fun translatePage(uri: Uri): PageTranslation? = withContext(Dispatchers.Default) {
		val bitmap = decodeMutableBitmap(uri) ?: return@withContext null
		val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
		try {
			val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
			val blocks = recognized.textBlocks
				.mapNotNull { block ->
					val box = block.boundingBox ?: return@mapNotNull null
					val text = block.text.trim()
					if (text.isEmpty()) null else RecognizedBlock(box, text)
				}
			if (blocks.isEmpty()) {
				bitmap.recycle()
				return@withContext null
			}
			val translated = translateBlocks(blocks)
			drawTranslations(bitmap, translated)
			val out = PageTranslation(saveToCache(bitmap), translated.size)
			bitmap.recycle()
			out
		} finally {
			recognizer.close()
		}
	}

	private suspend fun translateBlocks(blocks: List<RecognizedBlock>): List<RecognizedBlock> {
		val target = targetLanguage()
		if (target == TranslateLanguage.JAPANESE) {
			return blocks // device language is the source language; nothing to do
		}
		val translator = Translation.getClient(
			TranslatorOptions.Builder()
				.setSourceLanguage(TranslateLanguage.JAPANESE)
				.setTargetLanguage(target)
				.build(),
		)
		return try {
			translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
			blocks.map { RecognizedBlock(it.box, translator.translate(it.text).await()) }
		} finally {
			translator.close()
		}
	}

	private fun drawTranslations(bitmap: Bitmap, blocks: List<RecognizedBlock>) {
		val canvas = Canvas(bitmap)
		val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.WHITE
			style = Paint.Style.FILL
		}
		val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			color = Color.BLACK
			style = Paint.Style.STROKE
			strokeWidth = (bitmap.width * 0.0025f).coerceAtLeast(1f)
		}
		val radius = bitmap.width * 0.01f
		for (block in blocks) {
			if (block.text.isBlank()) continue
			val rectF = RectF(block.box)
			canvas.drawRoundRect(rectF, radius, radius, fill)
			canvas.drawRoundRect(rectF, radius, radius, border)
			drawTextInBox(canvas, block.text, block.box)
		}
	}

	private fun drawTextInBox(canvas: Canvas, text: String, box: Rect) {
		val padding = box.width() * 0.08f
		val maxWidth = (box.width() - padding * 2f).toInt().coerceAtLeast(1)
		val maxHeight = (box.height() - padding * 2f).coerceAtLeast(1f)
		val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
		var textSize = (box.height() * 0.5f).coerceAtLeast(MIN_TEXT_SIZE)
		var layout = buildLayout(text, paint, maxWidth, textSize)
		while (layout.height > maxHeight && textSize > MIN_TEXT_SIZE) {
			textSize = (textSize - 2f).coerceAtLeast(MIN_TEXT_SIZE)
			layout = buildLayout(text, paint, maxWidth, textSize)
		}
		canvas.save()
		val dx = box.left + padding
		val dy = box.top + padding + ((maxHeight - layout.height) / 2f).coerceAtLeast(0f)
		canvas.translate(dx, dy)
		layout.draw(canvas)
		canvas.restore()
	}

	private fun buildLayout(text: String, paint: TextPaint, width: Int, textSize: Float): StaticLayout {
		paint.textSize = textSize
		return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setIncludePad(false)
			.build()
	}

	private fun decodeMutableBitmap(uri: Uri): Bitmap? {
		val resolver = context.contentResolver
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
		var sampleSize = 1
		while (bounds.outWidth / sampleSize > MAX_DIMENSION || bounds.outHeight / sampleSize > MAX_DIMENSION) {
			sampleSize *= 2
		}
		val options = BitmapFactory.Options().apply {
			inSampleSize = sampleSize
			inMutable = true
			inPreferredConfig = Bitmap.Config.ARGB_8888
		}
		return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
	}

	private fun saveToCache(bitmap: Bitmap): Uri {
		val dir = File(context.cacheDir, "translated").apply { mkdirs() }
		// Keep only the most recent translated page around.
		dir.listFiles()?.forEach { it.delete() }
		val file = File(dir, "page_${System.currentTimeMillis()}.jpg")
		FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
		return Uri.fromFile(file)
	}

	private fun targetLanguage(): String {
		return TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH
	}

	private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
		addOnSuccessListener { cont.resume(it) }
		addOnFailureListener { cont.resumeWithException(it) }
		addOnCanceledListener { cont.cancel() }
	}

	private companion object {

		const val MAX_DIMENSION = 2048
		const val MIN_TEXT_SIZE = 12f
	}
}
