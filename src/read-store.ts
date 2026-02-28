import { readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const STORE_PATH = resolve(__dirname, "..", "gelesen-artikel.json");

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
  await writeFile(STORE_PATH, JSON.stringify([...urls], null, 2), "utf-8");
}
