package org.koitharu.kotatsu.details.ui

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.util.FileSize
import org.koitharu.kotatsu.core.util.ext.mangaSourceExtra
import org.koitharu.kotatsu.details.data.MangaDetails
import org.koitharu.kotatsu.details.ui.model.HistoryInfo
import org.koitharu.kotatsu.list.domain.ReadingProgress
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingInfo
import java.util.Locale

/**
 * Navigation/action hooks for the expressive details screen. The activity owns the [router]
 * and supplies these so the Compose layer stays free of Android navigation plumbing.
 */
class DetailsExpressiveActions(
	val onCoverClick: (Manga) -> Unit,
	val onTitleClick: (String) -> Unit,
	val onSourceClick: (Manga) -> Unit,
	val onLocalClick: (Manga) -> Unit,
	val onFavoriteClick: (Manga) -> Unit,
	val onAuthorClick: (String) -> Unit,
	val onTagClick: (MangaTag) -> Unit,
	val onScrobblingMore: () -> Unit,
	val onRelatedMore: (Manga) -> Unit,
	val onRelatedClick: (MangaListModel) -> Unit,
)

private val HERO_CORNER = 26.dp
private val CARD_CORNER = 24.dp
private val COVER_WIDTH = 142.dp
private val SCREEN_PADDING = 18.dp

@Composable
fun DetailsExpressiveScreen(
	details: MangaDetails?,
	historyInfo: HistoryInfo,
	isLoading: Boolean,
	favouriteCount: Int,
	favouriteLabel: String?,
	scrobblings: List<ScrobblingInfo>,
	related: List<MangaListModel>,
	localSize: Long,
	sourceTitle: String?,
	accent: Color?,
	imageLoader: ImageLoader,
	coverUrl: String?,
	backdropUrl: String?,
	isBackdropEnabled: Boolean,
	topInset: Dp,
	bottomContentPadding: Dp,
	actions: DetailsExpressiveActions,
) {
	val manga = details?.toManga()
	val accentColor = accent ?: MaterialTheme.colorScheme.primary
	val scheme = MaterialTheme.colorScheme

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(scheme.surface),
	) {
		if (isBackdropEnabled && backdropUrl != null) {
			ExpressiveBackdrop(
				url = backdropUrl,
				manga = manga,
				imageLoader = imageLoader,
				surface = scheme.surface,
			)
		}

		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(bottom = bottomContentPadding),
		) {
			// Clear the translucent top app bar / back button.
			Spacer(Modifier.height(topInset + 56.dp))

			if (manga == null) {
				LoadingHero()
			} else {
				HeroSection(
					manga = manga,
					details = details,
					sourceTitle = sourceTitle,
					accent = accentColor,
					imageLoader = imageLoader,
					coverUrl = coverUrl,
					actions = actions,
				)

				Spacer(Modifier.height(14.dp))
				FavouriteButton(
					label = favouriteLabel ?: stringResource(R.string.add_to_favourites),
					isFavourite = favouriteCount > 0,
					accent = accentColor,
					onClick = { actions.onFavoriteClick(manga) },
				)

				Spacer(Modifier.height(16.dp))
				ProgressCard(historyInfo = historyInfo, isLoading = isLoading, accent = accentColor)

				DescriptionCard(description = details.description)

				TagsSection(tags = manga.tags, onTagClick = actions.onTagClick)

				if (scrobblings.isNotEmpty()) {
					ScrobblingSection(
						items = scrobblings,
						imageLoader = imageLoader,
						accent = accentColor,
						onMore = actions.onScrobblingMore,
					)
				}

				if (related.isNotEmpty()) {
					RelatedSection(
						items = related,
						imageLoader = imageLoader,
						accent = accentColor,
						onMore = { actions.onRelatedMore(manga) },
						onItemClick = actions.onRelatedClick,
					)
				}

				if (localSize > 0L) {
					LocalSizeRow(size = localSize, manga = manga, onClick = actions.onLocalClick)
				}

				Spacer(Modifier.height(24.dp))
			}
		}
	}
}

