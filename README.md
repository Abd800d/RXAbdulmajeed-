# قارئ الوصفات الطبية — Rx Reader

<p align="center">
  <img src="docs/design/home-mockup.jpg" alt="التصميم المرجعي للشاشة الرئيسية" width="260">
</p>

تطبيق Android احترافي يقرأ الوصفات الطبية المكتوبة بخط اليد بالذكاء الاصطناعي، ويستخرج الأدوية بالاسم التجاري والعلمي مع الجرعة. يبحث بدقة عن الأدوية في قوقل والمصادر الرسمية، ويضم قاعدة بيانات أدوية دائمة قابلة للاستيراد والتصدير (CSV / Excel / JSON)، ومساعداً دوائياً ذكياً مرتبطاً بمصادر موثوقة. يدعم العربية والإنجليزية.

**إعداد: د. عبدالمجيد الماخذي — Prepared by Dr. Abdulmajeed Almakhedhi**

- 📋 **خطة البناء الكاملة:** [docs/PLAN.md](docs/PLAN.md)
- 🎨 **التصميم المرجعي:** [docs/design/home-mockup.jpg](docs/design/home-mockup.jpg)

## حالة المشروع

- ✅ **المرحلة 0:** هيكل Android متعدد الوحدات، ونظام التصميم، والشاشة الرئيسية بالعربية والإنجليزية، وبناء آلي.
- ✅ **المرحلة 1:** تسجيل الدخول (البريد الإلكتروني + حساب Google + استعادة كلمة المرور) وربط Firebase وشاشة «تواصل معنا».
- ✅ **المرحلة 2:** تصوير الوصفة (ماسح ذكي + كاميرا بديلة + معرض مع قص) وقراءتها بالذكاء الاصطناعي مع شاشة مراجعة للصيدلي.
- ⏳ **القادم — المرحلة 3:** قاعدة بيانات الأدوية الدائمة (حفظ السجلات + الاستيراد/التصدير).

## كيف يعمل التصوير والتحليل

1. زر **التصوير** يفتح ماسح المستندات الذكي (ML Kit) — يعمل بدون إذن كاميرا. إن تعذّر (لا توجد خدمات Google) تُفتح كاميرا بديلة داخل التطبيق.
2. زر **المعرض** يختار صورة ثم يقصّها قبل التحليل.
3. يمكن **مشاركة صورة وصفة** من واتساب أو المعرض إلى التطبيق مباشرة فيفتح التحليل.
4. تُفحص جودة الصورة (الاهتزاز/الإضاءة) قبل صرف أي استدعاء ذكاء اصطناعي.
5. شاشة **النتيجة** تعرض الأدوية مع الثقة والتعليمات، وتُظلَّل على الصورة الأصلية، مع إمكانية التعديل والتأكيد.

ملاحظة: القراءة الآلية تعمل بعد إعداد Firebase (أدناه) — وقبل ذلك يعرض التطبيق رسالة واضحة بدل الفشل الصامت.

## البناء والتشغيل

المتطلبات: أحدث إصدار من Android Studio مع JDK 21.

```bash
./gradlew assembleDebug
```

ملف التثبيت: `app/build/outputs/apk/debug/app-debug.apk`.
تُبنى نسخة تلقائياً مع كل دفع على الفرع، وتجدها في صفحة **Actions** ثم **rxreader-debug-apk**.

## إعداد Firebase (خطوة واحدة من المالك)

التطبيق يعمل بدون Firebase، لكن **الدخول وقراءة الوصفات** لن يعملا حتى تُنشئ المشروع:

1. ادخل إلى [Firebase Console](https://console.firebase.google.com) بحساب `7a8b1d@gmail.com` وأنشئ مشروعاً جديداً (بدون Google Analytics — يُفعّل لاحقاً).
2. أضف تطبيق Android: اسم الحزمة `com.almakhedhi.rxreader`، وبصمة SHA-1 التالية (مفتاح التطوير المشترك):
   `07:B5:45:90:CF:96:C9:D5:A7:27:90:2B:82:92:FC:16:46:AD:74:79`
3. من **Authentication ← Sign-in method** فعّل: **البريد الإلكتروني/كلمة المرور** و**Google**.
4. من **AI Logic** (أو Vertex AI في Firebase) فعّل واجهة Gemini لنموذج Flash المستخدم في قراءة الوصفات.
5. حمّل ملف `google-services.json` وضعه في `app/google-services.json` (مُتجاهَل من Git — لا يُرفع للمستودع).
6. لتفعيل الدخول والتحليل في نسخ البناء الآلي: الصق محتوى الملف في سر جديد باسم `GOOGLE_SERVICES_JSON` من صفحة المستودع: **Settings ← Secrets and variables ← Actions ← New repository secret**.

## هيكل المشروع

```text
app/                   # نقطة الدخول وحقن الاعتماديات (Hilt) والتنقل وبوابة الدخول
core/auth/             # مستودع المصادقة (Firebase Auth)
core/scanner/          # الماسح الذكي + تجهيز الصور + فحص الجودة
core/ai/               # قارئ الوصفات بالذكاء الاصطناعي (Firebase AI Logic)
core/designsystem/     # نظام التصميم: الألوان، خط Cairo، الثيم، المكونات المشتركة
feature/auth/          # شاشات الدخول وإنشاء الحساب والاستعادة
feature/home/          # الشاشة الرئيسية مطابقة للتصميم المرجعي
feature/scan/          # التصوير والقص والتحليل ومراجعة النتيجة
feature/support/       # شاشة «تواصل معنا» (واتساب + بريد)
docs/                  # الخطة والتصاميم
```

ملاحظة: `app/debug.p12` مفتاح توقيع للتطوير فقط، مضمّن عمداً لتوحيد البصمة بين الأجهزة والبناء الآلي. مفتاح النشر النهائي يُنشأ في مرحلة الإطلاق ولا يُرفع أبداً.

## الدعم والتواصل

- واتساب: [‎+967 781 733 795](https://wa.me/967781733795)
- البريد الإلكتروني: [7a8b1d@gmail.com](mailto:7a8b1d@gmail.com)

---

### English

**Rx Reader** is a native Android app that reads handwritten prescriptions using AI (Google Gemini). It extracts trade and scientific drug names with dosage and matches them against a persistent local drug database that supports CSV, Excel and JSON import and export. It also searches trusted medical sources and Google, and includes an AI drug assistant that cites its sources. The app is Arabic-first (RTL) with English support.

- Full build plan (in Arabic): [docs/PLAN.md](docs/PLAN.md).
- Phase 0 is done: multi-module setup, design system (Cairo font, mockup colors), home screen in Arabic and English, and CI that builds a debug APK.
- Phase 1 is done: sign-in (email + Google + password reset), Firebase wiring, Hilt DI, and the contact screen.
- Phase 2 is done: prescription capture (ML Kit smart scanner + in-app camera fallback + gallery with crop, share-to-app, quality gate) and AI reading with a pharmacist review screen.
- Build: `./gradlew assembleDebug` (Android Studio + JDK 21).
- Firebase setup (owner only): create a project under `7a8b1d@gmail.com`, add Android app `com.almakhedhi.rxreader` with SHA-1 `07:B5:45:90:CF:96:C9:D5:A7:27:90:2B:82:92:FC:16:46:AD:74:79`, enable Email/Password and Google providers plus AI Logic (Gemini) for the Flash model, then place `google-services.json` in `app/` (git-ignored) and also save its content as the `GOOGLE_SERVICES_JSON` repo secret so CI builds get Firebase.
