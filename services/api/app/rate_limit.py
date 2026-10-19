import asyncio
import hashlib
import time
from dataclasses import dataclass

import structlog
from fastapi import HTTPException, Request, status
from redis.asyncio import Redis

logger = structlog.get_logger(__name__)


@dataclass(frozen=True)
class RateLimit:
    requests: int
    window_seconds: int


class RateLimiter:
    def __init__(self, redis_url: str = "") -> None:
        self._redis = Redis.from_url(redis_url, decode_responses=True) if redis_url else None
        self._memory: dict[str, tuple[int, int]] = {}
        self._lock = asyncio.Lock()

    async def close(self) -> None:
        if self._redis is not None:
            await self._redis.aclose()

    async def _consume_memory(self, key: str, policy: RateLimit) -> bool:
        bucket = int(time.time() // policy.window_seconds)
        async with self._lock:
            old_bucket, count = self._memory.get(key, (bucket, 0))
            if old_bucket != bucket:
                count = 0
            count += 1
            self._memory[key] = (bucket, count)
            if len(self._memory) > 20_000:
                self._memory = {
                    item_key: value for item_key, value in self._memory.items() if value[0] >= bucket - 1
                }
            return count <= policy.requests

    async def consume(self, identifier: str, endpoint: str, policy: RateLimit) -> bool:
        bucket = int(time.time() // policy.window_seconds)
        digest = hashlib.sha256(identifier.encode()).hexdigest()
        key = f"aph:rate:{endpoint}:{digest}:{bucket}"
        if self._redis is not None:
            try:
                count = await self._redis.incr(key)
                if count == 1:
                    await self._redis.expire(key, policy.window_seconds + 5)
                return int(count) <= policy.requests
            except Exception as error:
                # Redis availability must not disable all read-only pharmacology
                # access. The process-local limit remains active while operators
                # are alerted to restore the shared limiter.
                logger.warning("rate_limit_redis_fallback", error_type=type(error).__name__)
        return await self._consume_memory(key, policy)


async def enforce_rate_limit(
    request: Request,
    endpoint: str,
    policy: RateLimit,
) -> None:
    forwarded = request.headers.get("x-forwarded-for", "").split(",")[0].strip()
    network = forwarded or (request.client.host if request.client else "unknown-network")
    device = request.headers.get("x-device-id", "unknown-device")[:200]
    limiter: RateLimiter = request.app.state.rate_limiter
    device_ok, network_ok = await asyncio.gather(
        limiter.consume(f"device:{device}", f"{endpoint}:device", policy),
        limiter.consume(
            f"network:{network}",
            f"{endpoint}:network",
            RateLimit(requests=policy.requests * 8, window_seconds=policy.window_seconds),
        ),
    )
    if not device_ok or not network_ok:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Request limit reached. Please try again later.",
            headers={"Retry-After": str(policy.window_seconds)},
        )
