import { createClient, type RealtimeChannel, type Session } from '@supabase/supabase-js';
import { db } from '../db/database';
import type { CloudProfile, CommunityMessage, Drug, Product, SearchResult, SourceReference } from '../types';
import { buildSearchTerms, slugify } from '../utils/text';

const supabaseUrl = (import.meta.env.VITE_SUPABASE_URL as string | undefined)?.trim();
const supabaseAnonKey = (import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined)?.trim();

export const cloudConfigured = Boolean(supabaseUrl && supabaseAnonKey && supabaseUrl.startsWith('https://'));
export const supabase = cloudConfigured ? createClient(supabaseUrl!, supabaseAnonKey!, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
  realtime: { params: { eventsPerSecond: 8 } },
}) : null;

const asArray = <T>(value: unknown): T[] => Array.isArray(value) ? value as T[] : [];
const asString = (value: unknown): string => String(value ?? '');

export function cloudRowToDrug(row: Record<string, unknown>): Drug {
  const genericName = asString(row.generic_name ?? row.genericName);
  const arabicName = asString(row.arabic_name ?? row.arabicName);
  const pharmacologyClass = asString(row.pharmacology_class ?? row.pharmacologyClass) || 'Unclassified';
  const pharmacologyClassAr = asString(row.pharmacology_class_ar ?? row.pharmacologyClassAr) || 'غير مصنف';
  const aliases = asArray<string>(row.aliases);
  return {
    id: asString(row.id) || slugify(genericName),
    genericName,
    arabicName,
    systemId: asString(row.system_id ?? row.systemId) || 'diagnostic',
    pharmacologyClass,
    pharmacologyClassAr,
    subclass: asString(row.subclass),
    subclassAr: asString(row.subclass_ar ?? row.subclassAr),
    aliases,
    description: asString(row.description),
    descriptionAr: asString(row.description_ar ?? row.descriptionAr),
    mechanism: asString(row.mechanism),
    mechanismAr: asString(row.mechanism_ar ?? row.mechanismAr),
    indications: asArray<string>(row.indications),
    indicationsAr: asArray<string>(row.indications_ar ?? row.indicationsAr),
    cautions: asArray<string>(row.cautions),
    cautionsAr: asArray<string>(row.cautions_ar ?? row.cautionsAr),
    interactions: asArray<string>(row.interactions),
    dosageNote: asString(row.dosage_note ?? row.dosageNote),
    dosageNoteAr: asString(row.dosage_note_ar ?? row.dosageNoteAr),
    image: asString(row.image_url ?? row.image) || undefined,
    searchTerms: buildSearchTerms(genericName, arabicName, aliases, pharmacologyClass, pharmacologyClassAr),
    sourceRefs: asArray<SourceReference>(row.source_refs ?? row.sourceRefs),
    locallyAdded: false,
    createdAt: asString(row.created_at ?? row.createdAt) || new Date().toISOString(),
    updatedAt: asString(row.updated_at ?? row.updatedAt) || new Date().toISOString(),
  };
}

export function cloudRowToProduct(row: Record<string, unknown>): Product {
  const brandName = asString(row.brand_name ?? row.brandName);
  const genericName = asString(row.generic_name ?? row.genericName);
  return {
    id: asString(row.id) || crypto.randomUUID(),
    drugId: asString(row.drug_id ?? row.drugId),
    brandName,
    brandNameAr: asString(row.brand_name_ar ?? row.brandNameAr),
    genericName,
    strength: asString(row.strength),
    dosageForm: asString(row.dosage_form ?? row.dosageForm),
    dosageFormAr: asString(row.dosage_form_ar ?? row.dosageFormAr),
    manufacturer: asString(row.manufacturer),
    country: asString(row.country),
    localAgent: asString(row.local_agent ?? row.localAgent),
    market: asString(row.market),
    barcode: asString(row.barcode),
    registrationNumber: asString(row.registration_number ?? row.registrationNumber),
    image: asString(row.image_url ?? row.image) || undefined,
    leafletUrl: asString(row.leaflet_url ?? row.leafletUrl),
    searchTerms: buildSearchTerms(brandName, row.brand_name_ar, genericName, row.strength, row.manufacturer, row.barcode),
    verified: asString(row.verification_status) === 'verified' || row.verified === true,
    updatedAt: asString(row.updated_at ?? row.updatedAt) || new Date().toISOString(),
  };
}

