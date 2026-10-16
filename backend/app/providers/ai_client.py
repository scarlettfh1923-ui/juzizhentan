from __future__ import annotations

import json
import logging
import re
from typing import Any, Optional

import httpx

from app.config import Settings

logger = logging.getLogger(__name__)


class AINotConfigured(Exception):
    pass


class AIRequestError(Exception):
    def __init__(self, message: str, status_code: Optional[int] = None):
        super().__init__(message)
        self.status_code = status_code


def _extract_json(text: str) -> Any:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        m = re.search(r"\{[\s\S]*\}", text)
        if m:
            return json.loads(m.group(0))
        raise


class AIClient:
    def __init__(self, settings: Settings):
        self.settings = settings

    @property
    def configured(self) -> bool:
        return bool(self.settings.ai_api_key and self.settings.ai_api_key.strip())

    async def chat_json(
        self,
        system: str,
        user: str,
        *,
        temperature: float = 0.7,
    ) -> Any:
        if not self.configured:
            raise AINotConfigured(
                "AI 未配置：请在后端 .env 中设置 AI_API_KEY（及可选的 AI_BASE_URL / AI_MODEL）"
            )

        user = user[: self.settings.ai_max_input_chars]
        url = self.settings.ai_base_url.rstrip("/") + "/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.settings.ai_api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": self.settings.ai_model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": temperature,
            "max_tokens": self.settings.ai_max_output_tokens,
            "response_format": {"type": "json_object"},
        }

        last_err: Optional[Exception] = None
        for attempt in range(self.settings.ai_max_retries + 1):
            try:
                async with httpx.AsyncClient(timeout=self.settings.ai_timeout_seconds) as client:
                    resp = await client.post(url, headers=headers, json=payload)
                if resp.status_code >= 400:
                    raise AIRequestError(
                        f"AI 接口错误 HTTP {resp.status_code}: {resp.text[:300]}",
                        status_code=resp.status_code,
                    )
                data = resp.json()
                content = data["choices"][0]["message"]["content"]
                return _extract_json(content)
            except (httpx.HTTPError, KeyError, json.JSONDecodeError, AIRequestError) as e:
                last_err = e
                logger.warning("AI attempt %s failed: %s", attempt + 1, e)
                continue
        raise AIRequestError(f"AI 调用失败: {last_err}")