@Composable
private fun ExpressiveBackdrop(
	url: String,
	manga: Manga?,
	imageLoader: ImageLoader,
	surface: Color,
) {
	val ctx = LocalContext.current
	val request = remember(url, manga?.source) {
		ImageRequest.Builder(ctx)
			.data(url)
			.crossfade(true)
			.mangaSourceExtra(manga?.source)
			.build()
	}
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(440.dp),
	) {
		val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			Modifier.blur(36.dp)
		} else {
			Modifier
		}
		AsyncImage(
			model = request,
			imageLoader = imageLoader,
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.fillMaxSize()
				.then(blurMod),
		)
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						0f to surface.copy(alpha = 0.32f),
						0.45f to surface.copy(alpha = 0.55f),
						0.82f to surface.copy(alpha = 0.92f),
						1f to surface,
					),
				),
		)
	}
}

@Composable
private fun HeroSection(
	manga: Manga,
	details: MangaDetails?,
	sourceTitle: String?,
	accent: Color,
	imageLoader: ImageLoader,
	coverUrl: String?,
	actions: DetailsExpressiveActions,
) {
	val ctx = LocalContext.current
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = SCREEN_PADDING),
	) {
		Surface(
			shape = RoundedCornerShape(HERO_CORNER),
			color = MaterialTheme.colorScheme.surfaceVariant,
			tonalElevation = 3.dp,
			shadowElevation = 8.dp,
			modifier = Modifier
				.width(COVER_WIDTH)
				.height(COVER_WIDTH * 1.45f),
		) {
			val coverRequest = remember(coverUrl, manga.source) {
				ImageRequest.Builder(ctx)
					.data(coverUrl)
					.crossfade(true)
					.mangaSourceExtra(manga.source)
					.build()
			}
			AsyncImage(
				model = coverRequest,
				imageLoader = imageLoader,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier
					.fillMaxSize()
					.clickable { actions.onCoverClick(manga) },
			)
		}

		Spacer(Modifier.width(16.dp))

		Column(
			modifier = Modifier
				.weight(1f)
				.heightIn(min = COVER_WIDTH * 1.45f),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(
				text = manga.title,
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 4,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.clickable { actions.onTitleClick(manga.title) },
			)
			val altTitle = manga.altTitles.firstOrNull()?.takeIf { it.isNotBlank() }
			if (altTitle != null) {
				Text(
					text = altTitle,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
			val authors = manga.authors.filter { it.isNotBlank() }
			if (authors.isNotEmpty()) {
				Text(
					text = authors.joinToString(", "),
					style = MaterialTheme.typography.labelLarge,
					color = accent,
					fontWeight = FontWeight.Medium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.clickable { actions.onAuthorClick(authors.first()) },
				)
			}
			StatPills(
				manga = manga,
				details = details,
				sourceTitle = sourceTitle,
				accent = accent,
				onSourceClick = { actions.onSourceClick(manga) },
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatPills(
	manga: Manga,
	details: MangaDetails?,
	sourceTitle: String?,
	accent: Color,
	onSourceClick: () -> Unit,
) {
	val ctx = LocalContext.current
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (manga.hasRating) {
			Pill(
				text = String.format(Locale.ROOT, "%.1f", manga.rating * 5f),
				icon = R.drawable.ic_star_small,
				accent = accent,
				highlighted = true,
			)
		}
		manga.state?.let { state ->
			Pill(text = stringResource(state.titleResId), icon = null, accent = accent)
		}
		if (manga.contentRating == ContentRating.SUGGESTIVE) {
			Pill(text = "16+", icon = null, accent = accent)
		} else if (manga.contentRating == ContentRating.ADULT) {
			Pill(text = "18+", icon = null, accent = accent, highlighted = true)
		}
		val locale = details?.getLocale()
		if (locale != null) {
			Pill(
				text = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
				icon = R.drawable.ic_language,
				accent = accent,
			)
		}
		if (!manga.isLocal) {
			val srcText = sourceTitle?.takeUnless { it.isBlank() } ?: manga.source.getTitle(ctx)
			Pill(
				text = srcText,
				icon = R.drawable.ic_manga_source,
				accent = accent,
				onClick = onSourceClick,
			)
		}
	}
}

@Composable
private fun Pill(
	text: String,
	icon: Int?,
	accent: Color,
	highlighted: Boolean = false,
	onClick: (() -> Unit)? = null,
) {
	val container = if (highlighted) {
		accent.copy(alpha = 0.20f)
	} else {
		MaterialTheme.colorScheme.surfaceContainerHigh
	}
	val content = if (highlighted) accent else MaterialTheme.colorScheme.onSurfaceVariant
	Surface(
		shape = RoundedCornerShape(50),
		color = container,
		modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(5.dp),
		) {
			if (icon != null) {
				Icon(
					painter = painterResource(icon),
					contentDescription = null,
					tint = content,
					modifier = Modifier.size(15.dp),
				)
			}
			Text(
				text = text,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.Medium,
				color = content,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun FavouriteButton(
	label: String,
	isFavourite: Boolean,
	accent: Color,
	onClick: () -> Unit,
) {
	val container = if (isFavourite) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh
	Surface(
		onClick = onClick,
		shape = RoundedCornerShape(20.dp),
		color = container,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = SCREEN_PADDING)
			.height(56.dp),
	) {
		Row(
			modifier = Modifier.fillMaxSize(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.Center,
		) {
			Icon(
				painter = painterResource(if (isFavourite) R.drawable.ic_heart else R.drawable.ic_heart_outline),
				contentDescription = null,
				tint = accent,
				modifier = Modifier.size(22.dp),
			)
			Spacer(Modifier.width(10.dp))
			Text(
				text = label,
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun ProgressCard(historyInfo: HistoryInfo, isLoading: Boolean, accent: Color) {
	val ctx = LocalContext.current
	val res = ctx.resources
	val chaptersText = when {
		isLoading -> stringResource(R.string.loading_)
		historyInfo.currentChapter >= 0 -> withTime(
			stringResource(R.string.chapter_d_of_d, historyInfo.currentChapter + 1, historyInfo.totalChapters),
			historyInfo, res,
		)
		historyInfo.totalChapters == 0 -> stringResource(R.string.no_chapters)
		historyInfo.totalChapters == -1 -> stringResource(R.string.error_occurred)
		else -> withTime(
			pluralStringResource(R.plurals.chapters, historyInfo.totalChapters, historyInfo.totalChapters),
			historyInfo, res,
		)
	}
	val hasHistory = historyInfo.history != null
	val percent = historyInfo.percent.coerceIn(0f, 1f)
	val displayPercent = if (ReadingProgress.isCompleted(historyInfo.percent)) 100 else (percent * 100f).toInt()

	SectionCard {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				painter = painterResource(R.drawable.ic_read),
				contentDescription = null,
				tint = accent,
				modifier = Modifier.size(22.dp),
			)
			Spacer(Modifier.width(12.dp))
			Text(
				text = chaptersText,
				style = MaterialTheme.typography.titleSmall,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
			)
			if (hasHistory && percent > 0f) {
				Text(
					text = stringResource(R.string.percent_string_pattern, displayPercent.toString()),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
					color = accent,
				)
			}
		}
		if (hasHistory && percent > 0f) {
			Spacer(Modifier.height(12.dp))
			val animated by animateFloatAsState(targetValue = percent, label = "progress")
			LinearProgressIndicator(
				progress = { animated },
				color = accent,
				trackColor = accent.copy(alpha = 0.18f),
				strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
				modifier = Modifier
					.fillMaxWidth()
					.height(8.dp)
					.clip(RoundedCornerShape(50)),
			)
		}
	}
}

@Composable
private fun DescriptionCard(description: CharSequence?) {
	val text = description?.toString()?.trim().orEmpty()
	var expanded by rememberSaveable { mutableStateOf(false) }
	SectionCard {
		Text(
			text = stringResource(R.string.description),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Spacer(Modifier.height(8.dp))
		Text(
			text = text.ifEmpty { stringResource(R.string.no_description) },
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = if (expanded) Int.MAX_VALUE else 5,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
				.fillMaxWidth()
				.animateContentSize()
				.clickable(enabled = text.isNotEmpty()) { expanded = !expanded },
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(tags: Set<MangaTag>, onTagClick: (MangaTag) -> Unit) {
	if (tags.isEmpty()) return
	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = SCREEN_PADDING, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		tags.forEach { tag ->
			Surface(
				shape = RoundedCornerShape(14.dp),
				color = MaterialTheme.colorScheme.secondaryContainer,
				onClick = { onTagClick(tag) },
			) {
				Text(
					text = tag.title,
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSecondaryContainer,
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
				)
			}
		}
	}
}

@Composable
private fun ScrobblingSection(
	items: List<ScrobblingInfo>,
	imageLoader: ImageLoader,
	accent: Color,
	onMore: () -> Unit,
) {
	SectionHeader(title = stringResource(R.string.tracking), action = stringResource(R.string.manage), accent = accent, onAction = onMore)
	LazyRow(
		contentPadding = PaddingValues(horizontal = SCREEN_PADDING),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		items(items, key = { it.scrobbler.name + it.targetId }) { info ->
			Surface(
				shape = RoundedCornerShape(18.dp),
				color = MaterialTheme.colorScheme.surfaceContainerHigh,
				onClick = onMore,
				modifier = Modifier.width(220.dp),
			) {
				Row(
					modifier = Modifier.padding(10.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					AsyncImage(
						model = info.coverUrl,
						imageLoader = imageLoader,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.size(48.dp, 64.dp)
							.clip(RoundedCornerShape(10.dp)),
					)
					Spacer(Modifier.width(10.dp))
					Column {
						Text(
							text = info.title,
							style = MaterialTheme.typography.labelLarge,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 2,
							overflow = TextOverflow.Ellipsis,
						)
						info.status?.let { status ->
							Spacer(Modifier.height(4.dp))
							Text(
								text = status.name.lowercase().replaceFirstChar { it.uppercase() },
								style = MaterialTheme.typography.bodySmall,
								color = accent,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun RelatedSection(
	items: List<MangaListModel>,
	imageLoader: ImageLoader,
	accent: Color,
	onMore: () -> Unit,
	onItemClick: (MangaListModel) -> Unit,
) {
	SectionHeader(title = stringResource(R.string.related_manga), action = stringResource(R.string.show_all), accent = accent, onAction = onMore)
	LazyRow(
		contentPadding = PaddingValues(horizontal = SCREEN_PADDING),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		items(items, key = { it.id }) { item ->
			Column(
				modifier = Modifier
					.width(120.dp)
					.clickable { onItemClick(item) },
			) {
				Surface(
					shape = RoundedCornerShape(18.dp),
					color = MaterialTheme.colorScheme.surfaceVariant,
					modifier = Modifier
						.width(120.dp)
						.height(168.dp),
				) {
					AsyncImage(
						model = item.coverUrl,
						imageLoader = imageLoader,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
				Spacer(Modifier.height(6.dp))
				Text(
					text = item.title,
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

@Composable
private fun LocalSizeRow(size: Long, manga: Manga, onClick: (Manga) -> Unit) {
	val ctx = LocalContext.current
	SectionCard(onClick = { onClick(manga) }) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(
				painter = painterResource(R.drawable.ic_storage_checked),
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.size(20.dp),
			)
			Spacer(Modifier.width(12.dp))
			Text(
				text = FileSize.BYTES.format(ctx, size),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
	}
}

@Composable
private fun SectionHeader(title: String, action: String, accent: Color, onAction: () -> Unit) {
	Spacer(Modifier.height(18.dp))
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = SCREEN_PADDING),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)
		Surface(
			shape = RoundedCornerShape(50),
			color = accent.copy(alpha = 0.14f),
			onClick = onAction,
		) {
			Text(
				text = action,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.Medium,
				color = accent,
				modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
			)
		}
	}
	Spacer(Modifier.height(10.dp))
}

@Composable
private fun SectionCard(
	onClick: (() -> Unit)? = null,
	content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
	val base = Modifier
		.fillMaxWidth()
		.padding(horizontal = SCREEN_PADDING, vertical = 7.dp)
	Surface(
		shape = RoundedCornerShape(CARD_CORNER),
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
	) {
		Column(modifier = Modifier.padding(18.dp), content = content)
	}
}

@Composable
private fun LoadingHero() {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(240.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = stringResource(R.string.loading_),
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

private fun withTime(base: String, info: HistoryInfo, res: android.content.res.Resources): String {
	val time = info.estimatedTime?.formatShort(res) ?: return base
	return res.getString(R.string.chapters_time_pattern, base, time)
}
