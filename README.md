# 句子侦探局

AI 驱动的文学表达训练 Android 应用：金句发现 → 名句解剖 → 素材拼装 → AI 讲评 → 本地训练记录。

- 包名：`com.scarlett.juzizhentan`
- 版本：`0.2.0`
- **不需要电脑后端**：在手机「设置」里填写 AI Base URL / API Key / Model 即可
- 不预置本地题库；关卡由手机直连 AI 动态生成
- API Key **只存本机**（Room），不写进 APK / 源码
- **金句发现**：AI 编排检索词 + 手机联网请求 DuckDuckGo，再由 AI 从真实网页摘要提炼短引文（不是凭记忆编造）

## 真机怎么用（推荐）

1. 安装 [Releases](https://github.com/scarlettfh1923-ui/juzizhentan/releases) 里的 `juzizhentan-debug.apk`
2. 打开 App → 右上角 **设置**
3. 填写：
   - **AI Base URL**：默认 `https://api.deepseek.com/v1`（其它 OpenAI 兼容服务也可）
   - **API Key**：你的 DeepSeek / 兼容服务 Key
   - **Model**：默认 `deepseek-chat`
4. 点 **保存并测试连接**
5. 返回首页，即可「发现好句 / 开始训练」

无需电脑局域网、无需 `10.0.2.2`、无需再跑 FastAPI。

## 目录结构

```
juzizhentan/
  android/          # Kotlin + Jetpack Compose 客户端（主交付）
  backend/          # 可选：旧版 FastAPI（App 已不依赖）
  docs/
  keystore/
  dist/             # 构建产物 APK
```

## Android 构建

```bash
export ANDROID_HOME=/opt/android-sdk
cd /workspace/juzizhentan/android
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../dist/juzizhentan-debug.apk
```

## 核心流程（客户端直连）

1. 设置页保存 AI 凭据到本机
2. 金句发现：DuckDuckGo（Instant Answer + HTML）→ AI 提炼卡片；`source_url` 必须来自真实 hits
3. 名句解剖 / 训练生成 / 讲评：`POST {base}/chat/completions`，本地校验 JSON（槽位 2–6，每槽 3–5）
4. 收藏与训练历史存 Room

## DuckDuckGo 降级说明

部分运营商/地区可能限制 DuckDuckGo。若搜索无结果：

- 换关键词重试
- 稍后再试
- **训练、解剖、讲评不依赖搜索**，只要 API Key 可用即可

## 可选后端

`backend/` 仍保留作参考或自行部署；**v0.2.0 起 App 默认不再连接后端**。

## 许可与版权

短引文学习用途；不批量抓取受版权保护全文；不虚构出处。
