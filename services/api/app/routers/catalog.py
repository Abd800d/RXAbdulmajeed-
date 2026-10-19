from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request, status

from app.catalog import CatalogService
from app.config import Settings
from app.models import CatalogSearchResponse, Language
from app.rate_limit import RateLimit, enforce_rate_limit
from app.security import get_gateway

router = APIRouter(prefix="/catalog", tags=["catalog"])


def get_catalog_service(request: Request) -> CatalogService:
    return CatalogService(get_gateway(request))


@router.get("/search", response_model=CatalogSearchResponse)
async def search_catalog(
    request: Request,
    service: Annotated[CatalogService, Depends(get_catalog_service)],
    q: Annotated[str, Query(min_length=2, max_length=180)],
    lang: Language = "en",
    limit: Annotated[int, Query(ge=1, le=100)] = 30,
) -> CatalogSearchResponse:
    settings: Settings = request.app.state.settings
    await enforce_rate_limit(
        request,
        "catalog-search",
        RateLimit(requests=settings.public_rate_limit_per_minute, window_seconds=60),
    )
    return await service.search(q, lang, min(limit, settings.maximum_search_results))


@router.get("/ingredients/{ingredient_id}", response_model=dict[str, Any])
async def ingredient_detail(
    ingredient_id: str,
    service: Annotated[CatalogService, Depends(get_catalog_service)],
) -> dict[str, Any]:
    ingredient = await service.published_ingredient(ingredient_id)
    if ingredient is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Published ingredient not found.")
    ingredient["products"] = await service.published_products(ingredient_id)
    return ingredient


@router.get("/products/{product_id}", response_model=dict[str, Any])
async def product_detail(
    product_id: str,
    service: Annotated[CatalogService, Depends(get_catalog_service)],
) -> dict[str, Any]:
    product = await service.published_product(product_id)
    if product is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Published product not found.")
    return product
