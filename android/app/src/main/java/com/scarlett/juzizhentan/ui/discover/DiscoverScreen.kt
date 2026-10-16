package com.scarlett.juzizhentan.ui.discover

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.data.model.QuoteCard
import com.scarlett.juzizhentan.ui.common.*
import com.scarlett.juzizhentan.ui.theme.AccentRed
import com.scarlett.juzizhentan.ui.theme.InkMuted

private val themes = listOf("孤独", "命运", "成长", "爱情", "时代")
private val techniques = listOf("以小见大", "反差对照", "意象比喻", "留白", "点面结合")

@Composable
fun DiscoverScreen(
    vm: DiscoverViewModel,
    onBack: () -> Unit,
    onAnalyze: (QuoteCard) -> Unit,
    onTrain: (QuoteCard) -> Unit
) {
    val state = vm.state
    val ctx = LocalContext.current

    Scaffold(topBar = { JuziTopBar("金句发现", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("作品 / 作者 / 主题 / 技巧（AI 联网发现）") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.search() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text("搜索") }
            }
            item {
                SectionLabel("主题")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(themes) { t ->
                        AssistChip(onClick = { vm.onQueryChange(t); vm.search(theme = t) }, label = { Text(t) })
                    }
                }
            }
            item {
                SectionLabel("写作技巧")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(techniques) { t ->
                        AssistChip(onClick = { vm.onQueryChange(t); vm.search(technique = t) }, label = { Text(t) })
                    }
                }
            }
            when {
                state.loading -> item { LoadingBox("AI 正在联网发现金句…") }
                state.error != null -> item { ErrorBox(state.error!!) { vm.search() } }
                else -> {
                    if (!state.message.isNullOrBlank()) {
                        item { Text(state.message!!, color = InkMuted) }
                    }
                    items(state.results) { card ->
                        QuoteResultCard(
                            card = card,
                            onAnalyze = { onAnalyze(card) },
                            onTrain = { onTrain(card) },
                            onFavorite = { vm.toggleFavorite(card) },
                            onOpenSource = {
                                card.source_url?.let { url ->
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun QuoteResultCard(
    card: QuoteCard,
    onAnalyze: () -> Unit,
    onTrain: () -> Unit,
    onFavorite: () -> Unit,
    onOpenSource: () -> Unit
) {
    PaperCard {
        Text("「${card.excerpt}」", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        val meta = listOfNotNull(card.work_title, card.author, card.source_title).distinct().joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerificationChip(card.verificationEnum())
            card.technique_tags.take(3).forEach { tag ->
                AssistChip(onClick = {}, label = { Text(tag) })
            }
        }
        if (card.recommend_reason.isNotBlank()) {
            Text(card.recommend_reason, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
        card.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = InkMuted) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAnalyze) { Text("解剖") }
            OutlinedButton(onClick = onTrain) { Text("训练") }
            TextButton(onClick = onFavorite) { Text("收藏") }
            if (!card.source_url.isNullOrBlank()) {
                TextButton(onClick = onOpenSource) { Text("来源") }
            }
        }
    }
}
