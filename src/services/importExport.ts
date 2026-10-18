import Papa from 'papaparse';
import { bodySystems, db } from '../db/database';
import type { Drug, ImportRecord, Product, SourceReference } from '../types';
import { buildSearchTerms, normalizeText, slugify } from '../utils/text';

export interface ImportProgress {
  processed: number;
  imported: number;
  skipped: number;
  percent?: number;
  stage: 'reading' | 'writing' | 'complete';
}

type RawRow = Record<string, unknown>;

const keyAliases: Record<string, string[]> = {
  genericName: ['genericname', 'generic_name', 'scientificname', 'scientific_name', 'inn', 'medicine', 'drugname', 'drug_name', 'الاسمالعلمي'],
  arabicName: ['arabicname', 'arabic_name', 'namear', 'name_ar', 'الاسمبالعربية', 'الاسمالعربي'],
  systemId: ['systemid', 'system_id', 'bodysystem', 'body_system', 'system', 'الجهاز'],
  pharmacologyClass: ['pharmacologyclass', 'pharmacology_class', 'drugclass', 'drug_class', 'class', 'المجموعةالدوائية'],
  pharmacologyClassAr: ['pharmacologyclassar', 'pharmacology_class_ar', 'classar', 'class_ar'],
  subclass: ['subclass', 'sub_class', 'subgroup'],
  aliases: ['aliases', 'synonyms', 'alias', 'keywords'],
  description: ['description', 'summary', 'overview'],
  descriptionAr: ['descriptionar', 'description_ar', 'summaryar', 'summary_ar'],
  mechanism: ['mechanism', 'mechanismofaction', 'mechanism_of_action'],
  mechanismAr: ['mechanismar', 'mechanism_ar'],
  indications: ['indications', 'uses'],
  indicationsAr: ['indicationsar', 'indications_ar', 'usesar'],
  cautions: ['cautions', 'warnings', 'safety'],
  cautionsAr: ['cautionsar', 'cautions_ar', 'warningsar'],
  dosageNote: ['dosagenote', 'dosage_note', 'dose', 'dosage'],
  dosageNoteAr: ['dosagenotear', 'dosage_note_ar'],
  brandName: ['brandname', 'brand_name', 'trade_name', 'tradename', 'brand', 'productname', 'product_name', 'اسمالمنتج', 'الاسمالتجاري'],
  brandNameAr: ['brandnamear', 'brand_name_ar', 'tradenamear', 'trade_name_ar'],
  strength: ['strength', 'concentration', 'قوة', 'التركيز'],
  dosageForm: ['dosageform', 'dosage_form', 'form', 'الشكلالصيدلاني'],
  dosageFormAr: ['dosageformar', 'dosage_form_ar', 'formar'],
  manufacturer: ['manufacturer', 'company', 'الشركةالمصنعة'],
  country: ['country', 'origin', 'بلدالمنشأ', 'الدولة'],
  localAgent: ['localagent', 'local_agent', 'agent', 'importer', 'supplier', 'الوكيل', 'المورد'],
  market: ['market', 'countrymarket', 'country_market', 'السوق'],
  barcode: ['barcode', 'gtin', 'ean', 'upc', 'الباركود'],
  registrationNumber: ['registrationnumber', 'registration_number', 'regno', 'reg_no'],
  image: ['image', 'imageurl', 'image_url', 'photo', 'photo_url', 'الصورة'],
  leafletUrl: ['leafleturl', 'leaflet_url', 'pilurl', 'pil_url'],
  verified: ['verified', 'isverified', 'is_verified'],
  recordType: ['recordtype', 'record_type', 'type'],
};

const normalizeKey = (key: string) => normalizeText(key).replace(/\s/g, '');

function getValue(row: RawRow, field: keyof typeof keyAliases): unknown {
  const entries = Object.entries(row);
  const aliases = new Set(keyAliases[field]);
  const found = entries.find(([key]) => aliases.has(normalizeKey(key)));
  return found?.[1];
}

const asText = (value: unknown): string => String(value ?? '').trim();
const asList = (value: unknown): string[] => {
  if (Array.isArray(value)) return value.map(asText).filter(Boolean);
  const text = asText(value);
  if (!text) return [];
  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text);
      if (Array.isArray(parsed)) return parsed.map(asText).filter(Boolean);
    } catch { /* use delimited fallback */ }
  }
  return text.split(/[|;\n]+/).map((item) => item.trim()).filter(Boolean);
};

const asBoolean = (value: unknown): boolean => ['true', 'yes', '1', 'verified', 'نعم'].includes(normalizeText(value));

