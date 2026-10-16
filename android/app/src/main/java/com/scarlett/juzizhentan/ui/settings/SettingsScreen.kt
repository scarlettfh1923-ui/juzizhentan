package com.scarlett.juzizhentan.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.ui.common.JuziTopBar
import com.scarlett.juzizhentan.ui.common.PaperCard
import com.scarlett.juzizhentan.ui.common.SectionLabel
import com.scarlett.juzizhentan.ui.theme.AccentRed
import com.scarlett.juzizhentan.ui.theme.InkMuted

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state = vm.state
    Scaffold(topBar = { JuziTopBar("设置", onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PaperCard {
                SectionLabel("后端地址")
                Text("模拟器默认 10.0.2.2:8765；真机请改为电脑局域网 IP。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = vm::onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveAndCheck() },
                    enabled = !state.checking,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text(if (state.checking) "检测中…" else "保存并检测连接") }
                state.error?.let { Text(it, color = AccentRed) }
                state.health?.let { h ->
                    Spacer(Modifier.height(8.dp))
                    Text("状态：${h.status}")
                    Text("AI 已配置：${if (h.ai_configured) "是" else "否"}")
                    Text("联网发现：${if (h.search_configured) "可用（${h.search_provider}）" else "未启用"}")
                    if (h.model.isNotBlank()) Text("模型：${h.model}")
                    Text("密钥不会显示在此页，请只在后端 .env 配置。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            PaperCard {
                SectionLabel("数据")
                OutlinedButton(onClick = { vm.clearCache() }) { Text("清理本地训练历史缓存") }
                if (state.cleared) Text("已清理本地训练历史。", color = InkMuted)
            }
            PaperCard {
                SectionLabel("关于")
                Text("句子侦探局 v0.1.0", style = MaterialTheme.typography.titleMedium)
                Text("AI 驱动的文学表达训练。金句发现由 AI 编排检索词并通过后端免费联网检索（默认 DuckDuckGo）找真实网页短引文，不是凭记忆编造；训练与讲评依赖后端 AI。不预置本地题库，不把密钥写入 APK。", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Spacer(Modifier.height(6.dp))
                Text("隐私：训练记录与收藏默认仅存本机。调用 AI/搜索时会向后端发送完成任务所需的文本；后端再转交第三方服务。", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
        }
    }
}
