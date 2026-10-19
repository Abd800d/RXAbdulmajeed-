from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request

from app.gateway import SupabaseGateway
from app.models import SyncChange, SyncChangesResponse
from app.rate_limit import RateLimit, enforce_rate_limit
from app.security import get_gateway

router = APIRouter(prefix="/sync", tags=["sync"])


@router.get("/changes", response_model=SyncChangesResponse)
async def catalog_changes(
    request: Request,
    gateway: Annotated[SupabaseGateway, Depends(get_gateway)],
    after: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=1000)] = 500,
) -> SyncChangesResponse:
    await enforce_rate_limit(request, "catalog-sync", RateLimit(requests=120, window_seconds=60))
    rows = await gateway.select(
        "catalog_change_feed",
        select="sequence,entity_type,entity_id,operation,entity_version,changed_at",
        filters={"sequence": f"gt.{after}", "is_public": "eq.true"},
        order="sequence.asc",
        limit=limit + 1,
    )
    has_more = len(rows) > limit
    changes = [SyncChange.model_validate(row) for row in rows[:limit]]
    next_sequence = changes[-1].sequence if changes else after
    return SyncChangesResponse(
        after_sequence=after,
        next_sequence=next_sequence,
        has_more=has_more,
        changes=changes,
    )
