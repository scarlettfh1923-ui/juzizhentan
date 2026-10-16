from __future__ import annotations

from enum import Enum
from typing import Any, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class VerificationStatus(str, Enum):
    VERIFIED = "VERIFIED"
    PARTIAL = "PARTIAL"
    UNVERIFIED = "UNVERIFIED"
    AI_ORIGINAL = "AI_ORIGINAL"


class HealthResponse(BaseModel):
    status: str = "ok"
    ai_configured: bool
    search_configured: bool
    search_provider: str
    model: str


class QuoteSearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=200)
    technique: Optional[str] = None
    theme: Optional[str] = None
    limit: int = Field(default=8, ge=1, le=15)


class QuoteCard(BaseModel):
    id: str
    excerpt: str = Field(..., max_length=500)
    work_title: Optional[str] = None
    author: Optional[str] = None
    technique_tags: list[str] = Field(default_factory=list)
    recommend_reason: str = ""
    verification: VerificationStatus = VerificationStatus.UNVERIFIED
    source_url: Optional[str] = None
    source_title: Optional[str] = None
    note: Optional[str] = None


class QuoteSearchResponse(BaseModel):
    query: str
    results: list[QuoteCard]
    message: Optional[str] = None


class AnalyzeRequest(BaseModel):
    excerpt: str = Field(..., min_length=1, max_length=800)
    work_title: Optional[str] = None
    author: Optional[str] = None
    source_url: Optional[str] = None
    verification: VerificationStatus = VerificationStatus.UNVERIFIED


class AnalysisDimension(BaseModel):
    key: str
    title: str
    content: str
    evidence: Optional[str] = None
    certainty: str = "inferred"  # explicit | inferred | subjective


class AnalyzeResponse(BaseModel):
    excerpt: str
    dimensions: list[AnalysisDimension]
    transferable_mechanism: str = ""
    simplified_version: Optional[str] = None
    simplified_diff: Optional[str] = None


class TrainingGenerateRequest(BaseModel):
    technique: Optional[str] = None
    theme: Optional[str] = None
    difficulty: Optional[str] = Field(default="medium")  # easy|medium|hard
    seed_excerpt: Optional[str] = None
    avoid_summaries: list[str] = Field(default_factory=list)


class CandidateOption(BaseModel):
    id: str
    text: str = Field(..., min_length=2, max_length=300)
    function: str = ""
    suitability: str = ""
    potential_issue: str = ""


class MaterialSlot(BaseModel):
    id: str
    label: str
    role: str = ""
    options: list[CandidateOption]

    @field_validator("options")
    @classmethod
    def check_options_count(cls, v: list[CandidateOption]) -> list[CandidateOption]:
        if not (3 <= len(v) <= 5):
            raise ValueError(f"each slot must have 3-5 options, got {len(v)}")
        return v


class TrainingLevel(BaseModel):
    theme: str
    training_goal: str
    difficulty: str
    scenario: str
    slots: list[MaterialSlot]
    reference_combination: dict[str, str] = Field(default_factory=dict)
    reference_rationale: str = ""
    common_pitfalls: list[str] = Field(default_factory=list)
    next_difficulty_hint: str = ""
    summary_fingerprint: str = ""

    @field_validator("slots")
    @classmethod
    def check_slots(cls, v: list[MaterialSlot]) -> list[MaterialSlot]:
        if not (2 <= len(v) <= 6):
            raise ValueError(f"slots count must be 2-6, got {len(v)}")
        return v

    @model_validator(mode="after")
    def ensure_ids(self) -> "TrainingLevel":
        for slot in self.slots:
            if not slot.id:
                raise ValueError("slot id required")
            for opt in slot.options:
                if not opt.id:
                    raise ValueError("option id required")
        return self


class CritiqueRequest(BaseModel):
    level: TrainingLevel
    selections: dict[str, str]  # slot_id -> option_id
    seed_excerpt: Optional[str] = None


class CritiqueResponse(BaseModel):
    combined_text: str
    overall_effect: str
    slot_comments: list[dict[str, Any]] = Field(default_factory=list)
    strengths: list[str] = Field(default_factory=list)
    problems: list[str] = Field(default_factory=list)
    swap_suggestions: list[str] = Field(default_factory=list)
    optimized_combination: str = ""
    mechanism_contrast: Optional[str] = None


class ErrorBody(BaseModel):
    detail: str
    code: str = "error"
