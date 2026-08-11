package com.ainovel.app.ui.analysis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.app.domain.analysis.AnalysisPhase
import com.ainovel.app.ui.settings.ModelSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisRunScreen(
    novelId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: AnalysisRunViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(novelId) {
        if (state.phase == AnalysisPhase.IDLE) viewModel.start(novelId)
    }

    LaunchedEffect(state.completed) {
        if (state.completed) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小说解析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    ModelSwitcher()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                state.message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            AnalysisPhaseRow("章节切分", state.phase, AnalysisPhase.SPLIT_CHAPTERS, "已完成 ${state.chapterCount} 章")
            AnalysisPhaseRow("人物提取", state.phase, AnalysisPhase.CHARACTERS, "分析中…")
            AnalysisPhaseRow("世界观提取", state.phase, AnalysisPhase.WORLDVIEW, "分析中…")
            AnalysisPhaseRow("情节与手法分析", state.phase, AnalysisPhase.PLOT_STYLE, "生成梗概与手法画像")

            Spacer(Modifier.height(16.dp))

            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "解析失败：$error",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.start(novelId) }) {
                            Text("重试")
                        }
                    }
                }
            }

            if (state.lastOutput.isNotBlank() && state.error == null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "当前阶段输出",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        state.lastOutput.take(2000),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.phase == AnalysisPhase.COMPLETED) {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                Text("解析完成，正在返回…", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun AnalysisPhaseRow(
    label: String,
    currentPhase: AnalysisPhase,
    phase: AnalysisPhase,
    detail: String
) {
    val done = currentPhase.ordinal > phase.ordinal || currentPhase == AnalysisPhase.COMPLETED
    val running = currentPhase == phase
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (running) {
            CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
        } else if (done) {
            Text("✓", color = MaterialTheme.colorScheme.primary)
        } else {
            Text("○", color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                done && currentPhase == AnalysisPhase.COMPLETED -> "完成"
                done -> "完成"
                running -> detail
                else -> "待执行"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
