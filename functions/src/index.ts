import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";

// Secret Manager key. NEVER hardcode a key here:
//   firebase functions:secrets:set IMAGE_SEARCH_KEY
const imageSearchKey = defineSecret("IMAGE_SEARCH_KEY");

/** One image in the callable contract (mirrors EnrichImage on Android). */
export interface KeyedImage {
  url: string;
  thumb?: string;
  width?: number;
  height?: number;
  license?: string;
  credit?: string;
  source?: string;
}

interface SearchImagesRequest {
  query?: unknown;
  limit?: unknown;
}

/**
 * SWAP POINT. Default provider is Serper (https://serper.dev — POST
 * /images, 2500 free searches). If the provisioned IMAGE_SEARCH_KEY belongs
 * to another image-search API, replace this function with that provider's
 * call; the callable contract {images[]} stays unchanged.
 */
async function fetchSerper(
  query: string,
  limit: number,
  key: string,
): Promise<KeyedImage[]> {
  const res = await fetch("https://google.serper.dev/images", {
    method: "POST",
    headers: { "X-API-KEY": key, "Content-Type": "application/json" },
    body: JSON.stringify({ q: query, num: limit }),
  });
  if (!res.ok) throw new Error(`serper http ${res.status}`);
  const data = (await res.json()) as { images?: Array<Record<string, unknown>> };
  const str = (v: unknown): string | undefined =>
    typeof v === "string" && v.length > 0 ? v : undefined;
  const num = (v: unknown): number | undefined =>
    typeof v === "number" ? v : undefined;
  return (data.images ?? []).flatMap((img) => {
    const url = str(img["imageUrl"]) ?? str(img["thumbnailUrl"]);
    if (!url) return [];
    return [
      {
        url,
        thumb: str(img["thumbnailUrl"]),
        width: num(img["imageWidth"]),
        height: num(img["imageHeight"]),
        credit: str(img["source"]) ?? str(img["domain"]),
        source: str(img["source"]) ?? "web",
      },
    ];
  });
}

/**
 * Callable `searchImages` — request {query, limit}, response {images[]}.
 * The Android app tries this first, then DailyMed, then Commons.
 */
export const searchImages = onCall({ secrets: [imageSearchKey], maxInstances: 5 }, async (
  request,
) => {
  const body = (request.data ?? {}) as SearchImagesRequest;
  const query = typeof body.query === "string" ? body.query.trim() : "";
  const limit =
    typeof body.limit === "number"
      ? Math.min(Math.max(Math.floor(body.limit), 1), 10)
      : 8;
  if (query.length < 2) {
    throw new HttpsError("invalid-argument", "query must be at least 2 chars");
  }
  const key = imageSearchKey.value();
  if (!key) {
    throw new HttpsError("failed-precondition", "IMAGE_SEARCH_KEY not set");
  }
  try {
    const images = await fetchSerper(query, limit, key);
    return { images };
  } catch (e) {
    throw new HttpsError("internal", e instanceof Error ? e.message : "search failed");
  }
});
