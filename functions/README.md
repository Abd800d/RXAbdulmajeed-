# RxReader image-search function (Phase 4)

Callable Cloud Function `searchImages`: keyed web image search for drug
pack photos. The Android app calls it first, then falls back to DailyMed
and Wikimedia Commons (both keyless), so the app works with or without it.

Contract — request `{query: string, limit: number}`,
response `{images: [{url, thumb?, width?, height?, license?, credit?, source?}]}`.

## Setup (owner only — keys never enter the repo)

```bash
npm --prefix functions install
firebase functions:secrets:set IMAGE_SEARCH_KEY   # paste the provider key
npm --prefix functions run deploy
```

Requires a Firebase project + Blaze plan (functions with secrets need it).

## Provider

Default: **Serper** (`POST google.serper.dev/images`, 2500 free searches).
If `IMAGE_SEARCH_KEY` belongs to another image-search API, replace
`fetchSerper` in `src/index.ts` — the callable contract stays unchanged.
