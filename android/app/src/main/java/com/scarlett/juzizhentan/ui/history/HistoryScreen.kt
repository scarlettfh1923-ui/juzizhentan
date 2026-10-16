package com.scarlett.juzizhentan.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.ui.common.JuziTopBar
import com.scarlett.juzizhentan.ui.common.PaperCard
import com.scarlett.juzizhentan.ui.common.SectionLabel
import com.scarlett.juzizhentan.ui.theme.InkMuted

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsState()
    val favorites by vm.favorites.collectAsState()

    Scaffold(topBar = { JuziTopBar("个人记录", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionLabel("训练历史") }
            if (history.isEmpty()) {
                item { Text("暂无训练记录", color = InkMuted) }
            } else {
                items(history) { h ->
                    PaperCard {
                        Text(h.theme, style = MaterialTheme.typography.titleMedium)
                        Text("${h.difficulty} · ${h.trainingGoal}", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(h.combinedText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { SectionLabel("收藏") }
            if (favorites.isEmpty()) {
                item { Text("暂无收藏", color = InkMuted) }
            } else {
                items(favorites) { f ->
                    PaperCard {
                        Text("「${f.excerpt}」", style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(f.workTitle, f.author).joinToString(" · "), color = InkMuted)
                    }
                }
            }
        }
    }
}
