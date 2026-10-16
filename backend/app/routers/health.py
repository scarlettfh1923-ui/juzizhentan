from fastapi import APIRouter, Request

from app.models.schemas import HealthResponse
from app.providers.search import is_search_configured

router = APIRouter(tags=["health"])


@router.get("/api/health", response_model=HealthResponse)
async def health(request: Request) -> HealthResponse:
    settings = request.app.state.settings
    ai = request.app.state.ai
    search_name = (settings.search_provider or "ddg").lower()
    return HealthResponse(
        status="ok",
        ai_configured=ai.configured,
        search_configured=is_search_configured(settings),
        search_provider=search_name,
        model=settings.ai_model if ai.configured else "",
    )
