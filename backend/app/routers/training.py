from fastapi import APIRouter, HTTPException, Request

from app.models.schemas import CritiqueRequest, CritiqueResponse, TrainingGenerateRequest, TrainingLevel
from app.providers.ai_client import AINotConfigured, AIRequestError

router = APIRouter(prefix="/api/training", tags=["training"])


@router.post("/generate", response_model=TrainingLevel)
async def generate_level(body: TrainingGenerateRequest, request: Request) -> TrainingLevel:
    svc = request.app.state.training_service
    try:
        return await svc.generate(body)
    except AINotConfigured as e:
        raise HTTPException(status_code=503, detail=str(e))
    except AIRequestError as e:
        raise HTTPException(status_code=502, detail=str(e))


@router.post("/critique", response_model=CritiqueResponse)
async def critique_level(body: CritiqueRequest, request: Request) -> CritiqueResponse:
    svc = request.app.state.training_service
    try:
        return await svc.critique(body)
    except AINotConfigured as e:
        raise HTTPException(status_code=503, detail=str(e))
    except AIRequestError as e:
        raise HTTPException(status_code=502, detail=str(e))
