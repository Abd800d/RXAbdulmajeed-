from datetime import UTC, datetime

from fastapi import APIRouter, Request

from app import __version__
from app.config import Settings
from app.models import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health(request: Request) -> HealthResponse:
    settings: Settings = request.app.state.settings
    configured = settings.supabase_configured
    return HealthResponse(
        status="ok" if configured else "degraded",
        service=settings.app_name,
        version=__version__,
        environment=settings.environment,
        supabase="configured" if configured else "unconfigured",
        search_engine="postgresql",
        ai_provider=settings.ai_provider,
        timestamp=datetime.now(UTC),
    )
