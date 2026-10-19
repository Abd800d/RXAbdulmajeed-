from typing import Any

import httpx

from .config import Settings


class GatewayNotConfiguredError(RuntimeError):
    pass


class UpstreamServiceError(RuntimeError):
    pass


class SupabaseGateway:
    """Narrow server-side adapter around Supabase REST/Auth APIs.

    Service-role credentials never leave this process. Public endpoints still
    add explicit `is_published`/`is_public` filters rather than relying on the
    service role's RLS bypass.
    """

    def __init__(self, settings: Settings, client: httpx.AsyncClient) -> None:
        self.settings = settings
        self.client = client

    def _require_configuration(self) -> tuple[str, str]:
        if not self.settings.supabase_configured:
            raise GatewayNotConfiguredError("The APH cloud database is not configured.")
        return (
            self.settings.supabase_url.rstrip("/"),
            self.settings.supabase_service_role_key.get_secret_value(),
        )

    def _headers(self, key: str, bearer: str | None = None) -> dict[str, str]:
        return {
            "apikey": key,
            "Authorization": f"Bearer {bearer or key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    async def rpc(self, function: str, payload: dict[str, Any]) -> Any:
        base_url, key = self._require_configuration()
        response = await self.client.post(
            f"{base_url}/rest/v1/rpc/{function}",
            headers=self._headers(key),
            json=payload,
        )
        if response.is_error:
            raise UpstreamServiceError(f"Supabase RPC {function} failed with status {response.status_code}.")
        return response.json()

    async def select(
        self,
        table: str,
        *,
        select: str = "*",
        filters: dict[str, str] | None = None,
        order: str | None = None,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        base_url, key = self._require_configuration()
        params: dict[str, str | int] = {"select": select}
        params.update(filters or {})
        if order:
            params["order"] = order
        if limit is not None:
            params["limit"] = limit
        response = await self.client.get(
            f"{base_url}/rest/v1/{table}",
            headers=self._headers(key),
            params=params,
        )
        if response.is_error:
            raise UpstreamServiceError(f"Supabase table {table} failed with status {response.status_code}.")
        payload = response.json()
        return payload if isinstance(payload, list) else []

    async def authenticate(self, access_token: str) -> dict[str, Any] | None:
        if not self.settings.supabase_url:
            raise GatewayNotConfiguredError("Supabase Auth is not configured.")
        anon_key = self.settings.supabase_anon_key.get_secret_value()
        if not anon_key:
            raise GatewayNotConfiguredError("Supabase anon key is not configured on the API.")
        response = await self.client.get(
            f"{self.settings.supabase_url.rstrip('/')}/auth/v1/user",
            headers=self._headers(anon_key, bearer=access_token),
        )
        if response.status_code in (401, 403):
            return None
        if response.is_error:
            raise UpstreamServiceError("Supabase Auth is temporarily unavailable.")
        payload = response.json()
        return payload if isinstance(payload, dict) else None
