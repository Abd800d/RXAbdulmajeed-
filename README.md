# قارئ الوصفات الطبية — Rx Reader

<p align="center">
  <img src="docs/design/home-mockup.jpg" alt="التصميم المرجعي للشاشة الرئيسية" width="260">
</p>

تطبيق Android احترافي يقرأ الوصفات الطبية المكتوبة بخط اليد بالذكاء الاصطناعي، ويستخرج الأدوية بالاسم التجاري والعلمي مع الجرعة. يبحث بدقة عن الأدوية في قوقل والمصادر الرسمية، ويضم قاعدة بيانات أدوية دائمة قابلة للاستيراد والتصدير (CSV / Excel / JSON)، ومساعداً دوائياً ذكياً مرتبطاً بمصادر موثوقة. يدعم العربية والإنجليزية.

**إعداد: د. عبدالمجيد الماخذي — Prepared by Dr. Abdulmajeed Almakhedhi**

- 📋 **خطة البناء الكاملة:** [docs/PLAN.md](docs/PLAN.md)
- 🎨 **التصميم المرجعي:** [docs/design/home-mockup.jpg](docs/design/home-mockup.jpg)

## حالة المشروع

- ✅ **المرحلة 0 (التأسيس) مكتملة:** هيكل Android متعدد الوحدات، ونظام التصميم بألوان التصميم المرجعي وخط Cairo، والشاشة الرئيسية بالعربية والإنجليزية، وبناء آلي يُنتج ملف APK.
- ⏳ **القادم — المرحلة 1:** تسجيل الدخول (البريد الإلكتروني + Google) وربط Firebase.

## البناء والتشغيل

المتطلبات: أحدث إصدار من Android Studio مع JDK 21.

```bash
./gradlew assembleDebug
```

ملف التثبيت: `app/build/outputs/apk/debug/app-debug.apk`.
تُبنى نسخة تلقائياً مع كل دفع على الفرع، وتجدها في صفحة **Actions** ثم **rxreader-debug-apk**.

## هيكل المشروع

```text
app/                   # نقطة الدخول (MainActivity) والشاشة الرئيسية
core/designsystem/     # نظام التصميم: الألوان، خط Cairo، الثيم
feature/home/          # الشاشة الرئيسية مطابقة للتصميم المرجعي
docs/                  # الخطة والتصاميم
```

## الدعم والتواصل

- واتساب: [‎+967 781 733 795](https://wa.me/967781733795)
- البريد الإلكتروني: [7a8b1d@gmail.com](mailto:7a8b1d@gmail.com)

---

### English

**Rx Reader** is a native Android app that reads handwritten prescriptions using AI (Google Gemini). It extracts trade and scientific drug names with dosage and matches them against a persistent local drug database that supports CSV, Excel and JSON import and export. It also searches trusted medical sources and Google, and includes an AI drug assistant that cites its sources. The app is Arabic-first (RTL) with English support.

- Full build plan (in Arabic): [docs/PLAN.md](docs/PLAN.md).
- Phase 0 is done: multi-module setup, design system (Cairo font, mockup colors), home screen in Arabic and English, and CI that builds a debug APK.
- Build: `./gradlew assembleDebug` (Android Studio + JDK 21).
