package com.scarlett.juzizhentan.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
                SectionLabel("AI 服务（本机直连）")
                Text(
                    "在手机上填写你的 API Key 即可使用金句发现、解剖、训练与讲评。Key 只保存在本机，不会写入安装包。请求直连你配置的 AI 服务（默认 DeepSeek），无需电脑后端。",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = vm::onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("AI Base URL") },
                    placeholder = { Text("https://api.deepseek.com/v1") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = vm::onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.model,
                    onValueChange = vm::onModelChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Model") },
                    placeholder = { Text("deepseek-chat") }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.saveAndTest() },
                        enabled = !state.checking,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text(if (state.checking) "测试中…" else "保存并测试连接")
                    }
                    OutlinedButton(onClick = { vm.save() }, enabled = !state.checking) {
                        Text("仅保存")
                    }
                }
                if (state.saved && state.error == null && state.testResult == null) {
                    Text("已保存。", color = InkMuted)
                }
                state.error?.let { Text(it, color = AccentRed) }
                state.testResult?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }

            PaperCard {
                TextButton(onClick = { vm.toggleAdvanced() }) {
                    Text(if (state.showAdvanced) "收起说明" else "使用说明与高级提示")
                }
                if (state.showAdvanced) {
                    Text(
                        "兼容 OpenAI Chat Completions 接口。金句发现：手机联网请求 DuckDuckGo，再用 AI 从真实网页摘要提炼短引文（不凭记忆编造）。若 DuckDuckGo 在你的网络不可用，可换关键词或稍后再试；训练与讲评只要 Key 可用即可。",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            PaperCard {
                SectionLabel("数据")
                OutlinedButton(onClick = { vm.clearCache() }) { Text("清理本地训练历史缓存") }
                if (state.cleared) Text("已清理本地训练历史。", color = InkMuted)
            }

            PaperCard {
                SectionLabel("关于")
                Text("句子侦探局 v0.2.0", style = MaterialTheme.typography.titleMedium)
                Text(
                    "AI 驱动的文学表达训练。客户端直连 AI；金句发现结合免费 DuckDuckGo 检索。不预置本地题库，不把密钥写入 APK。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "隐私：训练记录与收藏默认仅存本机。调用 AI/搜索时手机直接向第三方服务发送完成任务所需的文本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted
                )
            }
        }
    }
}
