package com.ainovel.app.ui.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.app.domain.agent.PipelinePhase
import com.ainovel.app.ui.settings.ModelSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationRunScreen(
    novelId: Long,
    isContinuation: Boolean = false,
    resume: Boolean = false,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    viewModel: CreationRunViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val phaseLabel by viewModel.phaseLabel.collectAsStateWithLifecycle()

    LaunchedEffect(novelId) {
        viewModel.startIfNeeded(novelId, isContinuation, resume)
    }

    val isRunning = state.phase == PipelinePhase.WORLDVIEW ||
        state.phase == PipelinePhase.OUTLINE ||
        state.phase == PipelinePhase.WRITE_CHAPTER ||
        state.phase == PipelinePhase.CONTINUITY_CHECK ||
        state.phase == PipelinePhase.POLISH

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创作中") },
                navigationIcon = {
                    if (state.phase != PipelinePhase.COMPLETED && state.phase != PipelinePhase.FAILED) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        phaseLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    if (state.totalChapters > 0) {
                        LinearProgressIndicator(
                            progress = {
                                val total = state.totalChapters.toFloat()
                                if (total <= 0f) 0f else state.chapterIndex / total
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "章节进度：${state.chapterIndex} / ${state.totalChapters}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "创作在后台持续进行中，返回后仍可查看已生成的章节",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            state.currentAgent?.let { agent ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("当前专家", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(agent.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            when {
                state.phase == PipelinePhase.COMPLETED -> {
                    Spacer(Modifier.height(24.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(state.message.ifBlank { "创作完成！" }, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { onOpenNovel(novelId) }) {
                            Text("查看作品")
                        }
                    }
                }
                state.phase == PipelinePhase.FAILED -> {
                    Spacer(Modifier.height(24.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("创作失败", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("返回")
                        }
                    }
                }
                state.phase == PipelinePhase.CANCELLED -> {
                    Spacer(Modifier.height(24.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("已停止生成", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("返回书架")
                        }
                    }
                }
                isRunning -> {
                    Spacer(Modifier.height(16.dp))
                    if (state.streamingText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                state.streamingText,
                                modifier = Modifier.padding(16.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = viewModel::stop,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("停止生成")
                        }
                    }
                }
                else -> {
                    Spacer(Modifier.height(24.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("准备中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
