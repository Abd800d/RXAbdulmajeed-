# APH production deployment runbook

This runbook turns the repository into a shared APH deployment. The repository intentionally contains no live Supabase project, private key, or provider credential.

## 1. Prerequisites

- Node.js 22+
- A Supabase organization/project
- Supabase CLI (`npx supabase ...` is sufficient)
- Java 21 and Android SDK 35 only when building Android locally
- Fresh provider keys; do not reuse any credential that has appeared in chat, source code, a screenshot, or an APK

Keep these two classes of configuration separate:

| Value | Exposure | Location |
|---|---|---|
| Supabase project URL | Public | `.env.local`, hosting variables, GitHub Actions variable |
| Supabase anon/publishable key | Public, protected by RLS | `.env.local`, hosting variables, GitHub Actions variable |
| Supabase service-role key | Secret, bypasses RLS | Trusted importer environment only |
| OpenAI/Gemini/Google keys | Secret | Supabase Edge Function secrets only |

Never put a service-role or provider key in `VITE_*`. Vite deliberately includes every `VITE_*` value in browser and APK assets.

## 2. Create and link the database

From the repository root:

```bash
npm ci
npx supabase login
npx supabase link --project-ref YOUR_PROJECT_REF
npx supabase db push
```

The migration creates:

- 18 body systems
- profiles and four roles
- drugs/products with trigram and full-text indexes
- audit records for interactive administrator edits
- import job tracking
- public catalog deletion tombstones for offline-cache cleanup
- public messages plus a separate administrator-only contact table
- rate limits, RLS policies, Storage buckets, and Realtime publications

Run local SQL validation before deploying any schema change:

```bash
npm run test:migration
```

This test executes the complete migration in PostgreSQL/WASM and checks typo search, full-text vectors, role-escalation protection, email privacy, rate-limited message submission, and tombstones.

## 3. Configure Supabase Auth

In **Authentication → URL Configuration**:

- Set the production Site URL, for example `https://aph.example.com`
- Add `https://aph.example.com/admin`
- Keep the required Capacitor redirect URLs if native magic links will be used
- Add localhost URLs only for development

In **Authentication → Providers → Email**:

- Enable email/password
- Enable email OTP/magic-link sign-in
- Require email confirmation in production
- Configure an SMTP provider suitable for production volume

The APH admin form supports both a magic link and a typed email code. To display a code, include Supabase’s token variable in the email template, for example:

```html
<h2>APH secure sign-in</h2>
<p>Your one-time code is <strong>{{ .Token }}</strong>.</p>
<p>You can also use <a href="{{ .ConfirmationURL }}">this secure sign-in link</a>.</p>
```

Verify the current template variable names in the Supabase dashboard before saving, then test delivery, expiration, and redirect behavior with a non-production account.

## 4. Bootstrap the first administrator

All public signups intentionally start as `user`; knowing the owner email alone must never grant administrator rights.

1. Open `/admin`, create the owner account, and confirm its email.
2. In the authenticated project’s Supabase SQL Editor, run:

```sql
update public.profiles
set role = 'admin'
where lower(email) = '7a8b1d@gmail.com';

select id, email, role
from public.profiles
where lower(email) = '7a8b1d@gmail.com';
```

3. Sign out and back in at `/admin`.

An administrator can grant another trusted account `editor` or `admin` through a trusted SQL session. Ordinary authenticated users cannot promote themselves; a database trigger protects `role` and `email`, in addition to RLS.

## 5. Deploy Edge Functions and secrets

Deploy public, rate-limited functions:

```bash
npx supabase functions deploy ai-assistant
npx supabase functions deploy enrich-medicine
npx supabase functions deploy identify-product
```

Set only the providers that will be used:

```bash
npx supabase secrets set OPENAI_API_KEY='NEW_SERVER_KEY'
npx supabase secrets set OPENAI_MODEL='gpt-4.1-mini'

npx supabase secrets set GEMINI_API_KEY='NEW_SERVER_KEY'
npx supabase secrets set GEMINI_MODEL='gemini-2.5-flash'

npx supabase secrets set GOOGLE_VISION_API_KEY='NEW_SERVER_KEY'
npx supabase secrets set GOOGLE_SEARCH_API_KEY='NEW_SERVER_KEY'
npx supabase secrets set GOOGLE_SEARCH_CX='YOUR_PROGRAMMABLE_SEARCH_ENGINE_ID'
```

At least one of OpenAI or Gemini is needed for server AI. Google Vision is optional for image identification. Google Custom Search is optional for enrichment imagery. RxNorm/RxNav, openFDA, DailyMed, and PubChem do not require keys for the implemented public requests.

