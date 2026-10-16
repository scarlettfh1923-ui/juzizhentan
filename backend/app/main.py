from __future__ import annotations

import logging
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.providers.ai_client import AIClient
from app.providers.search import build_search_provider
from app.routers import health, quotes, training
from app.services.cache import SimpleCache
from app.services.quotes import QuoteService
from app.services.training import TrainingService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("juzizhentan")


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="句子侦探局 API", version="0.1.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    cache_dir = Path(__file__).resolve().parent.parent / "cache"
    cache = SimpleCache(ttl_seconds=settings.cache_ttl_seconds, disk_dir=cache_dir)
    ai = AIClient(settings)
    search = build_search_provider(settings)

    app.state.settings = settings
    app.state.ai = ai
    app.state.cache = cache
    app.state.quote_service = QuoteService(ai, search, cache)
    app.state.training_service = TrainingService(ai, cache)

    app.include_router(health.router)
    app.include_router(quotes.router)
    app.include_router(training.router)

    @app.get("/")
    async def root():
        return {"app": "句子侦探局", "docs": "/docs", "health": "/api/health"}

    return app


app = create_app()
