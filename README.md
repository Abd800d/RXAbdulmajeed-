# APH · AbdulmajeedPharma

A bilingual, offline-first pharmacology and medicine catalog designed for mobile use in Yemen. The app is installable as a PWA and is ready to be wrapped as native Android/iOS software with Capacitor.

## Included

- 18 body-system pharmacology categories in Arabic and English
- Scientific-drug profiles, pharmacology classes, mechanisms, indications, safety notes, and cited sources
- Trade-product records with strength, dosage form, manufacturer, country, Yemen agent/importer, registration, barcode, and image fields
- Typo-tolerant scientific, Arabic, trade-name, and barcode search
- Camera/upload label recognition using on-device barcode detection and bilingual OCR
- Source-grounded medication assistant with NLM/FDA citations
- Permanent local IndexedDB storage plus PWA offline caching
- Manual medicine/product creation, full editing, local image storage, and deletion
- Chunked CSV import, JSON backup/restore, Excel import/export, and CSV export
- Live integrations prepared for RxNorm/RxNav, DailyMed, openFDA, PubChem, Google Images, and Google Lens
- Arabic RTL / English LTR interface
- WhatsApp and email support links for Dr. Abdulmajeed Almakhedhi

> The bundled records are a curated starter catalog, not a complete national formulary. APH is engineered to accept large external datasets; local product registration, importers, availability, and clinical content must be verified against current authoritative sources before production use.

## Run locally

```bash
npm install
npm run dev
```

Open `http://localhost:4173`.

## Validate and build

```bash
npm test
npm run build
```

The production PWA is written to `dist/`.

## Data import

Use **Data hub → Import database**. Supported formats:

- `.csv` (streamed in chunks; recommended for very large catalogs)
- `.xlsx` / `.xls`
- `.json`

Download the in-app CSV template for column names. Common recognized fields include:

```text
generic_name, arabic_name, body_system, pharmacology_class,
brand_name, strength, dosage_form, manufacturer, country,
local_agent, market, barcode, registration_number, image_url, verified
```

A full APH JSON export contains separate `drugs` and `products` arrays and can be restored without losing images or source metadata.

## Trusted online sources

The editor can enrich a scientific name from public RxNorm and openFDA endpoints. Product imagery can be researched through Google Images, while molecular structures use PubChem. Automatic licensed image ingestion or production AI should go through a secure backend, not an API key embedded in the client.

Set an optional source-grounded AI proxy in `.env.local`:

```bash
VITE_APH_AI_ENDPOINT=https://your-secure-api.example.com/medication-answer
```

The endpoint receives `{ question, language, policy }` and should return `{ answer, sources }`. Do not put provider secrets in `VITE_*` variables.

## Native Android APK

The repository includes a complete Capacitor Android project with APH launcher and splash artwork. Build an installable debug APK with:

```bash
npm ci
npm run build
npx cap sync android
cd android
./gradlew assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds and publishes the APK as a workflow artifact on every push to this Arena branch.

For iOS, add `@capacitor/ios` and run the equivalent Capacitor commands on macOS with Xcode.

## Ownership

© 2026 Dr. Abdulmajeed Abdullah Almakhedhi. All rights reserved.
