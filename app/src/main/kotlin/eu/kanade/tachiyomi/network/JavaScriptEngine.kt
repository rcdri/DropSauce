package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Executes JavaScript in a sandboxed WebView.
 *
 * Return value mirrors Android's [WebView.evaluateJavascript] semantics:
 * strings are JSON-quoted ("\"hello\""), numbers are plain ("42"),
 * null/undefined returns "null".
 *
 * @since extension-lib 1.4
 */
class JavaScriptEngine(private val context: Context) {

	@Suppress("UNCHECKED_CAST")
	suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Main) {
		val webView = WebView(context.applicationContext)
		webView.settings.javaScriptEnabled = true
		try {
			withTimeout(10_000L) {
				// Android requires a loaded page before evaluateJavascript will execute.
				suspendCancellableCoroutine { cont ->
					webView.webViewClient = object : WebViewClient() {
						override fun onPageFinished(view: WebView, url: String) {
							view.webViewClient = WebViewClient()
							if (cont.isActive) cont.resume(Unit)
						}
					}
					webView.loadUrl("about:blank")
				}
				suspendCancellableCoroutine { cont ->
					webView.evaluateJavascript(script) { result ->
						if (cont.isActive) cont.resume(result as T)
					}
				}
			}
		} finally {
			webView.destroy()
		}
	}
}
