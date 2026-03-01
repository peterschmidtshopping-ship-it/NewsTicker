package com.newsticker.data.local

import org.junit.Assert.*
import org.junit.Test

class ArticleFetcherTest {

    // ─── Handelsblatt — duplicate image detection ────────────────────────

    @Test
    fun `extractImageId - same image with different size params detected as duplicate`() {
        val id1 = ArticleFetcher.extractImageId(
            "https://www.handelsblatt.com/images/JKfZ5k9LDqrg-donald-trump.jpg?w=600"
        )
        val id2 = ArticleFetcher.extractImageId(
            "https://www.handelsblatt.com/images/JKfZ5k9LDqrg-donald-trump.jpg?w=1200"
        )
        assertEquals(id1, id2)
        assertTrue(id1.isNotEmpty())
    }

    @Test
    fun `extractImageId - same image with different extension detected as duplicate`() {
        val id1 = ArticleFetcher.extractImageId(
            "https://example.com/images/my-article-hero.jpg"
        )
        val id2 = ArticleFetcher.extractImageId(
            "https://example.com/images/my-article-hero.jpeg"
        )
        assertEquals(id1, id2)
    }

    @Test
    fun `extractImageId - different images are not flagged as duplicate`() {
        val id1 = ArticleFetcher.extractImageId(
            "https://example.com/images/article-one-hero.jpg"
        )
        val id2 = ArticleFetcher.extractImageId(
            "https://example.com/images/article-two-hero.jpg"
        )
        assertNotEquals(id1, id2)
    }

    @Test
    fun `extractImageId - short stem returns empty`() {
        val id = ArticleFetcher.extractImageId("https://example.com/images/ab.jpg")
        assertEquals("", id)
    }

    // ─── ZDF heute — navigation list removal ─────────────────────────────

    @Test
    fun `removeNavigationLists - nested ol with links and buttons removed`() {
        val html = """
            <p>Article text here.</p>
            <ol>
                <li><a href="/page1">Page 1</a></li>
                <li><a href="/page2">Page 2</a></li>
                <li><button>Next</button></li>
            </ol>
            <p>More content.</p>
        """.trimIndent()
        val result = ArticleFetcher.removeNavigationLists(html)
        assertFalse("Navigation <ol> should be removed", result.contains("<ol>"))
        assertTrue("Content paragraphs preserved", result.contains("Article text here."))
        assertTrue("Content paragraphs preserved", result.contains("More content."))
    }

    @Test
    fun `removeNavigationLists - flat ol with all links removed`() {
        val html = """
            <ol>
                <li><a href="/a">Link A</a></li>
                <li><a href="/b">Link B</a></li>
                <li><a href="/c">Link C</a></li>
                <li><a href="/d">Link D</a></li>
            </ol>
        """.trimIndent()
        val result = ArticleFetcher.removeNavigationLists(html)
        assertFalse("All-links list should be removed", result.contains("<ol>"))
    }

    @Test
    fun `removeNavigationLists - content ol with substantial text preserved`() {
        val html = """
            <ol>
                <li>This is a long paragraph of content that describes an important point in the article and should not be removed because it is real content.</li>
                <li>Another substantial paragraph that provides valuable information to the reader and is clearly not navigation.</li>
                <li>A third point that is equally important and contains enough text to distinguish it from a navigation list.</li>
            </ol>
        """.trimIndent()
        val result = ArticleFetcher.removeNavigationLists(html)
        assertTrue("Content list should be preserved", result.contains("<ol>"))
    }

    @Test
    fun `removeNavigationLists - ul with links also removed`() {
        val html = """
            <ul>
                <li><a href="/x">X</a></li>
                <li><a href="/y">Y</a></li>
            </ul>
        """.trimIndent()
        val result = ArticleFetcher.removeNavigationLists(html)
        assertFalse("Navigation <ul> should be removed", result.contains("<ul>"))
    }

    // ─── Tagesschau — duplicate title removal ────────────────────────────

    @Test
    fun `removeDuplicateTitle - exact match heading removed`() {
        val html = """<h1>Trump verhängt neue Zölle gegen Europa</h1><p>Content here.</p>"""
        val result = ArticleFetcher.removeDuplicateTitle(html, "Trump verhängt neue Zölle gegen Europa")
        assertFalse("Exact title heading should be removed", result.contains("<h1>"))
        assertTrue("Content preserved", result.contains("Content here."))
    }