function drugToCloudRow(drug: Drug, publish = true) {
  return {
    id: drug.id,
    generic_name: drug.genericName,
    arabic_name: drug.arabicName,
    system_id: drug.systemId,
    pharmacology_class: drug.pharmacologyClass,
    pharmacology_class_ar: drug.pharmacologyClassAr,
    subclass: drug.subclass || '',
    subclass_ar: drug.subclassAr || '',
    aliases: drug.aliases,
    description: drug.description,
    description_ar: drug.descriptionAr,
    mechanism: drug.mechanism,
    mechanism_ar: drug.mechanismAr,
    indications: drug.indications,
    indications_ar: drug.indicationsAr,
    cautions: drug.cautions,
    cautions_ar: drug.cautionsAr,
    interactions: drug.interactions || [],
    dosage_note: drug.dosageNote || '',
    dosage_note_ar: drug.dosageNoteAr || '',
    image_url: drug.image || null,
    source_refs: drug.sourceRefs,
    verification_status: drug.sourceRefs.length ? 'reviewed' : 'unverified',
    is_published: publish,
  };
}

function productToCloudRow(product: Product, publish = true) {
  return {
    id: product.id,
    drug_id: product.drugId,
    brand_name: product.brandName,
    brand_name_ar: product.brandNameAr || '',
    generic_name: product.genericName,
    strength: product.strength,
    dosage_form: product.dosageForm,
    dosage_form_ar: product.dosageFormAr,
    manufacturer: product.manufacturer,
    country: product.country,
    local_agent: product.localAgent,
    market: product.market,
    barcode: product.barcode || '',
    registration_number: product.registrationNumber || '',
    image_url: product.image || null,
    leaflet_url: product.leafletUrl || '',
    verification_status: product.verified ? 'verified' : 'unverified',
    is_published: publish,
  };
}

export async function getCloudSession(): Promise<Session | null> {
  if (!supabase) return null;
  const { data } = await supabase.auth.getSession();
  return data.session;
}

export async function signInAdmin(email: string, password: string) {
  if (!supabase) throw new Error('Supabase is not configured.');
  const { data, error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) throw error;
  return data;
}

export async function signUpAdmin(email: string, password: string, displayName: string) {
  if (!supabase) throw new Error('Supabase is not configured.');
  const { data, error } = await supabase.auth.signUp({ email, password, options: { data: { display_name: displayName } } });
  if (error) throw error;
  return data;
}

export async function sendAdminOtp(email: string) {
  if (!supabase) throw new Error('Supabase is not configured.');
  const redirect = `${window.location.origin}/admin`;
  const { error } = await supabase.auth.signInWithOtp({ email, options: { emailRedirectTo: redirect, shouldCreateUser: true } });
  if (error) throw error;
}

export async function verifyAdminOtp(email: string, token: string) {
  if (!supabase) throw new Error('Supabase is not configured.');
  const { data, error } = await supabase.auth.verifyOtp({ email, token: token.trim(), type: 'email' });
  if (error) throw error;
  return data;
}

export async function signOutCloud() {
  if (supabase) await supabase.auth.signOut();
}

export async function getCloudProfile(): Promise<CloudProfile | null> {
  if (!supabase) return null;
  const { data: auth } = await supabase.auth.getUser();
  if (!auth.user) return null;
  const { data, error } = await supabase.from('profiles').select('id,email,display_name,role').eq('id', auth.user.id).maybeSingle();
  if (error || !data) return null;
  return { id: data.id, email: data.email || auth.user.email || '', displayName: data.display_name || '', role: data.role } as CloudProfile;
}

async function readCloudTable(table: 'drugs' | 'products', since?: string, maxRows = 10_000) {
  if (!supabase) return [];
  const records: Record<string, unknown>[] = [];
  const pageSize = 1000;
  for (let from = 0; from < maxRows; from += pageSize) {
    let query = supabase.from(table).select('*').eq('is_published', true).order('updated_at', { ascending: false }).order('id', { ascending: true }).range(from, Math.min(from + pageSize - 1, maxRows - 1));
    // Include the boundary again. IndexedDB bulkPut makes the overlap idempotent and
    // avoids dropping rows that share a bulk-import timestamp.
    if (since) query = query.gte('updated_at', since);
    const { data, error } = await query;
    if (error) throw error;
    records.push(...(data || []));
    if (!data || data.length < pageSize) break;
  }
  return records;
}

async function readCatalogTombstones(since?: string, maxRows = 10_000) {
  if (!supabase) return [];
  let query = supabase.from('catalog_tombstones').select('entity_type,entity_id,deleted_at').order('deleted_at', { ascending: false }).limit(maxRows);
  if (since) query = query.gte('deleted_at', since);
  const { data, error } = await query;
  if (error) throw error;
  return (data || []) as Array<{ entity_type: 'drug' | 'product'; entity_id: string; deleted_at: string }>;
}

