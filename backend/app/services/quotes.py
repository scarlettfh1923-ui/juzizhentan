from __future__ import annotations

import logging
import re
from typing import Optional
from urllib.parse import urlparse

from app.models.schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    AnalysisDimension,
    QuoteCard,
    QuoteSearchRequest,
    QuoteSearchResponse,
    VerificationStatus,
)
from app.providers.ai_client import AIClient, AINotConfigured, AIRequestError
from app.providers.search import SearchHit, SearchNotConfigured, SearchProvider, hit_id
from app.services.cache import SimpleCache

logger = logging.getLogger(__name__)

ANALYZE_SYSTEM = """你是文学文本分析师。只输出合法 JSON，不要 markdown。
分析必须基于用户给出的原文，不要编造出处、作者、书名。
每个维度仅在确实适用时出现。certainty 取 explicit|inferred|subjective。
JSON 结构：
{
  "dimensions": [{"key":"...","title":"...","content":"...","evidence":"...","certainty":"inferred"}],
  "transferable_mechanism": "...",
  "simplified_version": "...",
  "simplified_diff": "..."
}
可用 key 示例：detail, perspective, scale, imagery, rhythm, contrast, emotion, meaning, transferable
"""

QUERY_GEN_SYSTEM = """你是文学检索助手。根据用户意图生成适合在中文互联网搜索文学金句/名著评论的检索词。
只输出合法 JSON：{"queries":["...","..."]}
要求：
- 2 到 4 条检索词，中文为主
- 偏向名著、作品名、作者、金句、精彩段落、影评/书评中的优秀表达
- 可含技巧词（如对照、意象），但不要空泛
- 不要输出解释文字
"""

EXTRACT_SYSTEM = """你是文学编辑。只能根据用户提供的联网搜索 hits（title/url/snippet）提炼金句卡片，只输出合法 JSON。
严禁：
- 用模型记忆补造句子、书名、作者、章节、URL
- 使用不在 hits 里的 source_url
- 把结果标成 AI_ORIGINAL（本接口只处理真实搜索结果）
- 把 verification 标成 VERIFIED（摘要级证据最多 PARTIAL）

verification 只能是 UNVERIFIED 或 PARTIAL：
- PARTIAL：摘要里较明确出现作品名或作者，且与标题/URL 大致一致
- UNVERIFIED：仅转载摘要、信息不全、或句子出处不确定

excerpt 必须是必要短引文，优先从 snippet 中摘录真实出现的句子，不超过 120 字；不要整页粘贴。
recommend_reason 说明为何值得学习表达（1 句）。
technique_tags 2–4 个短标签。
输出：
{"results":[{"excerpt":"...","work_title":null,"author":null,"technique_tags":["..."],"recommend_reason":"...","verification":"UNVERIFIED","source_url":"...","source_title":"...","note":null}]}
results 条数不超过 hits；若无法从某 hit 提炼短引文可跳过。
"""


