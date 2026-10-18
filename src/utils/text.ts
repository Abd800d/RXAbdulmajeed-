export const normalizeText = (value: unknown): string => String(value ?? '')
  .toLocaleLowerCase()
  .normalize('NFKD')
  .replace(/[\u0300-\u036f\u064B-\u065F\u0670]/g, '')
  .replace(/[إأآٱ]/g, 'ا')
  .replace(/ة/g, 'ه')
  .replace(/ى/g, 'ي')
  .replace(/ؤ/g, 'و')
  .replace(/ئ/g, 'ي')
  .replace(/[^\p{L}\p{N}]+/gu, ' ')
  .trim()
  .replace(/\s+/g, ' ');

export const slugify = (value: string): string => {
  const normalized = normalizeText(value).replace(/\s+/g, '-');
  return normalized || `record-${crypto.randomUUID()}`;
};

export const buildSearchTerms = (...values: Array<unknown>): string[] => {
  const terms = new Set<string>();
  values.flat(Infinity).forEach((value) => {
    const normalized = normalizeText(value);
    if (!normalized) return;
    terms.add(normalized);
    normalized.split(' ').filter((token) => token.length > 1).forEach((token) => terms.add(token));
  });
  return [...terms];
};

export const initials = (value: string): string => value
  .split(/\s+/)
  .filter(Boolean)
  .slice(0, 2)
  .map((part) => part[0]?.toUpperCase())
  .join('');

export const formatCount = (value: number, locale = 'en'): string =>
  new Intl.NumberFormat(locale === 'ar' ? 'ar-YE' : 'en-US', { notation: value > 9999 ? 'compact' : 'standard' }).format(value);

export const fileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
};
