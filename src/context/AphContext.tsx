import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { db, getCatalogSnapshot, getFavorites, getStats, initializeDatabase, toggleFavorite as toggleDbFavorite } from '../db/database';
import { useOnlineStatus } from '../hooks/useOnlineStatus';
import { cloudConfigured, subscribeToCatalog, supabase, syncCloudCatalog } from '../services/cloud';
import type { CatalogStats, CloudSyncStatus, Drug, Language, Product } from '../types';
import { translate, type TranslationKey } from '../utils/i18n';

interface ToastState {
  message: string;
  tone?: 'success' | 'error' | 'info';
}

interface AphContextValue {
  language: Language;
  setLanguage: (language: Language) => void;
  t: (key: TranslationKey) => string;
  isRtl: boolean;
  online: boolean;
  ready: boolean;
  error?: string;
  drugs: Drug[];
  products: Product[];
  stats: CatalogStats;
  favorites: string[];
  cloud: CloudSyncStatus;
  syncNow: () => Promise<void>;
  persistentStorage: boolean | null;
  requestPersistentStorage: () => Promise<boolean>;
  refresh: () => Promise<void>;
  toggleFavorite: (drugId: string) => Promise<void>;
  toast: ToastState | null;
  showToast: (message: string, tone?: ToastState['tone']) => void;
}

const emptyStats: CatalogStats = { drugCount: 0, productCount: 0, localProductCount: 0, favoriteCount: 0 };
const AphContext = createContext<AphContextValue | null>(null);

export function AphProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<Language>(() => (localStorage.getItem('aph-language') as Language) || 'en');
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string>();
  const [drugs, setDrugs] = useState<Drug[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [stats, setStats] = useState<CatalogStats>(emptyStats);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [persistentStorage, setPersistentStorage] = useState<boolean | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [cloud, setCloud] = useState<CloudSyncStatus>({ configured: cloudConfigured, syncing: false, connected: false });
  const syncingRef = useRef(false);
  const syncRequestedRef = useRef(false);
  const online = useOnlineStatus();

  const setLanguage = useCallback((next: Language) => {
    setLanguageState(next);
    localStorage.setItem('aph-language', next);
  }, []);

  const refresh = useCallback(async () => {
    const [snapshot, nextStats, nextFavorites] = await Promise.all([
      getCatalogSnapshot(),
      getStats(),
      getFavorites(),
    ]);
    setDrugs(snapshot.drugs);
    setProducts(snapshot.products);
    setStats(nextStats);
    setFavorites(nextFavorites);
  }, []);

  const syncNow = useCallback(async () => {
    if (!cloudConfigured || !online) return;
    if (syncingRef.current) {
      syncRequestedRef.current = true;
      return;
    }
    syncingRef.current = true;
    syncRequestedRef.current = false;
    setCloud((current) => ({ ...current, syncing: true, error: undefined }));
    try {
      const lastRow = await db.meta.get('lastCloudSync');
      const lastSync = typeof lastRow?.value === 'string' ? lastRow.value : undefined;
      const result = await syncCloudCatalog({ since: lastSync, maxRows: lastSync ? 5000 : 10_000 });
      await refresh();
      setCloud({ configured: true, syncing: false, connected: true, lastSync: result.syncedAt || lastSync });
    } catch (cause) {
      setCloud((current) => ({ ...current, syncing: false, connected: false, error: cause instanceof Error ? cause.message : 'Cloud synchronization failed.' }));
    } finally {
      syncingRef.current = false;
      if (syncRequestedRef.current) {
        syncRequestedRef.current = false;
        window.setTimeout(() => void syncNow(), 0);
      }
    }
  }, [online, refresh]);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        await initializeDatabase();
        await refresh();
        const lastRow = await db.meta.get('lastCloudSync');
        if (active && typeof lastRow?.value === 'string') setCloud((current) => ({ ...current, lastSync: lastRow.value as string }));
        if (navigator.storage?.persisted) {
          const persisted = await navigator.storage.persisted();
          if (active) setPersistentStorage(persisted);
        }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : 'Could not open the local database.');
      } finally {
        if (active) setReady(true);
      }
    })();
    return () => { active = false; };
  }, [refresh]);

  useEffect(() => {
    if (ready && online && cloudConfigured) void syncNow();
  }, [ready, online, syncNow]);

  useEffect(() => {
    if (!ready || !cloudConfigured) return undefined;
    const channel = subscribeToCatalog(() => void syncNow());
    return () => { if (channel && supabase) void supabase.removeChannel(channel); };
  }, [ready, syncNow]);

  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  }, [language]);

  const requestPersistentStorage = useCallback(async () => {
    const value = navigator.storage?.persist ? await navigator.storage.persist() : false;
    setPersistentStorage(value);
    return value;
  }, []);

  const toggleFavorite = useCallback(async (drugId: string) => {
    const next = await toggleDbFavorite(drugId);
    setFavorites(next);
    setStats((current) => ({ ...current, favoriteCount: next.length }));
  }, []);

  const showToast = useCallback((message: string, tone: ToastState['tone'] = 'success') => {
    setToast({ message, tone });
    window.setTimeout(() => setToast(null), 3400);
  }, []);

  const value = useMemo<AphContextValue>(() => ({
    language,
    setLanguage,
    t: (key) => translate(language, key),
    isRtl: language === 'ar',
    online,
    ready,
    error,
    drugs,
    products,
    stats,
    favorites,
    cloud,
    syncNow,
    persistentStorage,
    requestPersistentStorage,
    refresh,
    toggleFavorite,
    toast,
    showToast,
  }), [language, setLanguage, online, ready, error, drugs, products, stats, favorites, cloud, syncNow, persistentStorage, requestPersistentStorage, refresh, toggleFavorite, toast, showToast]);

  return <AphContext.Provider value={value}>{children}</AphContext.Provider>;
}

export function useAph(): AphContextValue {
  const value = useContext(AphContext);
  if (!value) throw new Error('useAph must be used inside AphProvider');
  return value;
}
