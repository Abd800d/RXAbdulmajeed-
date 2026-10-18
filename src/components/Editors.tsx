import { AlertTriangle, Check, CloudDownload, ExternalLink, ImagePlus, Images as ImageSearch, LoaderCircle, PackagePlus, Save, Search, Trash2, X } from 'lucide-react';
import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useAph } from '../context/AphContext';
import { bodySystems, deleteDrug, deleteProduct, saveDrug, saveProduct } from '../db/database';
import { googleImagesUrl, searchTrustedDrug } from '../services/trustedSources';
import type { Drug, Product } from '../types';
import { optimizeImage } from '../utils/image';
import { Modal } from './ui';

const listText = (value?: string[]) => value?.join('\n') ?? '';
const toList = (value: string) => value.split(/[\n;|]+/).map((item) => item.trim()).filter(Boolean);

interface MedicineEditorProps {
  drug?: Drug;
  onClose: () => void;
  onSaved: (drug: Drug) => void | Promise<void>;
  onDeleted?: (drugId: string) => void | Promise<void>;
}

export function MedicineEditor({ drug, onClose, onSaved, onDeleted }: MedicineEditorProps) {
  const { t, language, refresh, showToast } = useAph();
  const [form, setForm] = useState<Partial<Drug>>(() => drug ? { ...drug } : {
    genericName: '', arabicName: '', systemId: 'antiinfective', pharmacologyClass: '', pharmacologyClassAr: '',
    aliases: [], description: '', descriptionAr: '', mechanism: '', mechanismAr: '', indications: [], indicationsAr: [],
    cautions: [], cautionsAr: [], dosageNote: '', dosageNoteAr: '', sourceRefs: [],
  });
  const [aliases, setAliases] = useState(listText(drug?.aliases));
  const [indications, setIndications] = useState(listText(drug?.indications));
  const [indicationsAr, setIndicationsAr] = useState(listText(drug?.indicationsAr));
  const [cautions, setCautions] = useState(listText(drug?.cautions));
  const [cautionsAr, setCautionsAr] = useState(listText(drug?.cautionsAr));
  const [saving, setSaving] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [fetchMessage, setFetchMessage] = useState('');
  const [formError, setFormError] = useState('');

  const set = (key: keyof Drug, value: unknown) => setForm((current) => ({ ...current, [key]: value }));

  const imageChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try { set('image', await optimizeImage(file)); }
    catch (cause) { setFormError(cause instanceof Error ? cause.message : 'Could not process image.'); }
  };

  const fetchTrusted = async () => {
    if (!form.genericName?.trim()) { setFormError(t('requiredFields')); return; }
    setFetching(true);
    setFetchMessage('');
    setFormError('');
    const result = await searchTrustedDrug(form.genericName);
    setForm((current) => ({
      ...current,
      genericName: result.genericName || current.genericName,
      aliases: Array.from(new Set([...(current.aliases ?? []), ...result.brandNames])),
      description: current.description || result.description || '',
      indications: current.indications?.length ? current.indications : result.indications ?? [],
      cautions: current.cautions?.length ? current.cautions : result.warnings ?? [],
      sourceRefs: Array.from(new Map([...(current.sourceRefs ?? []), ...result.sourceRefs].map((source) => [source.url, source])).values()),
    }));
    setAliases((current) => Array.from(new Set([...toList(current), ...result.brandNames])).join('\n'));
    if (!indications && result.indications?.length) setIndications(result.indications.join('\n'));
    if (!cautions && result.warnings?.length) setCautions(result.warnings.join('\n'));
    setFetchMessage(result.rxcui
      ? `${language === 'ar' ? 'تم العثور على RxCUI' : 'Matched RxCUI'} ${result.rxcui}`
      : (language === 'ar' ? 'تمت مراجعة المصادر؛ أكمل الحقول يدوياً.' : 'Sources checked; complete the remaining fields manually.'));
    setFetching(false);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.genericName?.trim() || !form.systemId) { setFormError(t('requiredFields')); return; }
    setSaving(true);
    try {
      const saved = await saveDrug({
        ...form,
        genericName: form.genericName,
        systemId: form.systemId,
        aliases: toList(aliases),
        indications: toList(indications), indicationsAr: toList(indicationsAr),
        cautions: toList(cautions), cautionsAr: toList(cautionsAr),
      });
      await refresh();
      showToast(language === 'ar' ? 'تم حفظ الدواء محلياً' : 'Medicine saved locally');
      await onSaved(saved);
      onClose();
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : 'Could not save medicine.');
    } finally { setSaving(false); }
  };

  const remove = async () => {
    if (!drug || !window.confirm(t('confirmDelete'))) return;
    setSaving(true);
    setFormError('');
    try {
      if (onDeleted) await onDeleted(drug.id);
      await deleteDrug(drug.id);
      await refresh();
      showToast(language === 'ar' ? 'تم حذف الدواء' : 'Medicine deleted', 'info');
      onClose();
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : 'Could not delete medicine.');
    } finally { setSaving(false); }
  };

  return (
    <Modal onClose={onClose} className="editor-modal" labelledBy="medicine-editor-title">
      <form onSubmit={submit}>
        <header className="editor-header">
          <div><span className="section-kicker">APH · CATALOG EDITOR</span><h2 id="medicine-editor-title">{drug ? t('editMedicineTitle') : t('addMedicineTitle')}</h2></div>
          <button type="button" onClick={onClose} aria-label={t('close')}><X size={20} /></button>
        </header>

        <div className="editor-body">
          <section className="editor-image-row">
            <div className={`editor-image-preview ${form.image ? 'has-image' : ''}`}>{form.image ? <img src={form.image} alt="Medicine" /> : <ImagePlus size={28} />}</div>
            <div><strong>{t('medicineImage')}</strong><p>{language === 'ar' ? 'تُضغط الصورة وتحفظ بشكل دائم داخل قاعدة الجهاز.' : 'Optimized and stored permanently in the on-device database.'}</p><label className="secondary-button"><ImagePlus size={16} /> {form.image ? t('replacePhoto') : t('uploadPhoto')}<input hidden type="file" accept="image/*" onChange={imageChange} /></label></div>
          </section>

          <div className="editor-section-title"><span>01</span><div><strong>{language === 'ar' ? 'الهوية والتصنيف' : 'Identity & classification'}</strong><small>{language === 'ar' ? 'الاسم العلمي والمجموعة والجهاز' : 'Scientific name, class, and body system'}</small></div></div>
          <div className="form-grid">
            <label><span>{t('scientificName')} *</span><input value={form.genericName ?? ''} onChange={(event) => set('genericName', event.target.value)} placeholder="e.g. Paracetamol" dir="ltr" /></label>
            <label><span>{t('arabicName')}</span><input value={form.arabicName ?? ''} onChange={(event) => set('arabicName', event.target.value)} placeholder="مثال: باراسيتامول" dir="rtl" /></label>
            <label><span>{t('bodySystem')} *</span><select value={form.systemId ?? ''} onChange={(event) => set('systemId', event.target.value)}>{bodySystems.map((system) => <option key={system.id} value={system.id}>{language === 'ar' ? system.nameAr : system.name}</option>)}</select></label>
            <label><span>{t('aliases')}</span><textarea value={aliases} onChange={(event) => setAliases(event.target.value)} placeholder="Acetaminophen; Panadol" rows={2} /></label>
            <label><span>{t('pharmaClass')}</span><input value={form.pharmacologyClass ?? ''} onChange={(event) => set('pharmacologyClass', event.target.value)} placeholder="Analgesics and antipyretics" /></label>
            <label><span>{t('pharmaClassAr')}</span><input value={form.pharmacologyClassAr ?? ''} onChange={(event) => set('pharmacologyClassAr', event.target.value)} dir="rtl" placeholder="المسكنات وخافضات الحرارة" /></label>
          </div>

          <button className="trusted-fetch" type="button" onClick={fetchTrusted} disabled={fetching || !form.genericName?.trim()}>
            <span>{fetching ? <LoaderCircle className="spin" size={20} /> : <CloudDownload size={20} />}</span>
            <div><strong>{t('fetchTrusted')}</strong><small>RxNorm · DailyMed · openFDA</small></div><Search size={17} />
          </button>
          {fetchMessage && <div className="fetch-success"><Check size={15} />{fetchMessage}</div>}

          <div className="editor-section-title"><span>02</span><div><strong>{language === 'ar' ? 'المعلومات الدوائية' : 'Pharmacology content'}</strong><small>{language === 'ar' ? 'محتوى ثنائي اللغة وقابل للتحرير' : 'Editable bilingual reference content'}</small></div></div>
          <div className="form-grid">
            <label className="span-2"><span>{t('description')}</span><textarea value={form.description ?? ''} onChange={(event) => set('description', event.target.value)} rows={3} /></label>
            <label className="span-2"><span>{t('descriptionAr')}</span><textarea value={form.descriptionAr ?? ''} onChange={(event) => set('descriptionAr', event.target.value)} rows={3} dir="rtl" /></label>
            <label className="span-2"><span>{t('mechanism')}</span><textarea value={form.mechanism ?? ''} onChange={(event) => set('mechanism', event.target.value)} rows={3} /></label>
            <label className="span-2"><span>{t('mechanism')} · العربية</span><textarea value={form.mechanismAr ?? ''} onChange={(event) => set('mechanismAr', event.target.value)} rows={3} dir="rtl" /></label>
            <label><span>{t('indications')} · EN</span><textarea value={indications} onChange={(event) => setIndications(event.target.value)} rows={4} placeholder="One item per line" /></label>
            <label><span>{t('indications')} · AR</span><textarea value={indicationsAr} onChange={(event) => setIndicationsAr(event.target.value)} rows={4} dir="rtl" placeholder="عنصر في كل سطر" /></label>
            <label><span>{t('cautions')} · EN</span><textarea value={cautions} onChange={(event) => setCautions(event.target.value)} rows={4} placeholder="One item per line" /></label>
            <label><span>{t('cautions')} · AR</span><textarea value={cautionsAr} onChange={(event) => setCautionsAr(event.target.value)} rows={4} dir="rtl" placeholder="عنصر في كل سطر" /></label>
            <label><span>{t('dosing')} · EN</span><textarea value={form.dosageNote ?? ''} onChange={(event) => set('dosageNote', event.target.value)} rows={3} /></label>
            <label><span>{t('dosing')} · AR</span><textarea value={form.dosageNoteAr ?? ''} onChange={(event) => set('dosageNoteAr', event.target.value)} rows={3} dir="rtl" /></label>
          </div>
          {formError && <div className="form-error"><AlertTriangle size={16} />{formError}</div>}
        </div>

        <footer className="editor-footer">
          {drug && <button type="button" className="danger-button" onClick={remove} disabled={saving}><Trash2 size={16} /> {t('delete')}</button>}
          <span />
          <button type="button" className="secondary-button" onClick={onClose}>{t('cancel')}</button>
          <button type="submit" className="primary-button" disabled={saving}>{saving ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}{t('save')}</button>
        </footer>
      </form>
    </Modal>
  );
}

