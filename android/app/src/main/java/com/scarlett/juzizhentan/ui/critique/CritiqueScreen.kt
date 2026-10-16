package com.scarlett.juzizhentan.ui.critique

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.ui.common.*
import com.scarlett.juzizhentan.ui.theme.AccentRed
import com.scarlett.juzizhentan.ui.theme.InkMuted

@Composable
fun CritiqueScreen(
    vm: CritiqueViewModel,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit
) {
    val state = vm.state
    Scaffold(topBar = { JuziTopBar("AI 讲评", onBack) }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingBox("AI 正在针对你的选择讲评…") }
            state.error != null -> Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorBox(state.error!!) { vm.load() }
            }
            state.result != null -> {
                val r = state.result!!
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PaperCard {
                            SectionLabel("你的组合")
                            Text(r.combined_text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    item {
                        PaperCard {
                            SectionLabel("整体效果")
                            Text(r.overall_effect, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (r.strengths.isNotEmpty()) {
                        item {
                            PaperCard {
                                SectionLabel("优点")
                                r.strengths.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                    if (r.problems.isNotEmpty()) {
                        item {
                            PaperCard {
                                SectionLabel("问题")
                                r.problems.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                    if (r.slot_comments.isNotEmpty()) {
                        item {
                            PaperCard {
                                SectionLabel("各素材作用")
                                r.slot_comments.forEach { c ->
                                    Text(c.comment ?: "", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    if (r.swap_suggestions.isNotEmpty()) {
                        item {
                            PaperCard {
                                SectionLabel("可替换建议")
                                r.swap_suggestions.forEach { Text("· $it", color = InkMuted) }
                            }
                        }
                    }
                    if (r.optimized_combination.isNotBlank()) {
                        item {
                            PaperCard {
                                SectionLabel("优化组合")
                                Text(r.optimized_combination, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    r.mechanism_contrast?.takeIf { it.isNotBlank() }?.let { mc ->
                        item {
                            PaperCard {
                                SectionLabel("机制对照")
                                Text(mc, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                        ) { Text("重新训练本关") }
                        OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("进入下一关（随机）") }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
