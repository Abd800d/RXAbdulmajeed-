from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

Language = Literal["ar", "en"]


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="ignore")


class HealthResponse(ApiModel):
    status: Literal["ok", "degraded"]
    service: str
    version: str
    environment: str
    supabase: Literal["configured", "unconfigured"]
    search_engine: Literal["postgresql", "meilisearch"]
    ai_provider: str | None = None
    timestamp: datetime


class SourceReference(ApiModel):
    label: str
    url: str
    authority: str
    published_on: str | None = None
    retrieved_at: str | None = None


class CatalogSearchItem(ApiModel):
    ingredient: dict[str, Any]
    product: dict[str, Any] | None = None
    score: float = Field(ge=0, le=1)
    matched_on: str
    source_status: str = "catalog"


class CatalogSearchResponse(ApiModel):
    query: str
    language: Language
    results: list[CatalogSearchItem]
    total: int
    engine: str


class AssistantRequest(ApiModel):
    question: str = Field(min_length=2, max_length=2500)
    language: Language = "en"
    related_ingredient_id: str | None = Field(default=None, max_length=180)

    @field_validator("question")
    @classmethod
    def clean_question(cls, value: str) -> str:
        return value.replace("\x00", "").strip()


class AssistantResponse(ApiModel):
    answer: str
    language: Language
    grounded: bool
    confidence: Literal["low", "moderate", "high"]
    related_ingredient_id: str | None = None
    sources: list[SourceReference] = Field(default_factory=list)
    disclaimer: str


class SyncChange(ApiModel):
    sequence: int
    entity_type: Literal["drug", "product"]
    entity_id: str
    operation: Literal["UPSERT", "DELETE"]
    entity_version: int | None = None
    changed_at: datetime


class SyncChangesResponse(ApiModel):
    after_sequence: int
    next_sequence: int
    has_more: bool
    changes: list[SyncChange]


class AuthenticatedUser(ApiModel):
    id: str
    email: str | None = None
    role: str = "user"
