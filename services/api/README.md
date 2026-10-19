# APH FastAPI gateway

Server-only API for APH V2. It keeps Supabase service credentials and AI provider keys outside Flutter, JavaScript, and APK assets.

```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[test]'
cp .env.example .env
uvicorn app.main:app --reload
```

- Health: `GET /health`
- OpenAPI: `GET /openapi.json`
- Docs: `GET /docs`
- Search: `GET /v1/catalog/search?q=amoxicillin&lang=en`
- Sync cursor: `GET /v1/sync/changes?after=0`
- Grounded assistant: `POST /v1/assistant/answer`

The service intentionally returns `503` for cloud operations until valid server environment variables are configured.