    @Test
    fun `removeDuplicateTitle - partial word overlap ge 60 percent removed`() {
        val title = "Trump verhängt neue Zölle gegen Europa und Asien"
        val html = """<h2>Trump verhängt neue Zölle gegen Europa</h2><p>Details follow.</p>"""
        val result = ArticleFetcher.removeDuplicateTitle(html, title)
        assertFalse("High-overlap heading should be removed", result.contains("<h2>"))
    }

    @Test
    fun `removeDuplicateTitle - unrelated subheading preserved`() {
        val title = "Trump verhängt neue Zölle gegen Europa"
        val html = """<h2>Reaktionen aus Brüssel</h2><p>Content.</p>"""
        val result = ArticleFetcher.removeDuplicateTitle(html, title)
        assertTrue("Unrelated heading should be preserved", result.contains("<h2>"))
        assertTrue(result.contains("Reaktionen aus Brüssel"))
    }

    @Test
    fun `removeDuplicateTitle - short title with fewer than 3 words unchanged`() {
        val html = """<h1>News</h1><p>Content.</p>"""
        val result = ArticleFetcher.removeDuplicateTitle(html, "News")
        assertTrue("Short title should not trigger removal", result.contains("<h1>"))
    }

    // ─── Decoder — content extraction with entry-content div ─────────────

    @Test
    fun `extractGenericContent - article tag with real text preferred`() {
        val html = """
            <html><body>
            <article>
                <p>${"Lorem ipsum dolor sit amet. ".repeat(15)}</p>
            </article>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content extracted", result.contains("Lorem ipsum"))
    }

    @Test
    fun `extractGenericContent - form textarea input elements stripped`() {
        val html = """
            <html><body>
            <article>
                <p>${"Real article content here with enough text. ".repeat(10)}</p>
                <form action="/submit"><textarea>Write comment</textarea><input type="submit"/></form>
            </article>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertFalse("form should be stripped", result.contains("<form"))
        assertFalse("textarea should be stripped", result.contains("<textarea"))
        assertFalse("input should be stripped", result.contains("<input"))
        assertTrue("Real content preserved", result.contains("Real article content"))
    }

