# APH Mobile V2 (Flutter)

The new bilingual, offline-first APH client. The existing React/Capacitor application and `APH-AbdulmajeedPharma-v1.1.0.apk` remain intact during migration.

## Run

```bash
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter run \
  --dart-define=APH_API_BASE_URL=https://api.example.com \
  --dart-define=APH_SUPABASE_URL=https://PROJECT.supabase.co \
  --dart-define=APH_SUPABASE_ANON_KEY=PUBLIC_ANON_KEY
```

Only the Supabase **anon** key is permitted as a Dart define. Never add the service-role key or AI-provider secrets to this directory or an APK.

## Build transition artifacts

```bash
flutter build apk --release --dart-define=APH_API_BASE_URL=https://api.example.com
flutter build appbundle --release --dart-define=APH_API_BASE_URL=https://api.example.com
```

The checked-in release configuration intentionally uses debug signing for CI transition previews. It is **not** a Play production signature. Configure an encrypted owner-controlled keystore before a store release.

## Implemented foundation

- Material 3 Arabic/English interface with automatic RTL/LTR.
- All 18 APH body-system categories.
- Drift/SQLite permanent catalogue seeded from the current vetted APH starter records.
- Local search, favorites, ingredient detail, product detail summaries, barcode confirmation.
- Sequence-based resumable cloud synchronization with tombstone deletion.
- FastAPI search and grounded-assistant client boundaries.
- No server or provider secrets in mobile source.
