import Parser from "rss-parser";
import type { Article } from "./types.js";
import { FEEDS, ARTICLES_PER_FEED, type FeedConfig } from "./feeds.config.js";

const parser = new Parser();

async function fetchFeed(feed: FeedConfig): Promise<Article[]> {
  const result = await parser.parseURL(feed.url);

  return result.items.slice(0, ARTICLES_PER_FEED).map((item) => ({
    title: item.title ?? "",
    description: item.contentSnippet ?? item.content ?? "",
    link: item.link ?? "",
    pubDate: item.pubDate ?? item.isoDate ?? "",
    source: feed.source,
  }));
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

export async function fetchArticles(): Promise<Article[]> {
  const results = await Promise.all(FEEDS.map(fetchFeed));
  return interleave(results);
}
