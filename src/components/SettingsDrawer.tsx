import { BookOpenCheck, Check, ChevronRight, Cloud, ExternalLink, Globe2, Languages, Mail, MessageCircle, RefreshCw, ShieldCheck, Smartphone, Wifi, WifiOff, X } from 'lucide-react';
import { useAph } from '../context/AphContext';
import { AphLogo } from './ui';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

interface SettingsDrawerProps {
  onClose: () => void;
  installPrompt?: BeforeInstallPromptEvent | null;
  onInstalled?: () => void;
  onSupport: () => void;
}

export function SettingsDrawer({ onClose, installPrompt, onInstalled, onSupport }: SettingsDrawerProps) {
  const { t, language, setLanguage, online, stats, cloud, syncNow } = useAph();
  const isStandalone = window.matchMedia('(display-mode: standalone)').matches;

  const install = async () => {
    if (!installPrompt) return;
    await installPrompt.prompt();
    const choice = await installPrompt.userChoice;
    if (choice.outcome === 'accepted') onInstalled?.();
  };

  return (
    <div className="settings-layer" role="dialog" aria-modal="true" aria-labelledby="settings-title">
      <button className="settings-layer__scrim" onClick={onClose} aria-label={t('close')} />
      <aside className="settings-drawer">
        <header className="settings-drawer__header">
          <AphLogo inverse />
          <button onClick={onClose}><X size={20} /></button>
          <div><h2 id="settings-title">{t('settings')}</h2><p>Version 1.0 · Local-first</p></div>
        </header>
        <div className="settings-drawer__body">
          <section className="settings-section">
            <div className="settings-section__title"><Languages size={17} /><span>{t('appearanceLanguage')}</span></div>
            <div className="language-options">
              <button className={language === 'en' ? 'is-active' : ''} onClick={() => setLanguage('en')}><span>EN</span><div><strong>English</strong><small>Left to right</small></div>{language === 'en' && <Check size={17} />}</button>
              <button className={language === 'ar' ? 'is-active' : ''} onClick={() => setLanguage('ar')}><span>ع</span><div><strong>العربية</strong><small>من اليمين إلى اليسار</small></div>{language === 'ar' && <Check size={17} />}</button>
            </div>
          </section>

          <section className="settings-section">
            <div className="settings-section__title"><Globe2 size={17} /><span>{t('connectivity')}</span></div>
            <div className={`connectivity-card ${online ? 'is-online' : ''}`}>
              <span>{online ? <Wifi size={20} /> : <WifiOff size={20} />}</span>
              <div><strong>{online ? t('online') : t('offline')}</strong><small>{online ? 'NLM, openFDA & PubChem available' : `${stats.drugCount + stats.productCount} ${t('recordsReady')}`}</small></div><i />
            </div>
          </section>

          <section className="settings-section">
            <div className="settings-section__title"><Cloud size={17} /><span>{language === 'ar' ? 'قاعدة البيانات السحابية' : 'Cloud database'}</span></div>
            <div className={`connectivity-card ${cloud.connected ? 'is-online' : ''}`}>
              <span>{cloud.syncing ? <RefreshCw className="spin" size={20} /> : <Cloud size={20} />}</span>
              <div>
                <strong>{!cloud.configured ? (language === 'ar' ? 'غير مربوطة بعد' : 'Not configured yet') : cloud.connected ? (language === 'ar' ? 'متزامنة لحظياً' : 'Realtime connected') : (language === 'ar' ? 'البيانات المحلية متاحة' : 'Local cache available')}</strong>
                <small>{cloud.lastSync ? `${language === 'ar' ? 'آخر مزامنة' : 'Last sync'}: ${new Date(cloud.lastSync).toLocaleString(language === 'ar' ? 'ar-YE' : 'en-US')}` : 'Supabase · PostgreSQL · Realtime'}</small>
              </div>
              {cloud.configured && <button className="cloud-sync-button" onClick={() => void syncNow()} disabled={cloud.syncing}><RefreshCw size={14} /></button>}
            </div>
          </section>

          {!isStandalone && installPrompt && (
            <section className="install-card">
              <span><Smartphone size={23} /></span><div><strong>{t('installApp')}</strong><p>{t('installBody')}</p><button onClick={install}>{t('install')}</button></div>
            </section>
          )}

          <section className="settings-section">
            <div className="settings-section__title"><MessageCircle size={17} /><span>{t('contactDeveloper')}</span></div>
            <div className="support-links">
              <button onClick={() => { onClose(); onSupport(); }}><span className="support-links__inapp"><MessageCircle size={19} /></span><div><strong>{language === 'ar' ? 'المراسلة داخل التطبيق' : 'In-app messages'}</strong><small>{language === 'ar' ? 'أسئلة وردود مباشرة' : 'Questions and live replies'}</small></div><ChevronRight size={15} /></button>
              <a href="https://wa.me/967781733795?text=Hello%20Dr.%20Abdulmajeed%2C%20I%20need%20APH%20support" target="_blank" rel="noreferrer"><span className="support-links__whatsapp"><MessageCircle size={19} /></span><div><strong>{t('whatsapp')}</strong><small>+967 781 733 795</small></div><ExternalLink size={15} /></a>
              <a href="mailto:7a8b1d@gmail.com?subject=APH%20Support"><span className="support-links__email"><Mail size={19} /></span><div><strong>{t('email')}</strong><small>7a8b1d@gmail.com</small></div><ChevronRight size={15} /></a>
            </div>
          </section>

          <a className="settings-admin-link" href="/admin"><span><ShieldCheck size={19} /></span><div><strong>{language === 'ar' ? 'موقع إدارة APH' : 'APH administration site'}</strong><small>{language === 'ar' ? 'إدارة الأدوية والمنتجات والرسائل' : 'Manage medicines, products, and messages'}</small></div><ChevronRight size={16} /></a>

          <section className="settings-section">
            <div className="settings-section__title"><BookOpenCheck size={17} /><span>{t('trustedSources')}</span></div>
            <div className="settings-sources">
              <a href="https://www.nlm.nih.gov/research/umls/rxnorm/" target="_blank" rel="noreferrer"><span>NLM</span><strong>RxNorm & RxNav</strong><ExternalLink size={14} /></a>
              <a href="https://dailymed.nlm.nih.gov/" target="_blank" rel="noreferrer"><span>FDA</span><strong>DailyMed</strong><ExternalLink size={14} /></a>
              <a href="https://www.who.int/health-topics/medicines" target="_blank" rel="noreferrer"><span>WHO</span><strong>WHO Medicines</strong><ExternalLink size={14} /></a>
            </div>
          </section>

          <section className="rights-card">
            <ShieldCheck size={22} />
            <div><small>{t('rights')}</small><strong>Dr. Abdulmajeed Abdullah Almakhedhi</strong><p>{t('copyright')}</p><em>© {new Date().getFullYear()} APH · AbdulmajeedPharma</em></div>
          </section>
        </div>
      </aside>
    </div>
  );
}
