package org.koitharu.kotatsu.list.ui.adapter

import androidx.core.view.isVisible
import com.google.android.material.carousel.MaskableFrameLayout
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.list.AdapterDelegateClickListenerAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.setTooltipCompat
import org.koitharu.kotatsu.databinding.ItemMangaCarouselBinding
import org.koitharu.kotatsu.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.MangaGridModel
import org.koitharu.kotatsu.list.ui.model.MangaListModel

/**
 * Material 3 Expressive carousel variant of [mangaGridItemAD]. The item root is a
 * [MaskableFrameLayout] so a CarouselLayoutManager can mask/unmask it on scroll. The title fades
 * out as the item is masked down to a sliver so peeking items stay clean.
 */
fun mangaCarouselItemAD(
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<MangaGridModel, ListModel, ItemMangaCarouselBinding>(
	{ inflater, parent -> ItemMangaCarouselBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	(itemView as? MaskableFrameLayout)?.setOnMaskChangedListener { maskRect ->
		val w = itemView.width.toFloat()
		val ratio = if (w > 0f) maskRect.width() / w else 1f
		binding.textViewTitle.alpha = ((ratio - 0.35f) / 0.4f).coerceIn(0f, 1f)
	}

	bind { payloads ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.textViewTitle.isVisible = !item.isTitleHidden
		binding.progressView.setProgress(item.progress, PAYLOAD_PROGRESS_CHANGED in payloads)
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
