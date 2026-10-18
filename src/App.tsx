import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AphProvider, useAph } from './context/AphContext';
import type { AppTab, Drug, Product } from './types';
import { BottomNavigation, DesktopSidebar, MobileHeader } from './components/Navigation';
import { LoadingScreen } from './components/ui';
import { HomeScreen } from './screens/HomeScreen';
import { CatalogScreen } from './screens/CatalogScreen';
import { ScanScreen } from './screens/ScanScreen';
import { AssistantScreen } from './screens/AssistantScreen';
import { DataScreen } from './screens/DataScreen';
import { DrugDetail } from './components/DrugDetail';
import { MedicineEditor, ProductEditor } from './components/Editors';
import { SettingsDrawer } from './components/SettingsDrawer';
import { SupportCenter } from './components/SupportCenter';
import { AdminPortal } from './admin/AdminPortal';
import { cloudConfigured, hydrateDrugFromCloud } from './services/cloud';
import { db } from './db/database';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

function AphApp() {
  const { ready, error, toast, drugs, online, refresh } = useAph();
  const [activeTab, setActiveTab] = useState<AppTab>('home');
  const [selectedDrug, setSelectedDrug] = useState<Drug>();
  const [medicineEditor, setMedicineEditor] = useState<Drug | 'new'>();
  const [productEditor, setProductEditor] = useState<{ drug: Drug; product?: Product }>();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [supportOpen, setSupportOpen] = useState(false);
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [recentIds, setRecentIds] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem('aph-recent') || '[]') as string[]; }
    catch { return []; }
  });

  useEffect(() => {
    const capture = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', capture);
    return () => window.removeEventListener('beforeinstallprompt', capture);
  }, []);

  // Keep an open record synchronized after edits/import refreshes.
  useEffect(() => {
    if (!selectedDrug) return undefined;
    let active = true;
    const current = drugs.find((drug) => drug.id === selectedDrug.id);
    if (current && current.updatedAt !== selectedDrug.updatedAt) setSelectedDrug(current);
    if (!current && ready) {
      // The context intentionally exposes a bounded snapshot on million-row
      // catalogs. Confirm deletion in IndexedDB before closing an open record.
      void db.drugs.get(selectedDrug.id).then((stored) => {
        if (!active) return;
        setSelectedDrug((open) => {
          if (open?.id !== selectedDrug.id) return open;
          if (!stored || stored.updatedAt !== open.updatedAt) return stored;
          return open;
        });
      });
    }
    return () => { active = false; };
  }, [drugs, selectedDrug, ready]);

  const openDrug = (drug: Drug) => {
    setSelectedDrug(drug);
    setRecentIds((current) => {
      const next = [drug.id, ...current.filter((id) => id !== drug.id)].slice(0, 8);
      localStorage.setItem('aph-recent', JSON.stringify(next));
      return next;
    });
    if (online && cloudConfigured) {
      void hydrateDrugFromCloud(drug.id).then(async (cloudDrug) => {
        if (cloudDrug) setSelectedDrug((current) => current?.id === drug.id ? cloudDrug : current);
        await refresh();
      }).catch(() => { /* The cached record remains fully usable offline. */ });
    }
  };

  const navigate = (tab: AppTab) => {
    setActiveTab(tab);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  if (!ready) return <LoadingScreen />;
  if (error) return (
    <div className="fatal-error"><XCircle size={34} /><h1>APH could not start</h1><p>{error}</p><button onClick={() => location.reload()}>Try again</button></div>
  );

  return (
    <div className="app-shell">
      <DesktopSidebar activeTab={activeTab} onChange={navigate} onSettings={() => setSettingsOpen(true)} />
      <div className="app-workspace">
        <MobileHeader onSettings={() => setSettingsOpen(true)} />
        <main className="app-main">
          {activeTab === 'home' && <HomeScreen onOpenDrug={openDrug} onNavigate={navigate} recentIds={recentIds} />}
          {activeTab === 'catalog' && <CatalogScreen onOpenDrug={openDrug} onScan={() => navigate('scan')} onAddMedicine={() => setMedicineEditor('new')} />}
          {activeTab === 'scan' && <ScanScreen onOpenDrug={openDrug} />}
          {activeTab === 'assistant' && <AssistantScreen onOpenDrug={openDrug} />}
          {activeTab === 'data' && <DataScreen onAddMedicine={() => setMedicineEditor('new')} />}
        </main>
        <footer className="app-footer"><span>APH · AbdulmajeedPharma</span><span>© {new Date().getFullYear()} Dr. Abdulmajeed Abdullah Almakhedhi</span></footer>
        <BottomNavigation activeTab={activeTab} onChange={navigate} />
      </div>

      {selectedDrug && (
        <DrugDetail
          drug={selectedDrug}
          onClose={() => setSelectedDrug(undefined)}
          onEdit={(drug) => setMedicineEditor(drug)}
          onAddProduct={(drug) => setProductEditor({ drug })}
          onEditProduct={(drug, product) => setProductEditor({ drug, product })}
        />
      )}
      {medicineEditor && (
        <MedicineEditor
          drug={medicineEditor === 'new' ? undefined : medicineEditor}
          onClose={() => setMedicineEditor(undefined)}
          onSaved={(drug) => setSelectedDrug(drug)}
        />
      )}
      {productEditor && <ProductEditor {...productEditor} onClose={() => setProductEditor(undefined)} />}
      {settingsOpen && <SettingsDrawer onClose={() => setSettingsOpen(false)} installPrompt={installPrompt} onInstalled={() => setInstallPrompt(null)} onSupport={() => setSupportOpen(true)} />}
      {supportOpen && <SupportCenter onClose={() => setSupportOpen(false)} />}

      {toast && <div className={`app-toast app-toast--${toast.tone ?? 'success'}`}>
        {toast.tone === 'error' ? <AlertTriangle size={18} /> : toast.tone === 'info' ? <Info size={18} /> : <CheckCircle2 size={18} />}
        <span>{toast.message}</span>
      </div>}
    </div>
  );
}

export default function App() {
  const adminRoute = window.location.pathname === '/admin' || window.location.pathname.startsWith('/admin/');
  return <AphProvider>{adminRoute ? <AdminPortal /> : <AphApp />}</AphProvider>;
}
