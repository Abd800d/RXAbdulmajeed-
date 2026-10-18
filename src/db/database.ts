import Dexie, { type EntityTable } from 'dexie';
import Fuse from 'fuse.js';
import { bodySystems, seedDrugs, seedProducts } from '../data/seed';
import type { AppMeta, CatalogStats, Drug, ImportRecord, Product, SearchResult } from '../types';
import { buildSearchTerms, normalizeText, slugify } from '../utils/text';

class AphDatabase extends Dexie {
  drugs!: EntityTable<Drug, 'id'>;
  products!: EntityTable<Product, 'id'>;
  imports!: EntityTable<ImportRecord, 'id'>;
  meta!: EntityTable<AppMeta, 'key'>;

  constructor() {
    super('APHPharmaDatabase');
    this.version(1).stores({
      drugs: '&id, genericName, arabicName, systemId, pharmacologyClass, updatedAt, *searchTerms',
      products: '&id, drugId, brandName, genericName, country, market, updatedAt, *searchTerms',
      imports: '++id, importedAt, status',
      meta: '&key',
    });
  }
}

export const db = new AphDatabase();
const SEED_VERSION = 1;

export async function initializeDatabase(): Promise<void> {
  await db.open();
  const seedMeta = await db.meta.get('seedVersion');
  const drugCount = await db.drugs.count();

  if (!drugCount) {
    await db.transaction('rw', db.drugs, db.products, db.meta, async () => {
      await db.drugs.bulkPut(seedDrugs);
      await db.products.bulkPut(seedProducts);
      await db.meta.put({ key: 'seedVersion', value: SEED_VERSION });
      await db.meta.put({ key: 'favorites', value: ['paracetamol', 'amlodipine'] });
      await db.meta.put({ key: 'createdAt', value: new Date().toISOString() });
    });
  } else if (Number(seedMeta?.value ?? 0) < SEED_VERSION) {
    await db.meta.put({ key: 'seedVersion', value: SEED_VERSION });
  }
}

export async function getCatalogSnapshot(limit = 1500): Promise<{ drugs: Drug[]; products: Product[] }> {
  const [drugs, products] = await Promise.all([
    db.drugs.orderBy('genericName').limit(limit).toArray(),
    db.products.orderBy('brandName').limit(limit * 3).toArray(),
  ]);
  return { drugs, products };
}

export async function getStats(): Promise<CatalogStats> {
  const favorites = await getFavorites();
  const [drugCount, productCount, localProductCount] = await Promise.all([
    db.drugs.count(),
    db.products.count(),
    db.products.filter((product) => normalizeText(product.market).includes('yemen')).count(),
  ]);
  return { drugCount, productCount, localProductCount, favoriteCount: favorites.length };
}

export async function getFavorites(): Promise<string[]> {
  const row = await db.meta.get('favorites');
  return Array.isArray(row?.value) ? row.value as string[] : [];
}

export async function toggleFavorite(drugId: string): Promise<string[]> {
  const current = await getFavorites();
  const next = current.includes(drugId) ? current.filter((id) => id !== drugId) : [...current, drugId];
  await db.meta.put({ key: 'favorites', value: next });
  return next;
}

export async function getProductsForDrug(drugId: string): Promise<Product[]> {
  return db.products.where('drugId').equals(drugId).sortBy('brandName');
}

export async function saveDrug(input: Partial<Drug> & Pick<Drug, 'genericName' | 'systemId'>): Promise<Drug> {
  const now = new Date().toISOString();
  const existing = input.id ? await db.drugs.get(input.id) : undefined;
  const id = input.id || slugify(input.genericName);
  const record: Drug = {
    id,
    genericName: input.genericName.trim(),
    arabicName: input.arabicName?.trim() ?? '',
    systemId: input.systemId,
    pharmacologyClass: input.pharmacologyClass?.trim() ?? 'Unclassified',
    pharmacologyClassAr: input.pharmacologyClassAr?.trim() ?? 'غير مصنف',
    subclass: input.subclass?.trim() ?? '',
    subclassAr: input.subclassAr?.trim() ?? '',
    aliases: input.aliases ?? [],
    description: input.description?.trim() ?? '',
    descriptionAr: input.descriptionAr?.trim() ?? '',
    mechanism: input.mechanism?.trim() ?? '',
    mechanismAr: input.mechanismAr?.trim() ?? '',
    indications: input.indications ?? [],
    indicationsAr: input.indicationsAr ?? [],
    cautions: input.cautions ?? [],
    cautionsAr: input.cautionsAr ?? [],
    interactions: input.interactions ?? [],
    dosageNote: input.dosageNote?.trim() ?? '',
    dosageNoteAr: input.dosageNoteAr?.trim() ?? '',
    image: input.image ?? existing?.image,
    searchTerms: buildSearchTerms(input.genericName, input.arabicName, input.aliases, input.pharmacologyClass, input.pharmacologyClassAr),
    sourceRefs: input.sourceRefs ?? existing?.sourceRefs ?? [],
    locallyAdded: input.locallyAdded ?? existing?.locallyAdded ?? true,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  };
  await db.drugs.put(record);
  return record;
}

