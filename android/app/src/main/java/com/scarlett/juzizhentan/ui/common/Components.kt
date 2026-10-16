package com.scarlett.juzizhentan.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scarlett.juzizhentan.data.model.VerificationStatus
import com.scarlett.juzizhentan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuziTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper, titleContentColor = Ink)
    )
}

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardStroke, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun VerificationChip(status: VerificationStatus) {
    val (label, color) = when (status) {
        VerificationStatus.VERIFIED -> "已核实" to AccentGreen
        VerificationStatus.PARTIAL -> "部分核实" to BadgePartial
        VerificationStatus.UNVERIFIED -> "待核实" to BadgeUnverified
        VerificationStatus.AI_ORIGINAL -> "AI 原创" to AccentRed
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color)
    )
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    PaperCard {
        Text("出了点问题", style = MaterialTheme.typography.titleMedium, color = AccentRed)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
fun LoadingBox(text: String = "正在加载…") {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentRed)
            Spacer(Modifier.height(12.dp))
            Text(text, color = InkMuted)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AccentGreen,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
