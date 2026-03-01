package com.newsticker.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ArticleContent {
    /** Successfully extracted and themed HTML content */
    data class Html(val html: String) : ArticleContent()
    /** Extraction failed — UI should load the URL directly in WebView */
    data class LoadUrl(val url: String) : ArticleContent()
}

object ArticleFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val MIN_CONTENT_LENGTH = 200

    suspend fun fetchArticle(url: String, imageUrl: String = "", title: String = ""): ArticleContent = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ArticleContent.LoadUrl(url)
                response.body?.string() ?: return@withContext ArticleContent.LoadUrl(url)
            }

            val baseUrl = getBaseUrl(url)
            var extracted = extractGenericContent(html)
            extracted = removeJunkImages(extracted)
            if (title.isNotEmpty()) extracted = removeDuplicateTitle(extracted, title)

            // Check if we actually got meaningful article text
            val textOnly = extracted.replace(Regex("<[^>]*>"), "").trim()
            if (textOnly.length < MIN_CONTENT_LENGTH) {
                return@withContext ArticleContent.LoadUrl(url)
            }

            val withAbsoluteImages = makeImagesAbsolute(extracted, baseUrl)

            // Prepend RSS feed image only if a variant of it doesn't already appear in the article.
            // Compare by image path stem (e.g. "JKfZ5k9LDqrg...donald-trump") since sites
            // serve the same image at different sizes/formats between RSS and article HTML.
            val imageId = extractImageId(imageUrl)
            val isDuplicate = imageId.isNotEmpty() && withAbsoluteImages.contains(imageId)
            val leadImage = if (imageUrl.isNotEmpty() && !isDuplicate) {
                """<img src="$imageUrl" alt="" style="width:100%;margin-bottom:12px"/>"""
            } else ""

            ArticleContent.Html(wrapInDarkTheme(leadImage + withAbsoluteImages))
        } catch (_: Exception) {
            ArticleContent.LoadUrl(url)
        }
    }

    /** Remove heading elements whose text matches the article title (already shown in the native UI). */
    internal fun removeDuplicateTitle(html: String, title: String): String {
        val titleWords = title.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        if (titleWords.size < 3) return html
        return Regex(
            """<(h[1-6])[^>]*>[\s\S]{0,500}?</\1>""",
            RegexOption.IGNORE_CASE
        ).replace(html) { match ->
            val text = match.value.replace(Regex("<[^>]*>"), "").trim()
            val headingWords = text.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
            val overlap = headingWords.intersect(titleWords)
            // Remove if most heading words appear in the title
            if (headingWords.size >= 2 && overlap.size >= headingWords.size * 0.6) "" else match.value
        }
    }

    /** Extract a stable identifier from an image URL for deduplication.
     *  Returns the filename stem (without extension) so that the same image served at
     *  different sizes or formats (jpg/jpeg/webp/avif) is still recognised as a duplicate. */
    internal fun extractImageId(url: String): String {
        // Take the last path segment, strip query params and extension
        val path = url.substringBefore("?").substringBeforeLast("#")
        val filename = path.substringAfterLast("/")
        val stem = filename.substringBeforeLast(".")
        // Only use it if it's a meaningful name (not just a number or very short hash)
        return if (stem.length >= 4) stem else ""
    }

    private fun getBaseUrl(articleUrl: String): String {
        val match = Regex("^(https?://[^/]+)").find(articleUrl)
        return match?.groupValues?.get(1) ?: ""
    }

    /** Extract inner HTML of the first div whose class contains one of the given patterns,
     *  properly tracking nesting depth to find the matching closing tag. */
    internal fun extractDivByClass(html: String, vararg classPatterns: String): String? {
        for (pattern in classPatterns) {
            val openRegex = Regex("""<div[^>]+class="[^"]*$pattern[^"]*"[^>]*>""", RegexOption.IGNORE_CASE)
            val match = openRegex.find(html) ?: continue
            val contentStart = match.range.last + 1
            var depth = 1
            var i = contentStart
            while (i < html.length && depth > 0) {
                if (html.startsWith("<div", i, ignoreCase = true)) depth++
                else if (html.startsWith("</div>", i, ignoreCase = true)) {
                    depth--
                    if (depth == 0) return html.substring(contentStart, i)
                }
                i++
            }
        }
        return null
    }

    private fun stripTags(html: String, vararg tags: String): String {
        var result = html
        for (tag in tags) {
            result = Regex(
                "<$tag[^>]*>[\\s\\S]*?</$tag>",
                RegexOption.IGNORE_CASE
            ).replace(result, "")
        }
        return result
    }

    private fun textLength(html: String): Int =
        html.replace(Regex("<[^>]*>"), "").trim().length

    internal fun extractGenericContent(html: String): String {
        var cleaned = stripTags(html, "script", "style", "nav", "footer", "aside", "svg", "noscript", "header")

        // Extract candidates from different container tags
        val articleHtml = Regex(
            "<article[^>]*>([\\s\\S]*)</article>",
            RegexOption.IGNORE_CASE
        ).find(cleaned)?.groupValues?.get(1)

        val mainHtml = Regex(
            "<main[^>]*>([\\s\\S]*)</main>",
            RegexOption.IGNORE_CASE
        ).find(cleaned)?.groupValues?.get(1)

        // Look for div with common article-content class names (e.g. GameStar, WordPress)
        val contentDivHtml = extractDivByClass(cleaned,
            "article-content", "entry-content", "post-content", "story-body")

        val bodyHtml = Regex(
            "<body[^>]*>([\\s\\S]*)</body>",
            RegexOption.IGNORE_CASE
        ).find(cleaned)?.groupValues?.get(1)

        // Prefer narrowest container with enough content to avoid picking up
        // comments, sidebars, etc. that inflate the body's text length.
        val content = listOfNotNull(contentDivHtml, articleHtml, mainHtml)
            .firstOrNull { textLength(it) >= MIN_CONTENT_LENGTH }
            ?: listOfNotNull(mainHtml, articleHtml, bodyHtml).maxByOrNull { textLength(it) }
            ?: cleaned

        var result = stripTags(content, "script", "style", "nav", "footer", "aside", "svg", "noscript", "form", "header", "section", "textarea", "button", "iframe")
        // Remove stray form elements (input, select) that survive after form/textarea stripping
        result = Regex("""<(?:input|select)[^>]*/?>""", RegexOption.IGNORE_CASE).replace(result, "")
        // Nesting-aware removal FIRST — must run before removeByClassPattern which uses
        // simple regex that breaks on nested divs and would corrupt the structure.
        result = removeNestedDivs(result, Regex("""<div[^>]+id="comments"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+id="comment-modal[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+id="inactivity-popup"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+id="video-container"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+class="[^"]*\bmodal\b[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+class="[^"]*recirculation[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+class="[^"]*contentteaser[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+class="[^"]*notifications[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeNestedDivs(result, Regex("""<div[^>]+class="[^"]*\bbox-content\b[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        result = removeByClassPattern(result,
            "ad-", "teaser", "banner", "notice-banner", "cookie", "comment",
            "login", "plus-tafel", "plus-teaser", "contentteaser",
            "sidebar", "newsletter", "social", "anzeige", "magazine", "menu",
            "toolbar", "breadcrumb", "topnav", "nav-", "a-navigation",
            "special", "druckansicht", "kommentar", "heise-bot", "push-nach",
            // gamestar specific
            "content-meta", "content-label", "btn-tab", "do-toggle", "do-filter",
            "taglist", "OUTBRAIN", "jad-placeholder",
            "sticky-description", "content-view-count", "video-canvas",
            "ads-row", "cmp-split",
            // decoder specific
            "entry-header", "copy-url", "decoder-ad", "decoder-highlight",
            "icon-share", "icon-comment", "icon-link", "sr-only", "not-prose"
        )
        result = removeById(result,
            "header-login", "div-plus-teaser-paid-content", "socialshare"
        )
        result = removeByDataAttribute(result,
            "breadcrumb", "topic-tags", "content-module", "s-teaser"
        )
        result = removeByAriaLabel(result, "Pfadnavigation")
        result = removeTemplateVars(result)
        result = removeNavigationLists(result)
        result = stripLeadingJunk(result)

        return result
    }

    /** Remove <ul>/<ol> elements that are navigation-style (mostly links/buttons, little text).
     *  Matches innermost lists first (no nested lists inside), then loops to peel outer layers. */
    internal fun removeNavigationLists(html: String): String {
        // Match only lists that do NOT contain nested <ul>/<ol> (innermost first)
        val leafListRegex = Regex(
            """<(ul|ol)\b[^>]*>(?:(?!<(?:ul|ol)\b)[\s\S]){0,10000}?</\1>""",
            RegexOption.IGNORE_CASE
        )
        var result = html
        while (true) {
            val next = leafListRegex.replace(result) { match ->
                val block = match.value
                val linkCount = Regex("""<a\s""", RegexOption.IGNORE_CASE).findAll(block).count()
                val buttonCount = Regex("""<button[\s>]""", RegexOption.IGNORE_CASE).findAll(block).count()
                val interactiveCount = linkCount + buttonCount
                val liCount = Regex("""<li[\s>]""", RegexOption.IGNORE_CASE).findAll(block).count()
                val text = block.replace(Regex("<[^>]*>"), "").trim()
                // Navigation list: mostly links/buttons with little text per item
                if (liCount > 0 && interactiveCount >= liCount && text.length < liCount * 80) {
                    ""
                } else {
                    block
                }
            }
            if (next == result) break
            result = next
        }
        return result
    }

    /** Strip leading non-content junk before the first substantial <p> */
    internal fun stripLeadingJunk(html: String): String {
        // Find the first <p> that has at least 50 chars of text
        val firstP = Regex("""<p[\s>][\s\S]*?</p>""", RegexOption.IGNORE_CASE).find(html) ?: return html
        val pText = firstP.value.replace(Regex("<[^>]*>"), "").trim()
        if (pText.length >= 50) {
            // Keep everything from this <p> onward, but also keep any <figure>/<img> before it
            val beforeP = html.substring(0, firstP.range.first)
            val fromP = html.substring(firstP.range.first)
            // Preserve images/figures from before the first <p>
            val preservedMedia = Regex(
                """<(?:figure|img)[^>]*>[\s\S]*?(?:</figure>|/?>)""",
                RegexOption.IGNORE_CASE
            ).findAll(beforeP).joinToString("") { it.value }
            return preservedMedia + fromP
        }
        return html
    }

    private fun removeByDataAttribute(html: String, vararg testIds: String): String {
        var result = html
        for (id in testIds) {
            result = Regex(
                """<([a-z][a-z0-9]*)[^>]+data-testid="$id"[^>]*>[\s\S]{0,50000}?</\1>""",
                RegexOption.IGNORE_CASE
            ).replace(result, "")
        }
        return result
    }

    private fun removeByAriaLabel(html: String, vararg labels: String): String {
        var result = html
        for (label in labels) {
            result = Regex(
                """<([a-z][a-z0-9]*)[^>]+aria-label="$label"[^>]*>[\s\S]{0,50000}?</\1>""",
                RegexOption.IGNORE_CASE
            ).replace(result, "")
        }
        return result
    }

    /** Remove images that are icons, template vars, plus badges, or placeholders */
    internal fun removeJunkImages(html: String): String {
        return Regex(
            """<img[^>]*>""",
            RegexOption.IGNORE_CASE
        ).replace(html) { match ->
            val tag = match.value
            val src = Regex("""src="([^"]*)"""").find(tag)?.groupValues?.get(1) ?: ""
            when {
                // Template variable images: ${image}
                src.contains("\${") || src.contains("%7B") -> ""
                // Icon/logo SVGs
                src.contains("/icons/") -> ""
                // Heise plus branding
                src.contains("heise_plus") || src.contains("heiseplus") -> ""
                // Arrow/UI icons
                src.contains("arrow-") -> ""
                // Tiny tracking pixels or cloudimg placeholders with template vars
                src.contains("cloudimg.io") && src.contains("%7B") -> ""
                // VG Wort and other 1x1 tracking pixels
                tag.contains("""height="1"""") && tag.contains("""width="1"""") -> ""
                else -> tag
            }
        }
    }

    private fun removeTemplateVars(html: String): String {
        var result = Regex(
            """<[^>]+>[^<]*\$\{[^}]+\}[^<]*</[^>]+>""",
            RegexOption.IGNORE_CASE
        ).replace(html, "")
        result = result.replace(Regex("""\$\{[^}]+\}"""), "")
        return result
    }

    private fun removeByClassPattern(html: String, vararg patterns: String): String {
        var result = html
        for (pattern in patterns) {
            // Non-greedy won't work for nested tags, so use a length-limited greedy match
            result = Regex(
                """<([a-z][a-z0-9]*)[^>]+class="[^"]*$pattern[^"]*"[^>]*>[\s\S]{0,50000}?</\1>""",
                RegexOption.IGNORE_CASE
            ).replace(result, "")
        }
        return result
    }

    private fun removeById(html: String, vararg ids: String): String {
        var result = html
        for (id in ids) {
            result = Regex(
                """<([a-z][a-z0-9]*)[^>]+id="$id"[^>]*>[\s\S]{0,50000}?</\1>""",
                RegexOption.IGNORE_CASE
            ).replace(result, "")
        }
        return result
    }

    /** Remove all div blocks whose opening tag matches [openTagPattern], tracking nesting depth
     *  to find the correct closing tag (unlike simple regex which breaks on nested divs). */
    private fun removeNestedDivs(html: String, openTagPattern: Regex): String {
        var result = html
        while (true) {
            val match = openTagPattern.find(result) ?: break
            val start = match.range.first
            val afterOpen = match.range.last + 1
            var depth = 1
            var i = afterOpen
            var endIndex = -1
            while (i < result.length && depth > 0) {
                if (result.startsWith("<div", i, ignoreCase = true)) depth++
                else if (result.startsWith("</div>", i, ignoreCase = true)) {
                    depth--
                    if (depth == 0) { endIndex = i + 6; break }
                }
                i++
            }
            if (endIndex < 0) break // malformed HTML, stop
            result = result.substring(0, start) + result.substring(endIndex)
        }
        return result
    }

    internal fun makeImagesAbsolute(html: String, baseUrl: String): String {
        if (baseUrl.isEmpty()) return html

        var result = Regex("""src="(/[^"]+)"""").replace(html) { match ->
            """src="${baseUrl}${match.groupValues[1]}""""
        }

        result = Regex(
            """<img[^>]+src="data:image/svg[^"]*"[^>]*/?>""",
            RegexOption.IGNORE_CASE
        ).replace(result, "")

        return result
    }

    private fun wrapInDarkTheme(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
                    html { background: #1A1A2E; }
                    body {
                        background: #1A1A2E;
                        color: #E0E0E0;
                        font-family: -apple-system, sans-serif;
                        font-size: 18px;
                        line-height: 1.7;
                        padding: 0 12px;
                        margin: 0;
                        word-wrap: break-word;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                        border-radius: 8px;
                        margin: 12px 0;
                    }
                    a { color: #E94560; }
                    h1, h2, h3 { color: #FFFFFF; }
                    h1 { font-size: 1.3em; }
                    h2 { font-size: 1.15em; }
                    figure { margin: 8px 0; }
                    figcaption { font-size: 0.85em; color: #999; }
                    p { margin: 10px 0; }
                    .lead { font-size: 1.1em; font-weight: 500; }
                    video, audio, iframe { max-width: 100%; }
                    .bottom-actions {
                        display: flex;
                        gap: 8px;
                        margin: 24px 0 32px;
                    }
                    .bottom-actions button {
                        flex: 1;
                        padding: 12px 16px;
                        border-radius: 24px;
                        font-size: 16px;
                        font-weight: 500;
                        cursor: pointer;
                        border: none;
                    }
                    .btn-browser {
                        background: transparent;
                        border: 1px solid #555 !important;
                        color: #E0E0E0;
                    }
                    .btn-gelesen {
                        background: #2A2A3E;
                        color: #E0E0E0;
                    }
                </style>
            </head>
            <body>
                $content
                <div class="bottom-actions">
                    <button class="btn-browser" onclick="AndroidBridge.onBrowserClick()">Browser</button>
                    <button class="btn-gelesen" onclick="AndroidBridge.onGelesenClick()">Gelesen</button>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
