# APH · AbdulmajeedPharma

**APH** is a bilingual Arabic/English, mobile-first pharmacology and commercial-medicine platform owned by **Dr. Abdulmajeed Abdullah Almakhedhi**. The validated React/Capacitor V1 remains available while V2 moves the long-term mobile client to Flutter with permanent Drift/SQLite storage and adds a secure FastAPI gateway over Supabase/PostgreSQL.

## What is included

- All 18 requested body-system categories, pharmacology classes, scientific medicines, and editable trade products
- Product strength, dosage form, manufacturer, country, Yemen market/agent, registration, barcode, leaflet, and image fields
- Arabic RTL and English LTR interfaces
- Fast local search plus cloud PostgreSQL trigram/full-text search for million-row catalogs
- Offline-first IndexedDB storage, PWA caching, persistent-storage request, and on-demand cloud result caching
- Barcode scanning, Arabic/English OCR, and optional server-side Google Cloud Vision identification
- Source-grounded assistant with a deterministic offline mode and optional OpenAI/Gemini Edge Function mode
- Trusted-source enrichment from RxNorm/RxNav, openFDA/DailyMed, and PubChem; optional Google Custom Search imagery
- In-app public support questions and realtime administrator replies
- Separate responsive administration portal at `/admin`
- Password and email-code/magic-link authentication with `owner`, `admin`, `pharmacist_editor`, `reviewer`, `support`, and `user` roles
- Resumable batched CSV/TSV/JSON/JSONL/XLSX imports and local-image upload ingestion
- Capacitor Android project (`com.abdulmajeed.aph`) and automated APK workflow

> **Data integrity:** APH never fabricates a manufacturer, Yemen agent/importer, registration, or market availability. Missing values remain empty until an authorized administrator imports or verifies them. The bundled 22-medicine/24-product catalog is an offline starter, not a complete national formulary.

## V2 transition

The current application is retained unchanged as the stable migration baseline. New V2 components live alongside it:

- [`apps/aph_mobile`](apps/aph_mobile) — Flutter/Material 3 Arabic-English client, Drift/SQLite cache, favorites, barcode confirmation, cursor sync, and grounded-assistant boundary.
- [`services/api`](services/api) — FastAPI `/v1` gateway for protected Supabase access, search, synchronization, and source-grounded AI.
- [`supabase/migrations/202610190001_aph_v2_domain.sql`](supabase/migrations/202610190001_aph_v2_domain.sql) — normalized clinical/market/provenance/review/import/message/sync domain.
- [`docs/V2_ARCHITECTURE.md`](docs/V2_ARCHITECTURE.md) — approved boundaries, rollout stages, and definition of production completion.

The V2 preview build is separately named and does not replace `APH-AbdulmajeedPharma-v1.1.0.apk`.

## Local development

```bash
npm ci
npm run dev
```

Open `http://localhost:4173`. The mobile app works without cloud configuration. Open `http://localhost:4173/admin` for the administration portal.

### Validation

```bash
npm run validate
npm audit --audit-level=moderate
```

`npm run validate` runs TypeScript, 10 application tests, a real PostgreSQL-WASM migration/security test, and the production Vite/PWA build.

## Current V1 production architecture

| Layer | Implementation |
|---|---|
| Mobile/PWA | React 19, TypeScript, Vite, Capacitor |
| Permanent offline data | Dexie/IndexedDB and Workbox PWA cache |
| Shared database | Supabase PostgreSQL with RLS, audit log, tombstones, and import jobs |
| Search | Indexed local Fuse search + PostgreSQL `pg_trgm` and `tsvector` GIN indexes |
| Authentication | Supabase Auth password + email OTP/magic link |
| Images/import files | Supabase Storage |
| Live updates | Supabase Realtime |
| AI/search/vision secrets | Supabase Edge Function secrets only |
| Large imports | Trusted Node CLI with service-role key, batches, checkpoints, and image uploads |

The initial mobile sync caches the newest bounded catalog subset instead of trying to copy a million records into a phone. Cloud search covers the complete published database, and selected results are cached for later offline use. Realtime updates and public deletion tombstones keep cached records current.

