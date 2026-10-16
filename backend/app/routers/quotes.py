from fastapi import APIRouter, HTTPException, Request

from app.models.schemas import AnalyzeRequest, AnalyzeResponse, QuoteSearchRequest, QuoteSearchResponse
from app.providers.ai_client import AINotConfigured, AIRequestError
from app.providers.search import SearchNotConfigured

router = APIRouter(prefix="/api/quotes", tags=["quotes"])


@router.post("/search", response_model=QuoteSearchResponse)
async def search_quotes(body: QuoteSearchRequest, request: Request) -> QuoteSearchResponse:
    svc = request.app.state.quote_service
    try:
        return await svc.search_quotes(body)
    except SearchNotConfigured as e:
        raise HTTPException(status_code=503, detail=str(e))
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e))


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze_quote(body: AnalyzeRequest, request: Request) -> AnalyzeResponse:
    svc = request.app.state.quote_service
    try:
        return await svc.analyze(body)
    except AINotConfigured as e:
        raise HTTPException(status_code=503, detail=str(e))
    except AIRequestError as e:
        raise HTTPException(status_code=502, detail=str(e))
