import Parser from "rss-parser";
import type { Article } from "./types.js";
import { FEEDS, ARTICLES_PER_FEED, type FeedConfig } from "./feeds.config.js";

const parser = new Parser();

async function fetchFeed(
  feed: FeedConfig,
  readUrls: Set<string>,
): Promise<Article[]> {
  const result = await parser.parseURL(feed.url);

  const articles: Article[] = [];
  for (const item of result.items) {
    if (articles.length >= ARTICLES_PER_FEED) break;
    const link = item.link ?? "";
    if (readUrls.has(link)) continue;
    articles.push({
      title: item.title ?? "",
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
  readUrls: Set<string> = new Set(),
): Promise<Article[]> {
  const results = await Promise.all(
    FEEDS.map((feed) => fetchFeed(feed, readUrls)),
  );
  return interleave(results);
}
