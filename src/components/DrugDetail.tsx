import {
  AlertTriangle, ArrowLeft, BadgeCheck, Beaker, Bookmark, BookOpenCheck, Building2,
  CalendarClock, Check, ChevronRight, CircleAlert, ExternalLink, Factory, Globe2,
  HeartPulse, Images as ImageSearch, Info, MapPin, PackagePlus, Pencil, Pill, Share2, ShieldAlert,
  Sparkles, X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { useAph } from '../context/AphContext';
import { bodySystems, db } from '../db/database';
import { googleImagesUrl, pubChemImageUrl } from '../services/trustedSources';
import type { Drug, Product } from '../types';
import { MedicineVisual, SystemBadge } from './ui';

type DetailTab = 'overview' | 'products' | 'safety' | 'sources';

interface DrugDetailProps {
  drug: Drug;
  onClose: () => void;
  onEdit: (drug: Drug) => void;
  onAddProduct: (drug: Drug) => void;
  onEditProduct: (drug: Drug, product: Product) => void;
}

export function DrugDetail({ drug, onClose, onEdit, onAddProduct, onEditProduct }: DrugDetailProps) {
  const { t, language, isRtl, products, favorites, toggleFavorite, showToast } = useAph();
  const [tab, setTab] = useState<DetailTab>('overview');
  const [drugProducts, setDrugProducts] = useState<Product[]>(() => products.filter((product) => product.drugId === drug.id));
  const system = bodySystems.find((item) => item.id === drug.systemId) ?? bodySystems[17];
  const isFavorite = favorites.includes(drug.id);
  const heroProduct = drugProducts[0];

  useEffect(() => {
    let active = true;
    void db.products.where('drugId').equals(drug.id).sortBy('brandName').then((records) => {
      if (active) setDrugProducts(records);
    });
    return () => { active = false; };
  }, [drug.id, products]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const share = async () => {
    const payload = { title: `${drug.genericName} · APH`, text: `${drug.genericName} — ${drug.pharmacologyClass}\nAPH · AbdulmajeedPharma`, url: window.location.href };
    try {
      if (navigator.share) await navigator.share(payload);
      else {
        await navigator.clipboard.writeText(`${payload.text}\n${payload.url}`);
        showToast(language === 'ar' ? 'تم نسخ رابط الدواء' : 'Medicine link copied');
      }
    } catch { /* share cancelled */ }
  };

  const tabs: Array<{ id: DetailTab; label: string; count?: number }> = [
    { id: 'overview', label: t('detailOverview') },
    { id: 'products', label: t('detailProducts'), count: drugProducts.length },
    { id: 'safety', label: t('detailSafety') },
    { id: 'sources', label: t('detailSources'), count: drug.sourceRefs.length },
  ];

  return (
    <div className="detail-layer" role="dialog" aria-modal="true" aria-labelledby="drug-detail-title">
      <button className="detail-layer__scrim" onClick={onClose} aria-label={t('close')} />
      <article className="drug-detail">
        <div className="drug-detail__topbar">
          <button className="detail-back" onClick={onClose}>{isRtl ? <ChevronRight size={20} /> : <ArrowLeft size={20} />}<span>{t('catalog')}</span></button>
          <div className="drug-detail__topactions">
            <button onClick={share} aria-label={t('share')}><Share2 size={18} /></button>
            <button onClick={() => onEdit(drug)} aria-label={t('editMedicine')}><Pencil size={18} /></button>
            <button className="detail-close" onClick={onClose} aria-label={t('close')}><X size={20} /></button>
          </div>
        </div>

        <header className="drug-detail__hero" style={{ '--system-color': system.color, '--system-tint': system.tint } as React.CSSProperties}>
          <div className="drug-detail__visual-wrap">
            <span className="drug-detail__image-label"><ImageSearch size={13} /> {language === 'ar' ? 'صورة المنتج' : 'PRODUCT VIEW'}</span>
            <MedicineVisual drug={drug} product={heroProduct} size="large" />
          </div>
          <div className="drug-detail__identity">
            <SystemBadge system={system} label={language === 'ar' ? system.nameAr : system.name} />
            <h1 id="drug-detail-title">{drug.genericName}</h1>
            {drug.arabicName && <p className="drug-detail__arabic" lang="ar">{drug.arabicName}</p>}
            <div className="drug-detail__class"><span>{language === 'ar' ? drug.pharmacologyClassAr : drug.pharmacologyClass}</span>{drug.subclass && <><i /> <span>{language === 'ar' ? drug.subclassAr || drug.subclass : drug.subclass}</span></>}</div>
            <p className="drug-detail__summary">{language === 'ar' ? drug.descriptionAr || drug.description : drug.description}</p>
            <div className="drug-detail__hero-actions">
              <button className={isFavorite ? 'is-active' : ''} onClick={() => toggleFavorite(drug.id)}>
                {isFavorite ? <Check size={17} /> : <Bookmark size={17} />}{isFavorite ? t('favorited') : t('favorite')}
              </button>
              <button onClick={() => onAddProduct(drug)}><PackagePlus size={17} /> {t('addProduct')}</button>
            </div>
          </div>
          <div className="drug-detail__verified"><BookOpenCheck size={16} /><span><strong>{t('sourceBacked')}</strong><small>NLM · FDA</small></span></div>
        </header>

        <nav className="drug-detail__tabs">
          {tabs.map((item) => <button key={item.id} className={tab === item.id ? 'is-active' : ''} onClick={() => setTab(item.id)}>{item.label}{item.count !== undefined && <span>{item.count}</span>}</button>)}
        </nav>

        <div className="drug-detail__body">
          {tab === 'overview' && (
            <div className="detail-overview">
              <section className="info-panel info-panel--mechanism">
                <div className="info-panel__icon"><Beaker size={22} /></div>
                <div><span className="info-panel__eyebrow">PHARMACOLOGY</span><h2>{t('mechanism')}</h2><p>{language === 'ar' ? drug.mechanismAr || drug.mechanism : drug.mechanism}</p></div>
              </section>
              <section className="info-panel">
                <div className="info-panel__icon"><HeartPulse size={22} /></div>
                <div><span className="info-panel__eyebrow">CLINICAL USE</span><h2>{t('indications')}</h2><ul>{(language === 'ar' && drug.indicationsAr.length ? drug.indicationsAr : drug.indications).map((item) => <li key={item}><Check size={15} />{item}</li>)}</ul></div>
              </section>
              <section className="info-panel info-panel--wide info-panel--note">
                <div className="info-panel__icon"><Info size={21} /></div>
                <div><span className="info-panel__eyebrow">REFERENCE NOTE</span><h2>{t('dosing')}</h2><p>{language === 'ar' ? drug.dosageNoteAr || drug.dosageNote : drug.dosageNote}</p></div>
              </section>
              {drug.aliases.length > 0 && (
                <section className="aliases-panel"><strong>{language === 'ar' ? 'أسماء أخرى' : 'Also known as'}</strong><div>{drug.aliases.map((alias) => <span key={alias}>{alias}</span>)}</div></section>
              )}
            </div>
          )}

          {tab === 'products' && (
            <div className="products-detail">
              <div className="detail-section-heading"><div><span className="section-kicker">TRADE CATALOG</span><h2>{drugProducts.length} {t('products')}</h2></div><button className="secondary-button" onClick={() => onAddProduct(drug)}><PackagePlus size={16} /> {t('addProduct')}</button></div>
              {drugProducts.length ? <div className="product-detail-grid">{drugProducts.map((product) => (
                <article className="product-detail-card" key={product.id}>
                  <button className="product-detail-card__edit" onClick={() => onEditProduct(drug, product)} aria-label={t('editProductTitle')}><Pencil size={15} /></button>
                  <MedicineVisual drug={drug} product={product} size="medium" />
                  <div className="product-detail-card__heading"><div><h3>{product.brandName}</h3><p>{drug.genericName}</p></div>{product.verified ? <span className="verified-badge"><BadgeCheck size={14} />{t('verified')}</span> : <span className="pending-badge">{t('needsVerification')}</span>}</div>
                  <div className="product-specs">
                    <span><Pill size={15} /><small>{t('productStrength')}</small><strong>{product.strength || '—'}</strong></span>
                    <span><PackagePlus size={15} /><small>{t('dosageForm')}</small><strong>{language === 'ar' ? product.dosageFormAr || product.dosageForm : product.dosageForm || '—'}</strong></span>
                    <span><Factory size={15} /><small>{t('manufacturer')}</small><strong>{product.manufacturer || '—'}</strong></span>
                    <span><Globe2 size={15} /><small>{t('country')}</small><strong>{product.country || '—'}</strong></span>
                  </div>
                  <div className="product-agent"><MapPin size={15} /><span><small>{t('agent')}</small><strong>{product.localAgent || t('needsVerification')}</strong></span></div>
                </article>
              ))}</div> : <div className="detail-empty"><Pill size={28} /><h3>{language === 'ar' ? 'لا توجد منتجات محفوظة' : 'No saved products yet'}</h3><button className="primary-button" onClick={() => onAddProduct(drug)}>{t('addProduct')}</button></div>}
            </div>
          )}

          {tab === 'safety' && (
            <div className="safety-detail">
              <section className="safety-banner"><ShieldAlert size={26} /><div><span>CLINICAL SAFETY</span><h2>{t('cautions')}</h2><p>{t('educationalOnly')}</p></div></section>
              <div className="caution-list">
                {(language === 'ar' && drug.cautionsAr.length ? drug.cautionsAr : drug.cautions).map((item, index) => (
                  <div key={item}><span>{index + 1}</span><AlertTriangle size={19} /><p>{item}</p></div>
                ))}
              </div>
              {drug.interactions?.length ? <section className="interaction-panel"><h3><CircleAlert size={18} />{t('interactions')}</h3><ul>{drug.interactions.map((item) => <li key={item}>{item}</li>)}</ul></section> : null}
              <section className="dose-safety"><CalendarClock size={21} /><div><h3>{t('dosing')}</h3><p>{language === 'ar' ? drug.dosageNoteAr || drug.dosageNote : drug.dosageNote}</p></div></section>
            </div>
          )}

          {tab === 'sources' && (
            <div className="sources-detail">
              <div className="source-intro"><Sparkles size={22} /><div><h2>{t('trustedSources')}</h2><p>{language === 'ar' ? 'افتح المصدر الأساسي وتحقق من تاريخ النشرة والسوق قبل اتخاذ قرار سريري.' : 'Open the primary source and verify label date and market before a clinical decision.'}</p></div></div>
              <div className="source-list">
                {drug.sourceRefs.map((source) => (
                  <a href={source.url} target="_blank" rel="noreferrer" key={source.url}>
                    <span className={`source-authority source-authority--${source.authority.toLowerCase()}`}>{source.authority}</span>
                    <span><strong>{source.label}</strong><small>{source.accessedAt ? new Date(source.accessedAt).toLocaleDateString(language) : 'Primary medicine reference'}</small></span>
                    <ExternalLink size={17} />
                  </a>
                ))}
                <a href={`https://www.who.int/health-topics/medicines`} target="_blank" rel="noreferrer"><span className="source-authority source-authority--who">WHO</span><span><strong>WHO · Medicines</strong><small>Global medicine safety and policy</small></span><ExternalLink size={17} /></a>
              </div>
              <div className="source-tools">
                <a href={googleImagesUrl(`${heroProduct?.brandName || ''} ${drug.genericName}`)} target="_blank" rel="noreferrer"><ImageSearch size={19} /><span><strong>{t('googleImages')}</strong><small>{language === 'ar' ? 'ابحث عن صورة العبوة ثم تحقق من حقوق استخدامها' : 'Find packaging imagery; verify usage rights'}</small></span><ExternalLink size={16} /></a>
                <a href={pubChemImageUrl(drug.genericName)} target="_blank" rel="noreferrer"><Beaker size={19} /><span><strong>PubChem structure</strong><small>NIH molecular image</small></span><ExternalLink size={16} /></a>
              </div>
              <div className="update-stamp"><CalendarClock size={15} />{t('sourceUpdated')}: {new Date(drug.updatedAt).toLocaleDateString(language === 'ar' ? 'ar-YE' : 'en-US', { year: 'numeric', month: 'long', day: 'numeric' })}</div>
            </div>
          )}
        </div>

        <footer className="drug-detail__disclaimer"><CircleAlert size={15} /><span>{t('educationalOnly')}</span></footer>
      </article>
    </div>
  );
}
