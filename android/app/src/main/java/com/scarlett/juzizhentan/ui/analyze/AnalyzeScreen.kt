package com.scarlett.juzizhentan.ui.analyze

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.ui.common.*
import com.scarlett.juzizhentan.ui.theme.AccentRed
import com.scarlett.juzizhentan.ui.theme.InkMuted

@Composable
fun AnalyzeScreen(
    vm: AnalyzeViewModel,
    onBack: () -> Unit,
    onTrain: (String) -> Unit
) {
    val state = vm.state
    val card = state.card

    Scaffold(topBar = { JuziTopBar("名句解剖", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (card == null) {
                item { ErrorBox("无法解析金句数据") }
                return@LazyColumn
            }
            item {
                PaperCard {
                    Text("「${card.excerpt}」", style = MaterialTheme.typography.bodyLarge)
                    val meta = listOfNotNull(card.work_title, card.author).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, color = InkMuted)
                    VerificationChip(card.verificationEnum())
                }
            }
            when {
                state.loading -> item { LoadingBox("AI 正在分层分析…") }
                state.error != null -> item { ErrorBox(state.error!!) { vm.load() } }
                state.result != null -> {
                    val r = state.result!!
                    items(r.dimensions) { dim ->
                        PaperCard {
                            Text(dim.title, style = MaterialTheme.typography.titleMedium)
                            Text("确信度：${dim.certainty}", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(dim.content, style = MaterialTheme.typography.bodyLarge)
                            dim.evidence?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("依据：$it", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (r.transferable_mechanism.isNotBlank()) {
                        item {
                            PaperCard {
                                SectionLabel("可迁移机制")
                                Text(r.transferable_mechanism, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { vm.toggleSimplified() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.showSimplified) "收起删减对比" else "如果删掉这部分会怎样")
                        }
                    }
                    if (state.showSimplified) {
                        item {
                            PaperCard {
                                SectionLabel("简化版")
                                Text(r.simplified_version ?: "（无）", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(8.dp))
                                SectionLabel("差异说明")
                                Text(r.simplified_diff ?: "（无）", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { onTrain(card.excerpt) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) { Text("用这句话训练我") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
