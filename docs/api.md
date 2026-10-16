# 句子侦探局 API

Base URL 默认：`http://127.0.0.1:8765`（模拟器客户端用 `http://10.0.2.2:8765`）

## GET `/api/health`

返回服务状态，不暴露密钥。

```json
{
  "status": "ok",
  "ai_configured": true,
  "search_configured": true,
  "search_provider": "ddg",
  "model": "deepseek-chat"
}
```

`search_configured`：`SEARCH_PROVIDER=ddg`（或 duckduckgo）时为 true（免 Key）；`serper|brave|tavily` 需对应 Key；`none` 为 false。

## POST `/api/quotes/search`

金句发现流程（默认）：

1. AI 根据 query/theme/technique 生成 2–4 条检索词
2. 调用联网检索（默认 DuckDuckGo，无需付费搜索 Key）拉取 title/url/snippet
3. 去重后由 AI **仅从给定 hits** 提炼短引文卡片；`source_url` 必须来自 hits
4. 核实状态最多 `PARTIAL`（禁止用摘要证据标 `VERIFIED`；禁止 `AI_ORIGINAL` 冒充搜索结果）
5. hits 过少时可自动换一组检索词再搜一轮（最多 2 轮）

请求：

```json
{ "query": "白鹿原 命运", "technique": "以小见大", "theme": null, "limit": 8 }
```

响应：`results[]` 含 `excerpt`、`work_title`、`author`、`technique_tags`、`recommend_reason`、`verification`（`VERIFIED|PARTIAL|UNVERIFIED|AI_ORIGINAL`）、`source_url`、`source_title`、`note`。

检索失败：`502`；显式 `SEARCH_PROVIDER=none`：`503`。

## POST `/api/quotes/analyze`

请求：短引文 + 可选出处字段。响应：`dimensions[]`、`transferable_mechanism`、`simplified_version`、`simplified_diff`。

未配置 AI：`503`。

## POST `/api/training/generate`

请求：`technique` / `theme` / `difficulty` / `seed_excerpt` / `avoid_summaries`。

响应：结构化关卡（`theme`、`training_goal`、`difficulty`、`scenario`、`slots`（每槽 3–5 选项）、`reference_combination`、`reference_rationale`、`common_pitfalls`、`next_difficulty_hint`、`summary_fingerprint`）。

后端用 Pydantic 校验；失败最多再试 2 次。

## POST `/api/training/critique`

请求：`level`（完整关卡 JSON）+ `selections`（`slot_id -> option_id`）+ 可选 `seed_excerpt`。

响应：组合文本、整体效果、优缺点、替换建议、优化段落、机制对照。

## 配置

见 `backend/.env.example`。密钥仅存后端环境变量，禁止写入 APK。默认 `SEARCH_PROVIDER=ddg`，金句发现不需要第二把搜索 Key。
