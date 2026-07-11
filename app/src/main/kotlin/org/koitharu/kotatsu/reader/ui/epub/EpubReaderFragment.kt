package org.koitharu.kotatsu.reader.ui.epub

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.isNightMode
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.toZipUri
import org.koitharu.kotatsu.databinding.FragmentReaderEpubBinding
import org.koitharu.kotatsu.local.data.input.EpubBook
import org.koitharu.kotatsu.local.data.input.EpubParser
import org.koitharu.kotatsu.local.data.input.LocalMangaParser
import org.koitharu.kotatsu.reader.ui.ReaderState
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderAdapter
import org.koitharu.kotatsu.reader.ui.pager.BaseReaderFragment
import org.koitharu.kotatsu.reader.ui.pager.ReaderPage
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

/**
 * Text reader for EPUB books with scrolling and swipe-paged modes.
 */
@AndroidEntryPoint
class EpubReaderFragment : BaseReaderFragment<FragmentReaderEpubBinding>() {

	@Inject
	lateinit var settings: AppSettings

	private var currentChapterId = 0L
	private var currentHref: String? = null
	private var currentEpubFile: File? = null
	private var book: EpubBook? = null
	private var zipFile: ZipFile? = null
	private var pagesSnapshot: List<ReaderPage> = emptyList()
	private var canGoPrev = false
	private var canGoNext = false
	private var pendingSearchQuery: String? = null
	private var pagedTopInset = 0
	private var pagedBottomInset = 0
	private var scrollTopInset = 0
	private var preloadJob: Job? = null
	@Volatile private var preloadedNext: PreloadedChapter? = null
	private var previousPreloadJob: Job? = null
	@Volatile private var preloadedPrevious: PreloadedChapter? = null
	private val previousChapters = ArrayDeque<PreloadedChapter>()
	private var currentRawHtml = ""

	// current chapter document (href -> injected html), served by shouldInterceptRequest so the
	// WebView navigates to a real https url - loadDataWithBaseURL leaves an empty data: history
	// entry behind that blows up with ERR_INVALID_RESPONSE on renavigation
	@Volatile
	private var mainDocument: Pair<String, String>? = null

	@Volatile
	private var progressPm = 0 // reading position inside the chapter, permille

	@Volatile
	private var pendingPm = 0

	// when scrolling backwards into the previous chapter, land at its end instead of the top
	private var landAtEnd = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentReaderEpubBinding.inflate(inflater, container, false)