export async function syncCloudCatalog(options: { since?: string; maxRows?: number } = {}) {
  if (!supabase) return { drugs: 0, products: 0, removed: 0, syncedAt: undefined };
  // Save the time at which this snapshot began, not when it ended. Any mutation
  // made while pages are loading is therefore picked up by the next incremental run.
  const syncStartedAt = new Date().toISOString();
  const maxRows = options.maxRows ?? 10_000;
  const [drugRows, productRows, tombstones] = await Promise.all([
    readCloudTable('drugs', options.since, maxRows),
    readCloudTable('products', options.since, maxRows * 3),
    readCatalogTombstones(options.since, maxRows),
  ]);
  const drugs = drugRows.map(cloudRowToDrug);
  const products = productRows.map(cloudRowToProduct);
  await db.transaction('rw', db.drugs, db.products, db.meta, async () => {
    for (const tombstone of tombstones) {
      if (tombstone.entity_type === 'product') await db.products.delete(tombstone.entity_id);
      else {
        await db.products.where('drugId').equals(tombstone.entity_id).delete();
        await db.drugs.delete(tombstone.entity_id);
      }
    }
    if (drugs.length) await db.drugs.bulkPut(drugs);
    if (products.length) await db.products.bulkPut(products);
    await db.meta.put({ key: 'lastCloudSync', value: syncStartedAt });
  });
  return { drugs: drugs.length, products: products.length, removed: tombstones.length, syncedAt: syncStartedAt };
}

export async function hydrateDrugFromCloud(drugId: string, maxProducts = 2000, includeUnpublished = false): Promise<Drug | undefined> {
  if (!supabase) return undefined;
  let drugQuery = supabase.from('drugs').select('*').eq('id', drugId);
  let productQuery = supabase.from('products').select('*').eq('drug_id', drugId);
  if (!includeUnpublished) {
    drugQuery = drugQuery.eq('is_published', true);
    productQuery = productQuery.eq('is_published', true);
  }
  const [{ data: drugRow, error: drugError }, { data: productRows, error: productError }] = await Promise.all([
    drugQuery.maybeSingle(),
    productQuery.order('brand_name').limit(maxProducts),
  ]);
  if (drugError) throw drugError;
  if (productError) throw productError;
  if (!drugRow) return undefined;
  const drug = cloudRowToDrug(drugRow);
  const products = (productRows || []).map(cloudRowToProduct);
  await db.transaction('rw', db.drugs, db.products, async () => {
    await db.drugs.put(drug);
    if (products.length) await db.products.bulkPut(products);
  });
  return drug;
}

export async function searchCloudCatalog(query: string, limit = 50): Promise<SearchResult[]> {
  if (!supabase || query.trim().length < 2) return [];
  const { data, error } = await supabase.rpc('search_medicines', { search_query: query.trim(), max_results: limit });
  if (error) throw error;
  const results: SearchResult[] = (data || []).map((row: { drug: Record<string, unknown>; matched_product?: Record<string, unknown>; score?: number; matched_on?: string }) => ({
    drug: cloudRowToDrug(row.drug),
    matchedProduct: row.matched_product ? cloudRowToProduct(row.matched_product) : undefined,
    score: 1 - Number(row.score || 0),
    matchedOn: row.matched_on || 'cloud',
  }));
  await db.transaction('rw', db.drugs, db.products, async () => {
    if (results.length) await db.drugs.bulkPut(results.map((result) => result.drug));
    const matchedProducts = results.flatMap((result) => result.matchedProduct ? [result.matchedProduct] : []);
    if (matchedProducts.length) await db.products.bulkPut(matchedProducts);
  });
  return results;
}

export function subscribeToCatalog(onChange: () => void): RealtimeChannel | undefined {
  if (!supabase) return undefined;
  let refreshTimer: number | undefined;
  const schedule = () => {
    window.clearTimeout(refreshTimer);
    refreshTimer = window.setTimeout(onChange, 500);
  };
  return supabase.channel('aph-public-catalog')
    .on('postgres_changes', { event: '*', schema: 'public', table: 'drugs' }, schedule)
    .on('postgres_changes', { event: '*', schema: 'public', table: 'products' }, schedule)
    .on('postgres_changes', { event: '*', schema: 'public', table: 'catalog_tombstones' }, schedule)
    .subscribe();
}

