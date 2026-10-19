from typing import Any

from .gateway import SupabaseGateway
from .models import CatalogSearchItem, CatalogSearchResponse, Language, SourceReference


class CatalogService:
    def __init__(self, gateway: SupabaseGateway) -> None:
        self.gateway = gateway

    async def search(self, query: str, language: Language, limit: int) -> CatalogSearchResponse:
        rows = await self.gateway.rpc(
            "search_medicines",
            {"search_query": query.strip(), "max_results": limit},
        )
        results: list[CatalogSearchItem] = []
        for row in rows if isinstance(rows, list) else []:
            if not isinstance(row, dict) or not isinstance(row.get("drug"), dict):
                continue
            raw_score = float(row.get("score") or 0)
            ingredient = row["drug"]
            product = row.get("matched_product")
            results.append(
                CatalogSearchItem(
                    ingredient=ingredient,
                    product=product if isinstance(product, dict) else None,
                    score=max(0.0, min(1.0, raw_score)),
                    matched_on=str(row.get("matched_on") or "catalog"),
                    source_status=str(ingredient.get("verification_status") or "unverified"),
                )
            )
        return CatalogSearchResponse(
            query=query.strip(),
            language=language,
            results=results,
            total=len(results),
            engine="postgresql-trigram-fulltext",
        )

    async def published_ingredient(self, ingredient_id: str) -> dict[str, Any] | None:
        rows = await self.gateway.select(
            "drugs",
            filters={"id": f"eq.{ingredient_id}", "is_published": "eq.true"},
            limit=1,
        )
        return rows[0] if rows else None

    async def published_products(self, ingredient_id: str, limit: int = 500) -> list[dict[str, Any]]:
        return await self.gateway.select(
            "products",
            filters={"drug_id": f"eq.{ingredient_id}", "is_published": "eq.true"},
            order="brand_name.asc",
            limit=limit,
        )

    async def published_product(self, product_id: str) -> dict[str, Any] | None:
        rows = await self.gateway.select(
            "products",
            filters={"id": f"eq.{product_id}", "is_published": "eq.true"},
            limit=1,
        )
        return rows[0] if rows else None


def source_references(ingredient: dict[str, Any]) -> list[SourceReference]:
    sources: list[SourceReference] = []
    for source in ingredient.get("source_refs") or []:
        if not isinstance(source, dict) or not source.get("url"):
            continue
        sources.append(
            SourceReference(
                label=str(source.get("label") or source.get("authority") or "Source"),
                url=str(source["url"]),
                authority=str(source.get("authority") or "Unknown"),
                published_on=source.get("published_on"),
                retrieved_at=source.get("retrieved_at"),
            )
        )
    return sources[:12]
