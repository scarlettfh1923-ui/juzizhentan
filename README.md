# 句子侦探局

AI 驱动的文学表达训练 Android 应用：金句发现 → 名句解剖 → 素材拼装 → AI 讲评 → 本地训练记录。

- 包名：`com.scarlett.juzizhentan`
- 不预置本地题库；关卡由后端 AI 动态生成
- API Key 只放后端环境变量，不进 APK
- **金句发现**：AI 编排检索词 + 免费 DuckDuckGo 联网拉取真实网页，再由 AI 从摘要提炼短引文卡片（**不是** AI 凭记忆编造金句；默认**不需要**第二把付费搜索 Key）

## 目录结构

```
juzizhentan/
  android/          # Kotlin + Jetpack Compose 客户端
  backend/          # FastAPI 后端
  docs/             # API 与测试说明
  keystore/         # 本项目独立 debug keystore
  dist/             # 构建产物 APK
```

## 后端

### 1. 安装

```bash
cd /workspace/juzizhentan/backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# 编辑 .env：填写 AI_API_KEY 即可；SEARCH_PROVIDER 默认 ddg（免搜索 Key）
```

### 2. 启动

```bash
cd /workspace/juzizhentan/backend
source .venv/bin/activate
python run.py
# 或：uvicorn app.main:app --host 0.0.0.0 --port 8765
```

健康检查：`curl http://127.0.0.1:8765/api/health`

有 `AI_API_KEY` 且 `SEARCH_PROVIDER=ddg` 时，`ai_configured` 与 `search_configured` 均为 true。不会用模型记忆冒充联网搜索结果；卡片 `source_url` 必须来自真实检索 hits。

### 3. 环境变量摘要

| 变量 | 说明 |
|------|------|
| `AI_BASE_URL` | OpenAI 兼容地址，默认 DeepSeek |
| `AI_API_KEY` | 模型 Key（编排检索词、提炼卡片、解剖、训练、讲评） |
| `AI_MODEL` | 模型名 |
| `SEARCH_PROVIDER` | 默认 `ddg`；可选 `none` / `serper` / `brave` / `tavily` |
| `SERPER_API_KEY` / `BRAVE_API_KEY` / `TAVILY_API_KEY` | 仅在选用对应付费 provider 时需要 |
| `PORT` | 默认 8765 |

完整模板见 `backend/.env.example`。接口说明见 `docs/api.md`。

## Android 客户端

### 构建

```bash
export ANDROID_HOME=/opt/android-sdk
cd /workspace/juzizhentan/android
./gradlew assembleDebug assembleRelease
```

APK 复制到：

- `dist/juzizhentan-debug.apk`
- `dist/juzizhentan-release.apk`（当前亦使用本项目 debug keystore 签名，便于内测）

### 安装

```bash
adb install -r /workspace/juzizhentan/dist/juzizhentan-debug.apk
```

### 连接后端

- **模拟器**：设置页默认 `http://10.0.2.2:8765`
- **真机**：手机与电脑同一局域网，设置页改为 `http://<电脑局域网IP>:8765`，并确保电脑防火墙放行 8765

## 核心流程

1. 首页进入「发现好句」或「开始/随机训练」
2. 搜索走 `/api/quotes/search`：AI 生成检索词 → DuckDuckGo（或其它 provider）拉网页 → AI 提炼短引文；卡片展示核实状态与来源链接
3. 解剖走 `/api/quotes/analyze`
4. 训练走 `/api/training/generate`，分步点选素材，提交后 `/api/training/critique`
5. 收藏与训练历史存 Room（本机）

## 还需要你提供的凭据

- 一个 OpenAI 兼容 `AI_API_KEY`（推荐 DeepSeek）即可完成金句发现编排、解剖、生成、讲评
- 付费搜索 Key **可选**：默认 DDG 免费联网；若 DDG 被墙/限流，可改 `SEARCH_PROVIDER=serper|brave|tavily` 并填对应 Key

## 许可与版权

短引文学习用途；不批量抓取受版权保护全文；不虚构出处。
