from collections.abc import Iterator
from typing import Any

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


class FakeGateway:
    def __init__(self, _settings: Settings, _client: httpx.AsyncClient) -> None:
        self.search_rows: list[dict[str, Any]] = []
        self.tables: dict[str, list[dict[str, Any]]] = {}

    async def rpc(self, function: str, payload: dict[str, Any]) -> Any:
        assert function == "search_medicines"
        assert payload["max_results"] <= 100
        return self.search_rows

    async def select(
        self,
        table: str,
        *,
        select: str = "*",
        filters: dict[str, str] | None = None,
        order: str | None = None,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        del select, filters, order
        return self.tables.get(table, [])[:limit]

    async def authenticate(self, access_token: str) -> dict[str, Any] | None:
        if access_token != "valid-token":  # noqa: S105
            return None
        return {"id": "user-1", "email": "user@example.com", "app_metadata": {"role": "user"}}


@pytest.fixture
def client() -> Iterator[TestClient]:
    settings = Settings(
        environment="test",
        trusted_hosts="testserver",
        cors_origins="https://aph.example.com",
        public_rate_limit_per_minute=1000,
        assistant_rate_limit_per_hour=1000,
    )
    app = create_app(settings=settings, gateway_factory=FakeGateway)
    with TestClient(app) as test_client:
        yield test_client
