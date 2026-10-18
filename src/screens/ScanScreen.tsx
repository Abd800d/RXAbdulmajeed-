import { ArrowRight, Barcode, Camera, CheckCircle2, ExternalLink, FileImage, Images as ImageSearch, LoaderCircle, LockKeyhole, RotateCcw, ScanLine, Search } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { MedicineVisual } from '../components/ui';
import { useAph } from '../context/AphContext';
import { searchCatalog } from '../db/database';
import { googleLensUrl } from '../services/trustedSources';
import type { Drug, SearchResult } from '../types';
import { normalizeText } from '../utils/text';

interface ScanScreenProps {
  onOpenDrug: (drug: Drug) => void;
}

interface BarcodeDetectorLike {
  detect: (image: ImageBitmap) => Promise<Array<{ rawValue: string }>>;
}

declare global {
  interface Window {
    BarcodeDetector?: new (options?: { formats?: string[] }) => BarcodeDetectorLike;
  }
}

export function ScanScreen({ onOpenDrug }: ScanScreenProps) {
  const { t, language, drugs, products, online } = useAph();
  const cameraInput = useRef<HTMLInputElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string>();
  const [file, setFile] = useState<File>();
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState('');
  const [extracted, setExtracted] = useState('');
  const [matches, setMatches] = useState<SearchResult[]>([]);
  const [error, setError] = useState('');
  const [running, setRunning] = useState(false);

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  const reset = () => {
    if (preview) URL.revokeObjectURL(preview);
    setPreview(undefined);
    setFile(undefined);
    setProgress(0);
    setExtracted('');
    setMatches([]);
    setError('');
    setStatus('');
  };

  const identifyFromText = async (text: string): Promise<SearchResult[]> => {
    const normalized = ` ${normalizeText(text)} `;
    const exactDrug = drugs.filter((drug) => [drug.genericName, drug.arabicName, ...drug.aliases]
      .some((name) => normalizeText(name).length > 3 && normalized.includes(` ${normalizeText(name)} `)));
    const exactProductIds = products.filter((product) => [product.brandName, product.brandNameAr]
      .some((name) => name && normalizeText(name).length > 3 && normalized.includes(` ${normalizeText(name)} `)));
    const exact = new Map<string, SearchResult>();
    exactDrug.forEach((drug) => exact.set(drug.id, { drug, score: 0, matchedOn: 'ocr' }));
    exactProductIds.forEach((product) => {
      const drug = drugs.find((item) => item.id === product.drugId);
      if (drug) exact.set(drug.id, { drug, matchedProduct: product, score: 0, matchedOn: 'ocr-product' });
    });
    if (exact.size) return [...exact.values()].slice(0, 8);

    const candidates = text.split(/[\n\r,|]+/)
      .map((line) => line.replace(/[^\p{L}\p{N}\s/-]/gu, ' ').trim())
      .filter((line) => line.length >= 4 && line.length <= 70)
      .slice(0, 16);
    const fuzzy = new Map<string, SearchResult>();
    for (const candidate of candidates) {
      const result = await searchCatalog(candidate, 3);
      result.filter((item) => item.score < 0.34).forEach((item) => {
        if (!fuzzy.has(item.drug.id) || fuzzy.get(item.drug.id)!.score > item.score) fuzzy.set(item.drug.id, item);
      });
    }
    return [...fuzzy.values()].sort((a, b) => a.score - b.score).slice(0, 8);
  };

  const analyze = async (selected: File) => {
    setRunning(true);
    setError('');
    setMatches([]);
    setExtracted('');
    setProgress(4);
    setStatus(language === 'ar' ? 'جارٍ فحص الباركود…' : 'Checking for a barcode…');

    try {
      if (window.BarcodeDetector) {
        try {
          const bitmap = await createImageBitmap(selected);
          const detector = new window.BarcodeDetector({ formats: ['ean_13', 'ean_8', 'code_128', 'upc_a', 'upc_e', 'qr_code'] });
          const codes = await detector.detect(bitmap);
          bitmap.close();
          if (codes[0]?.rawValue) {
            const barcodeMatches = await searchCatalog(codes[0].rawValue, 8);
            setExtracted(`Barcode: ${codes[0].rawValue}`);
            if (barcodeMatches.length) {
              setMatches(barcodeMatches);
              setProgress(100);
              setStatus(language === 'ar' ? 'تمت قراءة الباركود' : 'Barcode recognized');
              setRunning(false);
              return;
            }
          }
        } catch { /* Continue to OCR. */ }
      }

      setStatus(language === 'ar' ? 'جارٍ تشغيل قراءة النص العربية والإنجليزية…' : 'Starting Arabic + English label recognition…');
      const { createWorker } = await import('tesseract.js');
      const worker = await createWorker(['eng', 'ara'], undefined, {
        logger: (message) => {
          if (typeof message.progress === 'number') setProgress(Math.max(8, Math.round(message.progress * 92)));
          if (message.status) setStatus(message.status.replace(/_/g, ' '));
        },
      });
      const result = await worker.recognize(selected);
      await worker.terminate();
      const text = result.data.text.trim();
      setExtracted(text);
      setMatches(await identifyFromText(text));
      setProgress(100);
      setStatus(language === 'ar' ? 'اكتمل تحليل الملصق' : 'Label analysis complete');
    } catch (cause) {
      setError(language === 'ar' ? 'تعذرت قراءة الصورة. تحقق من الاتصال لأول استخدام أو جرّب صورة أوضح.' : 'Could not read this image. Check your connection for first-time OCR or try a sharper photo.');
      console.error(cause);
    } finally {
      setRunning(false);
    }
  };

  const selectFile = (selected?: File) => {
    if (!selected) return;
    if (preview) URL.revokeObjectURL(preview);
    setFile(selected);
    setPreview(URL.createObjectURL(selected));
    void analyze(selected);
  };

  return (
    <div className="screen scan-screen">
      <div className="screen-title-row">
        <div><span className="section-kicker">APH · VISUAL SEARCH</span><h1>{t('scanTitle')}</h1><p>{t('scanSubtitle')}</p></div>
        <span className={`scan-online-pill ${online ? 'is-online' : ''}`}><i />{online ? t('online') : t('offline')}</span>
      </div>

      <div className="scan-layout">
        <section className={`scanner-card ${preview ? 'has-image' : ''}`}>
          <div className="scanner-corners"><i /><i /><i /><i /></div>
          {preview ? (
            <img src={preview} alt="Medicine label preview" />
          ) : (
            <div className="scanner-placeholder">
              <span className="scanner-placeholder__icon"><ScanLine size={42} strokeWidth={1.35} /></span>
              <h2>{t('scanInstructions')}</h2>
              <p>{language === 'ar' ? 'لأفضل نتيجة استخدم إضاءة جيدة وأظهر الاسم كاملاً.' : 'For best results, use good light and keep the full name visible.'}</p>
              <div className="scanner-placeholder__formats"><span><Barcode size={15} /> EAN / UPC</span><span><FileImage size={15} /> JPG / PNG</span></div>
            </div>
          )}
          {running && <div className="scanner-progress"><div><LoaderCircle className="spin" size={26} /><strong>{t('analyzing')}</strong><small>{status}</small><span><i style={{ width: `${progress}%` }} /></span><em>{progress}%</em></div></div>}
        </section>

        <div className="scan-actions">
          <button className="primary-button primary-button--large" onClick={() => cameraInput.current?.click()} disabled={running}><Camera size={19} /> {t('camera')}</button>
          <button className="secondary-button secondary-button--large" onClick={() => fileInput.current?.click()} disabled={running}><FileImage size={19} /> {t('uploadImage')}</button>
          {file && !running && <button className="text-button" onClick={reset}><RotateCcw size={16} /> {t('scanAgain')}</button>}
          <input ref={cameraInput} hidden type="file" accept="image/*" capture="environment" onChange={(event) => selectFile(event.target.files?.[0])} />
          <input ref={fileInput} hidden type="file" accept="image/*" onChange={(event) => selectFile(event.target.files?.[0])} />
        </div>

        <aside className="recognition-note"><span><LockKeyhole size={19} /></span><div><strong>{t('recognition')}</strong><p>{t('recognitionBody')}</p></div><CheckCircle2 size={18} /></aside>
      </div>

      {(extracted || error || (!running && file)) && (
        <section className="scan-results">
          <div className="scan-results__heading"><span className="section-kicker">MATCH ENGINE</span><h2>{t('possibleMatches')}</h2></div>
          {matches.length ? (
            <div className="scan-match-grid">{matches.map(({ drug, matchedProduct }, index) => (
              <button key={drug.id} onClick={() => onOpenDrug(drug)}>
                <span className="scan-match__rank">{String(index + 1).padStart(2, '0')}</span>
                <MedicineVisual drug={drug} product={matchedProduct || products.find((item) => item.drugId === drug.id)} size="medium" />
                <span><strong>{matchedProduct?.brandName || drug.genericName}</strong><small>{matchedProduct ? `${drug.genericName} · ${matchedProduct.strength}` : drug.pharmacologyClass}</small><em><CheckCircle2 size={13} /> {index === 0 ? 'Best match' : 'Possible match'}</em></span>
                <ArrowRight size={18} />
              </button>
            ))}</div>
          ) : (
            <div className="scan-no-match">
              <span><Search size={27} /></span><div><h3>{t('noScanMatch')}</h3><p>{error || t('noScanMatchBody')}</p></div>
              <a href={googleLensUrl()} target="_blank" rel="noreferrer"><ImageSearch size={17} /> {t('googleLens')} <ExternalLink size={14} /></a>
            </div>
          )}
          {extracted && <details className="extracted-text"><summary>{t('extractedText')}</summary><pre>{extracted}</pre></details>}
        </section>
      )}
    </div>
  );
}
