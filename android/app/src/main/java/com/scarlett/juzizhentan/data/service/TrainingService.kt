package com.scarlett.juzizhentan.data.service

import com.scarlett.juzizhentan.data.ai.AiClient
import com.scarlett.juzizhentan.data.ai.AiConfig
import com.scarlett.juzizhentan.data.ai.AiNotConfiguredException
import com.scarlett.juzizhentan.data.ai.AiRequestException
import com.scarlett.juzizhentan.data.model.CandidateOption
import com.scarlett.juzizhentan.data.model.CritiqueRequest
import com.scarlett.juzizhentan.data.model.CritiqueResponse
import com.scarlett.juzizhentan.data.model.MaterialSlot
import com.scarlett.juzizhentan.data.model.SlotComment
import com.scarlett.juzizhentan.data.model.TrainingGenerateRequest
import com.scarlett.juzizhentan.data.model.TrainingLevel
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class TrainingService(private val ai: AiClient) {

    fun generate(config: AiConfig, req: TrainingGenerateRequest): TrainingLevel {
        if (!config.configured) {
            throw AiNotConfiguredException("请先在设置页填写 AI API Key，再生成训练关卡。")
        }
        val avoid = req.avoid_summaries.take(10).joinToString("；")
        var user = buildString {
            appendLine("技巧: ${req.technique ?: "随机合适技巧"}")
            appendLine("主题偏好: ${req.theme ?: "不限"}")
            appendLine("难度: ${req.difficulty ?: "medium"}")
            appendLine("种子金句(可选，用于迁移机制而非照抄): ${req.seed_excerpt ?: "无"}")
            appendLine("请避免与这些摘要过于相似: ${avoid.ifBlank { "无" }}")
            append("请生成一套全新训练关卡 JSON。")
        }
        var lastErr: Exception? = null
        repeat(3) { attempt ->
            try {
                val data = ai.chatJson(config, GENERATE_SYSTEM, user, temperature = 0.85)
                return parseLevel(data)
            } catch (e: Exception) {
                lastErr = e
                if (e is AiNotConfiguredException) throw e
                if (e is AiRequestException && (e.httpCode == 401 || e.httpCode == 403)) throw e
                user += "\n上次输出校验失败: ${e.message}。请严格按 schema 重输出。"
            }
        }
        throw AiRequestException("关卡 JSON 校验失败: ${lastErr?.message}")
    }

    fun critique(config: AiConfig, req: CritiqueRequest): CritiqueResponse {
        if (!config.configured) {
            throw AiNotConfiguredException("请先在设置页填写 AI API Key，再使用讲评。")
        }
        val selectedTexts = linkedMapOf<String, String>()
        for (slot in req.level.slots) {
            val oid = req.selections[slot.id] ?: continue
            val text = slot.options.find { it.id == oid }?.text ?: continue
            selectedTexts[slot.id] = text
        }
        val selectionsJson = JSONObject()
        req.selections.forEach { (k, v) -> selectionsJson.put(k, v) }
        val selectedJson = JSONObject()
        selectedTexts.forEach { (k, v) -> selectedJson.put(k, v) }
        val payload = JSONObject()
            .put("level", levelToJson(req.level))
            .put("selections", selectionsJson)
            .put("selected_texts", selectedJson)
            .put("seed_excerpt", req.seed_excerpt)
        val data = ai.chatJson(config, CRITIQUE_SYSTEM, payload.toString(), temperature = 0.5)
        val combined = data.optString("combined_text").ifBlank {
            selectedTexts.values.joinToString("。")
        }
        val slotComments = mutableListOf<SlotComment>()
        val sc = data.optJSONArray("slot_comments") ?: JSONArray()
        for (i in 0 until sc.length()) {
            val o = sc.optJSONObject(i) ?: continue
            slotComments += SlotComment(
                slot_id = o.optString("slot_id").takeIf { it.isNotBlank() },
                option_id = o.optString("option_id").takeIf { it.isNotBlank() },
                comment = o.optString("comment").takeIf { it.isNotBlank() }
            )
        }
        return CritiqueResponse(
            combined_text = combined,
            overall_effect = data.optString("overall_effect"),
            slot_comments = slotComments,
            strengths = jsonStringList(data.optJSONArray("strengths")),
            problems = jsonStringList(data.optJSONArray("problems")),
            swap_suggestions = jsonStringList(data.optJSONArray("swap_suggestions")),
            optimized_combination = data.optString("optimized_combination"),
            mechanism_contrast = data.optString("mechanism_contrast").takeIf { it.isNotBlank() }
        )
    }

    private fun parseLevel(data: JSONObject): TrainingLevel {
        val slotsArr = data.optJSONArray("slots") ?: JSONArray()
        if (slotsArr.length() < 2 || slotsArr.length() > 6) {
            throw IllegalArgumentException("槽位数量须为 2–6，当前 ${slotsArr.length()}")
        }
        val slots = mutableListOf<MaterialSlot>()
        for (i in 0 until slotsArr.length()) {
            val slot = slotsArr.getJSONObject(i)
            val slotId = slot.optString("id").ifBlank { "slot_${i + 1}" }
            val optsArr = slot.optJSONArray("options") ?: JSONArray()
            if (optsArr.length() < 3 || optsArr.length() > 5) {
                throw IllegalArgumentException("槽位 $slotId 候选项须为 3–5，当前 ${optsArr.length()}")
            }
            val options = mutableListOf<CandidateOption>()
            for (j in 0 until optsArr.length()) {
                val opt = optsArr.getJSONObject(j)
                val text = opt.optString("text").trim()
                if (text.isBlank()) throw IllegalArgumentException("空候选项文本")
                options += CandidateOption(
                    id = opt.optString("id").ifBlank { "${slotId}_opt_${j + 1}" },
                    text = text,
                    function = opt.optString("function"),
                    suitability = opt.optString("suitability"),
                    potential_issue = opt.optString("potential_issue")
                )
            }
            slots += MaterialSlot(
                id = slotId,
                label = slot.optString("label").ifBlank { "槽位${i + 1}" },
                role = slot.optString("role"),
                options = options
            )
        }
        val ref = mutableMapOf<String, String>()
        val refObj = data.optJSONObject("reference_combination")
        if (refObj != null) {
            val keys = refObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                ref[k] = refObj.optString(k)
            }
        }
        val theme = data.optString("theme").ifBlank { "未命名主题" }
        val scenario = data.optString("scenario")
        var fingerprint = data.optString("summary_fingerprint")
        if (fingerprint.isBlank()) {
            fingerprint = sha12("$theme|$scenario")
        }
        return TrainingLevel(
            theme = theme,
            training_goal = data.optString("training_goal").ifBlank { "表达训练" },
            difficulty = data.optString("difficulty").ifBlank { "medium" },
            scenario = scenario,
            slots = slots,
            reference_combination = ref,
            reference_rationale = data.optString("reference_rationale"),
            common_pitfalls = jsonStringList(data.optJSONArray("common_pitfalls")),
            next_difficulty_hint = data.optString("next_difficulty_hint"),
            summary_fingerprint = fingerprint
        )
    }

    private fun levelToJson(level: TrainingLevel): JSONObject {
        val slots = JSONArray()
        for (s in level.slots) {
            val opts = JSONArray()
            for (o in s.options) {
                opts.put(
                    JSONObject()
                        .put("id", o.id)
                        .put("text", o.text)
                        .put("function", o.function)
                        .put("suitability", o.suitability)
                        .put("potential_issue", o.potential_issue)
                )
            }
            slots.put(
                JSONObject()
                    .put("id", s.id)
                    .put("label", s.label)
                    .put("role", s.role)
                    .put("options", opts)
            )
        }
        return JSONObject()
            .put("theme", level.theme)
            .put("training_goal", level.training_goal)
            .put("difficulty", level.difficulty)
            .put("scenario", level.scenario)
            .put("slots", slots)
            .put("reference_combination", run {
                val o = JSONObject()
                level.reference_combination.forEach { (k, v) -> o.put(k, v) }
                o
            })
            .put("reference_rationale", level.reference_rationale)
            .put("common_pitfalls", JSONArray(level.common_pitfalls))
            .put("next_difficulty_hint", level.next_difficulty_hint)
            .put("summary_fingerprint", level.summary_fingerprint)
    }

    companion object {
        private const val GENERATE_SYSTEM = """你是文学表达训练设计师。只输出合法 JSON，不要 markdown、不要题库式固定模板。
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

        private const val CRITIQUE_SYSTEM = """你是文学写作教练。只输出合法 JSON。
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

        private fun jsonStringList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) out += s
            }
            return out
        }

        private fun sha12(raw: String): String {
            val dig = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return dig.joinToString("") { "%02x".format(it) }.take(12)
        }
    }
}