	@SuppressLint("SetJavaScriptEnabled")
	override fun onViewBindingCreated(binding: FragmentReaderEpubBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		with(binding.webView) {
			settings.javaScriptEnabled = true
			settings.allowFileAccess = false
			settings.allowContentAccess = false
			settings.defaultTextEncodingName = "UTF-8"
			// keep the view out of the decor's touchables so the reader tap grid keeps working
			isClickable = false
			isLongClickable = false
			overScrollMode = View.OVER_SCROLL_NEVER
			webViewClient = EpubWebViewClient()
			addJavascriptInterface(Bridge(), "EpubBridge")
		}
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_THEME) { epubTheme }
			.observe(viewLifecycleOwner) { applyColors() }
		viewModel.uiState.observe(viewLifecycleOwner) { state ->
			canGoPrev = state != null && state.hasPreviousChapter()
			canGoNext = state != null && state.hasNextChapter()
			viewBinding?.webView?.evaluateJavascript(
				"if(window.__epub){__epub.setNav($canGoPrev,$canGoNext);}",
				null,
			)
		}
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_FONT_SIZE) { epubFontSize }
			.observe(viewLifecycleOwner) { applyTypography() }
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_FONT_FAMILY) { epubFontFamily }
			.observe(viewLifecycleOwner) { applyTypography() }
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_LINE_HEIGHT) { epubLineHeight }
			.observe(viewLifecycleOwner) { applyTypography() }
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_HORIZONTAL_PADDING) { epubHorizontalPadding }
			.observe(viewLifecycleOwner) { applyTypography() }
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_TEXT_ALIGN) { epubTextAlign }
			.observe(viewLifecycleOwner) { applyTypography() }
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_READING_MODE) { epubReadingMode }
			.observe(viewLifecycleOwner) {
				viewBinding?.root?.requestApplyInsets()
				applyTypography()
			}
		this.settings.observeAsFlow(AppSettings.KEY_EPUB_PUBLISHER_STYLE) { isEpubPublisherStyleEnabled }
			.observe(viewLifecycleOwner) { applyTypography() }
	}

	override fun onDestroyView() {
		preloadJob?.cancel()
		previousPreloadJob?.cancel()
		preloadedNext?.zip?.close()
		preloadedPrevious?.zip?.close()
		preloadedNext = null
		preloadedPrevious = null
		previousChapters.forEach { it.zip.close() }
		previousChapters.clear()
		viewBinding?.webView?.destroy()
		zipFile?.close()
		zipFile = null
		currentEpubFile = null
		super.onDestroyView()
	}

	override fun onCreateAdapter(): BaseReaderAdapter<*>? = null

	private fun barHeight(id: Int): Int {
		val bar = activity?.findViewById<View>(id) ?: return 0
		val margin = (bar.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
		return bar.height + margin
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		pagedTopInset = maxOf(pagedTopInset, barHeight(R.id.appbar_top), bars.top)
		pagedBottomInset = maxOf(pagedBottomInset, barHeight(R.id.toolbar_docked), bars.bottom)
		scrollTopInset = maxOf(scrollTopInset, barHeight(R.id.infoBar))
		val initialLeft = if (isPagedMode) bars.left else 0
		val initialTop = if (isPagedMode) pagedTopInset else scrollTopInset
		val initialRight = if (isPagedMode) bars.right else 0
		val initialBottom = if (isPagedMode) pagedBottomInset else 0
		var viewportChanged = v.paddingLeft != initialLeft || v.paddingTop != initialTop ||
			v.paddingRight != initialRight || v.paddingBottom != initialBottom
		viewBinding?.root?.updatePadding(
			left = initialLeft,
			top = initialTop,
			right = initialRight,
			bottom = initialBottom,
		)
		v.post {
			pagedTopInset = maxOf(pagedTopInset, barHeight(R.id.appbar_top))
			pagedBottomInset = maxOf(pagedBottomInset, barHeight(R.id.toolbar_docked))
			scrollTopInset = maxOf(scrollTopInset, barHeight(R.id.infoBar))
			val left = if (isPagedMode) bars.left else 0
			val top = if (isPagedMode) pagedTopInset else scrollTopInset
			val right = if (isPagedMode) bars.right else 0
			val bottom = if (isPagedMode) pagedBottomInset else 0
			viewportChanged = viewportChanged || v.paddingLeft != left || v.paddingTop != top ||
				v.paddingRight != right || v.paddingBottom != bottom
			v.updatePadding(left = left, top = top, right = right, bottom = bottom)
			if (viewportChanged) applyTypography()
		}
		return insets
	}

	override suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?) {
		pagesSnapshot = pages
		preloadNextChapter()
		preloadPreviousChapter()
		val target = when {
			pendingState != null -> pages.find { it.chapterId == pendingState.chapterId }
				?.let { it to pendingState.scroll.coerceIn(0, 1000) }

			else -> pages.find { it.chapterId == currentChapterId }?.let { it to progressPm }
		} ?: pages.firstOrNull()?.let { it to 0 } ?: return
		var (page, pm) = target
		if (landAtEnd) {
			landAtEnd = false
			if (page.chapterId != currentChapterId && pm == 0) {
				pm = 1000
			}
		}
		if (page.chapterId == currentChapterId) {
			if (pendingState != null && pm != progressPm) {
				viewBinding?.webView?.evaluateJavascript("if(window.__epub){__epub.restore($pm);}", null)
			}
			return
		}
		loadChapter(page, pm)
	}

	private suspend fun loadChapter(page: ReaderPage, pm: Int) {
		val uri = page.url.toUri()
		val file = File(uri.schemeSpecificPart)
		val href = uri.fragment.orEmpty()
		val html = withContext(Dispatchers.IO) {
			if (file != currentEpubFile) {
				book = EpubParser.parse(file)
				zipFile?.close()
				zipFile = ZipFile(file)
				currentEpubFile = file
			}
			if (href == LocalMangaParser.TOC_ENTRY) {
				buildTocHtml(book)
			} else {
				EpubParser.readEntryText(file, href) ?: "<p>${getString(R.string.not_found_404)}</p>"
			}
		}
		currentChapterId = page.chapterId
		currentRawHtml = html
		currentHref = href
		progressPm = pm
		pendingPm = pm
		val binding = viewBinding ?: return
		mainDocument = href to injectHtml(html)
		binding.webView.loadUrl("https://$EPUB_HOST/${encodePath(href)}")
		val index = pagesSnapshot.indexOfFirst { it.chapterId == page.chapterId }
		if (index >= 0) {
			viewModel.onCurrentPageChanged(index, index)
		}
		preloadNextChapter()
		preloadPreviousChapter()
	}

	private fun preloadNextChapter() {
		val chapters = viewModel.getMangaOrNull()?.chapters.orEmpty()
		val index = chapters.indexOfFirst { it.id == currentChapterId }
		val next = chapters.getOrNull(index + 1) ?: return
		if (preloadedNext?.chapterId == next.id) {
			attachPreloadedNext()
			return
		}
		if (preloadJob?.isActive == true) return
		preloadJob = viewLifecycleOwner.lifecycleScope.launch {
			val loaded = withContext(Dispatchers.IO) {
				val uri = next.url.toUri()
				val file = File(uri.schemeSpecificPart)
				val href = uri.fragment.orEmpty()
				val html = if (href == LocalMangaParser.TOC_ENTRY) {
					buildTocHtml(EpubParser.parse(file))
				} else {
					EpubParser.readEntryText(file, href)
				} ?: return@withContext null
				PreloadedChapter(next.id, file, href, html, ZipFile(file))
			} ?: return@launch
			preloadedNext?.zip?.close()
			preloadedNext = loaded
			attachPreloadedNext()
		}
	}

	private fun attachPreloadedNext() {
		val loaded = preloadedNext ?: return
		val base = "https://$EPUB_HOST/${encodePath(loaded.href)}"
		viewBinding?.webView?.evaluateJavascript("if(window.__epub){__epub.preload(${org.json.JSONObject.quote(base)});}", null)
	}

	private fun preloadPreviousChapter() {
		val chapters = viewModel.getMangaOrNull()?.chapters.orEmpty()
		val index = chapters.indexOfFirst { it.id == currentChapterId }
		val previous = chapters.getOrNull(index - 1) ?: return
		if (preloadedPrevious?.chapterId == previous.id) {
			attachPreloadedPrevious()
			return
		}
		if (previousPreloadJob?.isActive == true) return
		previousPreloadJob = viewLifecycleOwner.lifecycleScope.launch {
			val loaded = withContext(Dispatchers.IO) {
				val uri = previous.url.toUri()
				val file = File(uri.schemeSpecificPart)
				val href = uri.fragment.orEmpty()
				val html = if (href == LocalMangaParser.TOC_ENTRY) buildTocHtml(EpubParser.parse(file))
				else EpubParser.readEntryText(file, href)
					?: return@withContext null
				PreloadedChapter(previous.id, file, href, html, ZipFile(file))
			} ?: return@launch
			preloadedPrevious?.zip?.close()
			preloadedPrevious = loaded
			attachPreloadedPrevious()
		}
	}

	private fun attachPreloadedPrevious() {
		val loaded = preloadedPrevious ?: return
		val base = "https://$EPUB_HOST/${encodePath(loaded.href)}"
		viewBinding?.webView?.evaluateJavascript("if(window.__epub){__epub.preloadPrevious(${org.json.JSONObject.quote(base)});}", null)
	}

	override fun getCurrentState(): ReaderState? = currentChapterId.takeIf { it != 0L }?.let {
		ReaderState(chapterId = it, page = 0, scroll = progressPm)
	}

	override fun switchPageBy(delta: Int) {
		if (isPagedMode) return // paged EPUBs deliberately require a horizontal swipe
		val webView = viewBinding?.webView ?: return
		if (webView.canScrollVertically(delta)) {
			val d = (webView.height * 0.9).toInt() * delta
			webView.evaluateJavascript(
				"if(window.__epub){__epub.el().scrollBy({top:$d,left:0,behavior:${if (isAnimationEnabled()) "'smooth'" else "'auto'"}});}",
				null,
			)
		} else {
			landAtEnd = delta < 0
			viewModel.switchChapterBy(delta)
		}
	}

	// the bottom slider: in paged mode position is a page index (slider has stops), in scroll
	// mode it is chapter progress 0..1000 (a smooth in-chapter scrollbar)
	override fun switchPageTo(position: Int, smooth: Boolean) {
		if (isPagedMode) {
			viewBinding?.webView?.evaluateJavascript("if(window.__epub){__epub.goto($position);}", null)
		} else {
			val pm = position.coerceIn(0, 1000)
			progressPm = pm
			viewBinding?.webView?.evaluateJavascript("if(window.__epub){__epub.restore($pm);}", null)
		}
	}

	override fun scrollBy(delta: Int, smooth: Boolean): Boolean {
		if (isPagedMode) return false
		val webView = viewBinding?.webView ?: return false
		if (!webView.canScrollVertically(delta)) {
			return false
		}
		webView.evaluateJavascript(
			"if(window.__epub){__epub.el().scrollBy({top:$delta,left:0,behavior:${if (smooth && isAnimationEnabled()) "'smooth'" else "'auto'"}});}",
			null,
		)
		return true
	}

	// zoom is intentionally not supported for text books - adjust the text size instead
	override fun onZoomIn() = Unit

	override fun onZoomOut() = Unit

	fun showBookSearch() {
		val input = TextInputEditText(requireContext()).apply {
			setSingleLine()
		}
		val field = TextInputLayout(requireContext()).apply {
			hint = getString(R.string.epub_search_hint)
			boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
			setStartIconDrawable(R.drawable.ic_search)
			addView(input)
		}
		val container = FrameLayout(requireContext()).apply {
			val margin = (24 * resources.displayMetrics.density).toInt()
			setPadding(margin, 8, margin, 0)
			addView(field, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.epub_search_book)
			.setView(container)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.search) { _, _ -> searchBook(input.text.toString().trim()) }
			.show()
	}

	private fun searchBook(query: String) {
		if (query.isEmpty()) return
		Toast.makeText(requireContext(), R.string.loading_, Toast.LENGTH_SHORT).show()
		viewLifecycleOwner.lifecycleScope.launch {
			val chapters = viewModel.getMangaOrNull()?.chapters.orEmpty()
			val results = withContext(Dispatchers.IO) {
				chapters.asSequence().filterNot { it.url.toUri().fragment == LocalMangaParser.TOC_ENTRY }
					.mapNotNull { chapter ->
						val uri = chapter.url.toUri()
						val text = EpubParser.readEntryText(File(uri.schemeSpecificPart), uri.fragment.orEmpty())
							?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() }
							?: return@mapNotNull null
						val match = text.indexOf(query, ignoreCase = true).takeIf { it >= 0 } ?: return@mapNotNull null
						val start = (match - 45).coerceAtLeast(0)
						val end = (match + query.length + 70).coerceAtMost(text.length)
						SearchResult(chapter.id, chapter.title.orEmpty(), text.substring(start, end).replace(Regex("\\s+"), " ").trim(), match * 1000 / text.length.coerceAtLeast(1))
					}.take(MAX_SEARCH_RESULTS).toList()
			}
			if (results.isEmpty()) {
				Toast.makeText(requireContext(), R.string.epub_no_search_results, Toast.LENGTH_SHORT).show()
				return@launch
			}
			val adapter = object : ArrayAdapter<SearchResult>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, results) {
				override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
					val row = super.getView(position, convertView, parent)
					val result = getItem(position) ?: return row
					row.findViewById<TextView>(android.R.id.text1).apply {
						text = result.title.ifEmpty { getString(R.string.epub_untitled_chapter) }
						gravity = Gravity.START or Gravity.CENTER_VERTICAL
					}
					row.findViewById<TextView>(android.R.id.text2).apply {
						text = result.snippet
						gravity = Gravity.START or Gravity.CENTER_VERTICAL
						maxLines = 2
					}
					return row
				}
			}
			val dialog = MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.search_results)
				.setAdapter(adapter) { _, index ->
					val result = results[index]
					pendingSearchQuery = query
					viewModel.switchChapter(result.chapterId, result.progress)
				}
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			dialog.listView.apply {
				divider = ColorDrawable(requireContext().getThemeColor(materialR.attr.colorOutlineVariant, Color.GRAY))
				dividerHeight = resources.displayMetrics.density.toInt().coerceAtLeast(1)
			}
		}
	}

	private fun applyColors() {
		val binding = viewBinding ?: return
		val bg = resolveBackgroundColor()
		val fg = foregroundFor(bg)
		val accent = requireContext().getThemeColor(appcompatR.attr.colorPrimary, Color.BLUE)
		binding.root.setBackgroundColor(bg)
		binding.webView.setBackgroundColor(bg)
		binding.webView.evaluateJavascript(
			"var s=document.documentElement.style;" +
				"s.setProperty('--ep-bg','${bg.toCssColor()}');" +
				"s.setProperty('--ep-fg','${fg.toCssColor()}');" +
				"s.setProperty('--ep-ac','${accent.toCssColor()}');",
			null,
		)
	}

	// page theme is epub-only (settings.epubTheme), independent from the manga reader background;
	// "system" follows day/night mode, not the reader activity theme (which is always dark)
	private fun resolveBackgroundColor(): Int {
		val dark = when (settings.epubTheme) {
			"light" -> false
			"dark" -> true
			"black" -> return Color.BLACK
			else -> resources.isNightMode
		}
		return if (dark) {
			ContextThemeWrapper(requireContext(), materialR.style.ThemeOverlay_Material3_Dark)
				.getThemeColor(android.R.attr.colorBackground, Color.BLACK)
		} else {
			ContextThemeWrapper(requireContext(), materialR.style.ThemeOverlay_Material3_Light)
				.getThemeColor(android.R.attr.colorBackground, Color.WHITE)
		}
	}

	// the reader activity theme can be dark while the app is in day mode, so pick the text
	// color from the actual background luminance instead of the day/night flag
	private fun foregroundFor(backgroundColor: Int): Int =
		if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) {
			0xFF1B1B1F.toInt()
		} else {
			0xFFE4E4E8.toInt()
		}

	private fun Int.toCssColor(): String = String.format("#%06X", 0xFFFFFF and this)

	private val isPagedMode: Boolean
		get() = settings.epubReadingMode == EPUB_MODE_PAGED

	private fun applyTypography() {
		val webView = viewBinding?.webView ?: return
		val family = settings.epubFontFamily.replace("'", "\\'")
		webView.evaluateJavascript(
			"if(window.__epub){__epub.style('${settings.epubFontSize}%','$family'," +
				"'${settings.epubLineHeight / 100f}',${settings.epubHorizontalPadding}," +
				"'${settings.epubTextAlign}',${settings.isEpubPublisherStyleEnabled},$isPagedMode,$progressPm);}",
			null,
		)
	}

	private fun injectHtml(source: String): String {
		val bgColor = resolveBackgroundColor()
		val bg = bgColor.toCssColor()
		val fg = foregroundFor(bgColor).toCssColor()
		val accent = requireContext().getThemeColor(appcompatR.attr.colorPrimary, Color.BLUE).toCssColor()
		val head = """
			<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
			<style>
			:root{--ep-bg:$bg;--ep-fg:$fg;--ep-ac:$accent;--ep-fs:${settings.epubFontSize}%;
			--ep-font:${settings.epubFontFamily};--ep-lh:${settings.epubLineHeight / 100f};
			--ep-pad:${settings.epubHorizontalPadding}px;--ep-align:${settings.epubTextAlign};}
			html{background:var(--ep-bg) !important;}
			html:not(.ep-publisher) body{background:var(--ep-bg) !important;color:var(--ep-fg) !important;
			font-size:var(--ep-fs) !important;line-height:var(--ep-lh) !important;
			font-family:var(--ep-font) !important;text-align:var(--ep-align) !important;}
			body{margin:0 !important;padding:20px var(--ep-pad) !important;box-sizing:border-box;word-wrap:break-word;}
			html:not(.ep-publisher) body *{color:var(--ep-fg) !important;background-color:transparent !important;
			font-family:var(--ep-font) !important;line-height:var(--ep-lh) !important;}
			html:not(.ep-publisher) p,html:not(.ep-publisher) div,html:not(.ep-publisher) section,
			html:not(.ep-publisher) article,html:not(.ep-publisher) li{text-align:var(--ep-align) !important;
			font-size:inherit !important;}
			html:not(.ep-publisher) span{font-size:inherit !important;}
			html:not(.ep-publisher) h1,html:not(.ep-publisher) h2,html:not(.ep-publisher) h3,
			html:not(.ep-publisher) h4,html:not(.ep-publisher) h5,html:not(.ep-publisher) h6{text-align:left !important;}
			html:not(.ep-publisher) a,html:not(.ep-publisher) a *{color:var(--ep-ac) !important;}
			html.ep-paged{height:100%;overflow:hidden;touch-action:none;}
			html.ep-paged body{height:100%;overflow:visible;
			column-width:calc(100vw - (var(--ep-pad) * 2));
			column-gap:calc(var(--ep-pad) * 2);column-fill:auto;
			width:auto !important;max-width:none !important;will-change:transform;}
			.__epub_chapter_boundary{height:0;margin:20px 0;border-top:1px solid var(--ep-fg);opacity:.38;clear:both;}
			html.ep-paged .__epub_chapter_boundary{break-before:column;margin:0;border:0;opacity:0;}
			img,svg,image,video{max-width:100% !important;height:auto !important;}
			</style>
		""".trimIndent()
		val script = """
			<script>
			(function(){
			var nav={prev:false,next:false},paged=$isPagedMode;
			var E={
			 cur:0,pc:1,basePage:0,baseScroll:0,leaving:false,nextMarker:null,currentMarker:null,previousMarkers:[],preloadFailed:false,
			 el:function(){return document.scrollingElement||document.documentElement;},
			 stepW:function(){return document.documentElement.clientWidth||window.innerWidth;},
			 // page count depends only on text configs + viewport, so measure it ONCE per layout
			 // (after fonts settle / on config or size change) and cache it - never mid-read
			 totalPages:function(){return Math.max(1,Math.round(document.body.scrollWidth/this.stepW()));},
			 measure:function(){this.pc=Math.max(1,this.totalPages()-this.basePage);
			  if(this.cur>this.basePage+this.pc-1)this.cur=this.basePage+this.pc-1;return this.pc;},
			 pageCount:function(){return this.pc;},
			 preload:function(base){if(this.nextMarker)return;this.preloadFailed=false;
			  fetch('https://$EPUB_HOST$NEXT_DOCUMENT_PATH',{cache:'no-store'}).then(function(r){return r.text();}).then(function(raw){
			   var currentPages=E.measure();var d=new DOMParser().parseFromString(raw,'text/html');
			   d.querySelectorAll('[src],[href],[poster]').forEach(function(el){['src','href','poster'].forEach(function(a){var v=el.getAttribute(a);if(v&&!v.startsWith('#')){try{el.setAttribute(a,new URL(v,base).href);}catch(x){}}});});
			   var marker=document.createElement('div');marker.className='__epub_chapter_boundary';
			   document.body.appendChild(marker);while(d.body.firstChild)document.body.appendChild(d.body.firstChild);
			   E.nextMarker=marker;E.pc=currentPages;
			  }).catch(function(){E.preloadFailed=true;});},
			 preloadPrevious:function(base){if(this.currentMarker)return;
			  fetch('https://$EPUB_HOST$PREVIOUS_DOCUMENT_PATH',{cache:'no-store'}).then(function(r){return r.text();}).then(function(raw){
			   var beforePages=E.totalPages(),beforeScroll=E.el().scrollTop,first=document.body.firstChild;
			   var d=new DOMParser().parseFromString(raw,'text/html');
			   d.querySelectorAll('[src],[href],[poster]').forEach(function(el){['src','href','poster'].forEach(function(a){var v=el.getAttribute(a);if(v&&!v.startsWith('#')){try{el.setAttribute(a,new URL(v,base).href);}catch(x){}}});});
			   var frag=document.createDocumentFragment();while(d.body.firstChild)frag.appendChild(d.body.firstChild);
			   var marker=document.createElement('div');marker.className='__epub_chapter_boundary';frag.appendChild(marker);document.body.insertBefore(frag,first);
			   E.currentMarker=marker;
			   if(paged){var added=E.totalPages()-beforePages;E.basePage+=added;E.cur+=added;E.apply(false);}
			   else{E.baseScroll=marker.offsetTop;E.el().scrollTop=beforeScroll+E.baseScroll;}
			  });},
			 commitNext:function(){var marker=this.nextMarker;if(!marker||!EpubBridge.onSeamlessNext())return false;
			  if(this.currentMarker)this.previousMarkers.push(this.currentMarker);this.currentMarker=marker;
			  if(paged){this.basePage=this.cur;this.nextMarker=null;this.measure();}
			  else{this.baseScroll=marker.offsetTop;this.nextMarker=null;}
			  this.leaving=false;this.report();return true;},
			 commitPrevious:function(){var marker=this.currentMarker;if(!marker||!EpubBridge.onSeamlessPrevious())return false;
			  this.nextMarker=marker;this.currentMarker=this.previousMarkers.length?this.previousMarkers.pop():null;
			  this.baseScroll=this.currentMarker?this.currentMarker.offsetTop:0;this.leaving=false;this.report();return true;},
			 apply:function(animate){var b=document.body.style;
			  b.transition=animate?'transform .25s cubic-bezier(.25,.1,.25,1)':'none';
			  b.transform='translateX(-'+(this.cur*this.stepW())+'px)';},
			 report:function(){var pm,localPage=0;
			  if(paged){localPage=this.cur-this.basePage;pm=this.pc<=1?1000:Math.min(1000,Math.round(localPage*1000/(this.pc-1)));}
			  else{var se=this.el(),end=this.nextMarker?this.nextMarker.offsetTop:se.scrollHeight;var m=Math.max(0,end-this.baseScroll-window.innerHeight),p=Math.max(0,se.scrollTop-this.baseScroll);pm=m<=0?1000:Math.min(1000,Math.round(p*1000/m));}
			  EpubBridge.onProgress(pm,paged?localPage:0,paged?this.pc:0);},
			 restore:function(pm){
			  if(paged){this.cur=this.basePage+Math.max(0,Math.min(this.pc-1,Math.round(pm/1000*(this.pc-1))));this.apply(false);}
			  else{var se=this.el(),end=this.nextMarker?this.nextMarker.offsetTop:se.scrollHeight;se.scrollTop=this.baseScroll+pm/1000*Math.max(0,end-this.baseScroll-window.innerHeight);}
			  this.report();},
			 goto:function(p){if(!paged)return;
			  this.cur=this.basePage+Math.max(0,Math.min(this.pc-1,p));this.apply(true);this.report();},
			 page:function(d){if(!paged)return;var n=this.cur+d;
			  if(n<this.basePage){if(nav.prev)EpubBridge.onEdgeSwipe(-1);return;}
			  if(n>=this.basePage+this.pc){if(nav.next&&this.nextMarker){this.cur=n;this.apply(true);this.leaving=true;setTimeout(function(){E.commitNext();},250);}else if(nav.next&&this.preloadFailed)EpubBridge.onEdgeSwipe(1);else this.apply(true);return;}
			  this.cur=n;this.apply(true);this.report();},
			 style:function(fs,font,lh,pad,align,publisher,isPaged,pm){var s=document.documentElement.style;
			  s.setProperty('--ep-fs',fs);s.setProperty('--ep-font',font);s.setProperty('--ep-lh',lh);
			  s.setProperty('--ep-pad',pad+'px');s.setProperty('--ep-align',align);
			  paged=isPaged;document.documentElement.classList.toggle('ep-publisher',publisher);
			  document.documentElement.classList.toggle('ep-paged',paged);
			  if(!paged){document.body.style.transform='none';document.body.style.transition='none';}
			  requestAnimationFrame(function(){E.measure();E.restore(pm);});},
			 setNav:function(p,n){nav.prev=p;nav.next=n;}
			};
			document.documentElement.classList.toggle('ep-publisher',${settings.isEpubPublisherStyleEnabled});
			document.documentElement.classList.toggle('ep-paged',paged);
			window.__epub=E;
			var rt;window.addEventListener('resize',function(){clearTimeout(rt);rt=setTimeout(function(){if(paged){E.measure();E.apply(false);E.report();}},120);});
			var reporting=false;window.addEventListener('scroll',function(){if(paged)return;if(!reporting){reporting=true;requestAnimationFrame(function(){reporting=false;var center=E.el().scrollTop+window.innerHeight/2;if(E.nextMarker&&center>=E.nextMarker.offsetTop)E.commitNext();else if(E.currentMarker&&center<E.currentMarker.offsetTop)E.commitPrevious();else E.report();});}},{passive:true});
			// keep scrolling past the chapter edge -> seamlessly continue into the next/prev chapter
			function atBottom(){var se=E.el();return se.scrollTop+window.innerHeight>=se.scrollHeight-2;}
			function atTop(){return E.el().scrollTop<=2;}
			var startX=null,startY=null,fired=false,multi=false,dragging=false,dragDx=0;
			document.addEventListener('touchstart',function(e){
			 if(e.touches.length>1){multi=true;startX=null;startY=null;dragging=false;return;}
			 startX=e.touches[0].clientX;startY=e.touches[0].clientY;fired=false;multi=false;dragging=false;dragDx=0;},{passive:true});
			document.addEventListener('touchmove',function(e){
			 if(e.touches.length>1){multi=true;startX=null;startY=null;dragging=false;return;}
			 if(startY===null||fired||multi)return;
			 var dx=e.touches[0].clientX-startX,dy=e.touches[0].clientY-startY;
			 if(paged){
			  // follow the finger, snap on release - a half-recognized swipe can't strand the page
			  if(!dragging&&Math.abs(dx)>10&&Math.abs(dx)>Math.abs(dy)){dragging=true;}
			  if(dragging){dragDx=dx;
			   var t=E.pageCount(),off=dx;
			   // only resist at the true book edge - if an adjacent chapter exists, follow the finger fully so it flows over
			   if((E.cur<=E.basePage&&dx>0&&!nav.prev)||(E.cur>=E.basePage+t-1&&dx<0&&!nav.next))off=dx/3;
			   var b=document.body.style;b.transition='none';
			   b.transform='translateX('+(-(E.cur*E.stepW()-off))+'px)';}
			  return;
			 }
			 if(dy<-8&&atBottom()&&nav.next&&E.preloadFailed){fired=true;EpubBridge.onEdgeSwipe(1);}
			 else if(dy>8&&atTop()&&nav.prev){fired=true;EpubBridge.onEdgeSwipe(-1);}
			},{passive:true});
			document.addEventListener('touchend',function(e){
			 if(multi){if(e.touches.length===0)multi=false;startX=null;startY=null;dragging=false;return;}
			 if(startX===null)return;
			 if(paged&&dragging){
			  var w=E.stepW(),t=E.pageCount();
			  if(dragDx<=-w*0.2){
			   if(E.cur<E.basePage+t-1){E.cur++;E.apply(true);E.report();}
			   else if(nav.next&&E.nextMarker){E.cur++;E.apply(true);E.leaving=true;setTimeout(function(){E.commitNext();},250);}else if(nav.next&&E.preloadFailed){EpubBridge.onEdgeSwipe(1);}else{E.apply(true);}
			  }else if(dragDx>=w*0.2){
			   if(E.cur>E.basePage){E.cur--;E.apply(true);E.report();}
			   else if(nav.prev){EpubBridge.onEdgeSwipe(-1);}else{E.apply(true);}
			  }else{E.apply(true);}
			 }
			 startX=null;startY=null;dragging=false;dragDx=0;
			},{passive:true});
			// measure page count once, after fonts have loaded so column widths are final
			function boot(){if(!E.nextMarker)E.measure();EpubBridge.onReady();}
			window.addEventListener('load',function(){
			 if(document.fonts&&document.fonts.ready){document.fonts.ready.then(function(){requestAnimationFrame(boot);});}
			 else{requestAnimationFrame(boot);}
			});
			})();
			</script>
		""".trimIndent()
		val headClose = source.indexOf("</head>", ignoreCase = true)
		val sb = StringBuilder(source.length + head.length + script.length)
		if (headClose >= 0) {
			sb.append(source, 0, headClose).append(head).append(source, headClose, source.length)
		} else {
			sb.append(head).append(source)
		}
		val scriptAnchor = sb.lastIndexOf("</body>")
		if (scriptAnchor >= 0) {
			sb.insert(scriptAnchor, script)
		} else {
			sb.append(script)
		}
		return sb.toString()
	}

	private fun buildTocHtml(book: EpubBook?): String {
		val sb = StringBuilder("<html><head><title></title></head><body><h2>")
		sb.append(LocalMangaParser.TOC_TITLE.escapeHtml()).append("</h2>")
		book?.toc?.forEach { item ->
			sb.append("<p style=\"margin:0.4em 0 0.4em ")
				.append(item.level * 1.2f)
				.append("em;text-align:left;\"><a href=\"https://")
				.append(EPUB_HOST)
				.append('/')
				.append(encodePath(item.href))
				.append("\">")
				.append(item.title.escapeHtml())
				.append("</a></p>")
		}
		sb.append("</body></html>")
		return sb.toString()
	}

	private fun String.escapeHtml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

	private fun encodePath(path: String): String = android.net.Uri.encode(path, "/")

	private inner class Bridge {

		@JavascriptInterface
		fun onSeamlessNext(): Boolean {
			val next = preloadedNext ?: return false
			preloadedNext = null
			val currentZip = zipFile ?: return false
			previousChapters.addLast(PreloadedChapter(currentChapterId, currentEpubFile ?: return false, currentHref.orEmpty(), currentRawHtml, currentZip))
			zipFile = next.zip
			currentEpubFile = next.file
			currentHref = next.href
			currentChapterId = next.chapterId
			currentRawHtml = next.html
			progressPm = 0
			pendingPm = 0
			mainDocument = null
			view?.post {
				viewModel.onEpubChapterChanged(next.chapterId)
				preloadNextChapter()
			}
			return true
		}

		@JavascriptInterface
		fun onSeamlessPrevious(): Boolean {
			val previous = previousChapters.removeLastOrNull() ?: preloadedPrevious ?: return false
			if (preloadedPrevious?.chapterId == previous.chapterId) preloadedPrevious = null
			val currentZip = zipFile ?: return false
			preloadJob?.cancel()
			preloadedNext?.zip?.close()
			preloadedNext = PreloadedChapter(currentChapterId, currentEpubFile ?: return false, currentHref.orEmpty(), currentRawHtml, currentZip)
			zipFile = previous.zip
			currentEpubFile = previous.file
			currentHref = previous.href
			currentChapterId = previous.chapterId
			currentRawHtml = previous.html
			progressPm = 1000
			pendingPm = 1000
			mainDocument = null
			view?.post {
				viewModel.onEpubChapterChanged(previous.chapterId, 1000)
				preloadPreviousChapter()
			}
			return true
		}

		@JavascriptInterface
		fun onProgress(pm: Int, page: Int, pageCount: Int) {
			progressPm = pm.coerceIn(0, 1000)
			view?.post {
				viewModel.onEpubProgressChanged(progressPm, page, pageCount)
			}
		}

		@JavascriptInterface
		fun onReady() {
			view?.post {
				viewBinding?.webView?.evaluateJavascript(
					"if(window.__epub){__epub.setNav($canGoPrev,$canGoNext);__epub.restore($pendingPm);}",
					null,
				)
				attachPreloadedNext()
				attachPreloadedPrevious()
				pendingSearchQuery?.let { query ->
					pendingSearchQuery = null
					viewBinding?.webView?.evaluateJavascript("window.find(${org.json.JSONObject.quote(query)});", null)
				}
			}
		}

		@JavascriptInterface
		fun onEdgeSwipe(delta: Int) {
			view?.post {
				landAtEnd = delta < 0
				viewModel.switchChapterBy(delta)
			}
		}

	}

	private inner class EpubWebViewClient : WebViewClient() {

			override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
			val url = request.url
			if (url.host != EPUB_HOST) {
				// books are local: block any external resource
				return WebResourceResponse("text/plain", "utf-8", null)
			}
			if (url.path == NEXT_DOCUMENT_PATH) {
				val html = preloadedNext?.html ?: return WebResourceResponse("text/plain", "utf-8", null)
				return WebResourceResponse("text/html", "utf-8", html.byteInputStream())
			}
			if (url.path == PREVIOUS_DOCUMENT_PATH) {
				val html = preloadedPrevious?.html ?: return WebResourceResponse("text/plain", "utf-8", null)
				return WebResourceResponse("text/html", "utf-8", html.byteInputStream())
			}
			val entryName = url.path.orEmpty().removePrefix("/")
			mainDocument?.let { (href, html) ->
				if (entryName == href) {
					return WebResourceResponse("text/html", "utf-8", html.byteInputStream())
				}
			}
			val currentZip = zipFile
			val currentEntry = currentZip?.getEntry(entryName)
			if (currentZip != null && currentEntry != null) {
				return WebResourceResponse(guessMimeType(entryName), null, currentZip.getInputStream(currentEntry))
			}
			val nextZip = preloadedNext?.zip
			val nextEntry = nextZip?.getEntry(entryName)
			if (nextZip != null && nextEntry != null) return WebResourceResponse(guessMimeType(entryName), null, nextZip.getInputStream(nextEntry))
			val previousZip = preloadedPrevious?.zip ?: return null
			val previousEntry = previousZip.getEntry(entryName) ?: return null
			return WebResourceResponse(guessMimeType(entryName), null, previousZip.getInputStream(previousEntry))
		}

		override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
			val url = request.url
			if (url.host != EPUB_HOST) {
				return true // swallow external links
			}
			val entryName = url.path.orEmpty().removePrefix("/")
			if (entryName == currentHref) {
				return false // in-page anchor
			}
			val targetUrl = currentEpubFile?.toZipUri(entryName)?.toString()
			val chapter = viewModel.getMangaOrNull()?.chapters?.find { it.url == targetUrl }
			if (chapter != null) {
				viewModel.switchChapter(chapter.id, 0)
			}
			return true
		}
	}

	private companion object {

		const val EPUB_HOST = "epub.book"
		const val EPUB_MODE_PAGED = "paged"
		const val NEXT_DOCUMENT_PATH = "/__epub_next__"
		const val PREVIOUS_DOCUMENT_PATH = "/__epub_previous__"
		const val MAX_SEARCH_RESULTS = 100
		fun guessMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
			"html", "htm", "xhtml", "xml" -> "text/html"
			"css" -> "text/css"
			"js" -> "application/javascript"
			"jpg", "jpeg" -> "image/jpeg"
			"png" -> "image/png"
			"gif" -> "image/gif"
			"webp" -> "image/webp"
			"svg" -> "image/svg+xml"
			"ttf" -> "font/ttf"
			"otf" -> "font/otf"
			"woff" -> "font/woff"
			"woff2" -> "font/woff2"
			else -> "application/octet-stream"
		}
	}

	private data class SearchResult(val chapterId: Long, val title: String, val snippet: String, val progress: Int)
	private data class PreloadedChapter(val chapterId: Long, val file: File, val href: String, val html: String, val zip: ZipFile)
}
