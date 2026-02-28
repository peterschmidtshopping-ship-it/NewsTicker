import { describe, it, expect } from "vitest";
import Anthropic from "@anthropic-ai/sdk";
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import "dotenv/config";

const __dirname = dirname(fileURLToPath(import.meta.url));
const LOG_DIR = resolve(__dirname, "..", "..", "llm-logs");

const SYSTEM_PROMPT =
  'You are a news article filter. Given user preferences and a full article, decide whether the article matches the user\'s interests. Articles that match any exclusion preference should NOT be considered relevant. Respond with ONLY a JSON object: {"relevant": true} if the article is relevant, or {"relevant": false, "reason": "short reason in German"} if not.';

const client = new Anthropic();

async function callLLM(requestFile: string): Promise<unknown> {
  const userPrompt = await readFile(resolve(LOG_DIR, requestFile), "utf-8");

  const message = await client.messages.create({
    model: "claude-sonnet-4-20250514",
    max_tokens: 128,
    system: SYSTEM_PROMPT,
    messages: [{ role: "user", content: userPrompt }],
  });

  const text =
    message.content[0].type === "text" ? message.content[0].text : "";
  return JSON.parse(text);
}

// 10 articles: 5 expected relevant, 5 expected filtered out
const TEST_CASES: { file: string; title: string; expectedRelevant: boolean }[] = [
  // Should be relevant
  { file: "01-request.txt", title: "RTL meldet Sky-Deutschland-Übernahme", expectedRelevant: true },
  { file: "04-request.txt", title: "Jetzt können wirklich alle programmieren", expectedRelevant: true },
  { file: "14-request.txt", title: "Apple vollendet KI-Kurskorrektur: Xcode 26.3", expectedRelevant: true },
  { file: "17-request.txt", title: "Mondprogramm Artemis: NASA", expectedRelevant: true },
  { file: "19-request.txt", title: "Nvidia-Konkurrenz: Google TPU-Geschäft", expectedRelevant: true },
  // Should be filtered out
  { file: "09-request.txt", title: "KI-Kameras: Sicherheitslücken + Datenschutz", expectedRelevant: false },
  { file: "11-request.txt", title: "Schweiz: E-ID Sicherheitslücken", expectedRelevant: false },
  { file: "16-request.txt", title: "Dieselskandal: Interne Mails", expectedRelevant: false },
  { file: "20-request.txt", title: "Burger King: Überwachung + Datenschutz", expectedRelevant: false },
  { file: "08-request.txt", title: "iX-Workshop: GenAI für Security", expectedRelevant: false },
];

describe("article-filter integration (live API)", () => {
  it.each(TEST_CASES)(
    "$title → relevant=$expectedRelevant",
    async ({ file, expectedRelevant }) => {
      const result = await callLLM(file) as Record<string, unknown>;

      // Response must be valid JSON with a boolean "relevant" field
      expect(result).toBeDefined();
      expect(typeof result.relevant).toBe("boolean");

      // Check the filtering decision matches expectation
      expect(result.relevant).toBe(expectedRelevant);

      // Filtered-out articles must include a reason
      if (!result.relevant) {
        expect(typeof result.reason).toBe("string");
        expect((result.reason as string).length).toBeGreaterThan(0);
      }
    },
    30_000 // 30s timeout per test
  );
});