export async function saveProduct(input: Partial<Product> & Pick<Product, 'drugId' | 'brandName'>): Promise<Product> {
  const drug = await db.drugs.get(input.drugId);
  const now = new Date().toISOString();
  const existing = input.id ? await db.products.get(input.id) : undefined;
  const id = input.id || `${input.drugId}-${slugify(input.brandName)}-${crypto.randomUUID().slice(0, 6)}`;
  const record: Product = {
    id,
    drugId: input.drugId,
    brandName: input.brandName.trim(),
    brandNameAr: input.brandNameAr?.trim() ?? '',
    genericName: drug?.genericName ?? input.genericName ?? '',
    strength: input.strength?.trim() ?? '',
    dosageForm: input.dosageForm?.trim() ?? '',
    dosageFormAr: input.dosageFormAr?.trim() ?? '',
    manufacturer: input.manufacturer?.trim() ?? '',
    country: input.country?.trim() ?? '',
    localAgent: input.localAgent?.trim() ?? '',
    market: input.market?.trim() ?? 'Yemen',
    barcode: input.barcode?.trim() ?? '',
    registrationNumber: input.registrationNumber?.trim() ?? '',
    image: input.image ?? existing?.image,
    leafletUrl: input.leafletUrl?.trim() ?? '',
    searchTerms: buildSearchTerms(input.brandName, input.brandNameAr, drug?.genericName, drug?.arabicName, input.strength, input.manufacturer, input.barcode),
    verified: input.verified ?? false,
    updatedAt: now,
  };
  await db.products.put(record);
  return record;
}

export async function deleteDrug(drugId: string): Promise<void> {
  await db.transaction('rw', db.drugs, db.products, async () => {
    await db.products.where('drugId').equals(drugId).delete();
    await db.drugs.delete(drugId);
  });
}

export async function deleteProduct(productId: string): Promise<void> {
  await db.products.delete(productId);
}

const asProductSearchDocument = (product: Product) => ({
  ...product,
  combined: [product.brandName, product.brandNameAr, product.genericName, product.strength, product.manufacturer, product.barcode].filter(Boolean).join(' '),
});

export async function searchCatalog(rawQuery: string, limit = 60): Promise<SearchResult[]> {
  const query = normalizeText(rawQuery);
  if (!query) return [];

  const prefix = query.slice(0, Math.min(query.length, 2));
  let [drugCandidates, productCandidates] = await Promise.all([
    db.drugs.where('searchTerms').startsWith(prefix).limit(1400).toArray(),
    db.products.where('searchTerms').startsWith(prefix).limit(2200).toArray(),
  ]);

  // A one-character fallback preserves typo tolerance without pulling a million-row catalog into memory.
  if (drugCandidates.length + productCandidates.length < 12 && query.length > 1) {
    const first = query.slice(0, 1);
    [drugCandidates, productCandidates] = await Promise.all([
      db.drugs.where('searchTerms').startsWith(first).limit(1800).toArray(),
      db.products.where('searchTerms').startsWith(first).limit(2600).toArray(),
    ]);
  }

  // Small starter catalogs can be searched exhaustively; large imports stay index-bounded.
  const totalDrugs = await db.drugs.count();
  if (totalDrugs < 5000) {
    [drugCandidates, productCandidates] = await Promise.all([db.drugs.toArray(), db.products.toArray()]);
  }

  const drugFuse = new Fuse(drugCandidates, {
    keys: [
      { name: 'genericName', weight: 0.38 },
      { name: 'arabicName', weight: 0.3 },
      { name: 'aliases', weight: 0.2 },
      { name: 'pharmacologyClass', weight: 0.06 },
      { name: 'pharmacologyClassAr', weight: 0.06 },
    ],
    threshold: 0.42,
    distance: 120,
    includeScore: true,
    ignoreLocation: true,
    minMatchCharLength: query.length <= 3 ? 2 : 3,
  });

  const productDocs = productCandidates.map(asProductSearchDocument);
  const productFuse = new Fuse(productDocs, {
    keys: [
      { name: 'brandName', weight: 0.42 },
      { name: 'brandNameAr', weight: 0.18 },
      { name: 'genericName', weight: 0.25 },
      { name: 'manufacturer', weight: 0.06 },
      { name: 'strength', weight: 0.05 },
      { name: 'barcode', weight: 0.04 },
    ],
    threshold: 0.4,
    distance: 120,
    includeScore: true,
    ignoreLocation: true,
  });

  const map = new Map<string, SearchResult>();
  drugFuse.search(query, { limit }).forEach(({ item, score }) => {
    map.set(item.id, { drug: item, score: score ?? 0.5, matchedOn: 'generic' });
  });

  const productMatches = productFuse.search(query, { limit });
  const relatedDrugIds = [...new Set(productMatches.map(({ item }) => item.drugId))];
  const relatedDrugs = await db.drugs.bulkGet(relatedDrugIds);
  const relatedMap = new Map(relatedDrugs.filter(Boolean).map((drug) => [drug!.id, drug!]));
  productMatches.forEach(({ item, score }) => {
    const drug = relatedMap.get(item.drugId);
    if (!drug) return;
    const previous = map.get(drug.id);
    if (!previous || (score ?? 1) < previous.score) {
      map.set(drug.id, { drug, matchedProduct: item, score: score ?? 0.5, matchedOn: 'product' });
    }
  });

  return [...map.values()].sort((a, b) => a.score - b.score).slice(0, limit);
}

export async function findDrugInQuestion(question: string): Promise<Drug | undefined> {
  const normalizedQuestion = ` ${normalizeText(question)} `;
  const snapshot = await db.drugs.limit(5000).toArray();
  const exact = snapshot
    .flatMap((drug) => [drug.genericName, drug.arabicName, ...drug.aliases].map((name) => ({ drug, name: normalizeText(name) })))
    .filter(({ name }) => name.length > 2 && normalizedQuestion.includes(` ${name} `))
    .sort((a, b) => b.name.length - a.name.length)[0]?.drug;
  if (exact) return exact;

  const meaningfulTokens = normalizeText(question).split(' ').filter((token) => token.length > 3);
  for (const token of meaningfulTokens) {
    const match = await searchCatalog(token, 1);
    if (match[0] && match[0].score < 0.3) return match[0].drug;
  }
  return undefined;
}

export { bodySystems };