function resolveSystem(value: unknown): string {
  const normalized = normalizeText(value);
  const system = bodySystems.find((item) => [item.id, item.name, item.nameAr, item.shortName, item.shortNameAr].some((name) => normalizeText(name) === normalized));
  return system?.id ?? (bodySystems.some((item) => item.id === asText(value)) ? asText(value) : 'diagnostic');
}

function rowToDrug(row: RawRow): Drug | undefined {
  const genericName = asText(getValue(row, 'genericName'));
  if (!genericName) return undefined;
  const now = new Date().toISOString();
  const arabicName = asText(getValue(row, 'arabicName'));
  const pharmacologyClass = asText(getValue(row, 'pharmacologyClass')) || 'Unclassified';
  const pharmacologyClassAr = asText(getValue(row, 'pharmacologyClassAr')) || 'غير مصنف';
  const aliases = asList(getValue(row, 'aliases'));
  const id = slugify(genericName);
  const sourceRefs: SourceReference[] = [];
  return {
    id,
    genericName,
    arabicName,
    systemId: resolveSystem(getValue(row, 'systemId')),
    pharmacologyClass,
    pharmacologyClassAr,
    subclass: asText(getValue(row, 'subclass')),
    subclassAr: '',
    aliases,
    description: asText(getValue(row, 'description')),
    descriptionAr: asText(getValue(row, 'descriptionAr')),
    mechanism: asText(getValue(row, 'mechanism')),
    mechanismAr: asText(getValue(row, 'mechanismAr')),
    indications: asList(getValue(row, 'indications')),
    indicationsAr: asList(getValue(row, 'indicationsAr')),
    cautions: asList(getValue(row, 'cautions')),
    cautionsAr: asList(getValue(row, 'cautionsAr')),
    interactions: [],
    dosageNote: asText(getValue(row, 'dosageNote')),
    dosageNoteAr: asText(getValue(row, 'dosageNoteAr')),
    image: asText(getValue(row, 'image')),
    searchTerms: buildSearchTerms(genericName, arabicName, aliases, pharmacologyClass, pharmacologyClassAr),
    sourceRefs,
    locallyAdded: true,
    createdAt: now,
    updatedAt: now,
  };
}

function rowToProduct(row: RawRow, drug: Drug): Product | undefined {
  const brandName = asText(getValue(row, 'brandName'));
  if (!brandName) return undefined;
  const strength = asText(getValue(row, 'strength'));
  const dosageForm = asText(getValue(row, 'dosageForm'));
  const now = new Date().toISOString();
  return {
    id: `${drug.id}-${slugify(brandName)}-${slugify(strength || 'na')}-${slugify(dosageForm || 'na')}`,
    drugId: drug.id,
    brandName,
    brandNameAr: asText(getValue(row, 'brandNameAr')),
    genericName: drug.genericName,
    strength,
    dosageForm,
    dosageFormAr: asText(getValue(row, 'dosageFormAr')),
    manufacturer: asText(getValue(row, 'manufacturer')),
    country: asText(getValue(row, 'country')),
    localAgent: asText(getValue(row, 'localAgent')),
    market: asText(getValue(row, 'market')) || 'Yemen',
    barcode: asText(getValue(row, 'barcode')),
    registrationNumber: asText(getValue(row, 'registrationNumber')),
    image: asText(getValue(row, 'image')),
    leafletUrl: asText(getValue(row, 'leafletUrl')),
    searchTerms: buildSearchTerms(brandName, drug.genericName, drug.arabicName, strength, getValue(row, 'manufacturer'), getValue(row, 'barcode')),
    verified: asBoolean(getValue(row, 'verified')),
    updatedAt: now,
  };
}

