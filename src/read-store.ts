import { readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const STORE_PATH = resolve(__dirname, "..", "gelesen-artikel.json");
const MAX_ENTRIES = 5000;

interface ReadArticleEntry {
  url: string;
  title: string;
}

export interface ReadHistory {
  urls: Set<string>;
  titles: Set<string>;
}

export function normalizeUrl(url: string): string {
  const trimmed = url.trim();
  if (!trimmed) return "";

  try {
    const parsed = new URL(trimmed);
    parsed.hash = "";
    parsed.hostname = parsed.hostname.toLowerCase();
    if ((parsed.protocol === "https:" && parsed.port === "443") ||
        (parsed.protocol === "http:" && parsed.port === "80")) {
      parsed.port = "";
    }
    const normalizedPath = parsed.pathname.replace(/\/+$/, "");
    parsed.pathname = normalizedPath || "/";
    return parsed.toString();
  } catch {
    return trimmed.replace(/#.*$/, "").replace(/\/+$/, "");
  }
}

export function normalizeTitle(title: string): string {
  return title.trim().replace(/\s+/g, " ").toLocaleLowerCase("de-DE");
}

async function loadEntries(): Promise<ReadArticleEntry[]> {
  try {
    const data = await readFile(STORE_PATH, "utf-8");
    const parsed = JSON.parse(data) as unknown;
    if (!Array.isArray(parsed)) return [];

    return parsed
      .map((item): ReadArticleEntry | null => {
        if (typeof item === "string") {
          return {
            url: normalizeUrl(item),
            title: "",
          };
        }
        if (!item || typeof item !== "object") return null;
        const entry = item as { url?: unknown; title?: unknown };
        return {
          url: typeof entry.url === "string" ? normalizeUrl(entry.url) : "",
          title: typeof entry.title === "string" ? normalizeTitle(entry.title) : "",
        };
      })
      .filter((entry): entry is ReadArticleEntry => Boolean(entry && (entry.url || entry.title)));
  } catch {
    return [];
  }
}

export async function loadReadHistory(): Promise<ReadHistory> {
  const entries = await loadEntries();
  return {
    urls: new Set(entries.map((entry) => entry.url).filter(Boolean)),
    titles: new Set(entries.map((entry) => entry.title).filter(Boolean)),
  };
}

export async function markRead(url: string, title = ""): Promise<void> {
  const entries = await loadEntries();
  const normalizedEntry: ReadArticleEntry = {
    url: normalizeUrl(url),
    title: normalizeTitle(title),
  };
  const filtered = entries.filter(
    (entry) =>
      !(
        (normalizedEntry.url && entry.url === normalizedEntry.url) ||
        (normalizedEntry.title && entry.title === normalizedEntry.title)
      )
  );
  filtered.push(normalizedEntry);
  const trimmed =
    filtered.length > MAX_ENTRIES
      ? filtered.slice(filtered.length - MAX_ENTRIES)
      : filtered;
  await writeFile(STORE_PATH, JSON.stringify(trimmed, null, 2), "utf-8");
}
