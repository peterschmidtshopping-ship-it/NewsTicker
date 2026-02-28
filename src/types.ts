export interface Article {
  title: string;
  description: string;
  link: string;
  pubDate: string;
  source: string;
}

export interface RejectedArticle {
  article: Article;
  reason: string;
}

export interface FilteredResponse {
  articles: Article[];
  filteredOut: RejectedArticle[];
  warning?: string;
}

export interface ProgressEvent {
  checked: number;
  total: number;
  title: string;
  relevant: boolean;
}

export type OnProgress = (event: ProgressEvent) => void;
