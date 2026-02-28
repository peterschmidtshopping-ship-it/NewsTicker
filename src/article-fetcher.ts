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
  const $ = loadAndClean(html);

  // Make images absolute
  $("img").each((_i, el) => {
    const src = $(el).attr("src");
    if (src && src.startsWith("/")) {
      $(el).attr("src", "https://www.heise.de" + src);
    }
  });

  let content = $("article").html();
  if (!content?.trim()) {
    content = $("body").html() ?? "";
  }

  return content;
}
