from __future__ import annotations

import threading
from typing import Any, Dict, Optional


class ModelUsageTracker:
    """Process-local model usage aggregator for one extraction run."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.reset()

    def reset(self) -> None:
        with self._lock:
            self._provider: Optional[str] = None
            self._model: Optional[str] = None
            self._input_tokens = 0
            self._output_tokens = 0
            self._total_tokens = 0
            self._calls = 0
            self._estimated_calls = 0
            self._raw_events: list[Dict[str, Any]] = []

    def record(
        self,
        *,
        provider: Optional[str],
        model: Optional[str],
        prompt_tokens: Optional[int],
        completion_tokens: Optional[int],
        total_tokens: Optional[int],
        estimated: bool = False,
        raw_usage: Optional[Dict[str, Any]] = None,
    ) -> None:
        in_tok = int(prompt_tokens or 0)
        out_tok = int(completion_tokens or 0)
        tot_tok = int(total_tokens or 0)
        if tot_tok <= 0:
            tot_tok = in_tok + out_tok

        with self._lock:
            if provider and not self._provider:
                self._provider = str(provider)
            if model and not self._model:
                self._model = str(model)

            self._input_tokens += max(0, in_tok)
            self._output_tokens += max(0, out_tok)
            self._total_tokens += max(0, tot_tok)
            self._calls += 1
            if estimated:
                self._estimated_calls += 1
            if raw_usage:
                self._raw_events.append(raw_usage)

    def snapshot(self) -> Dict[str, Any]:
        with self._lock:
            return {
                "provider": self._provider,
                "model": self._model,
                "usage": {
                    "prompt_tokens": self._input_tokens,
                    "completion_tokens": self._output_tokens,
                    "total_tokens": self._total_tokens,
                },
                "input_tokens": self._input_tokens,
                "output_tokens": self._output_tokens,
                "total_tokens": self._total_tokens,
                "call_count": self._calls,
                "estimated_call_count": self._estimated_calls,
                "all_estimated": self._calls > 0 and self._calls == self._estimated_calls,
                "raw_usage_events": [self._to_jsonable(item) for item in self._raw_events[-20:]],
            }

    def _to_jsonable(self, value: Any) -> Any:
        if value is None or isinstance(value, (str, int, float, bool)):
            return value
        if isinstance(value, dict):
            return {str(k): self._to_jsonable(v) for k, v in value.items()}
        if isinstance(value, (list, tuple, set)):
            return [self._to_jsonable(v) for v in value]

        # Some SDKs return rich typed objects such as PromptTokensDetails.
        if hasattr(value, "model_dump"):
            try:
                return self._to_jsonable(value.model_dump())
            except Exception:
                pass
        if hasattr(value, "dict"):
            try:
                return self._to_jsonable(value.dict())
            except Exception:
                pass
        if hasattr(value, "__dict__"):
            try:
                return self._to_jsonable(vars(value))
            except Exception:
                pass
        return str(value)


usage_tracker = ModelUsageTracker()
