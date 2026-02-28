export interface FeedConfig {
  url: string;
  source: string;
}

export const FEEDS: FeedConfig[] = [
  { url: "https://www.heise.de/rss/heise.rdf", source: "heise" },
  { url: "https://www.spiegel.de/schlagzeilen/tops/index.rss", source: "spiegel" },
];

export const ARTICLES_PER_FEED = 10;
