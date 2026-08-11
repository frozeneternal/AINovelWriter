package com.ainovel.app.ui.worldview

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class WorldviewTab(val label: String) {
    CHARACTERS("人物设定"),
    WORLD("世界观"),
    PLOT("情节梗概"),
    STYLE("手法画像")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldviewScreen(
    novelId: Long,
    onBack: () -> Unit,
    viewModel: WorldviewViewModel = hiltViewModel()
) {
    val worldview by viewModel.worldview.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(WorldviewTab.CHARACTERS) }

    LaunchedEffect(novelId) {
        viewModel.init(novelId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解析档案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        val wv = worldview
        if (wv == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无解析档案，请先导入小说并完成解析",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    WorldviewTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.label) }
                        )
                    }
                }
                when (tab) {
                    WorldviewTab.CHARACTERS -> WorldviewSectionList(wv.characters)
                    WorldviewTab.WORLD -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        WorldviewSection("地理设定", wv.geography)
                        WorldviewSection("规则体系", wv.rules)
                        WorldviewSection("时间线", wv.timeline)
                        Spacer(Modifier.height(24.dp))
                    }
                    WorldviewTab.PLOT -> WorldviewSectionList(wv.plotSummary)
                    WorldviewTab.STYLE -> StyleProfileEditor(
                        initialText = wv.styleProfile,
                        saving = saving,
                        onSave = { viewModel.saveStyleProfile(novelId, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldviewSectionList(content: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (content.isBlank()) {
            Text("暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WorldviewSection("", content)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun WorldviewSection(title: String, content: String) {
    if (content.isBlank()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun StyleProfileEditor(
    initialText: String,
    saving: Boolean,
    onSave: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "手法画像用于续写时严格模仿原作者。可直接修改后保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("写作手法画像") },
            minLines = 10
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onSave(text) }, enabled = !saving) {
                Text(if (saving) "保存中…" else "保存手法画像")
            }
            Spacer(Modifier.width(12.dp))
        }
    }
}