    @Test
    fun `extractGenericContent - entry-content div extraction`() {
        val html = """
            <html><body>
            <div class="entry-content">
                <p>${"Decoder article text with lots of real content. ".repeat(10)}</p>
            </div>
            <div class="sidebar">Sidebar junk</div>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("entry-content text extracted", result.contains("Decoder article text"))
    }

    // ─── FAZ — general extraction ────────────────────────────────────────

    @Test
    fun `extractGenericContent - article tag extracted correctly`() {
        val html = """
            <html><body>
            <nav><a href="/">Home</a></nav>
            <article>
                <p>${"Die Bundesregierung hat heute neue Maßnahmen beschlossen. ".repeat(8)}</p>
            </article>
            <footer>Footer content</footer>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("Bundesregierung"))
        assertFalse("Nav should be stripped", result.contains("<nav"))
        assertFalse("Footer should be stripped", result.contains("<footer"))
    }

    @Test
    fun `extractGenericContent - login elements removed`() {
        val html = """
            <html><body>
            <article>
                <p>${"Important news content about the economy today. ".repeat(10)}</p>
                <div class="login-wall">Please log in to continue</div>
            </article>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("Important news content"))
        assertFalse("Login wall should be removed", result.contains("Please log in"))
    }

    // ─── GameStar — article-content div extraction ───────────────────────

    @Test
    fun `extractDivByClass - nested div with article-content class extracted`() {
        val html = """
            <div class="page-wrapper">
                <div class="article-content main-text">
                    <p>GameStar review of the new GPU.</p>
                    <div class="inner-box"><p>Benchmark results.</p></div>
                </div>
                <div class="comments">User comments here.</div>
            </div>
        """.trimIndent()
        val result = ArticleFetcher.extractDivByClass(html, "article-content")
        assertNotNull("Should extract article-content div", result)
        assertTrue("Contains article text", result!!.contains("GameStar review"))
        assertTrue("Contains nested content", result.contains("Benchmark results"))
        assertFalse("Comments excluded", result.contains("User comments"))
    }

    @Test
    fun `extractGenericContent - comment section outside article-content excluded`() {
        val html = """
            <html><body>
            <div class="article-content">
                <p>${"GameStar has tested the new graphics card in detail. ".repeat(8)}</p>
            </div>
            <div class="comment-section">
                <p>User123: Great article!</p>
            </div>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("GameStar has tested"))
        assertFalse("Comment section should be excluded", result.contains("User123"))
    }

    @Test
    fun `extractGenericContent - content-meta and content-label removed`() {
        val html = """
            <html><body>
            <div class="article-content">
                <div class="content-meta">Published: 2024-01-15</div>
                <div class="content-label">Review</div>
                <p>${"The game runs smoothly on modern hardware and provides great visuals. ".repeat(6)}</p>
            </div>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("game runs smoothly"))
        assertFalse("content-meta removed", result.contains("Published: 2024"))
        assertFalse("content-label removed", result.contains(">Review<"))
    }

    @Test
    fun `extractGenericContent - deeply nested comments div removed by id`() {
        val articleText = "GameStar has reviewed the latest expansion pack in great detail. ".repeat(8)
        val html = """
            <html><body>
            <div class="article-content">
                <p>$articleText</p>
                <div id="comments" class="row box box-toggle box-close teaser">
                    <div class="col-xs-12 box-title">
                        <div class="h3"><span>Kommentare</span><span class="count">(22)</span></div>
                    </div>
                    <div class="col-xs-12">
                        <div class="comments-rules-hint media kasten">
                            <div class="h4">Kommentar-Regeln von GameStar</div>
                            Bitte lies unsere Kommentar-Regeln
                        </div>
                    </div>
                    <div class="box-content">
                        <div class="media kasten">
                            <p>Nur angemeldete Benutzer können kommentieren.</p>
                        </div>
                    </div>
                </div>
            </div>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("latest expansion pack"))
        assertFalse("Kommentar-Regeln removed", result.contains("Kommentar-Regeln"))
        assertFalse("Comment prompt removed", result.contains("Nur angemeldete"))
        assertFalse("Comment count removed", result.contains("(22)"))
    }

    @Test
    fun `extractGenericContent - modal dialogs removed`() {
        val articleText = "This is a detailed review of the new hardware release from GameStar. ".repeat(8)
        val html = """
            <html><body>
            <div class="article-content">
                <p>$articleText</p>
            </div>
            <div id="comment-modal-login" class="modal fade">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header"><div class="h2">Nur für registrierte User</div></div>
                        <div class="modal-body"><p>Nur angemeldete Benutzer können kommentieren.</p></div>
                        <div class="modal-footer">
                            <a href="/login/">Ich habe ein Konto</a>
                            <a href="/registrierung/">Kostenlos registrieren</a>
                        </div>
                    </div>
                </div>
            </div>
            <div id="comment-modal-plus-login" class="modal fade">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header"><div class="h2">Weiter mit GameStar Plus</div></div>
                    </div>
                </div>
            </div>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("detailed review"))
        assertFalse("Login modal removed", result.contains("registrierte User"))
        assertFalse("Register link removed", result.contains("Kostenlos registrieren"))
        assertFalse("Plus modal removed", result.contains("Weiter mit GameStar Plus"))
    }

    @Test
    fun `extractGenericContent - recirculation box removed`() {
        val articleText = "The new processor delivers impressive benchmark results across all tests. ".repeat(8)
        val html = """
            <html><body>
            <article>
                <p>$articleText</p>
                <div class="recirculation-box">
                    <div class="title">Mehr zum Thema</div>
                    <div class="contentbox"><a href="/other">Related article</a></div>
                </div>
            </article>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("benchmark results"))
        assertFalse("Recirculation removed", result.contains("Mehr zum Thema"))
    }

    @Test
    fun `extractGenericContent - iframe ads stripped`() {
        val articleText = "Important gaming news that readers want to see in detail here. ".repeat(8)
        val html = """
            <html><body>
            <article>
                <p>$articleText</p>
                <iframe src="https://ads.example.com/widget" width="100%" height="500"></iframe>
            </article>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Article content present", result.contains("gaming news"))
        assertFalse("iframe stripped", result.contains("<iframe"))
    }

    @Test
    fun `extractGenericContent - GameStar video page junk fully removed`() {
        // Simulates the real HTML structure of a GameStar /videos/ page
        // which has NO article-content div, so extraction falls through to body
        val descriptionText = "Die Gaming-Branche steckt in einer tiefen Krise und niemand weiss genau wann sie endet oder wie schlimm es noch werden kann. ".repeat(5)
        val html = """
            <html><body>
            <div id="video-container" class="video-container m-x-auto">
                <div id="video-canvas" class="video-canvas">
                    <div id="player-139766"></div>
                    <div id="player-139766-desc" class="hide sticky-description">
                        <span class="info">Läuft gerade</span>
                        <span class="h3">Der Gaming-Kollaps</span>
                    </div>
                </div>
            </div>
            <div class="dis-flex space-between">
                <p class="info content-view-count">890 Aufrufe</p>
            </div>
            <span class="box-content">
                <span class="description article">
                    <p>$descriptionText</p>
                </span>
            </span>
            <div id="comments" class="row box box-toggle box-close teaser">
                <div class="col-xs-12 box-title">
                    <div class="h3"><span>Kommentare</span><span class="count">(7)</span></div>
                </div>
                <div class="col-xs-12">
                    <div class="comments-rules-hint media kasten">
                        <div class="h4">Kommentar-Regeln von GameStar</div>
                        Bitte lies unsere Kommentar-Regeln
                    </div>
                </div>
                <div class="box-content" data-id="139766">
                    <div class="col-xs-12">
                        <div class="media kasten">
                            <div class="media-body">
                                <p>Nur angemeldete Benutzer können kommentieren und bewerten.</p>
                            </div>
                            <div class="media-bottom">
                                <a href="/login/">Ich habe ein Konto</a>
                                <a href="/registrierung/">Kostenlos registrieren</a>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div id="comment-modal-login" class="modal fade">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header"><div class="h2">Nur für registrierte User</div></div>
                        <div class="modal-body"><p>Nur angemeldete Benutzer können kommentieren.</p></div>
                        <div class="modal-footer">
                            <a href="/login/">Ich habe ein Konto</a>
                            <a href="/registrierung/">Kostenlos registrieren</a>
                        </div>
                    </div>
                </div>
            </div>
            <div id="comment-modal-plus-login" class="modal fade">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header"><div class="h2">Weiter mit GameStar Plus</div></div>
                    </div>
                </div>
            </div>
            <div class="contentteaser row box contentitem-box">
                <div class="col-xs-12 box-title">
                    <div class="h3"><a href="/videos/">Empfohlen</a></div>
                    <div class="box-link box-link-top">
                        <a href="/videos/" title="alle anzeigen">alle anzeigen</a>
                    </div>
                </div>
                <div class="box-content">
                    <div class="media-body">
                        <p class="info">vor 13 Stunden</p>
                        <div class="h4"><a href="/other">Der Gaming-Kollaps</a></div>
                    </div>
                </div>
            </div>
            </body></html>
        """.trimIndent()
        val result = ArticleFetcher.extractGenericContent(html)
        assertTrue("Description text present", result.contains("Gaming-Branche steckt"))
        assertFalse("Video player removed", result.contains("Läuft gerade"))
        assertFalse("View count removed", result.contains("890 Aufrufe"))
        assertFalse("Kommentar-Regeln removed", result.contains("Kommentar-Regeln"))
        assertFalse("Login prompt removed", result.contains("Ich habe ein Konto"))
        assertFalse("Registration removed", result.contains("Kostenlos registrieren"))
        assertFalse("Modal removed", result.contains("registrierte User"))
        assertFalse("Plus modal removed", result.contains("Weiter mit GameStar Plus"))
        assertFalse("Teaser removed", result.contains("alle anzeigen"))
        assertFalse("Timestamp removed", result.contains("vor 13 Stunden"))
    }

    // ─── General / cross-cutting ─────────────────────────────────────────

    @Test
    fun `makeImagesAbsolute - relative URLs converted`() {
        val html = """<img src="/images/photo.jpg" alt="Photo"/><p>Text</p>"""
        val result = ArticleFetcher.makeImagesAbsolute(html, "https://www.example.com")
        assertTrue(
            "Relative src should become absolute",
            result.contains("""src="https://www.example.com/images/photo.jpg"""")
        )
    }

    @Test
    fun `makeImagesAbsolute - absolute URLs unchanged`() {
        val html = """<img src="https://cdn.example.com/photo.jpg" alt=""/><p>Text</p>"""
        val result = ArticleFetcher.makeImagesAbsolute(html, "https://www.example.com")
        assertTrue(
            "Absolute src should remain unchanged",
            result.contains("""src="https://cdn.example.com/photo.jpg"""")
        )
    }

    @Test
    fun `makeImagesAbsolute - data svg images removed`() {
        val html = """<img src="data:image/svg+xml;base64,abc123" alt="icon"/><p>Text</p>"""
        val result = ArticleFetcher.makeImagesAbsolute(html, "https://www.example.com")
        assertFalse("data:image/svg should be removed", result.contains("<img"))
        assertTrue("Text preserved", result.contains("Text"))
    }

    @Test
    fun `makeImagesAbsolute - empty baseUrl returns html unchanged`() {
        val html = """<img src="/images/photo.jpg"/><p>Text</p>"""
        val result = ArticleFetcher.makeImagesAbsolute(html, "")
        assertEquals(html, result)
    }

    @Test
    fun `stripLeadingJunk - junk before first substantial p removed`() {
        val junk = """<div class="breadcrumb">Home > News</div><span>Author</span>"""
        val content = """<p>${"This is a substantial paragraph with enough text to be real content. ".repeat(2)}</p><p>Second paragraph.</p>"""
        val html = junk + content
        val result = ArticleFetcher.stripLeadingJunk(html)
        assertFalse("Breadcrumb junk should be removed", result.contains("breadcrumb"))
        assertFalse("Author junk removed", result.contains("Author"))
        assertTrue("First <p> preserved", result.contains("substantial paragraph"))
    }

    @Test
    fun `stripLeadingJunk - images before first p preserved`() {
        val html = """<figure><img src="hero.jpg"/></figure><div>junk</div><p>${"Content paragraph with enough text to pass the threshold check. ".repeat(2)}</p>"""
        val result = ArticleFetcher.stripLeadingJunk(html)
        assertTrue("Figure/img before <p> should be preserved", result.contains("hero.jpg"))
        assertFalse("Non-media junk removed", result.contains(">junk<"))
        assertTrue("Content preserved", result.contains("Content paragraph"))
    }

    @Test
    fun `stripLeadingJunk - no p tag returns html unchanged`() {
        val html = """<div>Some content without paragraphs</div>"""
        val result = ArticleFetcher.stripLeadingJunk(html)
        assertEquals(html, result)
    }

    @Test
    fun `removeJunkImages - template variable images removed`() {
        val html = """<img src="https://example.com/${'$'}{image}" alt=""/><p>Text</p>"""
        val result = ArticleFetcher.removeJunkImages(html)
        assertFalse("Template var image removed", result.contains("<img"))
        assertTrue("Text preserved", result.contains("Text"))
    }

    @Test
    fun `removeJunkImages - icon images removed`() {
        val html = """<img src="https://example.com/icons/share.svg" alt="share"/><p>Text</p>"""
        val result = ArticleFetcher.removeJunkImages(html)
        assertFalse("Icon image removed", result.contains("<img"))
    }

    @Test
    fun `removeJunkImages - heise plus badge removed`() {
        val html = """<img src="https://heise.de/heise_plus_badge.png" alt="plus"/><p>Text</p>"""
        val result = ArticleFetcher.removeJunkImages(html)
        assertFalse("Heise plus badge removed", result.contains("<img"))
    }

    @Test
    fun `removeJunkImages - normal content image preserved`() {
        val html = """<img src="https://example.com/photos/article-hero.jpg" alt="Hero"/><p>Text</p>"""
        val result = ArticleFetcher.removeJunkImages(html)
        assertTrue("Normal image should be preserved", result.contains("article-hero.jpg"))
    }

    @Test
    fun `removeJunkImages - arrow icon removed`() {
        val html = """<img src="https://example.com/arrow-right.svg" alt=""/><p>Text</p>"""
        val result = ArticleFetcher.removeJunkImages(html)
        assertFalse("Arrow icon removed", result.contains("<img"))
    }

    @Test
    fun `removeJunkImages - tracking pixel 1x1 removed`() {
        val html = """<img src="https://vg05.met.vgwort.de/na/abc123" height="1" width="1" alt=""/><p>Text</p>"""
        val result = ArticleFetcher.removeJunkImages(html)
        assertFalse("1x1 tracking pixel removed", result.contains("<img"))
        assertTrue("Text preserved", result.contains("Text"))
    }
}
