# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

HeiseNewsTicker02 is an RSS news aggregator that reads the Heise News Ticker feed, filters articles using Claude API based on user preferences, and displays matching articles on an HTML page.

## Tech Stack

- **Runtime:** Node.js 22 + TypeScript
- **Execution:** `tsx` (runs TypeScript directly, no build step)
- **Web framework:** Express
- **Claude API:** `@anthropic-ai/sdk`
- **RSS parsing:** `rss-parser`

## Build & Run Commands

- `npm install` — Install dependencies
- `npm run dev` — Start dev server with auto-reload (tsx watch)
- `npm start` — Start production server
- Server runs at `http://localhost:3000`

## Configuration

- `.env` — Set `ANTHROPIC_API_KEY` here (required)
- `Artikel-Preferenzen.txt` — User preferences for article filtering (German)

## Architecture

```
Browser → GET / → Express serves public/index.html + style.css
Browser → GET /api/articles → Server pipeline:
  1. rss-fetcher.ts: Fetch + parse Heise RSS feed
  2. article-filter.ts: Read preferences, call Claude API, parse response
  3. Return filtered articles as JSON
Frontend renders article cards client-side
```

## Data Flow

1. Fetch RSS feed from `https://www.heise.de/rss/heise.rdf`
2. Read user preferences from `Artikel-Preferenzen.txt`
3. Send preferences + feed data to Claude API to determine article relevance
4. Render matching articles (title, description, link) as HTML

## Key Files

- `readme.txt` — Project specification (German)
- `Artikel-Preferenzen.txt` — User preference file for article filtering
- `src/index.ts` — Express server entry point
- `src/rss-fetcher.ts` — RSS feed fetching and parsing
- `src/article-filter.ts` — Claude API integration for filtering
- `src/types.ts` — Shared TypeScript interfaces
- `public/index.html` — Frontend page
- `public/style.css` — Styling
