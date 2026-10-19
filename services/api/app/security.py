from typing import Annotated, cast

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .gateway import SupabaseGateway
from .models import AuthenticatedUser

_bearer = HTTPBearer(auto_error=False)


def get_gateway(request: Request) -> SupabaseGateway:
    return cast(SupabaseGateway, request.app.state.gateway)


async def optional_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    gateway: Annotated[SupabaseGateway, Depends(get_gateway)],
) -> AuthenticatedUser | None:
    if credentials is None:
        return None
    payload = await gateway.authenticate(credentials.credentials)
    if not payload or not payload.get("id"):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired access token.",
        )
    app_metadata = payload.get("app_metadata") or {}
    return AuthenticatedUser(
        id=str(payload["id"]),
        email=payload.get("email"),
        role=str(app_metadata.get("role") or "user"),
    )


async def require_user(
    user: Annotated[AuthenticatedUser | None, Depends(optional_user)],
) -> AuthenticatedUser:
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required.",
        )
    return user