## Connect Supabase

No credentials or project-specific secrets are committed. A project owner must create/link a Supabase project before shared cloud features can operate.

```bash
cp .env.example .env.local
npx supabase login
npx supabase link --project-ref YOUR_PROJECT_REF
npx supabase db push
npx supabase functions deploy ai-assistant
npx supabase functions deploy enrich-medicine
npx supabase functions deploy identify-product
```

Put only public client values in `.env.local`:

```dotenv
VITE_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
VITE_SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY
```

Provider and service-role secrets must **never** use a `VITE_` prefix and must never be embedded in the web bundle or APK:

```bash
npx supabase secrets set OPENAI_API_KEY=NEW_KEY
npx supabase secrets set GEMINI_API_KEY=NEW_KEY
npx supabase secrets set GOOGLE_VISION_API_KEY=NEW_KEY
npx supabase secrets set GOOGLE_SEARCH_API_KEY=NEW_KEY GOOGLE_SEARCH_CX=YOUR_CX
```

See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for role bootstrap, Auth redirect/OTP setup, Edge secrets, import operations, production checks, and Android deployment.

## Large catalog import

The browser Data Hub is convenient for moderate files. Use the trusted server/admin CLI for hundreds of thousands or millions of rows:

```bash
SUPABASE_URL=https://YOUR_PROJECT.supabase.co \
SUPABASE_SERVICE_ROLE_KEY='SERVER_ONLY_KEY' \
node scripts/import-to-supabase.mjs ./verified-catalog.csv \
  --publish --images-dir ./verified-images
```

Resume safely after interruption:

```bash
SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \
node scripts/import-to-supabase.mjs ./verified-catalog.csv \
  --publish --images-dir ./verified-images --resume
```

Useful options:

- `--dry-run` parses and validates without credentials or writes
- `--batch-size 500` selects a durable batch size from 10–1000
- `--checkpoint /secure/path/job.json` changes the atomic checkpoint path
- `--images-dir ./images` uploads referenced JPG/PNG/WebP files to Storage
- `--no-images` skips local files while retaining remote HTTPS image URLs

CSV/TSV, top-level JSON arrays, structured APH JSON backups, JSONL/NDJSON, XLSX, and XLS are supported. CSV or JSONL is recommended for million-row ingestion because it is streamed in constant memory. XLSX must be loaded as a workbook and therefore needs substantially more trusted-machine memory.

Recognized fields include:

```text
generic_name, arabic_name, body_system, pharmacology_class,
brand_name, strength, dosage_form, manufacturer, country,
local_agent, market, barcode, registration_number,
drug_image, product_image, image_url, leaflet_url, verification_status
```

Local image paths can appear in image columns. With `--images-dir`, blank product images are also matched by product ID, barcode, or normalized brand name.

## Android APK

The Android application ID is `com.abdulmajeed.aph`; v1.1.0 uses `versionCode 2`, so it upgrades the earlier v1.0 build.

```bash
npm ci
npm run validate
npx cap sync android
cd android
./gradlew assembleDebug
```

The local output is `android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions validates the app, builds the APK with Java 21/SDK 35, uploads it as an artifact, and publishes `APH-AbdulmajeedPharma-v1.1.0.apk` to this Arena branch. Set repository variables `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` before triggering the workflow to produce a cloud-connected APK.

## Safety and privacy

- The assistant is an evidence reference, not a diagnosis or prescribing system.
- Provider keys and the Supabase service-role key stay server-side.
- Public support messages never expose the optional contact email; emails are stored in an administrator-only table.
- RLS prevents ordinary users from changing roles or editing catalog records.
- Uploaded Yemen/Arab market data should be published only after source and registration review.
- Previously exposed provider credentials must be revoked rather than reused.

## Contact and ownership

- WhatsApp: [`+967 781 733 795`](https://wa.me/967781733795)
- Email: [`7a8b1d@gmail.com`](mailto:7a8b1d@gmail.com)

© 2026 Dr. Abdulmajeed Abdullah Almakhedhi. All rights reserved.
