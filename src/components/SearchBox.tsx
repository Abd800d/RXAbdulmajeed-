import { Camera, LoaderCircle, Search, Sparkles, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useAph } from '../context/AphContext';
import { bodySystems, searchCatalog } from '../db/database';
import type { Drug, SearchResult } from '../types';
import { MedicineVisual } from './ui';

interface SearchBoxProps {
  value?: string;
  onChange?: (value: string) => void;
  onSelect: (drug: Drug) => void;
  onScan: () => void;
  autoFocus?: boolean;
  variant?: 'hero' | 'compact';
  showDropdown?: boolean;
}

export function SearchBox({ value, onChange, onSelect, onScan, autoFocus = false, variant = 'hero', showDropdown = true }: SearchBoxProps) {
  const { t, language } = useAph();
  const [internal, setInternal] = useState(value ?? '');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [focused, setFocused] = useState(false);
  const requestId = useRef(0);
  const query = value === undefined ? internal : value;

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      setLoading(false);
      return;
    }
    const current = ++requestId.current;
    setLoading(true);
    const timer = window.setTimeout(async () => {
      const next = await searchCatalog(query, 6);
      if (current === requestId.current) {
        setResults(next);
        setLoading(false);
      }
    }, 170);
    return () => window.clearTimeout(timer);
  }, [query]);

  const update = (next: string) => {
    if (value === undefined) setInternal(next);
    onChange?.(next);
  };

  const pick = (drug: Drug) => {
    update('');
    setFocused(false);
    onSelect(drug);
  };

  return (
    <div className={`search-box search-box--${variant}`}>
      <div className="search-box__field">
        {loading ? <LoaderCircle className="spin" size={21} /> : <Search size={21} />}
        <input
          value={query}
          onChange={(event) => update(event.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => window.setTimeout(() => setFocused(false), 160)}
          placeholder={t('searchPlaceholder')}
          autoFocus={autoFocus}
          aria-label={t('searchPlaceholder')}
        />
        {query && <button className="search-box__clear" onClick={() => update('')} aria-label={t('clearSearch')}><X size={17} /></button>}
        <button className="search-box__camera" onClick={onScan} aria-label={t('scan')}><Camera size={20} /></button>
      </div>
      {variant === 'hero' && <div className="search-box__hint"><Sparkles size={13} /> {t('typoHint')}</div>}
      {showDropdown && focused && query.length >= 2 && (
        <div className="search-box__results">
          {results.length ? results.map(({ drug, matchedProduct }) => {
            const system = bodySystems.find((item) => item.id === drug.systemId);
            return (
              <button key={drug.id} onMouseDown={(event) => event.preventDefault()} onClick={() => pick(drug)}>
                <MedicineVisual drug={drug} product={matchedProduct} size="small" />
                <span>
                  <strong>{matchedProduct?.brandName || (language === 'ar' && drug.arabicName ? drug.arabicName : drug.genericName)}</strong>
                  <small>{matchedProduct ? `${drug.genericName} · ${matchedProduct.strength}` : (language === 'ar' ? drug.pharmacologyClassAr : drug.pharmacologyClass)}</small>
                </span>
                {system && <em style={{ color: system.color }}>{language === 'ar' ? system.shortNameAr : system.shortName}</em>}
              </button>
            );
          }) : !loading && (
            <div className="search-box__no-results">
              <Search size={20} />
              <span><strong>{t('noResults')}</strong><small>{t('noResultsBody')}</small></span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
