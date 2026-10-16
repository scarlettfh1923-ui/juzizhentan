from __future__ import annotations

import hashlib
import json
import time
from pathlib import Path
from typing import Any, Optional


class SimpleCache:
    """Memory + optional disk cache for recent search/AI results. Not a question bank."""

    def __init__(self, ttl_seconds: int = 3600, disk_dir: Optional[Path] = None):
        self.ttl = ttl_seconds
        self._mem: dict[str, tuple[float, Any]] = {}
        self.disk_dir = disk_dir
        if disk_dir:
            disk_dir.mkdir(parents=True, exist_ok=True)

    def _key(self, namespace: str, payload: str) -> str:
        h = hashlib.sha256(f"{namespace}:{payload}".encode()).hexdigest()
        return h

    def get(self, namespace: str, payload: str) -> Optional[Any]:
        k = self._key(namespace, payload)
        now = time.time()
        if k in self._mem:
            exp, val = self._mem[k]
            if now < exp:
                return val
            del self._mem[k]
        if self.disk_dir:
            path = self.disk_dir / f"{k}.json"
            if path.exists():
                try:
                    data = json.loads(path.read_text(encoding="utf-8"))
                    if data.get("exp", 0) > now:
                        self._mem[k] = (data["exp"], data["val"])
                        return data["val"]
                except Exception:
                    pass
        return None

    def set(self, namespace: str, payload: str, value: Any) -> None:
        k = self._key(namespace, payload)
        exp = time.time() + self.ttl
        self._mem[k] = (exp, value)
        if self.disk_dir:
            path = self.disk_dir / f"{k}.json"
            try:
                path.write_text(
                    json.dumps({"exp": exp, "val": value}, ensure_ascii=False),
                    encoding="utf-8",
                )
            except Exception:
                pass