class QuoteService:
    def __init__(self, ai: AIClient, search: SearchProvider, cache: SimpleCache):
        self.ai = ai
        self.search = search
        self.cache = cache

    async def search_quotes(self, req: QuoteSearchRequest) -> QuoteSearchResponse:
        cache_key = f"{req.query}|{req.theme}|{req.technique}|{req.limit}"
        cached = self.cache.get("quote_search", cache_key)
        if cached:
            return QuoteSearchResponse.model_validate(cached)

        allowed_urls: set[str] = set()
        all_hits: list[SearchHit] = []
        used_queries: list[str] = []

        # 最多两轮：一轮初始检索词，一轮补搜
        for round_idx in range(2):
            queries = await self._build_queries(req, round_idx=round_idx, prior=used_queries)
            used_queries.extend(queries)
            for q in queries:
                try:
                    batch = await self.search.search(q, limit=max(5, min(8, req.limit + 2)))
                except SearchNotConfigured:
                    raise
                except Exception as e:
                    logger.warning("search failed for %r: %s", q, e)
                    continue
                for h in batch:
                    if not h.url:
                        continue
                    norm = _norm_url(h.url)
                    if norm in allowed_urls:
                        continue
                    allowed_urls.add(norm)
                    all_hits.append(h)

            # 质量门槛：至少 3 条有 snippet 的命中才停止补搜
            rich = [h for h in all_hits if (h.snippet or "").strip()]
            if len(rich) >= 3:
                break

        if not all_hits:
            return QuoteSearchResponse(
                query=req.query,
                results=[],
                message="联网检索未返回可用结果（可能被限流或关键词过窄），请换关键词重试。",
            )

        cards = await self._extract_cards(req, all_hits[: max(req.limit * 2, 12)], req.limit)

        msg = None
        if not cards:
            msg = "已检索到网页，但未能从摘要中提炼出可用短引文，请换关键词重试。"

        resp = QuoteSearchResponse(query=req.query, results=cards, message=msg)
        if cards:
            self.cache.set("quote_search", cache_key, resp.model_dump())
        return resp

    async def _build_queries(
        self,
        req: QuoteSearchRequest,
        *,
        round_idx: int,
        prior: list[str],
    ) -> list[str]:
        base = _heuristic_queries(req, round_idx=round_idx)
        if not self.ai.configured:
            return base

        try:
            prompt = (
                f"用户查询: {req.query}\n"
                f"主题: {req.theme or ''}\n"
                f"技巧: {req.technique or ''}\n"
                f"轮次: {round_idx + 1}/2\n"
                f"已用过的检索词（请避开重复）: {prior}\n"
                "请生成 2–4 条新的高质量检索词。"
            )
            data = await self.ai.chat_json(QUERY_GEN_SYSTEM, prompt, temperature=0.5)
            qs = []
            for q in data.get("queries") or []:
                s = str(q).strip()
                if s and s not in prior and s not in qs:
                    qs.append(s[:120])
            if 2 <= len(qs) <= 4:
                return qs
            if qs:
                # 不足 2 条时用启发式补齐
                for b in base:
                    if b not in qs and b not in prior:
                        qs.append(b)
                    if len(qs) >= 2:
                        break
                return qs[:4]
        except (AINotConfigured, AIRequestError) as e:
            logger.info("query gen fallback: %s", e)
        except Exception as e:
            logger.info("query gen error fallback: %s", e)
        return base

    async def _extract_cards(
        self,
        req: QuoteSearchRequest,
        hits: list[SearchHit],
        limit: int,
    ) -> list[QuoteCard]:
        url_set = {_norm_url(h.url) for h in hits if h.url}
        url_to_hit = {_norm_url(h.url): h for h in hits if h.url}

        # 无 AI 时：直接用 snippet 做待核实卡片（仍绑定真实 URL）
        fallback = _heuristic_cards(hits, req, limit)

        if not self.ai.configured:
            return fallback

        hits_payload = [
            {
                "title": h.title,
                "url": h.url,
                "snippet": (h.snippet or "")[:280],
            }
            for h in hits
        ]
        try:
            enriched = await self.ai.chat_json(
                EXTRACT_SYSTEM,
                (
                    f"用户查询: {req.query}\n"
                    f"技巧偏好: {req.technique}\n"
                    f"主题: {req.theme}\n"
                    f"需要最多 {limit} 条卡片。\n"
                    f"hits: {hits_payload}"
                ),
                temperature=0.3,
            )
        except (AINotConfigured, AIRequestError) as e:
            logger.info("AI extract skipped: %s", e)
            return fallback
        except Exception as e:
            logger.info("AI extract error: %s", e)
            return fallback

        cards: list[QuoteCard] = []
        seen_urls: set[str] = set()
        for item in enriched.get("results") or []:
            if len(cards) >= limit:
                break
            url = str(item.get("source_url") or "").strip()
            if not url or _norm_url(url) not in url_set:
                continue
            nu = _norm_url(url)
            if nu in seen_urls:
                continue
            excerpt = _short_excerpt(str(item.get("excerpt") or ""))
            if not excerpt:
                continue

            hit = url_to_hit.get(nu)
            ver = str(item.get("verification") or "UNVERIFIED").upper()
            # 禁止 VERIFIED / AI_ORIGINAL；最多 PARTIAL
            if ver == "VERIFIED":
                ver = "PARTIAL"
            if ver == "AI_ORIGINAL" or ver not in ("PARTIAL", "UNVERIFIED"):
                ver = "UNVERIFIED"

            work = item.get("work_title")
            author = item.get("author")
            # 空字符串当 null
            if isinstance(work, str) and not work.strip():
                work = None
            if isinstance(author, str) and not author.strip():
                author = None

            tags = item.get("technique_tags") or _guess_tags(req.technique, req.theme)
            tags = [str(t) for t in tags][:5]

            cards.append(
                QuoteCard(
                    id=hit_id(hit) if hit else hashlib_fallback(url, excerpt),
                    excerpt=excerpt,
                    work_title=work,
                    author=author,
                    technique_tags=tags,
                    recommend_reason=str(item.get("recommend_reason") or "来自联网检索摘要，出处待进一步核实。")[:200],
                    verification=VerificationStatus(ver),
                    source_url=url,
                    source_title=(str(item.get("source_title") or (hit.title if hit else "")) or None),
                    note=(str(item["note"])[:200] if item.get("note") else "出处依据为搜索摘要，请打开来源核对原文。"),
                )
            )
            seen_urls.add(nu)

        return cards if cards else fallback

    async def analyze(self, req: AnalyzeRequest) -> AnalyzeResponse:
        if not self.ai.configured:
            raise AINotConfigured(
                "AI 未配置：请在后端 .env 中设置 AI_API_KEY 后再使用名句解剖。"
            )
        payload = (
            f"原文短引文：{req.excerpt}\n"
            f"作品（若有，勿编造）：{req.work_title}\n"
            f"作者（若有）：{req.author}\n"
            f"来源URL（若有）：{req.source_url}\n"
            f"核实状态：{req.verification.value}\n"
            "请分析写作机制，并给出删减版对比。"
        )
        data = await self.ai.chat_json(ANALYZE_SYSTEM, payload, temperature=0.4)
        dims = []
        for d in data.get("dimensions", []):
            dims.append(
                AnalysisDimension(
                    key=str(d.get("key") or "misc"),
                    title=str(d.get("title") or ""),
                    content=str(d.get("content") or ""),
                    evidence=d.get("evidence"),
                    certainty=str(d.get("certainty") or "inferred"),
                )
            )
        return AnalyzeResponse(
            excerpt=req.excerpt,
            dimensions=dims,
            transferable_mechanism=str(data.get("transferable_mechanism") or ""),
            simplified_version=data.get("simplified_version"),
            simplified_diff=data.get("simplified_diff"),
        )


