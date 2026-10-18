import { describe, expect, it } from 'vitest';
import { buildSearchTerms, normalizeText, slugify } from './text';

describe('text normalization', () => {
  it('normalizes Arabic variants and diacritics', () => {
    expect(normalizeText('  أَمْلُودِيبِين  ')).toBe('املوديبين');
    expect(normalizeText('إبرة دوائية')).toBe('ابره دواييه');
  });

  it('builds searchable full phrases and tokens', () => {
    expect(buildSearchTerms('Amoxicillin Clavulanate', ['Augmentin'])).toEqual(
      expect.arrayContaining(['amoxicillin clavulanate', 'amoxicillin', 'clavulanate', 'augmentin']),
    );
  });

  it('creates stable human-readable ids', () => {
    expect(slugify('Insulin Glargine')).toBe('insulin-glargine');
  });
});