export async function uploadCatalogImage(dataUrl: string, pathPrefix: string): Promise<string> {
  if (!supabase || !dataUrl.startsWith('data:image/')) return dataUrl;
  const match = dataUrl.match(/^data:(image\/(?:png|jpeg|webp));base64,(.+)$/);
  if (!match) return dataUrl;
  const binary = atob(match[2]);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  const extension = match[1].split('/')[1].replace('jpeg', 'jpg');
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const hash = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  const path = `${pathPrefix}/${hash}.${extension}`;
  const { error } = await supabase.storage.from('medicine-images').upload(path, bytes, { contentType: match[1], cacheControl: '31536000', upsert: true });
  if (error) throw error;
  return supabase.storage.from('medicine-images').getPublicUrl(path).data.publicUrl;
}

export async function cloudUpsertDrug(drug: Drug, publish = true): Promise<Drug> {
  if (!supabase) throw new Error('Cloud database is not configured.');
  const image = drug.image?.startsWith('data:image/') ? await uploadCatalogImage(drug.image, `drugs/${drug.id}`) : drug.image;
  const payload = drugToCloudRow({ ...drug, image }, publish);
  const { data, error } = await supabase.from('drugs').upsert(payload).select('*').single();
  if (error) throw error;
  return cloudRowToDrug(data);
}

export async function cloudUpsertProduct(product: Product, publish = true): Promise<Product> {
  if (!supabase) throw new Error('Cloud database is not configured.');
  const image = product.image?.startsWith('data:image/') ? await uploadCatalogImage(product.image, `products/${product.drugId}`) : product.image;
  const payload = productToCloudRow({ ...product, image }, publish);
  const { data, error } = await supabase.from('products').upsert(payload).select('*').single();
  if (error) throw error;
  return cloudRowToProduct(data);
}

export async function cloudUnpublishDrug(drugId: string) {
  if (!supabase) throw new Error('Cloud database is not configured.');
  const { error } = await supabase.rpc('admin_unpublish_drug', { p_drug_id: drugId });
  if (error) throw error;
}

export async function cloudUnpublishProduct(productId: string) {
  if (!supabase) throw new Error('Cloud database is not configured.');
  const { error } = await supabase.from('products').update({ is_published: false }).eq('id', productId);
  if (error) throw error;
}

async function mapWithConcurrency<T, R>(items: T[], concurrency: number, mapper: (item: T) => Promise<R>): Promise<R[]> {
  const output = new Array<R>(items.length);
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor++;
      output[index] = await mapper(items[index]);
    }
  }));
  return output;
}

export async function publishLocalCatalog(drugs: Drug[], products: Product[], onProgress?: (done: number, total: number) => void) {
  if (!supabase) throw new Error('Cloud database is not configured.');
  const total = drugs.length + products.length;
  let done = 0;
  for (let index = 0; index < drugs.length; index += 100) {
    const rows = await mapWithConcurrency(drugs.slice(index, index + 100), 6, async (drug) => {
      const image = drug.image?.startsWith('data:image/') ? await uploadCatalogImage(drug.image, `drugs/${drug.id}`) : drug.image;
      return drugToCloudRow({ ...drug, image }, true);
    });
    const { error } = await supabase.from('drugs').upsert(rows);
    if (error) throw error;
    done += rows.length;
    onProgress?.(done, total);
  }
  for (let index = 0; index < products.length; index += 100) {
    const rows = await mapWithConcurrency(products.slice(index, index + 100), 6, async (product) => {
      const image = product.image?.startsWith('data:image/') ? await uploadCatalogImage(product.image, `products/${product.drugId}`) : product.image;
      return productToCloudRow({ ...product, image }, true);
    });
    const { error } = await supabase.from('products').upsert(rows);
    if (error) throw error;
    done += rows.length;
    onProgress?.(done, total);
  }
  return { drugs: drugs.length, products: products.length };
}

export async function getCloudStats() {
  if (!supabase) return { drugs: 0, products: 0, messages: 0 };
  const [drugs, products, messages] = await Promise.all([
    supabase.from('drugs').select('*', { count: 'exact', head: true }),
    supabase.from('products').select('*', { count: 'exact', head: true }),
    supabase.from('messages').select('*', { count: 'exact', head: true }),
  ]);
  const error = drugs.error || products.error || messages.error;
  if (error) throw error;
  return { drugs: drugs.count || 0, products: products.count || 0, messages: messages.count || 0 };
}

export function deviceId(): string {
  let value = localStorage.getItem('aph-device-id');
  if (!value) { value = crypto.randomUUID(); localStorage.setItem('aph-device-id', value); }
  return value;
}