def _heuristic_queries(req: QuoteSearchRequest, *, round_idx: int) -> list[str]:
    q = req.query.strip()
    parts = [q]
    if req.theme:
        parts.append(str(req.theme))
    if req.technique:
        parts.append(str(req.technique))
    core = " ".join(parts)
    if round_idx == 0:
        return [
            f"{core} 金句",
            f"{core} 精彩段落 文学",
            f"{q} 名句 原文",
        ][:4]
    # 第二轮换角度：评论 / 书评 / 赏析
    return [
        f"{q} 书评 金句",
        f"{core} 赏析 表达",
        f"{q} 影评 佳句" if "电影" in q or "台词" in q else f"{q} 散文 佳句",
    ]


def _heuristic_cards(hits: list[SearchHit], req: QuoteSearchRequest, limit: int) -> list[QuoteCard]:
    cards: list[QuoteCard] = []
    for h in hits:
        if len(cards) >= limit:
            break
        excerpt = _short_excerpt(h.snippet)
        if not excerpt:
            continue
        cards.append(
            QuoteCard(
                id=hit_id(h),
                excerpt=excerpt,
                work_title=None,
                author=None,
                technique_tags=_guess_tags(req.technique, req.theme),
                recommend_reason="来自联网搜索摘要，请点击解剖查看分析。",
                verification=VerificationStatus.UNVERIFIED,
                source_url=h.url or None,
                source_title=h.title or None,
                note="出处待核实：当前仅有搜索摘要。",
            )
        )
    return cards


def _short_excerpt(snippet: str) -> str:
    s = re.sub(r"\s+", " ", snippet or "").strip()
    if len(s) > 120:
        s = s[:117] + "…"
    return s


def _guess_tags(technique: Optional[str], theme: Optional[str]) -> list[str]:
    tags = []
    if technique:
        tags.append(technique)
    if theme:
        tags.append(theme)
    return tags[:5]


def _norm_url(url: str) -> str:
    try:
        p = urlparse(url.strip())
        host = (p.netloc or "").lower()
        path = (p.path or "").rstrip("/")
        return f"{host}{path}"
    except Exception:
        return url.strip().lower()


def hashlib_fallback(url: str, excerpt: str) -> str:
    import hashlib

    raw = f"{url}|{excerpt[:80]}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
