import Anthropic from "@anthropic-ai/sdk";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { fetchFullContent } from "./article-fetcher.js";
import type { Article, RejectedArticle, FilteredResponse, OnProgress } from "./types.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PREFERENCES_PATH = resolve(__dirname, "..", "Artikel-Preferenzen.txt");

const client = new Anthropic();
const MAX_CONCURRENT = 5;
const LOG_DIR = resolve(__dirname, "..", "llm-logs");

const SYSTEM_PROMPT =
  'You are a news article filter. Given user preferences and a full article, decide whether the article matches the user\'s interests. Articles that match any exclusion preference should NOT be considered relevant. Respond with ONLY a JSON object: {"relevant": true} if the article is relevant, or {"relevant": false, "reason": "short reason in German"} if not.';

async function evaluateArticle(
  index: number,
  article: Article,
  fullContent: string,
  preferences: string
): Promise<{ relevant: boolean; reason: string }> {
  const userPrompt = `## Preferences:\n${preferences}\n\n## Article:\nFeed: ${article.source}\nTitle: ${article.title}\nDescription: ${article.description}\n\n## Full Content:\n${fullContent}`;

  const pad = String(index + 1).padStart(2, "0");
  await writeFile(resolve(LOG_DIR, `${pad}-request.txt`), userPrompt, "utf-8");

  const message = await client.messages.create({
    model: "claude-sonnet-4-20250514",
    max_tokens: 128,
    system: SYSTEM_PROMPT,
    messages: [{ role: "user", content: userPrompt }],
  });

  const text =
    message.content[0].type === "text" ? message.content[0].text : "";

  await writeFile(resolve(LOG_DIR, `${pad}-response.txt`), text, "utf-8");

  try {
    const result = JSON.parse(text);
    return {
      relevant: result.relevant === true,
      reason: result.reason ?? "",
    };
  } catch {
    console.warn(`Could not parse LLM response for "${article.title}":`, text);
    return { relevant: true, reason: "" };
  }
}

async function processArticle(
  index: number,
  article: Article,
  preferences: string
): Promise<{ article: Article; relevant: boolean; reason: string }> {
  try {
    const fullContent = await fetchFullContent(article.link);
    const { relevant, reason } = await evaluateArticle(index, article, fullContent, preferences);
    console.log(`  ${relevant ? "+" : "-"} ${article.title}${reason ? " (" + reason + ")" : ""}`);
    return { article, relevant, reason };
  } catch (error) {
    console.warn(
      `Error processing "${article.title}":`,
      error instanceof Error ? error.message : error
    );
    return { article, relevant: true, reason: "" };
  }
}

interface FilterOptions {
  onProgress?: OnProgress;
  /** Send partial results after this many articles have been checked */
  batchDoneAfter?: number;
  onBatchDone?: (partial: FilteredResponse) => void;
}

function buildResponse(
  results: { article: Article; relevant: boolean; reason: string }[]
): FilteredResponse {
  const matched = results.filter((r) => r.relevant).map((r) => r.article);
  const filteredOut: RejectedArticle[] = results
    .filter((r) => !r.relevant)
    .map((r) => ({ article: r.article, reason: r.reason }));
  return { articles: matched, filteredOut };
}

export async function filterArticles(
  articles: Article[],
  onProgressOrOpts?: OnProgress | FilterOptions
): Promise<FilteredResponse> {
  const opts: FilterOptions =
    typeof onProgressOrOpts === "function"
      ? { onProgress: onProgressOrOpts }
      : onProgressOrOpts ?? {};

  const preferences = await readFile(PREFERENCES_PATH, "utf-8");

  console.log(`Filtering ${articles.length} articles (max ${MAX_CONCURRENT} concurrent)...`);

  await mkdir(LOG_DIR, { recursive: true });

  // Process in batches to limit concurrency
  const results: { article: Article; relevant: boolean; reason: string }[] = [];
  let checked = 0;
  let batchSent = false;

  for (let i = 0; i < articles.length; i += MAX_CONCURRENT) {
    const batch = articles.slice(i, i + MAX_CONCURRENT);
    const batchResults = await Promise.all(
      batch.map(async (article, j) => {
        const result = await processArticle(i + j, article, preferences);
        checked++;
        opts.onProgress?.({
          checked,
          total: articles.length,
          title: result.article.title,
          relevant: result.relevant,
        });
        return result;
      })
    );
    results.push(...batchResults);

    // Send partial results after batchDoneAfter articles
    if (
      !batchSent &&
      opts.batchDoneAfter &&
      opts.onBatchDone &&
      checked >= opts.batchDoneAfter
    ) {
      batchSent = true;
      opts.onBatchDone(buildResponse(results));
    }
  }

  const response = buildResponse(results);
  console.log(`Done: ${response.articles.length} relevant, ${response.filteredOut.length} filtered out`);

  return response;
}
