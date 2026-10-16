from __future__ import annotations

import asyncio
import hashlib
import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional

import httpx

from app.config import Settings

logger = logging.getLogger(__name__)


@dataclass
class SearchHit:
    title: str
    url: str
    snippet: str
    source: str


class SearchNotConfigured(Exception):
    pass


class SearchProvider(ABC):
    @abstractmethod
    async def search(self, query: str, limit: int = 8) -> list[SearchHit]:
        ...


class NoneSearchProvider(SearchProvider):
    async def search(self, query: str, limit: int = 8) -> list[SearchHit]:
        raise SearchNotConfigured(
            "搜索服务未配置：请在 .env 设置 SEARCH_PROVIDER=ddg（默认，免 Key）"
            "或 serper|brave|tavily 并填写对应 API Key。"
            "禁止将模型记忆当作实时搜索结果。"
        )


class DuckDuckGoProvider(SearchProvider):
    """免费联网检索，无需 API Key。使用 Python 包 ddgs。"""

    def __init__(self, timeout: float):
        self.timeout = timeout

    async def search(self, query: str, limit: int = 8) -> list[SearchHit]:
        return await asyncio.to_thread(self._search_sync, query, limit)

    def _search_sync(self, query: str, limit: int) -> list[SearchHit]:
        try:
            from ddgs import DDGS
        except ImportError as e:
            raise SearchNotConfigured(
                "DuckDuckGo 检索包未安装：请在 backend venv 中执行 pip install ddgs"
            ) from e

        hits: list[SearchHit] = []
        try:
            raw = DDGS().text(query, max_results=limit, region="cn-zh")
        except Exception as e:
            # 部分地区/网络可能限流；抛给上层转成 502
            raise RuntimeError(f"DuckDuckGo 检索失败: {e}") from e

        for item in raw or []:
            url = (item.get("href") or item.get("url") or "").strip()
            title = (item.get("title") or "").strip()
            snippet = (item.get("body") or item.get("snippet") or "").strip()
            if not url and not snippet:
                continue
            hits.append(
                SearchHit(title=title, url=url, snippet=snippet, source="ddg")
            )
            if len(hits) >= limit:
                break
        return hits


class SerperProvider(SearchProvider):
    def __init__(self, api_key: str, timeout: float):
        self.api_key = api_key
        self.timeout = timeout

    async def search(self, query: str, limit: int = 8) -> list[SearchHit]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            resp = await client.post(
                "https://google.serper.dev/search",
                headers={"X-API-KEY": self.api_key, "Content-Type": "application/json"},
                json={"q": query, "num": limit, "gl": "cn", "hl": "zh-cn"},
            )
            resp.raise_for_status()
            data = resp.json()
        hits: list[SearchHit] = []
        for item in data.get("organic", [])[:limit]:
            hits.append(
                SearchHit(
                    title=item.get("title") or "",
                    url=item.get("link") or "",
                    snippet=item.get("snippet") or "",
                    source="serper",
                )
            )
        return hits


class BraveProvider(SearchProvider):
    def __init__(self, api_key: str, timeout: float):
        self.api_key = api_key
        self.timeout = timeout

    async def search(self, query: str, limit: int = 8) -> list[SearchHit]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            resp = await client.get(
                "https://api.search.brave.com/res/v1/web/search",
                headers={"Accept": "application/json", "X-Subscription-Token": self.api_key},
                params={"q": query, "count": limit, "search_lang": "zh"},
            )
            resp.raise_for_status()
            data = resp.json()
        hits: list[SearchHit] = []
        for item in (data.get("web") or {}).get("results", [])[:limit]:
            hits.append(
                SearchHit(
                    title=item.get("title") or "",
                    url=item.get("url") or "",
                    snippet=item.get("description") or "",
                    source="brave",
                )
            )
        return hits


class TavilyProvider(SearchProvider):
    def __init__(self, api_key: str, timeout: float):
        self.api_key = api_key
        self.timeout = timeout

    async def search(self, query: str, limit: int = 8) -> list[SearchHit]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            resp = await client.post(
                "https://api.tavily.com/search",
                json={
                    "api_key": self.api_key,
                    "query": query,
                    "max_results": limit,
                    "include_answer": False,
                },
            )
            resp.raise_for_status()
            data = resp.json()
        hits: list[SearchHit] = []
        for item in data.get("results", [])[:limit]:
            hits.append(
                SearchHit(
                    title=item.get("title") or "",
                    url=item.get("url") or "",
                    snippet=item.get("content") or "",
                    source="tavily",
                )
            )
        return hits


def build_search_provider(settings: Settings) -> SearchProvider:
    name = (settings.search_provider or "ddg").strip().lower()
    if name in ("ddg", "duckduckgo"):
        return DuckDuckGoProvider(settings.search_timeout_seconds)
    if name == "serper" and settings.serper_api_key:
        return SerperProvider(settings.serper_api_key, settings.search_timeout_seconds)
    if name == "brave" and settings.brave_api_key:
        return BraveProvider(settings.brave_api_key, settings.search_timeout_seconds)
    if name == "tavily" and settings.tavily_api_key:
        return TavilyProvider(settings.tavily_api_key, settings.search_timeout_seconds)
    if name == "none":
        return NoneSearchProvider()
    # 未知或付费 provider 缺 Key：回退到免 Key 的 DDG，避免误伤默认体验
    if name in ("serper", "brave", "tavily"):
        logger.warning("SEARCH_PROVIDER=%s 但未配置对应 Key，回退到 ddg", name)
        return DuckDuckGoProvider(settings.search_timeout_seconds)
    return DuckDuckGoProvider(settings.search_timeout_seconds)


def hit_id(hit: SearchHit) -> str:
    raw = f"{hit.url}|{hit.snippet[:80]}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


def is_search_configured(settings: Settings) -> bool:
    name = (settings.search_provider or "ddg").strip().lower()
    if name in ("ddg", "duckduckgo"):
        return True
    if name == "serper":
        return bool(settings.serper_api_key)
    if name == "brave":
        return bool(settings.brave_api_key)
    if name == "tavily":
        return bool(settings.tavily_api_key)
    if name == "none":
        return False
    # 未知值按 ddg 处理（与 build 一致）
    return True
