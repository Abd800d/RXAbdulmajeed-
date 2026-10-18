import { cleanText, enforceRateLimit, handleOptions, json, adminClient } from '../_shared/http.ts';

Deno.serve(async (request) => {
  const options = handleOptions(request);
  if (options) return options;
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  const limited = await enforceRateLimit(request, 'identify-product', 20, 60);
  if (limited) return limited;

  const visionKey = Deno.env.get('GOOGLE_VISION_API_KEY');
  if (!visionKey) return json({ error: 'Google Cloud Vision is not configured on the server.' }, 503);

  try {
    const body = await request.json();
    const content = cleanText(body.imageBase64, 5_800_000).replace(/^data:image\/[^;]+;base64,/, '');
    if (!content || content.length > 5_600_000) return json({ error: 'Provide an image smaller than 4 MB.' }, 400);

    const response = await fetch(`https://vision.googleapis.com/v1/images:annotate?key=${encodeURIComponent(visionKey)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requests: [{ image: { content }, features: [{ type: 'WEB_DETECTION', maxResults: 12 }, { type: 'TEXT_DETECTION', maxResults: 8 }, { type: 'LOGO_DETECTION', maxResults: 8 }] }] }),
    });
    if (!response.ok) return json({ error: 'Google Vision could not analyze this image.' }, 502);
    const result = (await response.json())?.responses?.[0] || {};
    const detectedText = cleanText(result.fullTextAnnotation?.text || result.textAnnotations?.[0]?.description, 5000);
    const webEntities = (result.webDetection?.webEntities || []).map((entity: { description?: string; score?: number }) => ({ description: cleanText(entity.description, 180), score: entity.score || 0 })).filter((entity: { description: string }) => entity.description).slice(0, 12);
    const logos = (result.logoAnnotations || []).map((logo: { description?: string; score?: number }) => ({ description: cleanText(logo.description, 180), score: logo.score || 0 }));
    const candidates = [...logos, ...webEntities].sort((a, b) => b.score - a.score).map((item) => item.description);
    detectedText.split(/\n+/).map((line) => cleanText(line, 180)).filter((line) => line.length > 3 && line.length < 100).slice(0, 8).forEach((line) => candidates.push(line));

    const client = adminClient();
    const matchMap = new Map<string, unknown>();
    for (const candidate of [...new Set(candidates)].slice(0, 10)) {
      const { data } = await client.rpc('search_medicines', { search_query: candidate, max_results: 5 });
      (data || []).forEach((match: { drug?: { id?: string }; score?: number }) => {
        if (match.drug?.id && Number(match.score || 0) >= 0.18 && !matchMap.has(match.drug.id)) matchMap.set(match.drug.id, match);
      });
      if (matchMap.size >= 8) break;
    }

    return json({
      detectedText,
      webEntities,
      logos,
      visuallySimilarImages: (result.webDetection?.visuallySimilarImages || []).slice(0, 8),
      pagesWithMatchingImages: (result.webDetection?.pagesWithMatchingImages || []).slice(0, 8),
      catalogMatches: [...matchMap.values()].slice(0, 8),
      provider: 'Google Cloud Vision Web Detection',
    });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : 'Image identification failed.' }, 500);
  }
});
