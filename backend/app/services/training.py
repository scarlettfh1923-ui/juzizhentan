from __future__ import annotations

import hashlib
import logging
import uuid
from typing import Any

from pydantic import ValidationError

from app.models.schemas import (
    CritiqueRequest,
    CritiqueResponse,
    TrainingGenerateRequest,
    TrainingLevel,
)
from app.providers.ai_client import AIClient, AINotConfigured, AIRequestError
from app.services.cache import SimpleCache

logger = logging.getLogger(__name__)

GENERATE_SYSTEM = """你是文学表达训练设计师。只输出合法 JSON，不要 markdown、不要题库式固定模板。
每次必须生成新颖情境与素材。用户通过点击组合，不手动写作。
槽位数量 2–6，由主题决定；每槽 3–5 个候选项；候选项要有真实质量差异（有的具体但关联弱、有的情绪直白、有的比喻不匹配等），但不要荒谬。
字段：
{
  "theme": "...",
  "training_goal": "...",
  "difficulty": "easy|medium|hard",
  "scenario": "...",
  "slots": [
    {
      "id": "slot_a",
      "label": "...",
      "role": "...",
      "options": [
        {"id": "a1", "text": "...", "function": "...", "suitability": "...", "potential_issue": "..."}
      ]
    }
  ],
  "reference_combination": {"slot_a": "a1", "...": "..."},
  "reference_rationale": "...",
  "common_pitfalls": ["..."],
  "next_difficulty_hint": "...",
  "summary_fingerprint": "一句话摘要用于去重"
}
id 用简短英文。text 用中文，适合拼成连贯表达。
"""

CRITIQUE_SYSTEM = """你是文学写作教练。只输出合法 JSON。
根据用户实际选择的素材组合进行讲评，不要空泛夸奖。
{
  "combined_text": "把选项按槽位顺序自然拼成段落",
  "overall_effect": "...",
  "slot_comments": [{"slot_id":"...","option_id":"...","comment":"..."}],
  "strengths": ["..."],
  "problems": ["..."],
  "swap_suggestions": ["..."],
  "optimized_combination": "优化后的完整段落",
  "mechanism_contrast": "若有种子金句，对照其可迁移机制；否则可为 null"
}
"""


class TrainingService:
    def __init__(self, ai: AIClient, cache: SimpleCache):
        self.ai = ai
        self.cache = cache

    async def generate(self, req: TrainingGenerateRequest) -> TrainingLevel:
        if not self.ai.configured:
            raise AINotConfigured(
                "AI 未配置：请在后端 .env 中设置 AI_API_KEY 后再生成训练关卡。"
            )

        avoid = "；".join(req.avoid_summaries[:10])
        user = (
            f"技巧: {req.technique or '随机合适技巧'}\n"
            f"主题偏好: {req.theme or '不限'}\n"
            f"难度: {req.difficulty or 'medium'}\n"
            f"种子金句(可选，用于迁移机制而非照抄): {req.seed_excerpt or '无'}\n"
            f"请避免与这些摘要过于相似: {avoid or '无'}\n"
            "请生成一套全新训练关卡 JSON。"
        )

        last_err: Exception | None = None
        for attempt in range(3):
            try:
                data = await self.ai.chat_json(GENERATE_SYSTEM, user, temperature=0.85)
                data = _normalize_level(data)
                level = TrainingLevel.model_validate(data)
                if not level.summary_fingerprint:
                    level.summary_fingerprint = level.theme[:40]
                return level
            except (ValidationError, AIRequestError, KeyError, TypeError, ValueError) as e:
                last_err = e
                logger.warning("generate validate fail attempt %s: %s", attempt + 1, e)
                user = user + f"\n上次输出校验失败: {e}。请严格按 schema 重输出。"
                continue
        raise AIRequestError(f"关卡 JSON 校验失败: {last_err}")

    async def critique(self, req: CritiqueRequest) -> CritiqueResponse:
        if not self.ai.configured:
            raise AINotConfigured(
                "AI 未配置：请在后端 .env 中设置 AI_API_KEY 后再使用讲评。"
            )

        # Resolve selection texts locally for combined_text fallback
        selected_texts: dict[str, str] = {}
        for slot in req.level.slots:
            oid = req.selections.get(slot.id)
            if not oid:
                continue
            for opt in slot.options:
                if opt.id == oid:
                    selected_texts[slot.id] = opt.text
                    break

        payload = {
            "level": req.level.model_dump(),
            "selections": req.selections,
            "selected_texts": selected_texts,
            "seed_excerpt": req.seed_excerpt,
        }
        data = await self.ai.chat_json(CRITIQUE_SYSTEM, str(payload), temperature=0.5)
        combined = data.get("combined_text") or "。".join(selected_texts.values())
        return CritiqueResponse(
            combined_text=str(combined),
            overall_effect=str(data.get("overall_effect") or ""),
            slot_comments=list(data.get("slot_comments") or []),
            strengths=[str(x) for x in (data.get("strengths") or [])],
            problems=[str(x) for x in (data.get("problems") or [])],
            swap_suggestions=[str(x) for x in (data.get("swap_suggestions") or [])],
            optimized_combination=str(data.get("optimized_combination") or ""),
            mechanism_contrast=data.get("mechanism_contrast"),
        )


def _normalize_level(data: dict[str, Any]) -> dict[str, Any]:
    """Fill missing ids so pydantic can validate when model forgets them."""
    if not isinstance(data, dict):
        raise ValueError("root must be object")
    slots = data.get("slots") or []
    for i, slot in enumerate(slots):
        if not slot.get("id"):
            slot["id"] = f"slot_{i+1}"
        for j, opt in enumerate(slot.get("options") or []):
            if not opt.get("id"):
                opt["id"] = f"{slot['id']}_opt_{j+1}"
            for k in ("function", "suitability", "potential_issue"):
                opt.setdefault(k, "")
        slot.setdefault("role", "")
        slot.setdefault("label", f"槽位{i+1}")
    data.setdefault("theme", "未命名主题")
    data.setdefault("training_goal", "表达训练")
    data.setdefault("difficulty", "medium")
    data.setdefault("scenario", "")
    data.setdefault("reference_combination", {})
    data.setdefault("reference_rationale", "")
    data.setdefault("common_pitfalls", [])
    data.setdefault("next_difficulty_hint", "")
    if not data.get("summary_fingerprint"):
        raw = f"{data.get('theme')}|{data.get('scenario')}"
        data["summary_fingerprint"] = hashlib.sha256(raw.encode()).hexdigest()[:12]
    return data
