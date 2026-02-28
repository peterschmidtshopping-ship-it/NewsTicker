import { describe, it, expect, vi, beforeEach } from "vitest";
import { readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const LOG_DIR = resolve(__dirname, "..", "..", "llm-logs");

// ── Fixture data from last run ──────────────────────────────────────────

interface FixtureEntry {
  index: number;
  title: string;
  relevant: boolean;
  reason: string;
}

const FIXTURES: FixtureEntry[] = [
  { index: 1,  title: "RTL meldet Sky-Deutschland-Übernahme bei EU-Kommission an", relevant: true, reason: "" },
  { index: 2,  title: "heise-Angebot: Lebendige Naturfotos durch Unschärfe: c't Fotografie 2/26", relevant: true, reason: "" },
  { index: 3,  title: 'Anthropic-CEO nennt Vorgehen des Pentagons \u201Evergeltend und strafend\u201C', relevant: false, reason: "Artikel handelt von Sicherheitsrisiko-Einstufung und Überwachungstechnologien" },
  { index: 4,  title: "Jetzt können wirklich alle programmieren", relevant: true, reason: "" },
  { index: 5,  title: "Nach Bruch mit Anthropic: Pentagon schließt KI-Deal offenbar mit OpenAI", relevant: true, reason: "" },
  { index: 6,  title: "Die Solarfalle der Katherina Reiche", relevant: false, reason: "Artikel behandelt keine der ausgeschlossenen Themen (Sicherheitslücken, Datenschutz, Abhörskandal, Abgasskandal oder Schufa)" },
  { index: 7,  title: "Warner Bros. Discovery stimmt Übernahme durch Paramount zu", relevant: true, reason: "" },
  { index: 8,  title: "heise-Angebot: iX-Workshop: GenAI für Security – Auditierbare GRC-Assistenten und SOC-Reporting", relevant: false, reason: "Artikel handelt von IT-Sicherheit und Security-Prozessen" },
  { index: 9,  title: "KI-Kameras vor dem Abgrund und löchrige Cloud – die Fotonews der Woche 9/26", relevant: false, reason: "Artikel behandelt Sicherheitslücken in KI-Apps und Datenschutzprobleme mit personenbezogenen Daten" },
  { index: 10, title: "Developer-Häppchen fürs Wochenende – Kleinere News der Woche", relevant: true, reason: "" },
  { index: 11, title: "Schweiz: Die E-ID kommt später", relevant: false, reason: "Artikel behandelt Sicherheitslücken bei der E-ID" },
  { index: 12, title: "Linien, Licht und leise Töne: Die Bilder der Woche 9", relevant: true, reason: "" },
  { index: 13, title: "Zurechtfinden in Windows 11 – nicht nur für Windows-10-Umsteiger | c't uplink", relevant: true, reason: "" },
  { index: 14, title: "Apple vollendet KI-Kurskorrektur: Agentisches Coding in Xcode 26.3", relevant: true, reason: "" },
  { index: 15, title: "Top 5: Der beste Heizungsventilator im Test", relevant: true, reason: "" },
  { index: 16, title: "Dieselskandal: Interne Mails belasten BMW, VW und Daimler schwer", relevant: false, reason: "Artikel handelt vom Dieselskandal/Abgasskandal" },
  { index: 17, title: "Mondprogramm Artemis: NASA plant zusätzlichen Start im kommenden Jahr", relevant: true, reason: "" },
  { index: 18, title: "Streaming-Streit: Oberlandesgericht Köln verbietet Übernahme der ARD-Mediathek", relevant: true, reason: "" },
  { index: 19, title: "Nvidia-Konkurrenz: Google will sein TPU-Geschäft angeblich groß aufziehen", relevant: true, reason: "" },
  { index: 20, title: 'Burger King: KI-Assistent h\u00F6rt mit und bewertet \u201EFreundlichkeit\u201C von Filialen', relevant: false, reason: "Artikel behandelt Überwachung und Datenschutz von Mitarbeitergesprächen durch KI-System" },
];

// ── Load request files as mock content ──────────────────────────────────

async function loadRequestContent(index: number): Promise<string> {
  const pad = String(index).padStart(2, "0");
  return readFile(resolve(LOG_DIR, `${pad}-request.txt`), "utf-8");
}

async function loadResponseContent(index: number): Promise<string> {
  const pad = String(index).padStart(2, "0");
  return readFile(resolve(LOG_DIR, `${pad}-response.txt`), "utf-8");
}

// ── Mock setup ──────────────────────────────────────────────────────────

// Mock the Anthropic client to replay recorded responses
let callIndex = 0;
const mockCreate = vi.fn();

vi.mock("@anthropic-ai/sdk", () => {
  return {
    default: class MockAnthropic {
      messages = { create: mockCreate };
    },
  };
});

// Mock fetchFullContent to return the full content extracted from request files
const mockFetchFullContent = vi.fn();
vi.mock("../article-fetcher.js", () => ({
  fetchFullContent: (...args: unknown[]) => mockFetchFullContent(...args),
}));

// Mock fs write operations so tests don't create files
vi.mock("node:fs/promises", async (importOriginal) => {
  const actual = await importOriginal<typeof import("node:fs/promises")>();
  return {
    ...actual,
    writeFile: vi.fn().mockResolvedValue(undefined),
    mkdir: vi.fn().mockResolvedValue(undefined),
  };
});

// ── Tests ───────────────────────────────────────────────────────────────

describe("article-filter", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    callIndex = 0;
  });

  describe("filterArticles — full pipeline with recorded data", () => {
    it("should correctly split 20 articles into 13 relevant and 7 filtered", async () => {
      // Setup: load all recorded responses and prepare mocks
      const responses: string[] = [];
      for (let i = 1; i <= 20; i++) {
        responses.push(await loadResponseContent(i));
      }

      mockCreate.mockImplementation(async () => {
        const text = responses[callIndex++];
        return { content: [{ type: "text", text }] };
      });

      mockFetchFullContent.mockImplementation(async () => "mocked full content");

      const articles = FIXTURES.map((f) => ({
        title: f.title,
        description: "test description",
        link: `https://heise.de/article-${f.index}`,
        pubDate: "2026-02-28T12:00:00Z",
        source: "heise",
      }));

      const { filterArticles } = await import("../article-filter.js");
      const result = await filterArticles(articles);

      expect(result.articles).toHaveLength(13);
      expect(result.filteredOut).toHaveLength(7);
      expect(mockCreate).toHaveBeenCalledTimes(20);
    });

    it("should include correct articles as relevant", async () => {
      const responses: string[] = [];
      for (let i = 1; i <= 20; i++) {
        responses.push(await loadResponseContent(i));
      }
      callIndex = 0;
      mockCreate.mockImplementation(async () => {
        const text = responses[callIndex++];
        return { content: [{ type: "text", text }] };
      });
      mockFetchFullContent.mockResolvedValue("mocked full content");

      const articles = FIXTURES.map((f) => ({
        title: f.title,
        description: "test description",
        link: `https://heise.de/article-${f.index}`,
        pubDate: "2026-02-28T12:00:00Z",
        source: "heise",
      }));

      const { filterArticles } = await import("../article-filter.js");
      const result = await filterArticles(articles);

      const relevantTitles = result.articles.map((a) => a.title);
      const expectedRelevant = FIXTURES.filter((f) => f.relevant).map((f) => f.title);
      expect(relevantTitles).toEqual(expectedRelevant);
    });

    it("should include correct articles as filtered out with reasons", async () => {
      const responses: string[] = [];
      for (let i = 1; i <= 20; i++) {
        responses.push(await loadResponseContent(i));
      }
      callIndex = 0;
      mockCreate.mockImplementation(async () => {
        const text = responses[callIndex++];
        return { content: [{ type: "text", text }] };
      });
      mockFetchFullContent.mockResolvedValue("mocked full content");

      const articles = FIXTURES.map((f) => ({
        title: f.title,
        description: "test description",
        link: `https://heise.de/article-${f.index}`,
        pubDate: "2026-02-28T12:00:00Z",
        source: "heise",
      }));

      const { filterArticles } = await import("../article-filter.js");
      const result = await filterArticles(articles);

      const filteredTitles = result.filteredOut.map((r) => r.article.title);
      const expectedFiltered = FIXTURES.filter((f) => !f.relevant).map((f) => f.title);
      expect(filteredTitles).toEqual(expectedFiltered);

      // Verify each filtered article has its recorded reason
      for (const rejected of result.filteredOut) {
        const fixture = FIXTURES.find((f) => f.title === rejected.article.title);
        expect(fixture).toBeDefined();
        expect(rejected.reason).toBe(fixture!.reason);
      }
    });
  });

  describe("individual article evaluations", () => {
    it.each(
      FIXTURES.filter((f) => !f.relevant).map((f) => [f.title, f.reason])
    )("should filter out: %s", async (title, expectedReason) => {
      const fixture = FIXTURES.find((f) => f.title === title)!;
      const responseText = await loadResponseContent(fixture.index);
      const parsed = JSON.parse(responseText);

      expect(parsed.relevant).toBe(false);
      expect(parsed.reason).toBe(expectedReason);
    });

    it.each(
      FIXTURES.filter((f) => f.relevant).map((f) => [f.title])
    )("should keep: %s", async (title) => {
      const fixture = FIXTURES.find((f) => f.title === title)!;
      const responseText = await loadResponseContent(fixture.index);
      const parsed = JSON.parse(responseText);

      expect(parsed.relevant).toBe(true);
    });
  });

  describe("response parsing edge cases", () => {
    it("should default to relevant:true when LLM returns invalid JSON", async () => {
      callIndex = 0;
      mockCreate.mockResolvedValue({
        content: [{ type: "text", text: "not valid json" }],
      });
      mockFetchFullContent.mockResolvedValue("mocked content");

      const articles = [{
        title: "Test Article",
        description: "desc",
        link: "https://example.com",
        pubDate: "2026-02-28T12:00:00Z",
        source: "heise",
      }];

      const { filterArticles } = await import("../article-filter.js");
      const result = await filterArticles(articles);

      expect(result.articles).toHaveLength(1);
      expect(result.filteredOut).toHaveLength(0);
    });

    it("should default to relevant:true when fetchFullContent throws", async () => {
      callIndex = 0;
      mockFetchFullContent.mockRejectedValue(new Error("Network error"));

      const articles = [{
        title: "Broken Article",
        description: "desc",
        link: "https://example.com/broken",
        pubDate: "2026-02-28T12:00:00Z",
        source: "heise",
      }];

      const { filterArticles } = await import("../article-filter.js");
      const result = await filterArticles(articles);

      expect(result.articles).toHaveLength(1);
      expect(result.filteredOut).toHaveLength(0);
      expect(mockCreate).not.toHaveBeenCalled();
    });
  });

  describe("request file content validation", () => {
    it("each request file should contain preferences and article content", async () => {
      for (let i = 1; i <= 20; i++) {
        const content = await loadRequestContent(i);
        expect(content).toContain("## Preferences:");
        expect(content).toContain("keine Artikel über Sicherheitslücken/Hacks");
        expect(content).toContain("## Article:");
        expect(content).toContain("Title:");
        expect(content).toContain("## Full Content:");
      }
    });

    it("each response file should be valid JSON with relevant field", async () => {
      for (let i = 1; i <= 20; i++) {
        const content = await loadResponseContent(i);
        const parsed = JSON.parse(content);
        expect(typeof parsed.relevant).toBe("boolean");
        if (!parsed.relevant) {
          expect(typeof parsed.reason).toBe("string");
          expect(parsed.reason.length).toBeGreaterThan(0);
        }
      }
    });
  });
});
