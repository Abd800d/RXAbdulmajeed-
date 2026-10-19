from fastapi import APIRouter, HTTPException, Request, status

from app.assistant import AiProviderUnavailableError, AssistantService, NoGroundedContextError
from app.catalog import CatalogService
from app.config import Settings
from app.models import AssistantRequest, AssistantResponse
from app.rate_limit import RateLimit, enforce_rate_limit

router = APIRouter(prefix="/assistant", tags=["assistant"])


@router.post("/answer", response_model=AssistantResponse)
async def answer_question(payload: AssistantRequest, request: Request) -> AssistantResponse:
    settings: Settings = request.app.state.settings
    await enforce_rate_limit(
        request,
        "assistant",
        RateLimit(requests=settings.assistant_rate_limit_per_hour, window_seconds=3600),
    )
    service = AssistantService(
        settings=settings,
        client=request.app.state.http,
        catalog=CatalogService(request.app.state.gateway),
    )
    try:
        return await service.answer(payload)
    except NoGroundedContextError as error:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(error)) from error
    except AiProviderUnavailableError as error:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(error)) from error
