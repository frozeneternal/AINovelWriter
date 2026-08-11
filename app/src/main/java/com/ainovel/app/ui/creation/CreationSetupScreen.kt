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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme as M3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationSetupScreen(
    onBack: () -> Unit,
    onStart: (Long) -> Unit,
    viewModel: CreationSetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建小说") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = M3.colorScheme.primaryContainer
                )
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
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("书名") },
                placeholder = { Text("例如：星辰坠落之夜的守护者") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            GenreDropdown(
                genre = state.genre,
                onSelect = viewModel::updateGenre
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.theme,
                onValueChange = viewModel::updateTheme,
                label = { Text("主题 / 核心创意") },
                placeholder = { Text("描述你想表达的故事核心，例如：少年觉醒星火之力对抗宿命") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.style,
                onValueChange = viewModel::updateStyle,
                label = { Text("文风要求") },
                placeholder = { Text("例如：爽文风 / 细腻治愈 / 冷峻史诗") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.chapterCount.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let(viewModel::updateChapterCount)
                },
                label = { Text("章节数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("全自动创作模式", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "开启后自动走完所有专家步骤；关闭则每步由你确认",
                            style = M3.typography.bodySmall,
                            color = M3.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.mode,
                        onCheckedChange = viewModel::updateMode
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            ExpertPipelineCard()

            Spacer(Modifier.height(24.dp))

            state.error?.let {
                Text(
                    it,
                    color = M3.colorScheme.error,
                    style = M3.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = { viewModel.start(onStart) },
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.submitting) "创建中…" else "开始创作")
            }

            if (!state.isConfigValid) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "提示：尚未配置文本 API，请先在设置页完成配置",
                    style = M3.typography.bodySmall,
                    color = M3.colorScheme.error
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreDropdown(
    genre: String,
    onSelect: (String) -> Unit
) {
    val genres = listOf("玄幻", "都市", "科幻", "仙侠", "奇幻", "悬疑", "历史", "言情", "恐怖", "游戏")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = genre,
            onValueChange = {},
            readOnly = true,
            label = { Text("题材") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            genres.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g) },
                    onClick = {
                        onSelect(g)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExpertPipelineCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = M3.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("多专家协作管线", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            PipelineStep(Icons.Filled.WorkspacePremium, "世界观架构师", "构建人物、地理、规则、时间线")
            PipelineStep(Icons.Filled.Route, "大纲规划师", "规划全书结构与每章要点")
            PipelineStep(Icons.Filled.Edit, "章节作者", "展开剧情，创作精彩正文")
            PipelineStep(Icons.Filled.Science, "连续性编辑", "校验设定一致性与逻辑衔接")
            PipelineStep(Icons.Filled.AutoAwesome, "润色编辑", "提升文笔质感与节奏")
        }
    }
}

@Composable
private fun PipelineStep(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = M3.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = M3.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(desc, style = M3.typography.bodySmall, color = M3.colorScheme.onSurfaceVariant)
        }
    }
}
