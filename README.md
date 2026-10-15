# قارئ الوصفات الطبية — Rx Reader

<p align="center">
  <img src="docs/design/home-mockup.jpg" alt="التصميم المرجعي للشاشة الرئيسية" width="260">
</p>

تطبيق Android احترافي يقرأ الوصفات الطبية المكتوبة بخط اليد بالذكاء الاصطناعي، ويستخرج الأدوية بالاسم التجاري والعلمي مع الجرعة. يبحث بدقة عن الأدوية في قوقل والمصادر الرسمية، ويضم قاعدة بيانات أدوية دائمة قابلة للاستيراد والتصدير (CSV / Excel / JSON)، ومساعداً دوائياً ذكياً مرتبطاً بمصادر موثوقة. يدعم العربية والإنجليزية.

**إعداد: د. عبدالمجيد الماخذي — Prepared by Dr. Abdulmajeed Almakhedhi**

- 📋 **خطة البناء الكاملة:** [docs/PLAN.md](docs/PLAN.md)
- 🎨 **التصميم المرجعي:** [docs/design/home-mockup.jpg](docs/design/home-mockup.jpg)

## حالة المشروع

🟡 مرحلة التخطيط: الخطة جاهزة، وننتظر اعتمادها لبدء المرحلة 0 (التأسيس).

## التقنيات باختصار

Kotlin · Jetpack Compose · Material 3 · Room (FTS) · Hilt · WorkManager · ML Kit (Document Scanner) ·
Firebase (Auth · Firestore · Storage · AI Logic / Gemini · Cloud Functions · App Check · Crashlytics · Remote Config)

## الدعم والتواصل

- واتساب: [‎+967 781 733 795](https://wa.me/967781733795)
- البريد الإلكتروني: [7a8b1d@gmail.com](mailto:7a8b1d@gmail.com)

---

### English

**Rx Reader** is a native Android app that reads handwritten prescriptions using AI (Google Gemini). It extracts trade and scientific drug names with dosage and matches them against a persistent local drug database that supports CSV, Excel and JSON import and export. It also searches trusted medical sources and Google, and includes an AI drug assistant that cites its sources. The app is Arabic-first (RTL) with English support.

The full build plan (in Arabic) is in [docs/PLAN.md](docs/PLAN.md).