const mergeDrugRecords = (current: Drug | undefined, incoming: Drug): Drug => {
  if (!current) return incoming;
  return {
    ...current,
    ...incoming,
    arabicName: incoming.arabicName || current.arabicName,
    systemId: incoming.systemId === 'diagnostic' && current.systemId !== 'diagnostic' ? current.systemId : incoming.systemId,
    pharmacologyClass: incoming.pharmacologyClass === 'Unclassified' ? current.pharmacologyClass : incoming.pharmacologyClass,
    pharmacologyClassAr: incoming.pharmacologyClassAr === 'غير مصنف' ? current.pharmacologyClassAr : incoming.pharmacologyClassAr,
    subclass: incoming.subclass || current.subclass,
    subclassAr: incoming.subclassAr || current.subclassAr,
    aliases: incoming.aliases.length ? incoming.aliases : current.aliases,
    description: incoming.description || current.description,
    descriptionAr: incoming.descriptionAr || current.descriptionAr,
    mechanism: incoming.mechanism || current.mechanism,
    mechanismAr: incoming.mechanismAr || current.mechanismAr,
    indications: incoming.indications.length ? incoming.indications : current.indications,
    indicationsAr: incoming.indicationsAr.length ? incoming.indicationsAr : current.indicationsAr,
    cautions: incoming.cautions.length ? incoming.cautions : current.cautions,
    cautionsAr: incoming.cautionsAr.length ? incoming.cautionsAr : current.cautionsAr,
    dosageNote: incoming.dosageNote || current.dosageNote,
    dosageNoteAr: incoming.dosageNoteAr || current.dosageNoteAr,
    image: incoming.image || current.image,
    sourceRefs: incoming.sourceRefs.length ? incoming.sourceRefs : current.sourceRefs,
    searchTerms: buildSearchTerms(
      incoming.genericName, incoming.arabicName || current.arabicName,
      incoming.aliases.length ? incoming.aliases : current.aliases,
      incoming.pharmacologyClass === 'Unclassified' ? current.pharmacologyClass : incoming.pharmacologyClass,
    ),
    createdAt: current.createdAt,
    updatedAt: incoming.updatedAt,
  };
};

async function writeRows(rows: RawRow[]): Promise<{ imported: number; skipped: number }> {
  const drugs = new Map<string, Drug>();
  const products: Product[] = [];
  let skipped = 0;

  for (const row of rows) {
    const drug = rowToDrug(row);
    if (!drug) {
      skipped += 1;
      continue;
    }
    drugs.set(drug.id, mergeDrugRecords(drugs.get(drug.id), drug));
    const recordType = normalizeText(getValue(row, 'recordType'));
    if (recordType !== 'drug') {
      const product = rowToProduct(row, drug);
      if (product) products.push(product);
    }
  }

  const incomingDrugs = [...drugs.values()];
  const existingDrugs = await db.drugs.bulkGet(incomingDrugs.map((drug) => drug.id));
  const mergedDrugs = incomingDrugs.map((drug, index) => mergeDrugRecords(existingDrugs[index], drug));

  await db.transaction('rw', db.drugs, db.products, async () => {
    if (mergedDrugs.length) await db.drugs.bulkPut(mergedDrugs);
    if (products.length) await db.products.bulkPut(products);
  });
  return { imported: mergedDrugs.length + products.length, skipped };
}

async function importCsv(file: File, onProgress: (progress: ImportProgress) => void): Promise<{ imported: number; skipped: number }> {
  let imported = 0;
  let skipped = 0;
  let processed = 0;

  return new Promise((resolve, reject) => {
    Papa.parse<RawRow>(file, {
      header: true,
      skipEmptyLines: 'greedy',
      worker: true,
      chunkSize: 1024 * 1024,
      chunk: (result, parser) => {
        parser.pause();
        const rows = result.data;
        writeRows(rows).then((summary) => {
          imported += summary.imported;
          skipped += summary.skipped;
          processed += rows.length;
          onProgress({ processed, imported, skipped, percent: Math.min(98, Math.round((result.meta.cursor / file.size) * 100)), stage: 'writing' });
          parser.resume();
        }).catch(reject);
      },
      complete: () => resolve({ imported, skipped }),
      error: (error) => reject(error),
    });
  });
}

function restoreDrugRecord(row: RawRow): Drug | undefined {
  const mapped = rowToDrug(row);
  if (!mapped) return undefined;
  const saved = row as Partial<Drug>;
  const aliases = Array.isArray(saved.aliases) ? saved.aliases.map(asText).filter(Boolean) : mapped.aliases;
  return {
    ...mapped,
    ...saved,
    id: asText(saved.id) || mapped.id,
    genericName: asText(saved.genericName) || mapped.genericName,
    aliases,
    indications: Array.isArray(saved.indications) ? saved.indications.map(asText).filter(Boolean) : mapped.indications,
    indicationsAr: Array.isArray(saved.indicationsAr) ? saved.indicationsAr.map(asText).filter(Boolean) : mapped.indicationsAr,
    cautions: Array.isArray(saved.cautions) ? saved.cautions.map(asText).filter(Boolean) : mapped.cautions,
    cautionsAr: Array.isArray(saved.cautionsAr) ? saved.cautionsAr.map(asText).filter(Boolean) : mapped.cautionsAr,
    interactions: Array.isArray(saved.interactions) ? saved.interactions.map(asText).filter(Boolean) : [],
    sourceRefs: Array.isArray(saved.sourceRefs) ? saved.sourceRefs : [],
    searchTerms: buildSearchTerms(saved.genericName || mapped.genericName, saved.arabicName, aliases, saved.pharmacologyClass, saved.pharmacologyClassAr),
    createdAt: asText(saved.createdAt) || mapped.createdAt,
    updatedAt: asText(saved.updatedAt) || mapped.updatedAt,
  };
}

