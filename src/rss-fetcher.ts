import Parser from "rss-parser";
import type { Article } from "./types.js";
import { FEEDS, ARTICLES_PER_FEED, type FeedConfig } from "./feeds.config.js";
import { normalizeTitle, normalizeUrl, type ReadHistory } from "./read-store.js";

const parser = new Parser();

let feedWarnings: string[] = [];

async function fetchFeed(
  feed: FeedConfig,
): Promise<Article[]> {
  let result;
  try {
    result = await parser.parseURL(feed.url);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.warn(`Failed to fetch feed "${feed.source}": ${message}`);
    feedWarnings.push(`${feed.source}: ${message}`);
    return [];
  }

  const articles: Article[] = [];
  for (const item of result.items) {
    if (articles.length >= ARTICLES_PER_FEED) break;
    const link = item.link ?? "";
    const title = item.title ?? "";
    articles.push({
      title,
      description: item.contentSnippet ?? item.content ?? "",
      link,
      pubDate: item.pubDate ?? item.isoDate ?? "",
      source: feed.source,
    });
  }
  return articles;
}

function interleave(arrays: Article[][]): Article[] {
  const result: Article[] = [];
  const maxLen = Math.max(...arrays.map((a) => a.length));

  for (let i = 0; i < maxLen; i++) {
    for (const arr of arrays) {
      if (i < arr.length) {
        result.push(arr[i]);
      }
    }
  }

  return result;
}

export async function fetchArticles(
  readHistory: ReadHistory = { urls: new Set(), titles: new Set() },
): Promise<{ articles: Article[]; warnings: string[] }> {
  feedWarnings = [];
  const results = await Promise.all(
    FEEDS.map((feed) => fetchFeed(feed)),
  );
  return { articles: filterUnreadAndUnique(interleave(results), readHistory), warnings: feedWarnings };
}

function filterUnreadAndUnique(articles: Article[], readHistory: ReadHistory): Article[] {
  const seenUrls = new Set<string>();
  const seenTitles = new Set<string>();

  return articles.filter((article) => {
    const normalizedUrl = normalizeUrl(article.link);
    const normalizedTitle = normalizeTitle(article.title);
    const duplicate =
      (normalizedUrl && (readHistory.urls.has(normalizedUrl) || seenUrls.has(normalizedUrl))) ||
      (normalizedTitle && (readHistory.titles.has(normalizedTitle) || seenTitles.has(normalizedTitle)));

    if (!duplicate) {
      if (normalizedUrl) seenUrls.add(normalizedUrl);
      if (normalizedTitle) seenTitles.add(normalizedTitle);
    }

    return !duplicate;
  });
}
