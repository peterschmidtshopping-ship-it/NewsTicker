package com.newsticker.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/**
 * Renders extracted HTML content via loadDataWithBaseURL.
 * Includes a JS bridge for bottom "Browser" / "Gelesen" buttons.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ArticleWebView(
    html: String,
    baseUrl: String,
    onBrowserClick: () -> Unit,
    onGelesenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastLoadedHtml = remember { arrayOf("") }

    AndroidView(
        factory = { context ->
            createWebView(context, openLinksExternally = true).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(
                    BottomButtonsBridge(onBrowserClick, onGelesenClick),
                    "AndroidBridge"
                )
                lastLoadedHtml[0] = html
                loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (lastLoadedHtml[0] != html) {
                lastLoadedHtml[0] = html
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
}

/**
 * Loads a URL directly in WebView (fallback for bot-protected sites).
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ArticleWebViewUrl(
    url: String,
    modifier: Modifier = Modifier
) {
    val lastLoadedUrl = remember { arrayOf("") }

    AndroidView(
        factory = { context ->
            createWebView(context, openLinksExternally = false).apply {
                settings.javaScriptEnabled = true
                lastLoadedUrl[0] = url
                loadUrl(url)
            }
        },
        update = { webView ->
            if (lastLoadedUrl[0] != url) {
                lastLoadedUrl[0] = url
                webView.loadUrl(url)
            }
        },
        modifier = modifier
    )
}

private class BottomButtonsBridge(
    private val onBrowserClick: () -> Unit,
    private val onGelesenClick: () -> Unit
) {
    @JavascriptInterface
    fun onBrowserClick() {
        onBrowserClick.invoke()
    }

    @JavascriptInterface
    fun onGelesenClick() {
        onGelesenClick.invoke()
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createWebView(
    context: android.content.Context,
    openLinksExternally: Boolean
): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = false
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = false
        settings.useWideViewPort = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)

        // Prevent white flash: match dark theme background (#1A1A2E)
        setBackgroundColor(Color.parseColor("#1A1A2E"))
        // Disable overscroll glow that can flash during pager swipes
        overScrollMode = View.OVER_SCROLL_NEVER
        // Hardware layer for smoother compositing during pager animation
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (openLinksExternally) {
                    request?.url?.let { uri ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    return true
                }
                return false
            }
        }

        // Only block parent (pager) from intercepting when scrolling vertically.
        // Allow horizontal swipes to pass through to the pager.
        var startX = 0f
        var startY = 0f
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (dy > dx && dy > 10) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dx > dy && dx > 10) {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }
}