function restoreProductRecord(row: RawRow, drugsById: Map<string, Drug>): Product | undefined {
  const saved = row as Partial<Product>;
  const genericName = asText(saved.genericName || getValue(row, 'genericName'));
  const drugId = asText(saved.drugId) || slugify(genericName);
  let drug = drugsById.get(drugId);
  if (!drug && genericName) {
    drug = rowToDrug({ genericName, systemId: 'diagnostic' });
    if (drug) drugsById.set(drug.id, drug);
  }
  if (!drug) return undefined;
  const mapped = rowToProduct(row, drug);
  if (!mapped) return undefined;
  return {
    ...mapped,
    ...saved,
    id: asText(saved.id) || mapped.id,
    drugId: drug.id,
    brandName: asText(saved.brandName) || mapped.brandName,
    genericName: drug.genericName,
    searchTerms: buildSearchTerms(saved.brandName || mapped.brandName, saved.brandNameAr, drug.genericName, drug.arabicName, saved.strength, saved.manufacturer, saved.barcode),
    updatedAt: asText(saved.updatedAt) || mapped.updatedAt,
  };
}

async function importJson(file: File, onProgress: (progress: ImportProgress) => void): Promise<{ imported: number; skipped: number }> {
  onProgress({ processed: 0, imported: 0, skipped: 0, percent: 10, stage: 'reading' });
  const parsed = JSON.parse(await file.text()) as RawRow[] | { drugs?: RawRow[]; products?: RawRow[]; schemaVersion?: number };

  if (!Array.isArray(parsed) && (Array.isArray(parsed.drugs) || Array.isArray(parsed.products))) {
    const drugsById = new Map<string, Drug>();
    let skipped = 0;
    (parsed.drugs ?? []).forEach((row) => {
      const drug = restoreDrugRecord(row);
      if (drug) drugsById.set(drug.id, drug);
      else skipped += 1;
    });
    const products: Product[] = [];
    (parsed.products ?? []).forEach((row) => {
      const product = restoreProductRecord(row, drugsById);
      if (product) products.push(product);
      else skipped += 1;
    });
    const drugs = [...drugsById.values()];
    const total = drugs.length + products.length;
    const chunkSize = 2000;
    let imported = 0;
    for (let index = 0; index < Math.max(drugs.length, products.length); index += chunkSize) {
      const drugChunk = drugs.slice(index, index + chunkSize);
      const productChunk = products.slice(index, index + chunkSize);
      await db.transaction('rw', db.drugs, db.products, async () => {
        if (drugChunk.length) await db.drugs.bulkPut(drugChunk);
        if (productChunk.length) await db.products.bulkPut(productChunk);
      });
      imported += drugChunk.length + productChunk.length;
      onProgress({ processed: imported, imported, skipped, percent: Math.round((imported / Math.max(total, 1)) * 100), stage: 'writing' });
    }
    return { imported, skipped };
  }

  const rows = parsed as RawRow[];
  let imported = 0;
  let skipped = 0;
  const chunkSize = 2000;
  for (let index = 0; index < rows.length; index += chunkSize) {
    const summary = await writeRows(rows.slice(index, index + chunkSize));
    imported += summary.imported;
    skipped += summary.skipped;
    onProgress({ processed: Math.min(index + chunkSize, rows.length), imported, skipped, percent: Math.round((Math.min(index + chunkSize, rows.length) / Math.max(rows.length, 1)) * 100), stage: 'writing' });
  }
  return { imported, skipped };
}

async function importExcel(file: File, onProgress: (progress: ImportProgress) => void): Promise<{ imported: number; skipped: number }> {
  onProgress({ processed: 0, imported: 0, skipped: 0, percent: 10, stage: 'reading' });
  const XLSX = await import('@e965/xlsx');
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array', cellDates: false });
  const rows: RawRow[] = [];
  workbook.SheetNames.forEach((sheetName) => {
    const type = normalizeText(sheetName).includes('drug') || normalizeText(sheetName).includes('medicine') ? 'drug' : '';
    const sheetRows = XLSX.utils.sheet_to_json<RawRow>(workbook.Sheets[sheetName], { defval: '' });
    rows.push(...sheetRows.map((row) => type ? { ...row, recordType: 'drug' } : row));
  });
  let imported = 0;
  let skipped = 0;
  const chunkSize = 1500;
  for (let index = 0; index < rows.length; index += chunkSize) {
    const summary = await writeRows(rows.slice(index, index + chunkSize));
    imported += summary.imported;
    skipped += summary.skipped;
    onProgress({ processed: Math.min(index + chunkSize, rows.length), imported, skipped, percent: Math.round((Math.min(index + chunkSize, rows.length) / Math.max(rows.length, 1)) * 100), stage: 'writing' });
  }
  return { imported, skipped };
}

