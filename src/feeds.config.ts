import { readFileSync } from "fs";
import { join } from "path";

export interface FeedConfig {
  url: string;
  source: string;
}

interface FeedsJson {
  articlesPerFeed: number;
  feeds: { enabled: boolean; name: string; url: string }[];
}

const feedsJson: FeedsJson = JSON.parse(
  readFileSync(join(__dirname, "..", "feeds.json"), "utf-8")
);

export const FEEDS: FeedConfig[] = feedsJson.feeds
  .filter((f) => f.enabled)
  .map((f) => ({ url: f.url, source: f.name }));

export const ARTICLES_PER_FEED = feedsJson.articlesPerFeed;
