import * as cheerio from "cheerio";

const MAX_CONTENT_LENGTH = 10_000;

function loadAndClean(html: string) {
  const $ = cheerio.load(html);
  $("script, style, nav, footer, aside, .ad, .teaser-icon").remove();
  return $;
}

export async function fetchFullContent(url: string): Promise<string> {
  const response = await fetch(url, {
    headers: { "User-Agent": "HeiseNewsTicker/1.0" },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status} fetching ${url}`);
  }

  const html = await response.text();
  const $ = loadAndClean(html);

  let text = $("article").text();
  if (!text.trim()) {
    text = $("body").text();
  }

  text = text.replace(/\s+/g, " ").trim();
  return text.slice(0, MAX_CONTENT_LENGTH);
}

export async function fetchArticleHtml(url: string): Promise<string> {
  const response = await fetch(url, {
    headers: { "User-Agent": "HeiseNewsTicker/1.0" },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status} fetching ${url}`);
  }

  const html = await response.text();
  const $ = cheerio.load(html);

  // Extract the header lead text
  const lead = $(".a-article-header__lead").html() ?? "";

  // Extract the main article content
  const body = $(".article-content").html() ?? "";

  // Extract article title
  const title = $("article h1, article h2").first().text().trim();

  // Build clean HTML
  let content = "";
  if (title) {
    content += `<h1>${title}</h1>`;
  }
  if (lead) {
    content += `<p class="lead">${lead}</p>`;
  }
  content += body;

  // Clean up: remove teaser elements, template vars, ads
  const $clean = cheerio.load(content);
  $clean("script, style, .a-article-teaser, a-ad, [class*='teaser'], .notice-banner").remove();
  // Remove elements with unresolved template variables
  $clean("*").each((_i, el) => {
    const html = $clean(el).html() ?? "";
    if (html.includes("${")) {
      $clean(el).remove();
    }
  });

  // Make images absolute
  $clean("img").each((_i, el) => {
    const src = $clean(el).attr("src");
    if (src && src.startsWith("/")) {
      $clean(el).attr("src", "https://www.heise.de" + src);
    }
  });

  // Remove placeholder SVG images
  $clean("img").each((_i, el) => {
    const src = $clean(el).attr("src") ?? "";
    if (src.startsWith("data:image/svg")) {
      $clean(el).remove();
    }
  });

  return $clean.html() ?? "";
}
