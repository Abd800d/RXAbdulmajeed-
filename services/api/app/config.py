from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Server-only configuration.

    No value in this object is serialized to a mobile or web client. Public
    Supabase values can be supplied separately to those clients.
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="APH_",
        case_sensitive=False,
        extra="ignore",
    )

    environment: str = "development"
    app_name: str = "APH API"
    api_prefix: str = "/v1"
    cors_origins: str = "http://localhost:4173,http://localhost:3000"
    trusted_hosts: str = "localhost,127.0.0.1,testserver"

    supabase_url: str = ""
    supabase_anon_key: SecretStr = SecretStr("")
    supabase_service_role_key: SecretStr = SecretStr("")

    redis_url: str = ""
    meilisearch_url: str = ""
    meilisearch_api_key: SecretStr = SecretStr("")

    openai_api_key: SecretStr = SecretStr("")
    openai_model: str = "gpt-4.1-mini"
    gemini_api_key: SecretStr = SecretStr("")
    gemini_model: str = "gemini-2.5-flash"

    request_timeout_seconds: float = 20.0
    public_rate_limit_per_minute: int = 60
    assistant_rate_limit_per_hour: int = 25
    maximum_search_results: int = 100

    @property
    def parsed_cors_origins(self) -> list[str]:
        return [value.strip() for value in self.cors_origins.split(",") if value.strip()]

    @property
    def parsed_trusted_hosts(self) -> list[str]:
        return [value.strip() for value in self.trusted_hosts.split(",") if value.strip()]

    @property
    def supabase_configured(self) -> bool:
        return bool(
            self.supabase_url.startswith("https://")
            and self.supabase_service_role_key.get_secret_value()
        )

    @property
    def ai_provider(self) -> str | None:
        if self.openai_api_key.get_secret_value():
            return "openai"
        if self.gemini_api_key.get_secret_value():
            return "gemini"
        return None


@lru_cache
def get_settings() -> Settings:
    return Settings()
