package com.scarlett.juzizhentan.data.service

import com.scarlett.juzizhentan.data.ai.AiClient
import com.scarlett.juzizhentan.data.ai.AiConfig
import com.scarlett.juzizhentan.data.ai.AiNotConfiguredException
import com.scarlett.juzizhentan.data.ai.AiRequestException
import com.scarlett.juzizhentan.data.model.AnalyzeRequest
import com.scarlett.juzizhentan.data.model.AnalyzeResponse
import com.scarlett.juzizhentan.data.model.AnalysisDimension
import com.scarlett.juzizhentan.data.model.QuoteCard
import com.scarlett.juzizhentan.data.model.QuoteSearchRequest
import com.scarlett.juzizhentan.data.model.QuoteSearchResponse
import com.scarlett.juzizhentan.data.search.DuckDuckGoClient
import com.scarlett.juzizhentan.data.search.SearchHit
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class QuoteService(
    private val ai: AiClient,
    private val search: DuckDuckGoClient
) {
    fun searchQuotes(config: AiConfig, req: QuoteSearchRequest): QuoteSearchResponse {
        val allowed = LinkedHashMap<String, SearchHit>()
        val usedQueries = mutableListOf<String>()

        for (round in 0 until 2) {
            val queries = buildQueries(config, req, round, usedQueries)
            usedQueries.addAll(queries)
            for (q in queries) {
                val batch = runCatching { search.search(q, limit = maxOf(5, minOf(8, req.limit + 2))) }
                    .getOrDefault(emptyList())
                for (h in batch) {
                    if (h.url.isBlank()) continue
                    val key = DuckDuckGoClient.normUrl(h.url)
                    if (!allowed.containsKey(key)) allowed[key] = h
                }
            }
            val rich = allowed.values.count { it.snippet.isNotBlank() }
            if (rich >= 3) break
        }

        if (allowed.isEmpty()) {
            return QuoteSearchResponse(
                query = req.query,
                results = emptyList(),
                message = "联网检索未返回可用结果（手机网络可能限制 DuckDuckGo，或关键词过窄）。请换关键词重试；训练/解剖不依赖搜索，仍可在设置好 API Key 后使用。"
            )
        }

        val hits = allowed.values.take(maxOf(req.limit * 2, 12))
        val cards = extractCards(config, req, hits, req.limit)
        val msg = if (cards.isEmpty()) {
            "已检索到网页，但未能从摘要中提炼出可用短引文，请换关键词重试。"
        } else null
        return QuoteSearchResponse(query = req.query, results = cards, message = msg)
    }

    fun analyze(config: AiConfig, req: AnalyzeRequest): AnalyzeResponse {
        if (!config.configured) {
            throw AiNotConfiguredException("请先在设置页填写 AI API Key，再使用名句解剖。")
        }
        val payload = buildString {
            appendLine("原文短引文：${req.excerpt}")
            appendLine("作品（若有，勿编造）：${req.work_title}")
            appendLine("作者（若有）：${req.author}")
            appendLine("来源URL（若有）：${req.source_url}")
            appendLine("核实状态：${req.verification}")
            append("请分析写作机制，并给出删减版对比。")
        }
        val data = ai.chatJson(config, ANALYZE_SYSTEM, payload, temperature = 0.4)
        val dims = mutableListOf<AnalysisDimension>()
        val arr = data.optJSONArray("dimensions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            dims += AnalysisDimension(
                key = d.optString("key").ifBlank { "misc" },
                title = d.optString("title"),
                content = d.optString("content"),
                evidence = d.optString("evidence").takeIf { it.isNotBlank() },
                certainty = d.optString("certainty").ifBlank { "inferred" }
            )
        }
        return AnalyzeResponse(
            excerpt = req.excerpt,
            dimensions = dims,
            transferable_mechanism = data.optString("transferable_mechanism"),
            simplified_version = data.optString("simplified_version").takeIf { it.isNotBlank() },
            simplified_diff = data.optString("simplified_diff").takeIf { it.isNotBlank() }
        )
    }

    private fun buildQueries(
        config: AiConfig,
        req: QuoteSearchRequest,
        roundIdx: Int,
        prior: List<String>
    ): List<String> {
        val base = heuristicQueries(req, roundIdx)
        if (!config.configured) return base
        return try {
            val prompt = buildString {
                appendLine("用户查询: ${req.query}")
                appendLine("主题: ${req.theme.orEmpty()}")
                appendLine("技巧: ${req.technique.orEmpty()}")
                appendLine("轮次: ${roundIdx + 1}/2")
                appendLine("已用过的检索词（请避开重复）: $prior")
                append("请生成 2–4 条新的高质量检索词。")
            }
            val data = ai.chatJson(config, QUERY_GEN_SYSTEM, prompt, temperature = 0.5, maxTokens = 512)
            val qs = mutableListOf<String>()
            val arr = data.optJSONArray("queries") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s !in prior && s !in qs) qs += s.take(120)
            }
            when {
                qs.size in 2..4 -> qs
                qs.isNotEmpty() -> {
                    for (b in base) {
                        if (b !in qs && b !in prior) qs += b
                        if (qs.size >= 2) break
                    }
                    qs.take(4)
                }
                else -> base
            }
        } catch (_: Exception) {
            base
        }
    }

    private fun extractCards(
        config: AiConfig,
        req: QuoteSearchRequest,
        hits: List<SearchHit>,
        limit: Int
    ): List<QuoteCard> {
        val urlSet = hits.map { DuckDuckGoClient.normUrl(it.url) }.toSet()
        val urlToHit = hits.associateBy { DuckDuckGoClient.normUrl(it.url) }
        val fallback = heuristicCards(hits, req, limit)
        if (!config.configured) return fallback

        val hitsPayload = JSONArray()
        for (h in hits) {
            hitsPayload.put(
                JSONObject()
                    .put("title", h.title)
                    .put("url", h.url)
                    .put("snippet", h.snippet.take(280))
            )
        }
        val enriched = try {
            ai.chatJson(
                config,
                EXTRACT_SYSTEM,
                buildString {
                    appendLine("用户查询: ${req.query}")
                    appendLine("技巧偏好: ${req.technique}")
                    appendLine("主题: ${req.theme}")
                    appendLine("需要最多 $limit 条卡片。")
                    append("hits: $hitsPayload")
                },
                temperature = 0.3
            )
        } catch (_: AiNotConfiguredException) {
            return fallback
        } catch (_: AiRequestException) {
            return fallback
        } catch (_: Exception) {
            return fallback
        }

        val cards = mutableListOf<QuoteCard>()
        val seen = HashSet<String>()
        val results = enriched.optJSONArray("results") ?: JSONArray()
        for (i in 0 until results.length()) {
            if (cards.size >= limit) break
            val item = results.optJSONObject(i) ?: continue
            val url = item.optString("source_url").trim()
            val nu = DuckDuckGoClient.normUrl(url)
            if (url.isBlank() || nu !in urlSet || !seen.add(nu)) continue
            val excerpt = shortExcerpt(item.optString("excerpt"))
            if (excerpt.isBlank()) continue
            var ver = item.optString("verification").uppercase().ifBlank { "UNVERIFIED" }
            if (ver == "VERIFIED") ver = "PARTIAL"
            if (ver == "AI_ORIGINAL" || ver !in setOf("PARTIAL", "UNVERIFIED")) ver = "UNVERIFIED"
            val work = item.optString("work_title").takeIf { it.isNotBlank() }
            val author = item.optString("author").takeIf { it.isNotBlank() }
            val tagsArr = item.optJSONArray("technique_tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) {
                for (t in 0 until tagsArr.length()) tags += tagsArr.optString(t)
            }
            if (tags.isEmpty()) tags += guessTags(req.technique, req.theme)
            val hit = urlToHit[nu]
            cards += QuoteCard(
                id = hitId(hit, url, excerpt),
                excerpt = excerpt,
                work_title = work,
                author = author,
                technique_tags = tags.take(5),
                recommend_reason = item.optString("recommend_reason")
                    .ifBlank { "来自联网检索摘要，出处待进一步核实。" }
                    .take(200),
                verification = ver,
                source_url = url,
                source_title = item.optString("source_title").ifBlank { hit?.title.orEmpty() }
                    .takeIf { it.isNotBlank() },
                note = item.optString("note").takeIf { it.isNotBlank() }
                    ?: "出处依据为搜索摘要，请打开来源核对原文。"
            )
        }
        return if (cards.isNotEmpty()) cards else fallback
    }

    companion object {
        private const val ANALYZE_SYSTEM = """你是文学文本分析师。只输出合法 JSON，不要 markdown。
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

        private const val QUERY_GEN_SYSTEM = """你是文学检索助手。根据用户意图生成适合在中文互联网搜索文学金句/名著评论的检索词。
只输出合法 JSON：{"queries":["...","..."]}
要求：
- 2 到 4 条检索词，中文为主
- 偏向名著、作品名、作者、金句、精彩段落、影评/书评中的优秀表达
- 可含技巧词（如对照、意象），但不要空泛
- 不要输出解释文字
"""

        private const val EXTRACT_SYSTEM = """你是文学编辑。只能根据用户提供的联网搜索 hits（title/url/snippet）提炼金句卡片，只输出合法 JSON。
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

        private fun heuristicQueries(req: QuoteSearchRequest, roundIdx: Int): List<String> {
            val q = req.query.trim()
            val parts = mutableListOf(q)
            req.theme?.let { parts += it }
            req.technique?.let { parts += it }
            val core = parts.joinToString(" ")
            return if (roundIdx == 0) {
                listOf("$core 金句", "$core 精彩段落 文学", "$q 名句 原文").take(4)
            } else {
                listOf(
                    "$q 书评 金句",
                    "$core 赏析 表达",
                    if ("电影" in q || "台词" in q) "$q 影评 佳句" else "$q 散文 佳句"
                )
            }
        }

        private fun heuristicCards(hits: List<SearchHit>, req: QuoteSearchRequest, limit: Int): List<QuoteCard> {
            val cards = mutableListOf<QuoteCard>()
            for (h in hits) {
                if (cards.size >= limit) break
                val excerpt = shortExcerpt(h.snippet)
                if (excerpt.isBlank()) continue
                cards += QuoteCard(
                    id = hitId(h, h.url, excerpt),
                    excerpt = excerpt,
                    technique_tags = guessTags(req.technique, req.theme),
                    recommend_reason = "来自联网搜索摘要，请点击解剖查看分析。",
                    verification = "UNVERIFIED",
                    source_url = h.url.takeIf { it.isNotBlank() },
                    source_title = h.title.takeIf { it.isNotBlank() },
                    note = "出处待核实：当前仅有搜索摘要。"
                )
            }
            return cards
        }

        private fun shortExcerpt(snippet: String): String {
            var s = snippet.replace(Regex("\\s+"), " ").trim()
            if (s.length > 120) s = s.take(117) + "…"
            return s
        }

        private fun guessTags(technique: String?, theme: String?): List<String> {
            val tags = mutableListOf<String>()
            if (!technique.isNullOrBlank()) tags += technique
            if (!theme.isNullOrBlank()) tags += theme
            return tags.take(5)
        }

        private fun hitId(hit: SearchHit?, url: String, excerpt: String): String {
            val raw = "${hit?.url ?: url}|${excerpt.take(80)}"
            val md = MessageDigest.getInstance("SHA-256")
            val dig = md.digest(raw.toByteArray(Charsets.UTF_8))
            return dig.joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}
