import Parser from "rss-parser";
import type { Article } from "./types.js";

const FEED_URL = "https://www.heise.de/rss/heise.rdf";

const parser = new Parser();

export async function fetchArticles(): Promise<Article[]> {
  const feed = await parser.parseURL(FEED_URL);

  return feed.items.slice(0, 20).map((item) => ({
    title: item.title ?? "",
    description: item.contentSnippet ?? item.content ?? "",
    link: item.link ?? "",
    pubDate: item.pubDate ?? item.isoDate ?? "",
  }));
}