interface ProductEditorProps {
  drug: Drug;
  product?: Product;
  onClose: () => void;
  onSaved?: (product: Product) => void | Promise<void>;
  onDeleted?: (productId: string) => void | Promise<void>;
}

export function ProductEditor({ drug, product, onClose, onSaved, onDeleted }: ProductEditorProps) {
  const { t, language, refresh, showToast } = useAph();
  const [form, setForm] = useState<Partial<Product>>(() => product ? { ...product } : {
    drugId: drug.id, brandName: '', brandNameAr: '', strength: '', dosageForm: '', dosageFormAr: '',
    manufacturer: '', country: '', localAgent: '', market: '', barcode: '', registrationNumber: '', verified: false,
  });
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const set = (key: keyof Product, value: unknown) => setForm((current) => ({ ...current, [key]: value }));

  const imageChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try { set('image', await optimizeImage(file)); }
    catch (cause) { setFormError(cause instanceof Error ? cause.message : 'Could not process image.'); }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!form.brandName?.trim()) { setFormError(language === 'ar' ? 'اسم المنتج مطلوب.' : 'Brand name is required.'); return; }
    setSaving(true);
    try {
      const saved = await saveProduct({ ...form, drugId: drug.id, brandName: form.brandName });
      await refresh();
      if (onSaved) await onSaved(saved);
      showToast(language === 'ar' ? 'تم حفظ المنتج محلياً' : 'Trade product saved locally');
      onClose();
    } catch (cause) { setFormError(cause instanceof Error ? cause.message : 'Could not save product.'); }
    finally { setSaving(false); }
  };

  const remove = async () => {
    if (!product || !window.confirm(t('confirmDelete'))) return;
    setSaving(true);
    setFormError('');
    try {
      if (onDeleted) await onDeleted(product.id);
      await deleteProduct(product.id);
      await refresh();
      showToast(language === 'ar' ? 'تم حذف المنتج' : 'Product deleted', 'info');
      onClose();
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : 'Could not delete product.');
    } finally { setSaving(false); }
  };

  return (
    <Modal onClose={onClose} className="editor-modal editor-modal--product" labelledBy="product-editor-title">
      <form onSubmit={submit}>
        <header className="editor-header"><div><span className="section-kicker">{drug.genericName}</span><h2 id="product-editor-title">{product ? t('editProductTitle') : t('addProductTitle')}</h2></div><button type="button" onClick={onClose}><X size={20} /></button></header>
        <div className="editor-body">
          <section className="editor-image-row">
            <div className={`editor-image-preview ${form.image ? 'has-image' : ''}`}>{form.image ? <img src={form.image} alt="Product" /> : <PackagePlus size={29} />}</div>
            <div><strong>{t('productImage')}</strong><p>{language === 'ar' ? 'التقط أو ارفع صورة واضحة للعبوة.' : 'Capture or upload a clear pack photo.'}</p><div className="image-action-row"><label className="secondary-button"><ImagePlus size={16} /> {form.image ? t('replacePhoto') : t('uploadPhoto')}<input hidden type="file" accept="image/*" capture="environment" onChange={imageChange} /></label><a className="text-button" href={googleImagesUrl(`${form.brandName || drug.genericName} ${form.strength || ''}`)} target="_blank" rel="noreferrer"><ImageSearch size={15} /> Google <ExternalLink size={12} /></a></div></div>
          </section>
          <div className="form-grid">
            <label><span>{t('brandName')} *</span><input value={form.brandName ?? ''} onChange={(event) => set('brandName', event.target.value)} placeholder="e.g. Panadol" /></label>
            <label><span>{t('brandName')} · AR</span><input value={form.brandNameAr ?? ''} onChange={(event) => set('brandNameAr', event.target.value)} dir="rtl" /></label>
            <label><span>{t('strength')}</span><input value={form.strength ?? ''} onChange={(event) => set('strength', event.target.value)} placeholder="500 mg" /></label>
            <label><span>{t('form')}</span><input value={form.dosageForm ?? ''} onChange={(event) => set('dosageForm', event.target.value)} placeholder="Film-coated tablet" /></label>
            <label><span>{t('form')} · AR</span><input value={form.dosageFormAr ?? ''} onChange={(event) => set('dosageFormAr', event.target.value)} dir="rtl" placeholder="أقراص مغلفة" /></label>
            <label><span>{t('manufacturer')}</span><input value={form.manufacturer ?? ''} onChange={(event) => set('manufacturer', event.target.value)} /></label>
            <label><span>{t('country')}</span><input value={form.country ?? ''} onChange={(event) => set('country', event.target.value)} /></label>
            <label><span>{t('agent')}</span><input value={form.localAgent ?? ''} onChange={(event) => set('localAgent', event.target.value)} /></label>
            <label><span>{t('market')}</span><input value={form.market ?? ''} onChange={(event) => set('market', event.target.value)} placeholder="Yemen" /></label>
            <label><span>{t('barcode')}</span><input value={form.barcode ?? ''} onChange={(event) => set('barcode', event.target.value)} inputMode="numeric" /></label>
            <label><span>{t('registration')}</span><input value={form.registrationNumber ?? ''} onChange={(event) => set('registrationNumber', event.target.value)} /></label>
            <label className="checkbox-label span-2"><input type="checkbox" checked={form.verified ?? false} onChange={(event) => set('verified', event.target.checked)} /><span><Check size={14} /></span><strong>{t('locallyVerified')}</strong></label>
          </div>
          {formError && <div className="form-error"><AlertTriangle size={16} />{formError}</div>}
        </div>
        <footer className="editor-footer">
          {product && <button type="button" className="danger-button" onClick={remove} disabled={saving}><Trash2 size={16} /> {t('delete')}</button>}<span />
          <button type="button" className="secondary-button" onClick={onClose}>{t('cancel')}</button><button type="submit" className="primary-button" disabled={saving}>{saving ? <LoaderCircle className="spin" size={17} /> : <Save size={17} />}{t('save')}</button>
        </footer>
      </form>
    </Modal>
  );
}
