package com.scarlett.juzizhentan.ui.training

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.data.model.TrainingLevel
import com.scarlett.juzizhentan.ui.common.*
import com.scarlett.juzizhentan.ui.theme.*

private val techOptions = listOf("以小见大", "反差对照", "意象比喻", "节奏递进", "留白克制", "点面结合")

@Composable
fun TrainingScreen(
    vm: TrainingViewModel,
    onBack: () -> Unit,
    onSubmit: (TrainingLevel, Map<String, String>, String?, String?) -> Unit
) {
    val state = vm.state
    Scaffold(topBar = { JuziTopBar("素材拼装", onBack) }) { padding ->
        when {
            state.choosing -> GoalPicker(Modifier.padding(padding), vm)
            state.loading -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingBox("AI 正在生成新关卡…") }
            state.error != null -> Column(Modifier.padding(padding).padding(16.dp)) {
                ErrorBox(state.error!!) { vm.generate() }
            }
            state.level != null -> AssembleBoard(
                modifier = Modifier.padding(padding),
                vm = vm,
                onSubmit = {
                    val level = state.level!!
                    onSubmit(level, state.selections, state.seed, state.technique)
                }
            )
        }
    }
}

@Composable
private fun GoalPicker(modifier: Modifier, vm: TrainingViewModel) {
    val state = vm.state
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("选择训练方向（可跳过直接随机）", style = MaterialTheme.typography.titleMedium)
        }
        item {
            SectionLabel("写作技巧")
            techOptions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        FilterChip(
                            selected = state.technique == t,
                            onClick = { vm.setTechnique(if (state.technique == t) null else t) },
                            label = { Text(t) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        item {
            SectionLabel("难度")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("easy" to "容易", "medium" to "适中", "hard" to "进阶").forEach { (k, label) ->
                    FilterChip(
                        selected = state.difficulty == k,
                        onClick = { vm.setDifficulty(k) },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            Button(
                onClick = { vm.start() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("生成关卡") }
            TextButton(onClick = { vm.setTechnique(null); vm.start() }, modifier = Modifier.fillMaxWidth()) {
                Text("随机训练")
            }
        }
    }
}

@Composable
private fun AssembleBoard(
    modifier: Modifier,
    vm: TrainingViewModel,
    onSubmit: () -> Unit
) {
    val state = vm.state
    val level = state.level ?: return
    val slot = level.slots.getOrNull(state.slotIndex) ?: return

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(level.theme, style = MaterialTheme.typography.titleLarge)
        Text(level.training_goal, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text("情境：${level.scenario}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "步骤 ${state.slotIndex + 1}/${level.slots.size} · ${slot.label}",
            style = MaterialTheme.typography.titleMedium,
            color = AccentGreen
        )
        if (slot.role.isNotBlank()) Text(slot.role, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(slot.options) { opt ->
                val selected = state.selections[slot.id] == opt.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) AccentRed else CardStroke,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { vm.select(slot.id, opt.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) PaperDark else ColorWhiteSoft
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(opt.text, style = MaterialTheme.typography.bodyLarge)
                        if (opt.function.isNotBlank()) {
                            Text(opt.function, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                SectionLabel("当前组合预览")
                Text(vm.previewText().ifBlank { "（尚未选择）" }, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.prevSlot() }, enabled = state.slotIndex > 0, modifier = Modifier.weight(1f)) {
                Text("上一槽")
            }
            if (state.slotIndex < level.slots.lastIndex) {
                Button(onClick = { vm.nextSlot() }, modifier = Modifier.weight(1f)) { Text("下一槽") }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = vm.allSelected(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text("提交讲评") }
            }
        }
        TextButton(onClick = { vm.generate() }, modifier = Modifier.fillMaxWidth()) { Text("重新生成关卡") }
    }
}

private val ColorWhiteSoft = androidx.compose.ui.graphics.Color(0xFFFBF8F3)
