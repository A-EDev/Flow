from __future__ import annotations

import math
import time
from typing import Callable

from requests.adapters import HTTPAdapter


class PacedHTTPAdapter(HTTPAdapter):
    """Sequential collection transport; redirects share the same request budget."""

    def __init__(
        self,
        delay_seconds: float = 1.0,
        *,
        clock: Callable[[], float] = time.monotonic,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        if not math.isfinite(delay_seconds) or delay_seconds < 0:
            raise ValueError("request delay must be finite and nonnegative")
        super().__init__(max_retries=0)
        self._delay = delay_seconds
        self._clock = clock
        self._sleep = sleep
        self._next_request_at = 0.0

    def send(self, request, **kwargs):
        remaining = self._next_request_at - self._clock()
        if remaining > 0:
            self._sleep(remaining)
        if kwargs.get("timeout") is None:
            kwargs["timeout"] = (10, 30)
        try:
            return super().send(request, **kwargs)
        finally:
            self._next_request_at = self._clock() + self._delay
