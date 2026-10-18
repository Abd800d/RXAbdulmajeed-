import { findDrugInQuestion, getProductsForDrug } from '../db/database';
import { cloudConfigured, invokeCloudAi } from './cloud';
import type { AssistantMessage, Drug, Language, SourceReference } from '../types';

export interface AssistantAnswer {
  text: string;
  sources: SourceReference[];
  relatedDrugId?: string;
}

const asksFor = (question: string, words: string[]) => words.some((word) => question.toLocaleLowerCase().includes(word));

async function askConfiguredAi(question: string, language: Language, drug?: Drug): Promise<AssistantAnswer | undefined> {
  try {
    if (cloudConfigured && navigator.onLine) {
      const result = await invokeCloudAi({
        question,
        language,
        relatedDrugId: drug?.id,
        localContext: drug ? {
          id: drug.id,
          genericName: drug.genericName,
          arabicName: drug.arabicName,
          pharmacologyClass: drug.pharmacologyClass,
          mechanism: drug.mechanism,
          indications: drug.indications,
          cautions: drug.cautions,
          sourceRefs: drug.sourceRefs,
        } : undefined,
      });
      if (result?.answer) return { text: result.answer, sources: result.sources ?? drug?.sourceRefs ?? [], relatedDrugId: result.relatedDrugId || drug?.id };
    }

    const endpoint = import.meta.env.VITE_APH_AI_ENDPOINT as string | undefined;
    if (!endpoint) return undefined;
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, language, relatedDrugId: drug?.id, policy: 'source-grounded-pharmacology-only' }),
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) return undefined;
    const result = await response.json() as { answer?: string; sources?: SourceReference[] };
    if (!result.answer) return undefined;
    return { text: result.answer, sources: result.sources ?? drug?.sourceRefs ?? [], relatedDrugId: drug?.id };
  } catch {
    return undefined;
  }
}

export async function answerMedicationQuestion(question: string, language: Language): Promise<AssistantAnswer | undefined> {
  const drug = await findDrugInQuestion(question);
  const cloudAnswer = await askConfiguredAi(question, language, drug);
  if (cloudAnswer) return cloudAnswer;
  if (!drug) return undefined;

  const products = await getProductsForDrug(drug.id);
  const normalized = question.toLocaleLowerCase();
  const safetyOnly = asksFor(normalized, ['warning', 'caution', 'safe', 'interaction', 'تحذير', 'احتياط', 'آمن', 'تداخل']);
  const mechanismOnly = asksFor(normalized, ['mechanism', 'work', 'action', 'آلية', 'يعمل', 'عمل']);
  const productOnly = asksFor(normalized, ['brand', 'product', 'trade', 'strength', 'منتج', 'تجاري', 'قوة', 'بديل']);

  if (language === 'ar') {
    const title = `${drug.arabicName || drug.genericName} · ${drug.genericName}`;
    if (safetyOnly) {
      return {
        text: `أهم التحذيرات\n${(drug.cautionsAr.length ? drug.cautionsAr : drug.cautions).map((item) => `• ${item}`).join('\n')}\n\nملاحظة الجرعة\n${drug.dosageNoteAr || drug.dosageNote || 'تُراجع النشرة الحالية والحالة السريرية للمريض.'}`,
        sources: drug.sourceRefs,
        relatedDrugId: drug.id,
      };
    }
    if (mechanismOnly) {
      return { text: `${title}\n\nآلية العمل\n${drug.mechanismAr || drug.mechanism}\n\nالمجموعة الدوائية\n${drug.pharmacologyClassAr || drug.pharmacologyClass}`, sources: drug.sourceRefs, relatedDrugId: drug.id };
    }
    if (productOnly) {
      const productLines = products.length
        ? products.slice(0, 8).map((product) => `• ${product.brandName} — ${product.strength}، ${product.dosageFormAr || product.dosageForm} — ${product.manufacturer}`).join('\n')
        : 'لا توجد منتجات تجارية محفوظة محلياً لهذا الدواء بعد.';
      return { text: `${title}\n\nالمنتجات المحفوظة\n${productLines}\n\nتحقق دائماً من تسجيل المنتج وتوفره الحالي في اليمن.`, sources: drug.sourceRefs, relatedDrugId: drug.id };
    }
    return {
      text: `${title}\n${drug.descriptionAr || drug.description}\n\nالمجموعة الدوائية\n${drug.pharmacologyClassAr || drug.pharmacologyClass}\n\nآلية العمل\n${drug.mechanismAr || drug.mechanism}\n\nأهم الاستطبابات\n${(drug.indicationsAr.length ? drug.indicationsAr : drug.indications).map((item) => `• ${item}`).join('\n')}\n\nتنبيه مهم\n${(drug.cautionsAr.length ? drug.cautionsAr : drug.cautions).slice(0, 3).map((item) => `• ${item}`).join('\n')}`,
      sources: drug.sourceRefs,
      relatedDrugId: drug.id,
    };
  }

  const title = `${drug.genericName}${drug.arabicName ? ` · ${drug.arabicName}` : ''}`;
  if (safetyOnly) {
    return {
      text: `Important cautions\n${drug.cautions.map((item) => `• ${item}`).join('\n')}\n\nDosing note\n${drug.dosageNote || 'Check the current label and the patient-specific clinical context.'}`,
      sources: drug.sourceRefs,
      relatedDrugId: drug.id,
    };
  }
  if (mechanismOnly) {
    return { text: `${title}\n\nHow it works\n${drug.mechanism}\n\nPharmacology class\n${drug.pharmacologyClass}`, sources: drug.sourceRefs, relatedDrugId: drug.id };
  }
  if (productOnly) {
    const productLines = products.length
      ? products.slice(0, 8).map((product) => `• ${product.brandName} — ${product.strength}, ${product.dosageForm} — ${product.manufacturer}`).join('\n')
      : 'No trade products are saved locally for this medicine yet.';
    return { text: `${title}\n\nSaved products\n${productLines}\n\nAlways verify the product’s current registration and availability in Yemen.`, sources: drug.sourceRefs, relatedDrugId: drug.id };
  }
  return {
    text: `${title}\n${drug.description}\n\nPharmacology class\n${drug.pharmacologyClass}\n\nHow it works\n${drug.mechanism}\n\nCommon indications\n${drug.indications.map((item) => `• ${item}`).join('\n')}\n\nImportant caution\n${drug.cautions.slice(0, 3).map((item) => `• ${item}`).join('\n')}`,
    sources: drug.sourceRefs,
    relatedDrugId: drug.id,
  };
}

export function createMessage(role: AssistantMessage['role'], text: string, extra: Partial<AssistantMessage> = {}): AssistantMessage {
  return { id: crypto.randomUUID(), role, text, timestamp: new Date().toISOString(), ...extra };
}
