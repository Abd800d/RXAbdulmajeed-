import { Check, ChevronRight, CloudDownload, Database, DatabaseZap, Download, FileArchive, FileJson, FileSpreadsheet, HardDrive, History, Import, Info, LoaderCircle, LockKeyhole, Plus, ShieldCheck, Table2, UploadCloud, XCircle } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useAph } from '../context/AphContext';
import { downloadCsvTemplate, exportCsv, exportExcel, exportJson, importDatabaseFile, recentImports, type ImportProgress } from '../services/importExport';
import type { ImportRecord } from '../types';
import { fileSize, formatCount } from '../utils/text';

interface DataScreenProps {
  onAddMedicine: () => void;
}

export function DataScreen({ onAddMedicine }: DataScreenProps) {
  const { t, language, stats, persistentStorage, requestPersistentStorage, refresh, showToast } = useAph();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState<ImportProgress>();
  const [activeFile, setActiveFile] = useState<File>();
  const [error, setError] = useState('');
  const [history, setHistory] = useState<ImportRecord[]>([]);
  const [storage, setStorage] = useState<{ usage?: number; quota?: number }>({});
  const [exporting, setExporting] = useState('');

  const loadMeta = async () => {
    setHistory(await recentImports());
    if (navigator.storage?.estimate) setStorage(await navigator.storage.estimate());
  };

  useEffect(() => { void loadMeta(); }, []);

  const handleFile = async (file?: File) => {
    if (!file) return;
    setActiveFile(file);
    setError('');
    setProgress({ processed: 0, imported: 0, skipped: 0, percent: 1, stage: 'reading' });
    try {
      const result = await importDatabaseFile(file, setProgress);
      await refresh();
      await loadMeta();
      showToast(`${t('imported')} ${formatCount(result.importedRows, language)} ${t('rows')}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Import failed');
    }
  };

  const runExport = async (type: 'json' | 'csv' | 'excel') => {
    setExporting(type);
    try {
      if (type === 'json') await exportJson();
      if (type === 'csv') await exportCsv();
      if (type === 'excel') await exportExcel();
      showToast(language === 'ar' ? 'تم إنشاء النسخة الاحتياطية' : 'Backup created');
    } catch (cause) {
      showToast(cause instanceof Error ? cause.message : 'Export failed', 'error');
    } finally {
      setExporting('');
    }
  };

  const percentage = progress?.percent ?? 0;

  return (
    <div className="screen data-screen">
      <div className="screen-title-row">
        <div><span className="section-kicker">APH · LOCAL-FIRST</span><h1>{t('dataTitle')}</h1><p>{t('dataSubtitle')}</p></div>
        <button className="primary-button" onClick={onAddMedicine}><Plus size={17} /> {t('addMedicine')}</button>
      </div>

      <section className="database-overview">
        <div className="database-overview__heading">
          <span className="database-overview__icon"><DatabaseZap size={24} /></span>
          <div><small>{t('databaseHealth')}</small><h2>APH Local Vault</h2></div>
          <span className="database-status"><i />{t('offlineReady')}</span>
        </div>
        <div className="database-metrics">
          <div><span><Database size={17} /></span><strong>{formatCount(stats.drugCount, language)}</strong><small>{t('totalMedicines')}</small></div>
          <div><span><Table2 size={17} /></span><strong>{formatCount(stats.productCount, language)}</strong><small>{t('tradeProducts')}</small></div>
          <div><span><ShieldCheck size={17} /></span><strong>{formatCount(stats.localProductCount, language)}</strong><small>{t('localListings')}</small></div>
          <div><span><HardDrive size={17} /></span><strong>{storage.usage ? fileSize(storage.usage) : '—'}</strong><small>{storage.quota ? `${fileSize(storage.quota)} available` : 'Device storage'}</small></div>
        </div>
        <div className="persistence-bar">
          <span className={persistentStorage ? 'is-persistent' : ''}>{persistentStorage ? <Check size={16} /> : <Info size={16} />}</span>
          <div><strong>{persistentStorage ? t('storedPermanently') : t('storageRequest')}</strong><small>{language === 'ar' ? 'البيانات والصور محفوظة محلياً حتى دون إنترنت.' : 'Medicine records and images remain available without internet.'}</small></div>
          {!persistentStorage && <button onClick={async () => {
            const granted = await requestPersistentStorage();
            showToast(granted ? t('storedPermanently') : (language === 'ar' ? 'سيقرر المتصفح سياسة التخزين تلقائياً' : 'Your browser will manage storage automatically'), granted ? 'success' : 'info');
          }}>{t('storageRequest')}</button>}
        </div>
      </section>

      <div className="data-main-grid">
        <section className="data-panel import-panel">
          <div className="data-panel__heading"><span><Import size={20} /></span><div><small>CSV · XLSX · JSON</small><h2>{t('importDatabase')}</h2></div></div>
          <div
            className={`drop-zone ${dragging ? 'is-dragging' : ''} ${progress ? 'has-progress' : ''}`}
            onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => { event.preventDefault(); setDragging(false); void handleFile(event.dataTransfer.files[0]); }}
          >
            {progress ? (
              <div className="import-progress">
                <span className={`import-progress__icon ${progress.stage === 'complete' ? 'is-complete' : ''}`}>{progress.stage === 'complete' ? <Check size={25} /> : <LoaderCircle className="spin" size={25} />}</span>
                <div><h3>{activeFile?.name}</h3><p>{activeFile ? fileSize(activeFile.size) : ''} · {progress.stage === 'complete' ? t('imported') : t('importing')}</p></div>
                <strong>{percentage}%</strong>
                <div className="import-progress__bar"><i style={{ width: `${percentage}%` }} /></div>
                <div className="import-progress__stats"><span>{formatCount(progress.imported, language)} {t('imported')}</span><span>{formatCount(progress.skipped, language)} {t('skipped')}</span></div>
                {progress.stage === 'complete' && <button className="text-button" onClick={() => { setProgress(undefined); setActiveFile(undefined); }}>{language === 'ar' ? 'استيراد ملف آخر' : 'Import another file'}</button>}
              </div>
            ) : (
              <>
                <span className="drop-zone__icon"><UploadCloud size={30} /></span>
                <h3>{t('importDropTitle')}</h3><p>{t('importDropBody')}</p>
                <button className="secondary-button" onClick={() => inputRef.current?.click()}><CloudDownload size={17} /> {t('chooseFile')}</button>
                <small>Recommended CSV columns: generic_name, body_system, brand_name, strength, dosage_form</small>
              </>
            )}
            <input ref={inputRef} hidden type="file" accept=".csv,.json,.xlsx,.xls,text/csv,application/json" onChange={(event) => void handleFile(event.target.files?.[0])} />
          </div>
          {error && <div className="import-error"><XCircle size={17} /><span>{error}</span><button onClick={() => setError('')}>×</button></div>}
          <button className="template-link" onClick={downloadCsvTemplate}><Download size={15} /> {t('dataTemplate')}</button>
        </section>

        <section className="data-panel export-panel">
          <div className="data-panel__heading"><span><FileArchive size={20} /></span><div><small>PORTABLE & OWNED BY YOU</small><h2>{t('exportBackup')}</h2></div></div>
          <div className="export-options">
            <button onClick={() => runExport('json')} disabled={!!exporting}>
              <span className="export-options__icon export-options__icon--json">{exporting === 'json' ? <LoaderCircle className="spin" size={21} /> : <FileJson size={21} />}</span>
              <span><strong>{t('exportJson')}</strong><small>{language === 'ar' ? 'الأدوية والمنتجات والصور والبيانات' : 'Medicines, products, images & metadata'}</small></span><ChevronRight size={17} />
            </button>
            <button onClick={() => runExport('csv')} disabled={!!exporting}>
              <span className="export-options__icon export-options__icon--csv">{exporting === 'csv' ? <LoaderCircle className="spin" size={21} /> : <Table2 size={21} />}</span>
              <span><strong>{t('exportCsv')}</strong><small>{language === 'ar' ? 'متوافق مع أدوات قواعد البيانات' : 'Compatible with database tools'}</small></span><ChevronRight size={17} />
            </button>
            <button onClick={() => runExport('excel')} disabled={!!exporting}>
              <span className="export-options__icon export-options__icon--xlsx">{exporting === 'excel' ? <LoaderCircle className="spin" size={21} /> : <FileSpreadsheet size={21} />}</span>
              <span><strong>{t('exportExcel')}</strong><small>{language === 'ar' ? 'صفحتان: الأدوية والمنتجات' : 'Two sheets: medicines & products'}</small></span><ChevronRight size={17} />
            </button>
          </div>
          <div className="data-privacy"><LockKeyhole size={18} /><div><strong>{t('dataPrivacy')}</strong><p>{t('dataPrivacyBody')}</p></div></div>
        </section>
      </div>

      <section className="import-history">
        <div className="detail-section-heading"><div><span className="section-kicker">ACTIVITY</span><h2><History size={18} /> {t('importHistory')}</h2></div></div>
        {history.length ? <div className="history-table">
          <div className="history-table__head"><span>File</span><span>Type</span><span>{t('rows')}</span><span>Date</span><span>Status</span></div>
          {history.map((record) => (
            <div key={record.id}>
              <span><FileSpreadsheet size={16} /><strong>{record.fileName}</strong></span><span>{record.fileType}</span><span>{formatCount(record.importedRows, language)}</span><span>{new Date(record.importedAt).toLocaleDateString(language === 'ar' ? 'ar-YE' : 'en-US')}</span><span className={`history-status history-status--${record.status}`}><i />{record.status}</span>
            </div>
          ))}
        </div> : <div className="history-empty"><History size={22} /><span>{t('noImports')}</span></div>}
      </section>
    </div>
  );
}
