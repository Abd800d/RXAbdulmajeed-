#!/usr/bin/env node
/**
 * APH trusted bulk importer
 *
 * Streams CSV/TSV, JSON arrays, structured APH JSON, and JSONL; batches
 * idempotent upserts; persists an atomic resume checkpoint after each committed
 * batch; and uploads referenced local images to Supabase Storage.
 *
 * The service-role key belongs only in the trusted shell that runs this file.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { parse } from 'csv-parse';
import { createClient } from '@supabase/supabase-js';
import chain from 'stream-chain';
import { parser as jsonParser } from 'stream-json';
import { pick as jsonPick } from 'stream-json/filters/pick.js';
import { streamArray } from 'stream-json/streamers/stream-array.js';

const argv = process.argv.slice(2);
const option = (name) => {
  const exact = argv.indexOf(name);
  if (exact >= 0) return argv[exact + 1];
  return argv.find((item) => item.startsWith(`${name}=`))?.slice(name.length + 1);
};
const has = (name) => argv.includes(name);
const fileArgument = argv.find((item, index) => !item.startsWith('--') && (index === 0 || !argv[index - 1]?.startsWith('--') || !['--checkpoint', '--images-dir', '--batch-size'].includes(argv[index - 1])));
const filePath = fileArgument ? path.resolve(fileArgument) : '';
const publish = has('--publish');
const resume = has('--resume');
const dryRun = has('--dry-run');
const noImages = has('--no-images');
const batchSize = Math.min(1000, Math.max(10, Number(option('--batch-size') || process.env.IMPORT_BATCH_SIZE || 500)));
const imagesDir = option('--images-dir') ? path.resolve(option('--images-dir')) : undefined;
const checkpointPath = path.resolve(option('--checkpoint') || `${filePath}.aph-checkpoint.json`);
const url = process.env.SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

const usage = `Usage:
  SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \\
    node scripts/import-to-supabase.mjs <catalog.csv|json|jsonl|xlsx> [options]

Options:
  --publish                 Publish imported rows to mobile users
  --resume                  Continue after the last committed checkpoint
  --checkpoint <file>       Override the checkpoint JSON path
  --batch-size <10-1000>    Rows per durable batch (default: 500)
  --images-dir <directory>  Resolve image paths/names and upload them to Storage
  --no-images               Keep remote URLs but skip all local image uploads
  --dry-run                 Parse and validate without Supabase credentials/writes

For image ingestion, put a relative/absolute path in drug_image or image/image_url.
When --images-dir is present, blank product images are also matched by product ID,
barcode, or normalized brand name (.jpg/.jpeg/.png/.webp).`;

if (!filePath || !fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
  console.error(usage);
  process.exit(1);
}
if (!Number.isFinite(batchSize)) {
  console.error('Batch size must be a number.');
  process.exit(1);
}
if (imagesDir && (!fs.existsSync(imagesDir) || !fs.statSync(imagesDir).isDirectory())) {
  console.error(`Image directory does not exist: ${imagesDir}`);
  process.exit(1);
}
if (!dryRun && (!url || !serviceKey)) {
  console.error('SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set in the trusted shell environment.');
  process.exit(1);
}
if (resume && !fs.existsSync(checkpointPath)) {
  console.error(`Resume checkpoint not found: ${checkpointPath}`);
  process.exit(1);
}

const supabase = dryRun ? null : createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
const sourceStat = fs.statSync(filePath);
const fingerprint = {
  path: filePath,
  size: sourceStat.size,
  mtimeMs: Math.trunc(sourceStat.mtimeMs),
};
const extension = path.extname(filePath).toLowerCase();
const fileType = extension.replace('.', '') || 'unknown';

const normalize = (value) => String(value ?? '')
  .toLowerCase()
  .normalize('NFKD')
  .replace(/[\u0300-\u036f\u064B-\u065F\u0670]/g, '')
  .replace(/[إأآٱ]/g, 'ا')
  .replace(/ة/g, 'ه')
  .replace(/ى/g, 'ي')
  .replace(/[^\p{L}\p{N}]+/gu, ' ')
  .trim();
const slug = (value) => normalize(value).replace(/\s+/g, '-').slice(0, 140);
const normKey = (value) => normalize(value).replace(/\s/g, '');
const stableId = (prefix, parts) => {
  const readable = slug(parts.filter(Boolean).join('-'));
  const digest = crypto.createHash('sha1').update(parts.join('\u001f')).digest('hex').slice(0, 12);
  return `${prefix}-${readable.slice(0, 120) || 'record'}-${digest}`;
};

const keys = {
  drugId: ['drugid', 'medicineid', 'genericid'],
  productId: ['productid', 'tradeid', 'brandid'],
  generic: ['genericname', 'scientificname', 'inn', 'drugname', 'medicine', 'الاسمالعلمي'],
  arabic: ['arabicname', 'namear', 'الاسمالعربي'],
  system: ['systemid', 'bodysystem', 'system', 'الجهاز'],
  class: ['pharmacologyclass', 'drugclass', 'class', 'المجموعةالدوائية'],
  classAr: ['pharmacologyclassar', 'classar'],
  subclass: ['subclass'], subclassAr: ['subclassar'],
  brand: ['brandname', 'tradename', 'brand', 'productname', 'اسمالمنتج', 'الاسمالتجاري'],
  brandAr: ['brandnamear', 'tradenamear'],
  strength: ['strength', 'concentration', 'التركيز'],
  form: ['dosageform', 'form', 'الشكلالصيدلاني'], formAr: ['dosageformar', 'formar'],
  manufacturer: ['manufacturer', 'company', 'الشركةالمصنعة'],
  country: ['country', 'origin', 'الدولة'],
  agent: ['localagent', 'agent', 'importer', 'supplier', 'الوكيل', 'المورد'],
  market: ['market', 'countrymarket', 'السوق'],
  barcode: ['barcode', 'gtin', 'ean', 'upc', 'الباركود'],
  registration: ['registrationnumber', 'regno'],
  image: ['image', 'imageurl', 'photo'],
  drugImage: ['drugimage', 'medicineimage', 'moleculeimage'],
  productImage: ['productimage', 'packageimage', 'packimage'],
  leaflet: ['leafleturl', 'leaflet'],
  description: ['description', 'summary'], descriptionAr: ['descriptionar', 'summaryar'],
  mechanism: ['mechanism', 'mechanismofaction'], mechanismAr: ['mechanismar', 'mechanismofactionar'],
  indications: ['indications', 'uses'], indicationsAr: ['indicationsar', 'usesar'],
  cautions: ['cautions', 'warnings', 'safety'], cautionsAr: ['cautionsar', 'warningsar'],
  interactions: ['interactions'], aliases: ['aliases', 'synonyms', 'keywords'],
  dosageNote: ['dosagenote', 'dosingnote'], dosageNoteAr: ['dosagenotear', 'dosingnotear'],
  verification: ['verificationstatus', 'verified', 'verification'],
};
const normalizedKeySets = Object.fromEntries(Object.entries(keys).map(([field, values]) => [field, new Set(values.map(normKey))]));
const pick = (row, field) => {
  const allowed = normalizedKeySets[field];
  const entry = Object.entries(row || {}).find(([key]) => allowed.has(normKey(key)));
  return String(entry?.[1] ?? '').trim();
};
const list = (value) => Array.isArray(value)
  ? value.map((item) => String(item).trim()).filter(Boolean)
  : String(value || '').split(/[|;\n]+/).map((item) => item.trim()).filter(Boolean);
const sourceRefs = (value) => {
  if (Array.isArray(value)) return value.filter((entry) => entry && typeof entry === 'object');
  if (!value) return [];
  try {
    const parsed = JSON.parse(String(value));
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return String(value).split(/[|;\n]+/).map((urlValue) => urlValue.trim()).filter((urlValue) => /^https?:\/\//i.test(urlValue)).flatMap((sourceUrl) => {
      try {
        const authority = new URL(sourceUrl).hostname;
        return [{ label: authority, url: sourceUrl, authority }];
      } catch { return []; }
    });
  }
};
const verificationStatus = (row) => {
  const value = normalize(pick(row, 'verification'));
  if (['verified', 'reviewed', 'rejected', 'unverified'].includes(value)) return value;
  return ['true', 'yes', '1', 'نعم'].includes(value) ? 'verified' : 'unverified';
};

const systems = new Set(['nervous', 'cardiovascular', 'blood', 'respiratory', 'gastrointestinal', 'hepatobiliary', 'endocrine', 'renal', 'reproductive', 'musculoskeletal', 'skin', 'eye', 'ent', 'immune', 'antiinfective', 'oncology', 'emergency', 'diagnostic']);
const systemAliases = new Map(Object.entries({
  'nervous system': 'nervous', 'الجهاز العصبي': 'nervous',
  'cardiovascular system': 'cardiovascular', 'جهاز القلب والاوعيه': 'cardiovascular',
  'blood and hematopoietic system': 'blood', 'الدم وتكوين الدم': 'blood',
  'respiratory system': 'respiratory', 'الجهاز التنفسي': 'respiratory',
  'gastrointestinal system': 'gastrointestinal', 'الجهاز الهضمي': 'gastrointestinal',
  'hepatobiliary system': 'hepatobiliary', 'الكبد والقنوات الصفراويه': 'hepatobiliary',
  'endocrine system': 'endocrine', 'جهاز الغدد الصماء': 'endocrine',
  'renal and urinary system': 'renal', 'الكلى والجهاز البولي': 'renal',
  'reproductive system': 'reproductive', 'الجهاز التناسلي': 'reproductive',
  'musculoskeletal system': 'musculoskeletal', 'الجهاز العضلي الهيكلي': 'musculoskeletal',
  'skin system': 'skin', 'الجلد': 'skin', 'eye': 'eye', 'العين': 'eye',
  'ear nose and throat': 'ent', 'الانف والاذن والحنجره': 'ent',
  'immune system': 'immune', 'الجهاز المناعي': 'immune',
  'anti infective system': 'antiinfective', 'مضادات العدوى': 'antiinfective',
  oncology: 'oncology', 'الاورام': 'oncology',
  'emergency and toxicology': 'emergency', 'الطوارئ والسموم': 'emergency',
  'diagnostic and miscellaneous': 'diagnostic', 'التشخيص والادويه المتنوعه': 'diagnostic',
}).map(([key, value]) => [normalize(key), value]));
const resolveSystem = (value) => {
  const normalized = normalize(value);
  const compact = normalized.replace(/ /g, '');
  return systems.has(compact) ? compact : systemAliases.get(normalized) || 'diagnostic';
};

let checkpoint = null;
if (resume) {
  checkpoint = JSON.parse(fs.readFileSync(checkpointPath, 'utf8'));
  const sameSource = checkpoint?.source?.path === fingerprint.path
    && checkpoint?.source?.size === fingerprint.size
    && checkpoint?.source?.mtimeMs === fingerprint.mtimeMs;
  if (!sameSource) {
    console.error('Checkpoint source fingerprint does not match this file. Start without --resume or choose the correct checkpoint.');
    process.exit(1);
  }
  if (Boolean(checkpoint.publish) !== publish) {
    console.error(`Checkpoint publish mode is ${checkpoint.publish ? 'published' : 'draft'}; resume with the same --publish setting.`);
    process.exit(1);
  }
  if (checkpoint.complete) {
    console.log(`Checkpoint is already complete: ${checkpointPath}`);
    process.exit(0);
  }
}

let sourceRows = Number(checkpoint?.sourceRows || 0);
const resumeAfterRow = sourceRows;
let parsedRows = 0;
let committedSourceRows = sourceRows;
let drugCount = Number(checkpoint?.drugCount || 0);
let productCount = Number(checkpoint?.productCount || 0);
let skipped = Number(checkpoint?.skipped || 0);
let imageUploads = Number(checkpoint?.imageUploads || 0);
let imageMisses = Number(checkpoint?.imageMisses || 0);
let jobId = checkpoint?.jobId || null;
let pendingSourceRows = 0;
let drugs = new Map();
let products = new Map();
const imageCache = new Map();
const warningSamples = [];

const checkpointState = (complete = false) => ({
  version: 1,
  source: fingerprint,
  fileType,
  publish,
  jobId,
  sourceRows: committedSourceRows,
  drugCount,
  productCount,
  skipped,
  imageUploads,
  imageMisses,
  complete,
  updatedAt: new Date().toISOString(),
});
const writeCheckpoint = (complete = false) => {
  if (dryRun) return;
  const temporary = `${checkpointPath}.${process.pid}.tmp`;
  fs.mkdirSync(path.dirname(checkpointPath), { recursive: true });
  fs.writeFileSync(temporary, `${JSON.stringify(checkpointState(complete), null, 2)}\n`, { mode: 0o600 });
  fs.renameSync(temporary, checkpointPath);
};

async function ensureImportJob() {
  if (!supabase) return;
  if (jobId) {
    const { data, error } = await supabase.from('import_jobs').select('id').eq('id', jobId).maybeSingle();
    if (error) throw error;
    if (data) return;
  }
  const { data, error } = await supabase.from('import_jobs').insert({
    file_name: path.basename(filePath),
    file_type: fileType,
    status: 'processing',
    total_rows: committedSourceRows,
    imported_rows: Math.max(0, committedSourceRows - skipped),
    skipped_rows: skipped,
  }).select('id').single();
  if (error) throw error;
  jobId = data.id;
  writeCheckpoint(false);
}

async function updateImportJob(status = 'processing', errorMessage = null) {
  if (!supabase || !jobId) return;
  const complete = status === 'complete';
  const { error } = await supabase.from('import_jobs').update({
    status,
    total_rows: committedSourceRows,
    imported_rows: Math.max(0, committedSourceRows - skipped),
    skipped_rows: skipped,
    error_message: errorMessage,
    completed_at: complete ? new Date().toISOString() : null,
  }).eq('id', jobId);
  if (error) throw error;
}

const mergeRows = (previous, incoming) => {
  if (!previous) return incoming;
  const result = { ...previous };
  for (const [key, value] of Object.entries(incoming)) {
    const meaningful = Array.isArray(value) ? value.length > 0 : value !== '' && value !== null && value !== undefined;
    if (meaningful || !(key in result)) result[key] = value;
  }
  result.is_published = previous.is_published || incoming.is_published;
  if (previous.verification_status === 'verified') result.verification_status = 'verified';
  return result;
};

const imageExtensions = ['.jpg', '.jpeg', '.png', '.webp'];
const mimeByExtension = { '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };
const localImageCandidate = (rawValue, candidates = []) => {
  const value = String(rawValue || '').trim();
  if (value && /^https?:\/\//i.test(value)) return { remote: value };
  if (value && /^data:image\/(png|jpeg|webp);base64,/i.test(value)) return { dataUrl: value };
  if (value && !noImages) {
    const withoutScheme = value.replace(/^file:\/\//i, '');
    const paths = [path.isAbsolute(withoutScheme) ? withoutScheme : path.resolve(path.dirname(filePath), withoutScheme)];
    if (imagesDir) paths.unshift(path.resolve(imagesDir, withoutScheme));
    const found = paths.find((candidate) => fs.existsSync(candidate) && fs.statSync(candidate).isFile());
    if (found) return { local: found };
  }
  if (!value && imagesDir && !noImages) {
    for (const candidate of candidates.filter(Boolean).map(slug)) {
      for (const ext of imageExtensions) {
        const possible = path.resolve(imagesDir, `${candidate}${ext}`);
        if (fs.existsSync(possible) && fs.statSync(possible).isFile()) return { local: possible };
      }
    }
  }
  if (value && !noImages) return { missing: value };
  return {};
};

async function resolveImage(rawValue, kind, id, candidates = []) {
  const candidate = localImageCandidate(rawValue, [id, ...candidates]);
  if (candidate.remote) return candidate.remote;
  if (!candidate.local && !candidate.dataUrl) {
    if (candidate.missing) {
      imageMisses += 1;
      if (warningSamples.length < 12) warningSamples.push(`Image not found: ${candidate.missing}`);
    }
    return null;
  }

  let buffer;
  let extensionValue;
  let contentType;
  let cacheKey;
  if (candidate.dataUrl) {
    const match = candidate.dataUrl.match(/^data:(image\/(?:png|jpeg|webp));base64,(.+)$/i);
    if (!match) return null;
    buffer = Buffer.from(match[2], 'base64');
    contentType = match[1].toLowerCase();
    extensionValue = contentType === 'image/jpeg' ? '.jpg' : `.${contentType.split('/')[1]}`;
    cacheKey = crypto.createHash('sha256').update(buffer).digest('hex');
  } else {
    const resolved = path.resolve(candidate.local);
    cacheKey = resolved;
    if (imageCache.has(cacheKey)) return imageCache.get(cacheKey);
    extensionValue = path.extname(resolved).toLowerCase();
    contentType = mimeByExtension[extensionValue];
    if (!contentType) {
      imageMisses += 1;
      if (warningSamples.length < 12) warningSamples.push(`Unsupported image type: ${resolved}`);
      return null;
    }
    const stat = fs.statSync(resolved);
    if (stat.size > 8 * 1024 * 1024) {
      imageMisses += 1;
      if (warningSamples.length < 12) warningSamples.push(`Image exceeds 8 MiB: ${resolved}`);
      return null;
    }
    buffer = fs.readFileSync(resolved);
  }
  if (buffer.length > 8 * 1024 * 1024) {
    imageMisses += 1;
    return null;
  }
  const digest = crypto.createHash('sha256').update(buffer).digest('hex');
  const storagePath = `imports/${kind}/${digest.slice(0, 2)}/${digest}${extensionValue}`;
  if (dryRun || noImages) {
    imageCache.set(cacheKey, null);
    return null;
  }
  const { error } = await supabase.storage.from('medicine-images').upload(storagePath, buffer, { contentType, upsert: true, cacheControl: '31536000' });
  if (error) throw new Error(`Image upload failed for ${id}: ${error.message}`);
  const publicUrl = supabase.storage.from('medicine-images').getPublicUrl(storagePath).data.publicUrl;
  imageUploads += 1;
  imageCache.set(cacheKey, publicUrl);
  return publicUrl;
}

const verificationRank = { unverified: 0, reviewed: 1, verified: 2, rejected: 3 };
async function preservePublishedMetadata(table, rows) {
  if (!supabase || !rows.length) return rows;
  const { data, error } = await supabase.from(table).select('id,image_url,is_published,verification_status').in('id', rows.map((row) => row.id));
  if (error) throw error;
  const existing = new Map((data || []).map((row) => [row.id, row]));
  return rows.map((row) => {
    const previous = existing.get(row.id);
    if (!previous) return row;
    const previousRank = verificationRank[previous.verification_status] ?? 0;
    const nextRank = verificationRank[row.verification_status] ?? 0;
    return {
      ...row,
      image_url: row.image_url || previous.image_url || null,
      is_published: Boolean(row.is_published || previous.is_published),
      verification_status: previousRank > nextRank ? previous.verification_status : row.verification_status,
    };
  });
}

async function flush() {
  let drugRows = [...drugs.values()];
  let productRows = [...products.values()];
  if (!dryRun) {
    if (drugRows.length) {
      drugRows = await preservePublishedMetadata('drugs', drugRows);
      const { error } = await supabase.from('drugs').upsert(drugRows, { onConflict: 'id' });
      if (error) throw error;
    }
    if (productRows.length) {
      productRows = await preservePublishedMetadata('products', productRows);
      const { error } = await supabase.from('products').upsert(productRows, { onConflict: 'id' });
      if (error) throw error;
    }
  }
  drugCount += drugRows.length;
  productCount += productRows.length;
  committedSourceRows = sourceRows;
  drugs = new Map();
  products = new Map();
  pendingSourceRows = 0;
  await updateImportJob('processing');
  writeCheckpoint(false);
  process.stdout.write(`\rRows ${committedSourceRows.toLocaleString()} · drugs ${drugCount.toLocaleString()} · products ${productCount.toLocaleString()} · skipped ${skipped.toLocaleString()} · images ${imageUploads.toLocaleString()}`);
}

function explicitId(row, field, fallback) {
  const picked = pick(row, field);
  return String(picked || fallback || '').trim();
}

async function consume(row, recordType = 'combined') {
  parsedRows += 1;
  if (parsedRows <= resumeAfterRow) return;
  sourceRows += 1;
  pendingSourceRows += 1;

  if (!row || typeof row !== 'object' || Array.isArray(row)) {
    skipped += 1;
    if (pendingSourceRows >= batchSize) await flush();
    return;
  }

  const genericName = pick(row, 'generic');
  const brandName = pick(row, 'brand');
  const declaredType = normalize(row.record_type || row.recordType || row.type);
  if (recordType === 'combined' && declaredType === 'drug') recordType = 'drug';
  if (recordType === 'combined' && (declaredType === 'product' || (!genericName && brandName && (row.drug_id || row.drugId || pick(row, 'drugId'))))) {
    recordType = 'product';
  }
  const rowDrugId = explicitId(row, 'drugId', recordType === 'drug' ? row.id : row.drug_id || row.drugId);
  const drugId = rowDrugId || (genericName ? stableId('drug', [genericName]) : '');

  if (recordType !== 'product') {
    if (!genericName || !drugId) {
      skipped += 1;
      if (pendingSourceRows >= batchSize) await flush();
      return;
    }
    const genericImageValue = pick(row, 'image');
    const drugImageValue = pick(row, 'drugImage') || (!brandName ? genericImageValue : '');
    const drugImage = await resolveImage(drugImageValue, 'drugs', drugId, [genericName]);
    const incomingDrug = {
      id: drugId,
      generic_name: genericName,
      arabic_name: pick(row, 'arabic'),
      system_id: resolveSystem(pick(row, 'system')),
      pharmacology_class: pick(row, 'class') || 'Unclassified',
      pharmacology_class_ar: pick(row, 'classAr') || 'غير مصنف',
      subclass: pick(row, 'subclass'),
      subclass_ar: pick(row, 'subclassAr'),
      aliases: list(row.aliases ?? pick(row, 'aliases')),
      description: pick(row, 'description'),
      description_ar: pick(row, 'descriptionAr'),
      mechanism: pick(row, 'mechanism'),
      mechanism_ar: pick(row, 'mechanismAr'),
      indications: list(row.indications ?? pick(row, 'indications')),
      indications_ar: list(row.indicationsAr ?? row.indications_ar ?? pick(row, 'indicationsAr')),
      cautions: list(row.cautions ?? pick(row, 'cautions')),
      cautions_ar: list(row.cautionsAr ?? row.cautions_ar ?? pick(row, 'cautionsAr')),
      interactions: list(row.interactions ?? pick(row, 'interactions')),
      dosage_note: pick(row, 'dosageNote'),
      dosage_note_ar: pick(row, 'dosageNoteAr'),
      image_url: drugImage,
      source_refs: sourceRefs(row.source_refs ?? row.sourceRefs),
      verification_status: verificationStatus(row),
      is_published: publish,
    };
    drugs.set(drugId, mergeRows(drugs.get(drugId), incomingDrug));
  }

  if (recordType === 'product' || brandName) {
    const productDrugId = rowDrugId || String(row.drug_id || row.drugId || drugId || '').trim();
    if (!productDrugId || !brandName) {
      skipped += 1;
      if (pendingSourceRows >= batchSize) await flush();
      return;
    }
    const strength = pick(row, 'strength');
    const form = pick(row, 'form');
    const manufacturer = pick(row, 'manufacturer');
    const country = pick(row, 'country');
    const barcode = pick(row, 'barcode');
    const productId = explicitId(row, 'productId', recordType === 'product' ? row.id : row.product_id || row.productId)
      || (barcode ? stableId('product', [barcode]) : stableId('product', [productDrugId, brandName, strength, form, manufacturer, country]));
    const productImageValue = pick(row, 'productImage') || pick(row, 'image');
    const productImage = await resolveImage(productImageValue, 'products', productId, [barcode, brandName]);
    const incomingProduct = {
      id: productId,
      drug_id: productDrugId,
      brand_name: brandName,
      brand_name_ar: pick(row, 'brandAr'),
      generic_name: genericName,
      strength,
      dosage_form: form,
      dosage_form_ar: pick(row, 'formAr'),
      manufacturer,
      country,
      local_agent: pick(row, 'agent'),
      market: pick(row, 'market'),
      barcode,
      registration_number: pick(row, 'registration'),
      image_url: productImage,
      leaflet_url: pick(row, 'leaflet'),
      verification_status: verificationStatus(row),
      is_published: publish,
    };
    products.set(productId, mergeRows(products.get(productId), incomingProduct));
  }

  if (pendingSourceRows >= batchSize) await flush();
}

async function importCsv() {
  const parser = fs.createReadStream(filePath).pipe(parse({
    columns: true,
    bom: true,
    skip_empty_lines: true,
    relax_column_count: true,
    trim: true,
    delimiter: extension === '.tsv' ? '\t' : ',',
  }));
  for await (const row of parser) await consume(row, 'combined');
}

const firstNonWhitespace = () => {
  const descriptor = fs.openSync(filePath, 'r');
  const buffer = Buffer.alloc(4096);
  const bytes = fs.readSync(descriptor, buffer, 0, buffer.length, 0);
  fs.closeSync(descriptor);
  return buffer.subarray(0, bytes).toString('utf8').replace(/^\uFEFF/, '').trimStart()[0];
};

async function streamJsonArray(filter, recordType) {
  const stages = [fs.createReadStream(filePath), jsonParser()];
  if (filter) stages.push(jsonPick({ filter }));
  stages.push(streamArray());
  const pipeline = chain(stages);
  for await (const entry of pipeline) await consume(entry.value, recordType);
}

async function importJson() {
  if (firstNonWhitespace() === '[') {
    await streamJsonArray(undefined, 'combined');
    return;
  }
  // Full APH backups are shaped as { drugs: [...], products: [...] }. Reading
  // the file twice keeps memory constant and avoids retaining a million-row map.
  await streamJsonArray('drugs', 'drug');
  await streamJsonArray('products', 'product');
}

async function importJsonLines() {
  const input = readline.createInterface({ input: fs.createReadStream(filePath), crlfDelay: Infinity });
  for await (const line of input) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    await consume(JSON.parse(trimmed), 'combined');
  }
}

async function importExcel() {
  const XLSX = await import('@e965/xlsx');
  const workbook = XLSX.read(fs.readFileSync(filePath), { type: 'buffer', dense: true });
  for (const sheetName of workbook.SheetNames) {
    const rows = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], { defval: '', raw: false });
    for (const row of rows) await consume(row, 'combined');
  }
}

async function main() {
  console.log(`${dryRun ? 'Validating' : 'Importing'} ${path.basename(filePath)} into APH${publish ? ' (published)' : ' (draft)'}${resume ? ` from row ${resumeAfterRow.toLocaleString()}` : ''}…`);
  await ensureImportJob();
  if (extension === '.csv' || extension === '.tsv') await importCsv();
  else if (extension === '.json') await importJson();
  else if (extension === '.jsonl' || extension === '.ndjson') await importJsonLines();
  else if (extension === '.xlsx' || extension === '.xls') await importExcel();
  else throw new Error('Supported formats: CSV, TSV, JSON, JSONL, NDJSON, XLSX, XLS');
  if (pendingSourceRows || drugs.size || products.size) await flush();
  committedSourceRows = sourceRows;
  await updateImportJob('complete');
  writeCheckpoint(true);
  console.log(`\nComplete: ${committedSourceRows.toLocaleString()} rows committed, ${drugCount.toLocaleString()} drug upserts, ${productCount.toLocaleString()} product upserts, ${skipped.toLocaleString()} skipped, ${imageUploads.toLocaleString()} images uploaded.`);
  if (warningSamples.length) console.warn(warningSamples.map((warning) => `  • ${warning}`).join('\n'));
  if (!dryRun) console.log(`Checkpoint: ${checkpointPath}\nImport job: ${jobId}`);
}

try {
  await main();
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  try {
    await updateImportJob(committedSourceRows ? 'partial' : 'failed', message.slice(0, 2000));
    writeCheckpoint(false);
  } catch (checkpointError) {
    console.error(`Could not persist failure state: ${checkpointError instanceof Error ? checkpointError.message : checkpointError}`);
  }
  console.error(`\nImport stopped after committed row ${committedSourceRows.toLocaleString()}: ${message}`);
  console.error(`Resume safely with: node scripts/import-to-supabase.mjs ${JSON.stringify(filePath)} --resume${publish ? ' --publish' : ''}`);
  process.exitCode = 1;
}
