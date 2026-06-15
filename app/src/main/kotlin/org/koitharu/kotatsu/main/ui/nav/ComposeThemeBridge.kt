package org.koitharu.kotatsu.main.ui.nav

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

/**
 * Builds a Material3 [ColorScheme] by resolving the host theme's color attributes
 * (?attr/colorPrimary etc.). Uses the host Context's theme so day/night and dynamic-color
 * variants are picked up without re-implementing the resolution logic in Compose.
 */
fun composeColorSchemeFromTheme(context: Context, isDark: Boolean): ColorScheme {
	fun c(attr: Int, fallback: Color): Color {
		val tv = TypedValue()
		val resolved = context.theme.resolveAttribute(attr, tv, true)
		if (!resolved) return fallback
		val argb = if (tv.resourceId != 0) {
			ResourcesCompat.getColor(context.resources, tv.resourceId, context.theme)
		} else {
			tv.data
		}
		return Color(argb)
	}

	val base = if (isDark) darkColorScheme() else lightColorScheme()
	return base.copy(
		primary = c(appcompatR.attr.colorPrimary, base.primary),
		onPrimary = c(materialR.attr.colorOnPrimary, base.onPrimary),
		primaryContainer = c(materialR.attr.colorPrimaryContainer, base.primaryContainer),
		onPrimaryContainer = c(materialR.attr.colorOnPrimaryContainer, base.onPrimaryContainer),
		secondary = c(materialR.attr.colorSecondary, base.secondary),
		onSecondary = c(materialR.attr.colorOnSecondary, base.onSecondary),
		tertiary = c(materialR.attr.colorTertiary, base.tertiary),
		onTertiary = c(materialR.attr.colorOnTertiary, base.onTertiary),
		surface = c(materialR.attr.colorSurface, base.surface),
		onSurface = c(materialR.attr.colorOnSurface, base.onSurface),
		surfaceContainer = c(materialR.attr.colorSurfaceContainer, base.surfaceContainer),
		surfaceContainerHigh = c(materialR.attr.colorSurfaceContainerHigh, base.surfaceContainerHigh),
		surfaceContainerHighest = c(materialR.attr.colorSurfaceContainerHighest, base.surfaceContainerHighest),
		onSurfaceVariant = c(materialR.attr.colorOnSurfaceVariant, base.onSurfaceVariant),
		outline = c(materialR.attr.colorOutline, base.outline),
		outlineVariant = c(materialR.attr.colorOutlineVariant, base.outlineVariant),
	)
}

/**
 * Builds a full Material 3 [ColorScheme] from a single seed colour using the Material colour-utilities
 * tonal palettes — the same machinery Android's dynamic colour uses. Every role (accents, containers,
 * surfaces and background) is derived from [seed], so a whole screen can be recoloured from e.g. a manga
 * cover instead of the app theme. [isDark] selects the light/dark tone mapping.
 */
@SuppressLint("RestrictedApi")
fun composeColorSchemeFromSeed(seed: Int, isDark: Boolean): ColorScheme {
	val scheme: DynamicScheme = SchemeTonalSpot(Hct.fromInt(seed), isDark, 0.0)
	val mdc = MaterialDynamicColors()
	fun DynamicColor.color() = Color(getArgb(scheme))
	val base = if (isDark) darkColorScheme() else lightColorScheme()
	return base.copy(
		primary = mdc.primary().color(),
		onPrimary = mdc.onPrimary().color(),
		primaryContainer = mdc.primaryContainer().color(),
		onPrimaryContainer = mdc.onPrimaryContainer().color(),
		inversePrimary = mdc.inversePrimary().color(),
		secondary = mdc.secondary().color(),
		onSecondary = mdc.onSecondary().color(),
		secondaryContainer = mdc.secondaryContainer().color(),
		onSecondaryContainer = mdc.onSecondaryContainer().color(),
		tertiary = mdc.tertiary().color(),
		onTertiary = mdc.onTertiary().color(),
		tertiaryContainer = mdc.tertiaryContainer().color(),
		onTertiaryContainer = mdc.onTertiaryContainer().color(),
		background = mdc.background().color(),
		onBackground = mdc.onBackground().color(),
		surface = mdc.surface().color(),
		onSurface = mdc.onSurface().color(),
		surfaceVariant = mdc.surfaceVariant().color(),
		onSurfaceVariant = mdc.onSurfaceVariant().color(),
		surfaceTint = mdc.surfaceTint().color(),
		inverseSurface = mdc.inverseSurface().color(),
		inverseOnSurface = mdc.inverseOnSurface().color(),
		error = mdc.error().color(),
		onError = mdc.onError().color(),
		errorContainer = mdc.errorContainer().color(),
		onErrorContainer = mdc.onErrorContainer().color(),
		outline = mdc.outline().color(),
		outlineVariant = mdc.outlineVariant().color(),
		scrim = mdc.scrim().color(),
		surfaceBright = mdc.surfaceBright().color(),
		surfaceDim = mdc.surfaceDim().color(),
		surfaceContainer = mdc.surfaceContainer().color(),
		surfaceContainerHigh = mdc.surfaceContainerHigh().color(),
		surfaceContainerHighest = mdc.surfaceContainerHighest().color(),
		surfaceContainerLow = mdc.surfaceContainerLow().color(),
		surfaceContainerLowest = mdc.surfaceContainerLowest().color(),
	)
}
