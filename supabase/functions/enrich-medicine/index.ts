import { cleanText, enforceRateLimit, handleOptions, json } from '../_shared/http.ts';

const fetchJson = async (url: string) => {
  try {
    const response = await fetch(url, { headers: { Accept: 'application/json' } });
    return response.ok ? await response.json() : undefined;
  } catch { return undefined; }
};

const firstText = (value: unknown): string => Array.isArray(value) ? cleanText(value[0], 5000) : '';

Deno.serve(async (request) => {
  const options = handleOptions(request);
  if (options) return options;
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  const limited = await enforceRateLimit(request, 'enrich-medicine', 60, 60);
  if (limited) return limited;

  try {
    const { name: rawName } = await request.json();
    const requestedName = cleanText(rawName, 180);
    if (requestedName.length < 2) return json({ error: 'Medicine name is required.' }, 400);

    const approximate = await fetchJson(`https://rxnav.nlm.nih.gov/REST/approximateTerm.json?term=${encodeURIComponent(requestedName)}&maxEntries=1`);
    const rxcui = approximate?.approximateGroup?.candidate?.[0]?.rxcui;
    const properties = rxcui ? await fetchJson(`https://rxnav.nlm.nih.gov/REST/rxcui/${encodeURIComponent(rxcui)}/properties.json`) : undefined;
    const canonicalName = cleanText(properties?.properties?.name || requestedName, 180);

    const [rx, fda] = await Promise.all([
      fetchJson(`https://rxnav.nlm.nih.gov/REST/drugs.json?name=${encodeURIComponent(canonicalName)}`),
      fetchJson(`https://api.fda.gov/drug/label.json?search=${encodeURIComponent(`openfda.generic_name:"${canonicalName}"`)}&limit=1`),
    ]);
    const concepts = rx?.drugGroup?.conceptGroup?.flatMap((group: { conceptProperties?: Array<{ name?: string }> }) => group.conceptProperties || []) || [];
    const label = fda?.results?.[0];
    const genericName = cleanText(label?.openfda?.generic_name?.[0] || canonicalName, 180);
    const brandNames = [...new Set([...(label?.openfda?.brand_name || []), ...concepts.map((concept: { name?: string }) => concept.name).filter(Boolean)])]
      .map((value) => cleanText(value, 180)).filter((value) => value && value.toLowerCase() !== genericName.toLowerCase()).slice(0, 25);

    let images: Array<{ title: string; link: string; source: string }> = [];
    const googleKey = Deno.env.get('GOOGLE_SEARCH_API_KEY');
    const googleCx = Deno.env.get('GOOGLE_SEARCH_CX');
    if (googleKey && googleCx) {
      const imageResult = await fetchJson(`https://www.googleapis.com/customsearch/v1?key=${encodeURIComponent(googleKey)}&cx=${encodeURIComponent(googleCx)}&searchType=image&safe=active&num=5&q=${encodeURIComponent(`${genericName} medicine product package`)}`);
      images = (imageResult?.items || []).map((item: { title?: string; link?: string; displayLink?: string }) => ({ title: cleanText(item.title, 180), link: cleanText(item.link, 1000), source: cleanText(item.displayLink, 250) })).filter((item: { link: string }) => item.link);
    }

    return json({
      rxcui,
      genericName,
      brandNames,
      description: firstText(label?.description),
      indications: firstText(label?.indications_and_usage) ? [firstText(label?.indications_and_usage)] : [],
      warnings: [firstText(label?.boxed_warning), firstText(label?.warnings)].filter(Boolean),
      manufacturers: label?.openfda?.manufacturer_name || [],
      moleculeImage: `https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/${encodeURIComponent(genericName)}/PNG?image_size=420x420`,
      productImages: images,
      sources: [
        { label: `RxNorm / RxNav · ${genericName}`, url: `https://mor.nlm.nih.gov/RxNav/search?searchBy=String&searchTerm=${encodeURIComponent(genericName)}`, authority: 'NLM' },
        { label: `DailyMed labels · ${genericName}`, url: `https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=${encodeURIComponent(genericName)}`, authority: 'FDA' },
        { label: `PubChem · ${genericName}`, url: `https://pubchem.ncbi.nlm.nih.gov/#query=${encodeURIComponent(genericName)}`, authority: 'NLM' },
      ],
    });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : 'Enrichment failed.' }, 500);
  }
});
