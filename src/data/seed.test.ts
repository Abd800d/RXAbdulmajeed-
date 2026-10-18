import { describe, expect, it } from 'vitest';
import { bodySystems, seedDrugs, seedProducts } from './seed';

describe('APH starter catalog', () => {
  it('covers every requested body-system category', () => {
    expect(bodySystems).toHaveLength(18);
    const represented = new Set(seedDrugs.map((drug) => drug.systemId));
    bodySystems.forEach((system) => expect(represented.has(system.id)).toBe(true));
  });

  it('uses unique ids and valid product relationships', () => {
    expect(new Set(seedDrugs.map((drug) => drug.id)).size).toBe(seedDrugs.length);
    expect(new Set(seedProducts.map((product) => product.id)).size).toBe(seedProducts.length);
    const drugIds = new Set(seedDrugs.map((drug) => drug.id));
    seedProducts.forEach((product) => expect(drugIds.has(product.drugId)).toBe(true));
  });

  it('includes bilingual safety and source information', () => {
    seedDrugs.forEach((drug) => {
      expect(drug.genericName).toBeTruthy();
      expect(drug.arabicName).toBeTruthy();
      expect(drug.cautions.length).toBeGreaterThan(0);
      expect(drug.cautionsAr.length).toBeGreaterThan(0);
      expect(drug.sourceRefs.length).toBeGreaterThanOrEqual(2);
    });
  });
});
