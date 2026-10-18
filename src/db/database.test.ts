import 'fake-indexeddb/auto';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { db, getStats, initializeDatabase, searchCatalog } from './database';
import { importDatabaseFile } from '../services/importExport';

beforeAll(async () => {
  await db.delete();
  await initializeDatabase();
});

afterAll(async () => {
  await db.delete();
});

describe('offline medicine database', () => {
  it('seeds scientific drugs and trade products', async () => {
    const stats = await getStats();
    expect(stats.drugCount).toBeGreaterThanOrEqual(20);
    expect(stats.productCount).toBeGreaterThanOrEqual(20);
  });

  it('finds misspelled scientific names', async () => {
    const results = await searchCatalog('paracitamol');
    expect(results[0]?.drug.id).toBe('paracetamol');
  });

  it('finds trade names and Arabic names', async () => {
    const brand = await searchCatalog('Panadol');
    const arabic = await searchCatalog('ميتفورمن');
    expect(brand[0]?.drug.id).toBe('paracetamol');
    expect(arabic[0]?.drug.id).toBe('metformin');
  });

  it('restores full JSON backups without losing images or sources', async () => {
    const backup = {
      schemaVersion: 1,
      drugs: [{
        id: 'test-medicine', genericName: 'Test Medicine', arabicName: 'دواء اختباري', systemId: 'nervous',
        pharmacologyClass: 'Test class', pharmacologyClassAr: 'مجموعة اختبارية', aliases: ['TestMed'],
        description: 'Full saved description', descriptionAr: 'وصف محفوظ', mechanism: 'Test mechanism', mechanismAr: 'آلية اختبارية',
        indications: ['Test indication'], indicationsAr: ['استطباب اختباري'], cautions: ['Test caution'], cautionsAr: ['تحذير اختباري'],
        interactions: [], dosageNote: 'Test dose note', dosageNoteAr: 'ملاحظة جرعة', image: 'data:image/png;base64,AA==',
        searchTerms: ['test medicine'], sourceRefs: [{ label: 'Test source', url: 'https://example.com', authority: 'Other' }],
        locallyAdded: true, createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-02T00:00:00.000Z',
      }],
      products: [{
        id: 'test-product', drugId: 'test-medicine', brandName: 'Test Brand', genericName: 'Test Medicine', strength: '10 mg',
        dosageForm: 'Tablet', dosageFormAr: 'أقراص', manufacturer: 'Test Labs', country: 'Yemen', localAgent: 'Test Agent',
        market: 'Yemen', image: 'data:image/png;base64,BB==', searchTerms: ['test brand'], verified: true, updatedAt: '2026-01-02T00:00:00.000Z',
      }],
    };
    const file = new File([JSON.stringify(backup)], 'backup.json', { type: 'application/json' });
    await importDatabaseFile(file, () => undefined);
    const restoredDrug = await db.drugs.get('test-medicine');
    const restoredProduct = await db.products.get('test-product');
    expect(restoredDrug?.description).toBe('Full saved description');
    expect(restoredDrug?.sourceRefs[0]?.label).toBe('Test source');
    expect(restoredDrug?.image).toContain('data:image/png');
    expect(restoredProduct?.localAgent).toBe('Test Agent');
    expect(restoredProduct?.image).toContain('data:image/png');
  });
});
