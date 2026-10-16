package com.scarlett.juzizhentan.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.data.model.QuoteCard
import com.scarlett.juzizhentan.ui.common.JuziTopBar
import com.scarlett.juzizhentan.ui.common.PaperCard
import com.scarlett.juzizhentan.ui.common.SectionLabel
import com.scarlett.juzizhentan.ui.theme.AccentRed
import com.scarlett.juzizhentan.ui.theme.InkMuted

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onDiscover: () -> Unit,
    onTrain: () -> Unit,
    onRandom: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onOpenFavorite: (QuoteCard) -> Unit
) {
    val favorites by vm.favorites.collectAsState()
    val history by vm.history.collectAsState()

    Scaffold(
        topBar = {
            JuziTopBar(
                title = "句子侦探局",
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "阅读好句 · 解剖机制 · 拼装表达",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (!vm.aiConfigured) {
                item {
                    PaperCard {
                        Text("先配置 AI", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "打开右上角设置，填写 DeepSeek（或其它 OpenAI 兼容）API Key，即可在手机上直接使用，无需电脑后端。",
                            color = InkMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                            Text("去设置")
                        }
                    }
                }
            }
            item {
                PaperCard {
                    Text("发现好句", style = MaterialTheme.typography.titleMedium)
                    Text("手机直连 AI + 联网发现优质表达", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDiscover, colors = ButtonDefaults.buttonColors(containerColor = AccentRed)) {
                        Text("去发现")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperCard(Modifier.weight(1f)) {
                        Text("开始训练", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onTrain, modifier = Modifier.fillMaxWidth()) { Text("选技巧") }
                    }
                    PaperCard(Modifier.weight(1f)) {
                        Text("随机训练", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRandom, modifier = Modifier.fillMaxWidth()) { Text("交给 AI") }
                    }
                }
            }
            item {
                TextButton(onClick = onHistory) { Text("查看训练记录") }
            }
            if (history.isNotEmpty()) {
                item { SectionLabel("最近训练") }
                items(history.take(3)) { h ->
                    PaperCard {
                        Text(h.theme, style = MaterialTheme.typography.titleMedium)
                        Text(h.trainingGoal, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(h.combinedText.take(80) + if (h.combinedText.length > 80) "…" else "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (favorites.isNotEmpty()) {
                item { SectionLabel("收藏的金句") }
                items(favorites.take(5)) { f ->
                    PaperCard(Modifier.clickable { onOpenFavorite(vm.favoriteToCard(f)) }) {
                        Text("「${f.excerpt}」", style = MaterialTheme.typography.bodyLarge)
                        val meta = listOfNotNull(f.workTitle, f.author).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
