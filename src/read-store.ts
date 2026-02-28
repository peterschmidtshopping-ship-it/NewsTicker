import { readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const STORE_PATH = resolve(__dirname, "..", "gelesen-artikel.json");
const MAX_ENTRIES = 5000;

export async function loadReadUrls(): Promise<Set<string>> {
  try {
    const data = await readFile(STORE_PATH, "utf-8");
    const urls: string[] = JSON.parse(data);
    return new Set(urls);
  } catch {
    return new Set();
  }
}

export async function markRead(url: string): Promise<void> {
  const urls = await loadReadUrls();
  urls.add(url);
  const arr = [...urls];
  const trimmed = arr.length > MAX_ENTRIES ? arr.slice(arr.length - MAX_ENTRIES) : arr;
  await writeFile(STORE_PATH, JSON.stringify(trimmed, null, 2), "utf-8");
}
