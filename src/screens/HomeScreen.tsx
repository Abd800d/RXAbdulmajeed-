import { ArrowRight, BadgeCheck, ChevronRight, Cloud, DatabaseZap, MapPin, ShieldCheck } from 'lucide-react';
import { useMemo } from 'react';
import { useAph } from '../context/AphContext';
import { bodySystems } from '../db/database';
import type { AppTab, Drug } from '../types';
import { formatCount } from '../utils/text';
import { SearchBox } from '../components/SearchBox';
import { MedicineVisual, StatusDot, SystemIcon } from '../components/ui';

interface HomeScreenProps {
  onOpenDrug: (drug: Drug) => void;
  onNavigate: (tab: AppTab) => void;
  recentIds: string[];
}

export function HomeScreen({ onOpenDrug, onNavigate, recentIds }: HomeScreenProps) {
  const { t, language, isRtl, drugs, products, stats, online } = useAph();
  const hour = new Date().getHours();
  const greeting = hour < 12 ? t('goodMorning') : hour < 18 ? t('goodAfternoon') : t('goodEvening');
  const systemCounts = useMemo(() => new Map(bodySystems.map((system) => [system.id, drugs.filter((drug) => drug.systemId === system.id).length])), [drugs]);
  const recent = recentIds.map((id) => drugs.find((drug) => drug.id === id)).filter(Boolean) as Drug[];
  const featured = (recent.length ? recent : drugs.filter((drug) => ['paracetamol', 'amlodipine', 'amoxicillin-clavulanic-acid'].includes(drug.id))).slice(0, 4);
  const localProducts = products.filter((product) => product.market.toLowerCase().includes('yemen')).slice(0, 4);

  return (
    <div className="screen home-screen">
      <div className="home-greeting">
        <div>
          <span>{greeting},</span>
          <h1>{t('greetingName')} <span aria-hidden="true">👋</span></h1>
        </div>
        <div className="home-greeting__meta">
          <StatusDot online={online} label={online ? t('online') : t('offlineReady')} />
          <small>Abdulmajeed Almakhedhi</small>
        </div>
      </div>

      <section className="hero-card">
        <div className="hero-card__ornament hero-card__ornament--one" />
        <div className="hero-card__ornament hero-card__ornament--two" />
        <div className="hero-card__copy">
          <span className="eyebrow"><ShieldCheck size={14} /> {t('heroEyebrow')}</span>
          <h2>{isRtl ? <>كل دواء. <em>في مكان موثوق</em> واحد.</> : <>Every medicine.<br /><em>One trusted place.</em></>}</h2>
          <p>{t('heroBody')}</p>
          <SearchBox onSelect={onOpenDrug} onScan={() => onNavigate('scan')} />
        </div>
        <div className="hero-card__art" aria-hidden="true">
          <span className="hero-card__ring"><i /><b /></span>
          <span className="hero-card__pill"><i /><b /></span>
          <span className="hero-card__cross">+</span>
          <span className="hero-card__dot hero-card__dot--1" />
          <span className="hero-card__dot hero-card__dot--2" />
        </div>
        <div className="hero-card__facts">
          <span><DatabaseZap size={16} /><strong>{formatCount(stats.drugCount, language)}</strong><small>{t('medicines')}</small></span>
          <span><Cloud size={16} /><strong>{online ? t('syncReady') : t('offlineReady')}</strong><small>NLM · FDA · WHO</small></span>
        </div>
      </section>

      <section className="home-section">
        <div className="section-heading">
          <div><span className="section-kicker">01 · PHARMACOLOGY</span><h2>{t('browseSystems')}</h2></div>
          <button onClick={() => onNavigate('catalog')}>{t('seeAll')} <ArrowRight size={16} className={isRtl ? 'flip' : ''} /></button>
        </div>
        <div className="systems-grid systems-grid--home">
          {bodySystems.slice(0, 8).map((system) => (
            <button key={system.id} className="system-card" style={{ '--system-color': system.color, '--system-tint': system.tint } as React.CSSProperties} onClick={() => {
              sessionStorage.setItem('aph-selected-system', system.id);
              onNavigate('catalog');
            }}>
              <span className="system-card__icon"><SystemIcon system={system} size={23} /></span>
              <span className="system-card__copy">
                <strong>{language === 'ar' ? system.shortNameAr : system.shortName}</strong>
                <small>{systemCounts.get(system.id) ?? 0} {t('medicines')}</small>
              </span>
              <ChevronRight size={16} className={isRtl ? 'flip' : ''} />
            </button>
          ))}
        </div>
      </section>

      <div className="home-two-columns">
        <section className="home-section home-section--flush">
          <div className="section-heading section-heading--compact">
            <div><span className="section-kicker">02 · QUICK ACCESS</span><h2>{t('recentlyViewed')}</h2></div>
            <button onClick={() => onNavigate('catalog')}>{t('viewAll')}</button>
          </div>
          <div className="medicine-row">
            {featured.map((drug) => {
              const product = products.find((item) => item.drugId === drug.id);
              return (
                <button className="medicine-card" key={drug.id} onClick={() => onOpenDrug(drug)}>
                  <MedicineVisual drug={drug} product={product} size="medium" />
                  <span className="medicine-card__copy">
                    <strong>{product?.brandName || drug.genericName}</strong>
                    <small>{product ? `${drug.genericName} · ${product.strength}` : (language === 'ar' ? drug.pharmacologyClassAr : drug.pharmacologyClass)}</small>
                  </span>
                  <span className="medicine-card__open"><ChevronRight size={16} className={isRtl ? 'flip' : ''} /></span>
                </button>
              );
            })}
          </div>
        </section>

        <section className="home-section home-section--flush market-section">
          <div className="section-heading section-heading--compact">
            <div><span className="section-kicker">03 · LOCAL CATALOG</span><h2>{t('localMarket')}</h2></div>
            <span className="yemen-chip"><MapPin size={13} /> Yemen</span>
          </div>
          <div className="market-list">
            {(localProducts.length ? localProducts : products.slice(0, 3)).map((product) => {
              const drug = drugs.find((item) => item.id === product.drugId);
              if (!drug) return null;
              return (
                <button key={product.id} onClick={() => onOpenDrug(drug)}>
                  <MedicineVisual drug={drug} product={product} size="small" />
                  <span><strong>{product.brandName} <small>{product.strength}</small></strong><em>{product.manufacturer} · {product.country}</em></span>
                  {product.verified ? <BadgeCheck className="verified-icon" size={18} /> : <span className="verify-dot" title={t('needsVerification')} />}
                </button>
              );
            })}
          </div>
          <div className="market-note"><ShieldCheck size={15} /><span>{t('needsVerification')} · Yemen SFDA</span></div>
        </section>
      </div>
    </div>
  );
}
