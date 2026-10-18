import { ChevronRight, Filter, LibraryBig, Plus, SearchX, SlidersHorizontal } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { SearchBox } from '../components/SearchBox';
import { EmptyState, MedicineVisual, SystemIcon } from '../components/ui';
import { useAph } from '../context/AphContext';
import { bodySystems, searchCatalog } from '../db/database';
import type { Drug, SearchResult } from '../types';

interface CatalogScreenProps {
  onOpenDrug: (drug: Drug) => void;
  onScan: () => void;
  onAddMedicine: () => void;
}

export function CatalogScreen({ onOpenDrug, onScan, onAddMedicine }: CatalogScreenProps) {
  const { t, language, isRtl, drugs, products } = useAph();
  const [selectedSystem, setSelectedSystem] = useState(() => sessionStorage.getItem('aph-selected-system') || 'all');
  const [selectedClass, setSelectedClass] = useState('all');
  const [query, setQuery] = useState('');
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    sessionStorage.removeItem('aph-selected-system');
  }, []);

  useEffect(() => {
    if (query.trim().length < 2) {
      setSearchResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const timer = window.setTimeout(async () => {
      setSearchResults(await searchCatalog(query, 120));
      setSearching(false);
    }, 180);
    return () => window.clearTimeout(timer);
  }, [query]);

  const classes = useMemo(() => Array.from(new Set(drugs
    .filter((drug) => selectedSystem === 'all' || drug.systemId === selectedSystem)
    .map((drug) => language === 'ar' ? drug.pharmacologyClassAr : drug.pharmacologyClass)
    .filter(Boolean))), [drugs, selectedSystem, language]);

  const visible = useMemo(() => {
    const source = query.trim().length >= 2 ? searchResults.map((result) => result.drug) : drugs;
    return source.filter((drug) => {
      const systemMatch = selectedSystem === 'all' || drug.systemId === selectedSystem;
      const className = language === 'ar' ? drug.pharmacologyClassAr : drug.pharmacologyClass;
      return systemMatch && (selectedClass === 'all' || className === selectedClass);
    });
  }, [query, searchResults, drugs, selectedSystem, selectedClass, language]);

  const selectedSystemData = bodySystems.find((system) => system.id === selectedSystem);

  return (
    <div className="screen catalog-screen">
      <div className="screen-title-row">
        <div><span className="section-kicker">APH · KNOWLEDGE BASE</span><h1>{t('catalogTitle')}</h1><p>{t('catalogSubtitle')}</p></div>
        <button className="primary-button desktop-only" onClick={onAddMedicine}><Plus size={17} /> {t('addMedicine')}</button>
      </div>

      <div className="catalog-toolbar">
        <SearchBox value={query} onChange={setQuery} onSelect={onOpenDrug} onScan={onScan} variant="compact" showDropdown={false} />
        <button className="filter-button" aria-label="Filters"><SlidersHorizontal size={19} /><span>{t('allClasses')}</span></button>
      </div>

      <div className="system-filter" aria-label={t('browseSystems')}>
        <button className={selectedSystem === 'all' ? 'is-active' : ''} onClick={() => { setSelectedSystem('all'); setSelectedClass('all'); }}>
          <span><LibraryBig size={19} /></span><strong>{t('allSystems')}</strong><small>{drugs.length}</small>
        </button>
        {bodySystems.map((system) => {
          const count = drugs.filter((drug) => drug.systemId === system.id).length;
          return (
            <button key={system.id} className={selectedSystem === system.id ? 'is-active' : ''} style={{ '--system-color': system.color, '--system-tint': system.tint } as React.CSSProperties} onClick={() => { setSelectedSystem(system.id); setSelectedClass('all'); }}>
              <span><SystemIcon system={system} size={19} /></span>
              <strong>{language === 'ar' ? system.shortNameAr : system.shortName}</strong><small>{count}</small>
            </button>
          );
        })}
      </div>

      <div className="class-filter">
        <button className={selectedClass === 'all' ? 'is-active' : ''} onClick={() => setSelectedClass('all')}><Filter size={13} /> {t('allClasses')}</button>
        {classes.map((className) => <button key={className} className={selectedClass === className ? 'is-active' : ''} onClick={() => setSelectedClass(className)}>{className}</button>)}
      </div>

      <div className="catalog-results-heading">
        <div>
          {selectedSystemData && <span style={{ color: selectedSystemData.color }}><SystemIcon system={selectedSystemData} size={17} /></span>}
          <strong>{selectedSystemData ? (language === 'ar' ? selectedSystemData.nameAr : selectedSystemData.name) : t('allSystems')}</strong>
        </div>
        <small>{searching ? '…' : visible.length} {visible.length === 1 ? t('result') : t('results')}</small>
      </div>

      {visible.length ? (
        <div className="drug-grid">
          {visible.map((drug) => {
            const system = bodySystems.find((item) => item.id === drug.systemId)!;
            const drugProducts = products.filter((item) => item.drugId === drug.id);
            const matched = searchResults.find((result) => result.drug.id === drug.id)?.matchedProduct;
            const heroProduct = matched || drugProducts[0];
            return (
              <button className="drug-list-card" key={drug.id} onClick={() => onOpenDrug(drug)}>
                <MedicineVisual drug={drug} product={heroProduct} size="medium" />
                <span className="drug-list-card__copy">
                  <span className="drug-list-card__system" style={{ color: system.color }}><SystemIcon system={system} size={13} /> {language === 'ar' ? system.shortNameAr : system.shortName}</span>
                  <strong>{matched?.brandName || (language === 'ar' && drug.arabicName ? drug.arabicName : drug.genericName)}</strong>
                  <em>{matched ? `${drug.genericName} · ${matched.strength}` : (language === 'ar' ? drug.pharmacologyClassAr : drug.pharmacologyClass)}</em>
                  <small>{drugProducts.length} {t('products')}</small>
                </span>
                <ChevronRight size={18} className={isRtl ? 'flip' : ''} />
              </button>
            );
          })}
        </div>
      ) : (
        <EmptyState
          icon={<SearchX size={28} />}
          title={t('noResults')}
          body={t('noResultsBody')}
          action={<button className="secondary-button" onClick={() => { setQuery(''); setSelectedSystem('all'); setSelectedClass('all'); }}>{t('clearSearch')}</button>}
        />
      )}

      <button className="floating-add mobile-only" onClick={onAddMedicine}><Plus size={22} /></button>
    </div>
  );
}
