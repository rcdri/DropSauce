package org.koitharu.kotatsu.history.ui.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.ProgressIndicatorMode.NONE
import org.koitharu.kotatsu.core.prefs.ProgressIndicatorMode.PERCENT_READ
import org.koitharu.kotatsu.list.domain.ReadingProgress

/**
 * A small frosted "pill" badge that shows the read percentage (e.g. "29%") over a cover.
 * It auto-sizes to its text, so layouts should give it wrap_content bounds.
 */
class ReadingProgressView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	@AttrRes defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

	private val hPadding = dp(10f)
	private val vPadding = dp(5f)
	private val textSizeNormal = sp(12.5f)
	private val textSizeSmall = sp(10.5f)

	private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = SCRIM_COLOR
		style = Paint.Style.FILL
	}
	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		typeface = Typeface.DEFAULT_BOLD
		textSize = textSizeNormal
	}
	private val rectF = RectF()
	private val percentPattern = context.getString(R.string.percent_string_pattern)

	private var text: String = ""

	var progress: ReadingProgress? = null
		set(value) {
			field = value
			val newText = when (value?.mode) {
				null, NONE -> ""
				PERCENT_READ -> if (value.percent in 0f..1f) {
					percentPattern.format(ReadingProgress.percentToString(value.percent))
				} else {
					""
				}
			}
			if (newText != text) {
				text = newText
				requestLayout()
				invalidate()
			}
		}

	init {
		if (isInEditMode) {
			progress = ReadingProgress(0.29f, 20, PERCENT_READ)
		}
	}

	fun setProgress(percent: Float, @Suppress("UNUSED_PARAMETER") animate: Boolean) {
		progress = ReadingProgress(percent, 1, PERCENT_READ)
	}

	fun setProgress(value: ReadingProgress?, @Suppress("UNUSED_PARAMETER") animate: Boolean) {
		progress = value
	}

	/** Picks a smaller text size for compact (small) grid cells. */
	fun setSmall(small: Boolean) {
		val size = if (small) textSizeSmall else textSizeNormal
		if (textPaint.textSize != size) {
			textPaint.textSize = size
			requestLayout()
			invalidate()
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (text.isEmpty()) {
			setMeasuredDimension(0, 0)
			return
		}
		val fm = textPaint.fontMetrics
		val w = (textPaint.measureText(text) + hPadding * 2f).toInt()
		val h = (fm.descent - fm.ascent + vPadding * 2f).toInt()
		setMeasuredDimension(
			resolveSize(w, widthMeasureSpec),
			resolveSize(h, heightMeasureSpec),
		)
	}

	override fun onDraw(canvas: Canvas) {
		if (text.isEmpty()) {
			return
		}
		rectF.set(0f, 0f, width.toFloat(), height.toFloat())
		val radius = height / 2f
		canvas.drawRoundRect(rectF, radius, radius, bgPaint)
		val fm = textPaint.fontMetrics
		val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
		canvas.drawText(text, width / 2f, baseline, textPaint)
	}

	private fun dp(value: Float) = TypedValue.applyDimension(
		TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
	)

	private fun sp(value: Float) = TypedValue.applyDimension(
		TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics,
	)

	companion object {

		// Translucent dark "frosted" scrim — keeps the white text legible over any cover.
		private const val SCRIM_COLOR = 0x99000000.toInt()
	}
}
