import * as cheerio from "cheerio";

const MAX_CONTENT_LENGTH = 10_000;

function getBaseUrl(articleUrl: string): string {
  try {
    const parsed = new URL(articleUrl);
    return `${parsed.protocol}//${parsed.host}`;
  } catch {
    return "";
  }
}

function isHeiseUrl(url: string): boolean {
  return url.includes("heise.de");
}

function loadAndClean(html: string) {
  const $ = cheerio.load(html);
  $("script, style, nav, footer, aside, .ad, .teaser-icon").remove();
  return $;
}

export async function fetchFullContent(url: string): Promise<string> {
  const response = await fetch(url, {
    headers: { "User-Agent": "NewsTicker/1.0" },
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

function extractHeiseHtml($: cheerio.CheerioAPI): string {
  const lead = $(".a-article-header__lead").html() ?? "";
  const body = $(".article-content").html() ?? "";
  const title = $("article h1, article h2").first().text().trim();

  let content = "";
  if (title) {
    content += `<h1>${title}</h1>`;
  }
  if (lead) {
    content += `<p class="lead">${lead}</p>`;
  }
  content += body;

  const $clean = cheerio.load(content);
  $clean("script, style, .a-article-teaser, a-ad, [class*='teaser'], .notice-banner").remove();
  $clean("*").each((_i, el) => {
    const html = $clean(el).html() ?? "";
    if (html.includes("${")) {
      $clean(el).remove();
    }
  });

  return $clean.html() ?? "";
}

function extractGenericHtml($: cheerio.CheerioAPI): string {
  // Try <article> first, fall back to <body>
  let articleEl = $("article");
  if (!articleEl.length) {
    articleEl = $("body");
  }

  // Clean out non-content elements
  articleEl.find("script, style, nav, footer, aside, .ad, [class*='teaser'], .notice-banner").remove();

  const title = articleEl.find("h1, h2").first().text().trim();
  const bodyHtml = articleEl.html() ?? "";

  let content = "";
  if (title) {
    content += `<h1>${title}</h1>`;
  }
  content += bodyHtml;

  const $clean = cheerio.load(content);
  $clean("script, style, svg").remove();

  return $clean.html() ?? "";
}

export async function fetchArticleHtml(url: string): Promise<string> {
  const response = await fetch(url, {
    headers: { "User-Agent": "NewsTicker/1.0" },
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status} fetching ${url}`);
  }

  const html = await response.text();
  const $ = cheerio.load(html);

  const extractedHtml = isHeiseUrl(url) ? extractHeiseHtml($) : extractGenericHtml($);

  // Make images absolute using the article's own base URL
  const baseUrl = getBaseUrl(url);
  const $final = cheerio.load(extractedHtml);

  $final("img").each((_i, el) => {
    const src = $final(el).attr("src");
    if (src && src.startsWith("/")) {
      $final(el).attr("src", baseUrl + src);
    }
  });

  // Remove placeholder SVG images
  $final("img").each((_i, el) => {
    const src = $final(el).attr("src") ?? "";
    if (src.startsWith("data:image/svg")) {
      $final(el).remove();
    }
  });

  return $final.html() ?? "";
}
