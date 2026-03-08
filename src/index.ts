import "dotenv/config";
import express from "express";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { writeFile } from "node:fs/promises";
import { fetchArticles } from "./rss-fetcher.js";
import { filterArticles } from "./article-filter.js";
import { fetchArticleHtml } from "./article-fetcher.js";
import { loadReadUrls, markRead } from "./read-store.js";
import type { Article, RejectedArticle, FilteredResponse } from "./types.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT ?? 3000;

if (!process.env.ANTHROPIC_API_KEY) {
  console.error("ERROR: ANTHROPIC_API_KEY is not set in .env file");
  process.exit(1);
}

const app = express();

app.use(express.json());
app.use(express.static(resolve(__dirname, "..", "public")));

app.post("/api/articles/read", async (req, res) => {
  const url = req.body?.url;
  if (!url || typeof url !== "string") {
    res.status(400).json({ error: "Missing url" });
    return;
  }
  try {
    await markRead(url);
    res.json({ ok: true });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Unknown error";
    res.status(500).json({ error: message });
  }
});

app.get("/api/articles", async (_req, res) => {
  try {
    const readUrls = await loadReadUrls();
    const { articles, warnings } = await fetchArticles(readUrls);
    const result = await filterArticles(articles);
    if (warnings.length > 0) {
      result.warning = warnings.join("; ");
    }
    writeResultFile(result).catch((err) =>
      console.error("Failed to write result file:", err)
    );
    res.json(result);
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Unknown error occurred";
    console.error("Error in /api/articles:", message);
    res.status(502).json({ error: message });
  }
});

app.get("/api/articles/stream", async (_req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });

  const send = (event: string, data: unknown) => {
    res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  };

  try {
    const readUrls = await loadReadUrls();
    const { articles, warnings } = await fetchArticles(readUrls);
    send("init", { total: articles.length, warnings: warnings.length > 0 ? warnings : undefined });

    const FIRST_BATCH = 10;

    const result = await filterArticles(articles, {
      onProgress(progress) {
        send("progress", progress);
      },
      batchDoneAfter: FIRST_BATCH,
      onBatchDone(partial) {
        send("batch-done", partial);
      },
    });

    if (warnings.length > 0) {
      result.warning = warnings.join("; ");
    }

    writeResultFile(result).catch((err) =>
      console.error("Failed to write result file:", err)
    );

    send("done", result);
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Unknown error occurred";
    console.error("Error in /api/articles/stream:", message);
    send("error", { error: message });
  }

  res.end();
});

app.get("/api/articles/all", async (_req, res) => {
  try {
    const readUrls = await loadReadUrls();
    const { articles, warnings } = await fetchArticles(readUrls);
    const response: { articles: Article[]; warning?: string } = { articles };
    if (warnings.length > 0) {
      response.warning = warnings.join("; ");
    }
    res.json(response);
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Unknown error occurred";
    console.error("Error in /api/articles/all:", message);
    res.status(502).json({ error: message });
  }
});

app.get("/api/article-content", async (req, res) => {
  const url = req.query.url as string;
  if (!url) {
    res.status(400).json({ error: "Missing url parameter" });
    return;
  }
  try {
    const html = await fetchArticleHtml(url);
    res.json({ html });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Unknown error";
    res.status(502).json({ error: message });
  }
});

const RESULT_FILE = resolve(__dirname, "..", "Artikel-Ergebnis.txt");

function formatArticleList(articles: Article[]): string {
  return articles
    .map((a, i) => `  ${i + 1}) ${a.title}\n     ${a.link}`)
    .join("\n");
}

async function writeResultFile(result: FilteredResponse): Promise<void> {
  const timestamp = new Date().toLocaleString("de-DE");
  let text = `News Ticker — Ergebnis vom ${timestamp}\n`;
  text += "=".repeat(60) + "\n\n";

  text += `Passende Artikel (${result.articles.length}):\n`;
  text += "-".repeat(40) + "\n";
  text += result.articles.length > 0
    ? formatArticleList(result.articles)
    : "  (keine)";
  text += "\n\n";

  text += `Ausgefilterte Artikel (${result.filteredOut.length}):\n`;
  text += "-".repeat(40) + "\n";
  text += result.filteredOut.length > 0
    ? result.filteredOut
        .map((r, i) => `  ${i + 1}) ${r.article.title}\n     ${r.article.link}\n     Grund: ${r.reason}`)
        .join("\n")
    : "  (keine)";
  text += "\n";

  await writeFile(RESULT_FILE, text, "utf-8");
  console.log("Result written to", RESULT_FILE);
}

app.listen(PORT, () => {
  console.log(`NewsTicker running at http://localhost:${PORT}`);
});