Apply provider-side restrictions where available:

- enable only the specific API being used
- set quotas and billing alerts
- keep the key server-side
- rotate immediately if exposure is suspected

Type-check all Edge Functions with Deno before deployment:

```bash
npx -y deno@2.9.6 check \
  supabase/functions/_shared/http.ts \
  supabase/functions/ai-assistant/index.ts \
  supabase/functions/enrich-medicine/index.ts \
  supabase/functions/identify-product/index.ts
```

## 6. Configure web/mobile public values

Create `.env.local` from `.env.example`:

```dotenv
VITE_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
VITE_SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY
```

Then rebuild; Vite reads these values at build time:

```bash
npm run validate
```

For GitHub Actions, create repository **Actions variables** (not provider secrets):

- `VITE_SUPABASE_URL`
- `VITE_SUPABASE_ANON_KEY`

The workflow passes only those public values into the APK build. It does not accept or embed service/provider keys.

## 7. Seed or import verified catalog data

### Small starter publication

Sign in as admin, open **Import & publish**, review the local starter records, and use **Publish now**. This publishes the local records in batches.

### Large files

Run the trusted CLI on an administrator machine or controlled worker:

```bash
export SUPABASE_URL='https://YOUR_PROJECT.supabase.co'
export SUPABASE_SERVICE_ROLE_KEY='SERVER_ONLY_SERVICE_ROLE_KEY'

node scripts/import-to-supabase.mjs /data/yemen-verified.csv \
  --publish \
  --images-dir /data/product-images \
  --checkpoint /secure/checkpoints/yemen-verified.json
```

If the process or network stops, rerun the same command with `--resume`. A checkpoint advances only after both drug and product upserts succeed. Retrying a partial batch is safe because IDs and Storage paths are deterministic.

Before a real import:

```bash
node scripts/import-to-supabase.mjs /data/yemen-verified.csv --dry-run
```

Operational notes:

- Prefer CSV/TSV or JSONL/NDJSON for million-row constant-memory streaming.
- Top-level JSON arrays and APH `{ "drugs": [...], "products": [...] }` backups are streamed.
- XLSX/XLS are supported but loaded as workbooks; allocate trusted-machine memory accordingly.
- Default imports are drafts. `--publish` makes them visible.
- A draft re-import will not unpublish already published records.
- Existing verified/rejected status and uploaded images are not silently downgraded or erased.
- Local JPG/PNG/WebP images must be ≤8 MiB and are deduplicated by SHA-256 in Storage.
- Import only images that APH is licensed or authorized to distribute.
- Never infer missing local agents, registrations, countries, or manufacturers.

## 8. Hosted smoke test

After deployment, verify all of the following against the real project:

1. A normal user can read only published drugs/products.
2. A normal user cannot create/edit catalog rows or change their profile role.
3. Admin password login works.
4. Email code and magic-link login work and return to `/admin`.
5. Admin medicine/product edits appear on a second browser/device through Realtime.
6. Unpublishing/deleting a cached record removes it from the second device through tombstones.
7. A typo and an Arabic name return expected cloud search results.
8. An optional support email is visible in admin inbox but absent from public API responses.
9. A public reply appears in the mobile support feed.
10. AI refuses an unnamed/unmatched medicine and answers a matched medicine only from published context.
11. Vision rejects oversized files and identifies a known package when Google Vision is configured.
12. Offline restart still opens cached content and local favorites.

Inspect Supabase database/API/Auth/Edge logs during this test. The local migration test cannot substitute for hosted Auth, Storage, Realtime, SMTP, provider billing, or network behavior.

## 9. Android build and update

APH v1.1.0 has Android `versionCode 2`, allowing upgrade over v1.0.

```bash
npm ci
npm run validate
npx cap sync android
cd android
./gradlew assembleDebug
```

The debug APK is installable and signed with the Android debug key. For a long-term production distribution channel, create and protect a persistent release keystore, configure Gradle release signing through CI secrets, and never commit the keystore or passwords. All future upgrades must use the same release key and an increasing `versionCode`.

## 10. Backups, monitoring, and maintenance

- Enable Supabase backups/PITR appropriate to the project tier.
- Test a restore before relying on it.
- Monitor table/storage growth, especially images, audit records, messages, and tombstones.
- Review Edge Function errors and provider quotas.
- Periodically close/hide resolved support threads.
- Keep source references and medicine labels current.
- Review RLS and run `npm run test:migration` after every schema change.
- Run `npm audit` and rebuild the APK after dependency/security updates.
