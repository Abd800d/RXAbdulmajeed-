import type { OnlineDrugResult, SourceReference } from '../types';

const requestJson = async <T>(url: string, timeout = 10_000): Promise<T | undefined> => {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(timeout), headers: { Accept: 'application/json' } });
    if (!response.ok) return undefined;
    return await response.json() as T;
  } catch {
    return undefined;
  }
};

interface RxNormResponse {
  drugGroup?: {
    name?: string;
    conceptGroup?: Array<{
      tty?: string;
      conceptProperties?: Array<{ rxcui: string; name: string; synonym?: string }>;
    }>;
  };
}

interface OpenFdaResponse {
  results?: Array<{
    openfda?: { generic_name?: string[]; brand_name?: string[]; manufacturer_name?: string[] };
    description?: string[];
    indications_and_usage?: string[];
    warnings?: string[];
    boxed_warning?: string[];
  }>;
}

const cleanLabelText = (value?: string[]): string | undefined => value?.[0]
  ?.replace(/<[^>]+>/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()
  .slice(0, 4000);

export async function searchTrustedDrug(name: string): Promise<OnlineDrugResult> {
  const query = name.trim();
  const rxUrl = `https://rxnav.nlm.nih.gov/REST/drugs.json?name=${encodeURIComponent(query)}`;
  const fdaUrl = `https://api.fda.gov/drug/label.json?search=${encodeURIComponent(`openfda.generic_name:"${query}"`)}&limit=1`;
  const [rx, fda] = await Promise.all([
    requestJson<RxNormResponse>(rxUrl),
    requestJson<OpenFdaResponse>(fdaUrl),
  ]);

  const rxConcepts = rx?.drugGroup?.conceptGroup
    ?.flatMap((group) => group.conceptProperties ?? []) ?? [];
  const label = fda?.results?.[0];
  const genericName = label?.openfda?.generic_name?.[0] || rx?.drugGroup?.name || query;
  const brandNames = Array.from(new Set([
    ...(label?.openfda?.brand_name ?? []),
    ...rxConcepts.map((concept) => concept.name).filter((item) => item.toLowerCase() !== genericName.toLowerCase()),
  ])).slice(0, 12);

  const sourceRefs: SourceReference[] = [
    { label: `RxNorm / RxNav · ${query}`, url: `https://mor.nlm.nih.gov/RxNav/search?searchBy=String&searchTerm=${encodeURIComponent(query)}`, authority: 'NLM', accessedAt: new Date().toISOString() },
    { label: `DailyMed labels · ${query}`, url: `https://dailymed.nlm.nih.gov/dailymed/search.cfm?query=${encodeURIComponent(query)}`, authority: 'FDA', accessedAt: new Date().toISOString() },
  ];

  return {
    rxcui: rxConcepts[0]?.rxcui,
    genericName,
    brandNames,
    description: cleanLabelText(label?.description),
    indications: cleanLabelText(label?.indications_and_usage) ? [cleanLabelText(label?.indications_and_usage)!] : [],
    warnings: [cleanLabelText(label?.boxed_warning), cleanLabelText(label?.warnings)].filter(Boolean) as string[],
    sourceRefs,
  };
}

export function pubChemImageUrl(name: string, size = 420): string {
  return `https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/${encodeURIComponent(name)}/PNG?image_size=${size}x${size}`;
}

export function googleImagesUrl(query: string): string {
  return `https://www.google.com/search?tbm=isch&safe=active&q=${encodeURIComponent(`${query} medicine product`)}`;
}

export function googleLensUrl(): string {
  return 'https://lens.google.com/';
}
