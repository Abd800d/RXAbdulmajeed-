import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

class AphLocalizations {
  const AphLocalizations(this.locale);

  final Locale locale;

  bool get isArabic => locale.languageCode == 'ar';

  static const supportedLocales = [Locale('ar'), Locale('en')];

  static const LocalizationsDelegate<AphLocalizations> delegate = _AphLocalizationsDelegate();

  static AphLocalizations of(BuildContext context) =>
      Localizations.of<AphLocalizations>(context, AphLocalizations)!;

  static const _strings = <String, Map<String, String>>{
    'ar': {
      'appName': 'عبدالمجيد فارما',
      'home': 'الرئيسية',
      'search': 'البحث',
      'scan': 'مسح',
      'favorites': 'المفضلة',
      'settings': 'الإعدادات',
      'welcome': 'مرجع دوائي موثوق، معك أينما كنت',
      'searchHint': 'ابحث عن مادة فعالة أو اسم تجاري…',
      'bodySystems': 'الأجهزة والمجالات العلاجية',
      'viewAll': 'عرض الكل',
      'offlineReady': 'المكتبة المحفوظة متاحة دون اتصال',
      'online': 'متصل بالخدمة',
      'offline': 'وضع عدم الاتصال',
      'noResults': 'لا توجد نتائج مطابقة',
      'tryAnotherSearch': 'جرّب اسماً علمياً أو تجارياً آخر',
      'recentlyUpdated': 'تم التحديث مؤخراً',
      'clinicalInformation': 'المعلومات السريرية',
      'brandProducts': 'المنتجات التجارية',
      'indications': 'دواعي الاستعمال',
      'mechanism': 'آلية العمل',
      'dosing': 'الجرعات',
      'warnings': 'التحذيرات',
      'contraindications': 'موانع الاستعمال',
      'interactions': 'التداخلات',
      'adverseEffects': 'الآثار الجانبية',
      'sources': 'المصادر',
      'notDocumented': 'غير موثق في السجل المعتمد',
      'syncNow': 'مزامنة الآن',
      'lastSync': 'آخر مزامنة',
      'never': 'لم تتم بعد',
      'language': 'اللغة',
      'arabic': 'العربية',
      'english': 'English',
      'appearance': 'المظهر',
      'about': 'حول APH',
      'rights': 'الحقوق والملكية',
      'medicalNotice': 'معلومات تثقيفية؛ لا تستبدل تقييم الطبيب أو الصيدلي.',
      'confirmMedicine': 'تأكد من الدواء',
      'scanInstruction': 'وجّه الكاميرا نحو الباركود. يجب تأكيد المطابقة قبل استخدامها.',
      'assistant': 'المساعد السريري',
      'comingOnline': 'تتطلب هذه الميزة اتصالاً بالخدمة الآمنة.',
      'saved': 'محفوظ',
      'save': 'حفظ',
      'retry': 'إعادة المحاولة',
      'unknown': 'غير مؤكد',
      'verified': 'موثق',
      'details': 'التفاصيل',
      'catalogue': 'الدليل الدوائي',
    },
    'en': {
      'appName': 'AbdulmajeedPharma',
      'home': 'Home',
      'search': 'Search',
      'scan': 'Scan',
      'favorites': 'Favorites',
      'settings': 'Settings',
      'welcome': 'Trusted medicine knowledge, wherever you are',
      'searchHint': 'Search an ingredient or brand…',
      'bodySystems': 'Body systems & therapeutic areas',
      'viewAll': 'View all',
      'offlineReady': 'Saved library is available offline',
      'online': 'Connected to service',
      'offline': 'Offline mode',
      'noResults': 'No matching results',
      'tryAnotherSearch': 'Try another scientific or brand name',
      'recentlyUpdated': 'Recently updated',
      'clinicalInformation': 'Clinical information',
      'brandProducts': 'Branded products',
      'indications': 'Indications',
      'mechanism': 'Mechanism of action',
      'dosing': 'Dosing',
      'warnings': 'Warnings',
      'contraindications': 'Contraindications',
      'interactions': 'Interactions',
      'adverseEffects': 'Adverse effects',
      'sources': 'Sources',
      'notDocumented': 'Not documented in the approved record',
      'syncNow': 'Sync now',
      'lastSync': 'Last sync',
      'never': 'Not yet',
      'language': 'Language',
      'arabic': 'العربية',
      'english': 'English',
      'appearance': 'Appearance',
      'about': 'About APH',
      'rights': 'Rights & ownership',
      'medicalNotice': 'Educational information; not a substitute for clinician or pharmacist assessment.',
      'confirmMedicine': 'Confirm the medicine',
      'scanInstruction': 'Point at a barcode. You must confirm the match before relying on it.',
      'assistant': 'Clinical Assistant',
      'comingOnline': 'This feature requires the secure online service.',
      'saved': 'Saved',
      'save': 'Save',
      'retry': 'Retry',
      'unknown': 'Unconfirmed',
      'verified': 'Verified',
      'details': 'Details',
      'catalogue': 'Medicine catalogue',
    },
  };

  String text(String key) => _strings[locale.languageCode]?[key] ?? _strings['en']![key] ?? key;
}

class _AphLocalizationsDelegate extends LocalizationsDelegate<AphLocalizations> {
  const _AphLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => ['ar', 'en'].contains(locale.languageCode);

  @override
  Future<AphLocalizations> load(Locale locale) => SynchronousFuture(AphLocalizations(locale));

  @override
  bool shouldReload(_AphLocalizationsDelegate old) => false;
}

extension AphLocalizationContext on BuildContext {
  AphLocalizations get l10n => AphLocalizations.of(this);
}
