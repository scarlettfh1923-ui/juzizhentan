# 测试结果（诚实记录）

测试日期：2026-10-16（含金句发现改为 AI + DuckDuckGo）

## 环境

- Java 21、Android SDK 35、Python 3.13
- 项目路径：`/workspace/juzizhentan`

## 已验证

| 项 | 结果 | 说明 |
|----|------|------|
| 后端可启动 | 通过 | `python run.py`，监听 8765 |
| GET /api/health | 通过 | `ai_configured=true`, `search_configured=true`, `search_provider=ddg`（有 AI Key 时） |
| 金句搜索 AI+DDG | 通过 | POST `/api/quotes/search` HTTP 200；真实 `source_url`；verification 仅 PARTIAL/UNVERIFIED |
| AI generate / critique | 通过 | 此前已用 AI_API_KEY 联调 |
| Pydantic 关卡校验 | 通过 | `backend/tests/test_schema.py` |
| Android assembleDebug | 通过 | `dist/juzizhentan-debug.apk`（约 61MB，文案已更新） |
| Android assembleRelease | 通过 | 既有 `dist/juzizhentan-release.apk`（本次未强制重打 release） |
| 客户端无硬编码 API Key | 通过 | 源码扫描无 sk-/API_KEY |
| 包名 | 通过 | `com.scarlett.juzizhentan`，应用名「句子侦探局」 |

## 验收标准对照（摘要）

- 金句发现：默认 DuckDuckGo 免搜索 Key；AI 编排检索词并从 hits 提炼短引文；禁止模型记忆冒充搜索；禁止 VERIFIED/AI_ORIGINAL 冒充核实原文
- 付费搜索 Serper/Brave/Tavily 仍可选

## 未完成 / 可选

- DDG 在部分网络可能限流/失败：可改 `SEARCH_PROVIDER=serper|brave|tavily` 并填 Key
- 可点击原文片段联动分析维度：MVP 以维度卡片列表呈现
- Release 正式签名：当前 release 用本项目 debug keystore，仅供内测
