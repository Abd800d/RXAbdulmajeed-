import re
import uuid
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

import httpx
import structlog
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse
from starlette.middleware.base import RequestResponseEndpoint
from starlette.responses import Response

from app import __version__
from app.config import Settings, get_settings
from app.gateway import GatewayNotConfiguredError, SupabaseGateway, UpstreamServiceError
from app.rate_limit import RateLimiter
from app.routers import assistant, catalog, health, sync

GatewayFactory = Callable[[Settings, httpx.AsyncClient], SupabaseGateway]
_request_id_pattern = re.compile(r"^[A-Za-z0-9._:-]{8,128}$")


def create_app(
    settings: Settings | None = None,
    gateway_factory: GatewayFactory = SupabaseGateway,
) -> FastAPI:
    configuration = settings or get_settings()

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        structlog.configure(
            processors=[
                structlog.processors.TimeStamper(fmt="iso", utc=True),
                structlog.processors.add_log_level,
                structlog.processors.JSONRenderer(),
            ]
        )
        timeout = httpx.Timeout(configuration.request_timeout_seconds)
        application.state.settings = configuration
        application.state.http = httpx.AsyncClient(timeout=timeout, follow_redirects=False)
        application.state.gateway = gateway_factory(configuration, application.state.http)
        application.state.rate_limiter = RateLimiter(configuration.redis_url)
        yield
        await application.state.rate_limiter.close()
        await application.state.http.aclose()

    application = FastAPI(
        title="AbdulmajeedPharma API",
        summary="Secure catalog, synchronization, and grounded-assistant gateway for APH V2.",
        version=__version__,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        lifespan=lifespan,
    )

    if configuration.parsed_trusted_hosts:
        application.add_middleware(
            TrustedHostMiddleware,
            allowed_hosts=configuration.parsed_trusted_hosts,
        )
    application.add_middleware(
        CORSMiddleware,
        allow_origins=configuration.parsed_cors_origins,
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "X-Device-Id", "X-Request-Id"],
        expose_headers=["X-Request-Id"],
        max_age=600,
    )

    @application.middleware("http")
    async def secure_requests(
        request: Request,
        call_next: RequestResponseEndpoint,
    ) -> Response:
        content_length = request.headers.get("content-length")
        if content_length and content_length.isdigit() and int(content_length) > 2 * 1024 * 1024:
            return JSONResponse(
                {"detail": "Request body is too large."},
                status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            )
        supplied_id = request.headers.get("x-request-id", "")
        request_id = supplied_id if _request_id_pattern.match(supplied_id) else str(uuid.uuid4())
        request.state.request_id = request_id
        response = await call_next(request)
        response.headers["X-Request-Id"] = request_id
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        response.headers["Cache-Control"] = "no-store"
        return response

    @application.exception_handler(GatewayNotConfiguredError)
    async def configuration_error(_request: Request, error: GatewayNotConfiguredError) -> JSONResponse:
        return JSONResponse({"detail": str(error)}, status_code=status.HTTP_503_SERVICE_UNAVAILABLE)

    @application.exception_handler(UpstreamServiceError)
    async def upstream_error(_request: Request, error: UpstreamServiceError) -> JSONResponse:
        return JSONResponse({"detail": str(error)}, status_code=status.HTTP_502_BAD_GATEWAY)

    application.include_router(health.router)
    application.include_router(catalog.router, prefix=configuration.api_prefix)
    application.include_router(sync.router, prefix=configuration.api_prefix)
    application.include_router(assistant.router, prefix=configuration.api_prefix)
    return application


app = create_app()
