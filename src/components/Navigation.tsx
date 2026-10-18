import { Bot, Database, Home, LayoutGrid, ScanLine, Settings2, WifiOff } from 'lucide-react';
import type { AppTab } from '../types';
import { useAph } from '../context/AphContext';
import { AphLogo, StatusDot } from './ui';

const navItems: Array<{ id: AppTab; icon: typeof Home; labelKey: 'home' | 'catalog' | 'scan' | 'assistant' | 'data' }> = [
  { id: 'home', icon: Home, labelKey: 'home' },
  { id: 'catalog', icon: LayoutGrid, labelKey: 'catalog' },
  { id: 'scan', icon: ScanLine, labelKey: 'scan' },
  { id: 'assistant', icon: Bot, labelKey: 'assistant' },
  { id: 'data', icon: Database, labelKey: 'data' },
];

interface NavigationProps {
  activeTab: AppTab;
  onChange: (tab: AppTab) => void;
  onSettings: () => void;
}

export function DesktopSidebar({ activeTab, onChange, onSettings }: NavigationProps) {
  const { t, language, setLanguage, online, stats } = useAph();
  return (
    <aside className="desktop-sidebar">
      <AphLogo inverse />
      <div className="desktop-sidebar__status">
        <StatusDot online={online} label={online ? t('online') : t('offline')} />
        <small>{stats.drugCount + stats.productCount} {t('recordsReady')}</small>
      </div>
      <nav className="desktop-sidebar__nav" aria-label="Primary navigation">
        {navItems.map(({ id, icon: Icon, labelKey }) => (
          <button key={id} className={activeTab === id ? 'is-active' : ''} onClick={() => onChange(id)}>
            <Icon size={20} strokeWidth={1.9} />
            <span>{t(labelKey)}</span>
            {id === 'assistant' && <em>AI</em>}
          </button>
        ))}
      </nav>
      <div className="desktop-sidebar__footer">
        <button className="sidebar-language" onClick={() => setLanguage(language === 'en' ? 'ar' : 'en')}>
          <span>{language === 'en' ? 'ع' : 'EN'}</span>
          <div><strong>{t('appearanceLanguage')}</strong><small>{language === 'en' ? 'العربية' : 'English'}</small></div>
        </button>
        <button className="sidebar-settings" onClick={onSettings}><Settings2 size={19} /> {t('settings')}</button>
        <div className="sidebar-owner"><span>AA</span><div><strong>Abdulmajeed Almakhedhi</strong><small>Founder & curator</small></div></div>
      </div>
    </aside>
  );
}

export function MobileHeader({ onSettings }: Pick<NavigationProps, 'onSettings'>) {
  const { online, language, setLanguage } = useAph();
  return (
    <header className="mobile-header">
      <AphLogo />
      <div className="mobile-header__actions">
        {!online && <span className="mobile-offline"><WifiOff size={15} /></span>}
        <button className="language-toggle" onClick={() => setLanguage(language === 'en' ? 'ar' : 'en')} aria-label="Change language">
          {language === 'en' ? 'ع' : 'EN'}
        </button>
        <button className="icon-button" onClick={onSettings} aria-label="Settings"><Settings2 size={20} /></button>
      </div>
    </header>
  );
}

export function BottomNavigation({ activeTab, onChange }: Omit<NavigationProps, 'onSettings'>) {
  const { t } = useAph();
  return (
    <nav className="bottom-navigation" aria-label="Primary navigation">
      {navItems.map(({ id, icon: Icon, labelKey }) => (
        <button key={id} className={`${activeTab === id ? 'is-active' : ''} ${id === 'scan' ? 'is-scan' : ''}`} onClick={() => onChange(id)}>
          <span className="bottom-navigation__icon"><Icon size={id === 'scan' ? 23 : 20} strokeWidth={id === 'scan' ? 2.2 : 1.9} /></span>
          <small>{t(labelKey)}</small>
        </button>
      ))}
    </nav>
  );
}