export async function importDatabaseFile(file: File, onProgress: (progress: ImportProgress) => void): Promise<ImportRecord> {
  const extension = file.name.split('.').pop()?.toLowerCase();
  let summary: { imported: number; skipped: number };
  try {
    if (extension === 'csv' || file.type.includes('csv')) summary = await importCsv(file, onProgress);
    else if (extension === 'json' || file.type.includes('json')) summary = await importJson(file, onProgress);
    else if (['xlsx', 'xls'].includes(extension ?? '')) summary = await importExcel(file, onProgress);
    else throw new Error('Unsupported file type. Use CSV, JSON, XLSX, or XLS.');

    const record: ImportRecord = {
      fileName: file.name,
      fileType: extension?.toUpperCase() ?? file.type,
      importedRows: summary.imported,
      skippedRows: summary.skipped,
      importedAt: new Date().toISOString(),
      status: summary.skipped ? 'partial' : 'complete',
    };
    const id = await db.imports.add(record);
    onProgress({ processed: summary.imported + summary.skipped, imported: summary.imported, skipped: summary.skipped, percent: 100, stage: 'complete' });
    return { ...record, id };
  } catch (error) {
    await db.imports.add({ fileName: file.name, fileType: extension?.toUpperCase() ?? 'Unknown', importedRows: 0, skippedRows: 0, importedAt: new Date().toISOString(), status: 'failed' });
    throw error;
  }
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const dateSuffix = () => new Date().toISOString().slice(0, 10);

export async function exportJson(): Promise<void> {
  const [drugs, products] = await Promise.all([db.drugs.toArray(), db.products.toArray()]);
  const payload = { app: 'APH · AbdulmajeedPharma', schemaVersion: 1, exportedAt: new Date().toISOString(), drugs, products };
  downloadBlob(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }), `APH-backup-${dateSuffix()}.json`);
}

function flattenProduct(product: Product, drug?: Drug): RawRow {
  return {
    generic_name: drug?.genericName ?? product.genericName,
    arabic_name: drug?.arabicName ?? '',
    body_system: drug?.systemId ?? '',
    pharmacology_class: drug?.pharmacologyClass ?? '',
    brand_name: product.brandName,
    strength: product.strength,
    dosage_form: product.dosageForm,
    manufacturer: product.manufacturer,
    country: product.country,
    local_agent: product.localAgent,
    market: product.market,
    barcode: product.barcode ?? '',
    registration_number: product.registrationNumber ?? '',
    image_url: product.image ?? '',
    verified: product.verified,
  };
}

export async function exportCsv(): Promise<void> {
  const [drugs, products] = await Promise.all([db.drugs.toArray(), db.products.toArray()]);
  const drugMap = new Map(drugs.map((drug) => [drug.id, drug]));
  const csv = Papa.unparse(products.map((product) => flattenProduct(product, drugMap.get(product.drugId))));
  downloadBlob(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }), `APH-catalog-${dateSuffix()}.csv`);
}

export async function exportExcel(): Promise<void> {
  const XLSX = await import('@e965/xlsx');
  const [drugs, products] = await Promise.all([db.drugs.toArray(), db.products.toArray()]);
  const drugMap = new Map(drugs.map((drug) => [drug.id, drug]));
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(drugs), 'Drugs');
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(products.map((product) => flattenProduct(product, drugMap.get(product.drugId)))), 'Products');
  XLSX.writeFile(workbook, `APH-catalog-${dateSuffix()}.xlsx`, { compression: true });
}

export function downloadCsvTemplate(): void {
  const rows = [{
    generic_name: 'Paracetamol', arabic_name: 'باراسيتامول', body_system: 'musculoskeletal',
    pharmacology_class: 'Analgesics and antipyretics', brand_name: 'Example brand', strength: '500 mg',
    dosage_form: 'Tablet', manufacturer: 'Manufacturer', country: 'Country', local_agent: 'Yemen agent',
    market: 'Yemen', barcode: '', registration_number: '', image_url: '', verified: false,
  }];
  const csv = Papa.unparse(rows);
  downloadBlob(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }), 'APH-import-template.csv');
}

export async function recentImports(): Promise<ImportRecord[]> {
  return db.imports.orderBy('importedAt').reverse().limit(8).toArray();
}