function rowToMessage(row: Record<string, unknown>): CommunityMessage {
  return {
    id: asString(row.id), parentId: asString(row.parent_id) || undefined, deviceId: asString(row.device_id),
    userId: asString(row.user_id) || undefined, authorName: asString(row.author_name) || 'APH user', authorEmail: asString(row.author_email) || undefined,
    body: asString(row.body), messageType: (row.message_type || 'question') as CommunityMessage['messageType'], isPublic: row.is_public !== false,
    status: (row.status || 'open') as CommunityMessage['status'], createdAt: asString(row.created_at), updatedAt: asString(row.updated_at),
  };
}

const publicMessageColumns = 'id,parent_id,device_id,user_id,author_name,body,message_type,is_public,status,created_at,updated_at';

export async function listCommunityMessages(limit = 100): Promise<CommunityMessage[]> {
  if (!supabase) return [];
  // Select an explicit public projection. Contact email addresses live in the
  // administrator-only message_contacts table and can never leak through `*`.
  const { data, error } = await supabase.from('messages').select(publicMessageColumns).eq('is_public', true).neq('status', 'hidden').order('created_at', { ascending: false }).limit(limit);
  if (error) throw error;
  return (data || []).map(rowToMessage);
}

export async function listAdminMessages(limit = 250): Promise<CommunityMessage[]> {
  if (!supabase) return [];
  const { data, error } = await supabase.from('messages')
    .select(`${publicMessageColumns},message_contacts(author_email)`)
    .order('created_at', { ascending: false })
    .limit(limit);
  if (error) throw error;
  return (data || []).map((row) => {
    const contact = row.message_contacts as { author_email?: string } | Array<{ author_email?: string }> | null;
    const authorEmail = Array.isArray(contact) ? contact[0]?.author_email : contact?.author_email;
    return rowToMessage({ ...row, author_email: authorEmail });
  });
}

export async function sendCommunityMessage(input: { body: string; authorName?: string; authorEmail?: string }) {
  if (!supabase) throw new Error('Cloud messaging is not configured.');
  const { data, error } = await supabase.rpc('submit_public_message', {
    p_device_id: deviceId(),
    p_author_name: input.authorName?.trim() || 'APH user',
    p_author_email: input.authorEmail?.trim() || '',
    p_body: input.body.trim(),
  });
  if (error) throw error;
  return rowToMessage(data as Record<string, unknown>);
}

export async function replyToMessage(parentId: string, body: string, isPublic = true) {
  if (!supabase) throw new Error('Cloud messaging is not configured.');
  const profile = await getCloudProfile();
  const { data, error } = await supabase.from('messages').insert({
    parent_id: parentId, device_id: deviceId(), author_name: profile?.displayName || 'Dr. Abdulmajeed',
    body: body.trim(), message_type: 'reply', is_public: isPublic, status: 'answered',
  }).select(publicMessageColumns).single();
  if (error) throw error;
  await supabase.from('messages').update({ status: 'answered' }).eq('id', parentId);
  return rowToMessage(data);
}

export function subscribeToMessages(onChange: () => void): RealtimeChannel | undefined {
  return supabase?.channel('aph-community').on('postgres_changes', { event: '*', schema: 'public', table: 'messages' }, onChange).subscribe();
}

export async function invokeCloudAi(payload: Record<string, unknown>) {
  if (!supabase) return undefined;
  const { data, error } = await supabase.functions.invoke('ai-assistant', { body: payload, headers: { 'x-device-id': deviceId() } });
  if (error) throw error;
  return data as { answer?: string; sources?: SourceReference[]; relatedDrugId?: string; grounded?: boolean; error?: string };
}

export async function invokeCloudEnrichment(name: string) {
  if (!supabase) return undefined;
  const { data, error } = await supabase.functions.invoke('enrich-medicine', { body: { name }, headers: { 'x-device-id': deviceId() } });
  if (error) throw error;
  return data;
}

async function prepareVisionImage(file: File): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file);
    const maxDimension = 1800;
    const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height));
    if (file.size <= 3_500_000 && scale === 1) {
      bitmap.close();
      return file;
    }
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Canvas is unavailable.');
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return await new Promise<Blob>((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error('Could not prepare image.')), 'image/jpeg', 0.82));
  } catch {
    return file;
  }
}

export async function identifyProductWithGoogle(file: File) {
  if (!supabase) return undefined;
  const prepared = await prepareVisionImage(file);
  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(prepared);
  });
  const { data, error } = await supabase.functions.invoke('identify-product', { body: { imageBase64: dataUrl, mimeType: prepared.type }, headers: { 'x-device-id': deviceId() } });
  if (error) throw error;
  return data as { detectedText?: string; catalogMatches?: Array<{ drug: Record<string, unknown>; matched_product?: Record<string, unknown>; score?: number; matched_on?: string }>; provider?: string; error?: string };
}
