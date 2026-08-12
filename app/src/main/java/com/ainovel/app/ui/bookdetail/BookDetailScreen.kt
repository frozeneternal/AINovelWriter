package com.ainovel.app.ui.bookdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ainovel.app.data.local.entity.ChapterEntity
import com.ainovel.app.domain.model.NovelSource
import com.ainovel.app.domain.model.NovelStatus
import com.ainovel.app.ui.settings.ModelSwitcher
import com.ainovel.app.ui.creation.WordCountDropdown
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    novelId: Long,
    onBack: () -> Unit,
    onOpenReader: (Int) -> Unit,
    onOpenWorldview: () -> Unit,
    onStartCreation: (String, Int) -> Unit,
    onStartContinuation: (String, Int, Int) -> Unit,
    onResumeCreation: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val novel = uiState.novel
    var showDirectionDialog by remember { mutableStateOf(false) }
    var directionInput by remember { mutableStateOf("") }
    var pendingIsContinuation by remember { mutableStateOf(false) }
    var continuationChapters by remember { mutableStateOf(5) }
    var wordCountInput by remember { mutableStateOf(0) }

    LaunchedEffect(novelId) {
        viewModel.init(novelId)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(novel?.title ?: "书籍详情") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            novel?.let { n ->
                Row(modifier = Modifier.padding(16.dp)) {
                    if (n.coverPath != null) {
                        AsyncImage(
                            model = File(n.coverPath),
                            contentDescription = n.title,
                            modifier = Modifier
                                .width(110.dp)
                                .height(150.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(150.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                n.title.take(1).ifBlank { "书" },
                                fontSize = 42.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(n.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(n.genre.ifBlank { "未分类" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(n.synopsis, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                        Spacer(Modifier.height(8.dp))
                        Text(statusLabel(n.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (n.status == NovelStatus.WRITING) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.creationPaused) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (uiState.creationPaused) "后台创作已暂停" else "正在后台创作中",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "已完成 ${n.currentChapterIndex}/${n.totalChapters} 章，可离开本页继续生成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.creationPaused) {
                                TextButton(
                                    onClick = {
                                        viewModel.resumeCreation()
                                        onResumeCreation()
                                    }
                                ) {
                                    Text("继续")
                                }
                            } else {
                                TextButton(onClick = viewModel::pauseCreation) {
                                    Text("暂停")
                                }
                            }
                            IconButton(onClick = onResumeCreation) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "查看进度")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (n.source == NovelSource.IMPORTED) {
                        Button(
                            onClick = {
                                pendingIsContinuation = true
                                continuationChapters = 5
                                wordCountInput = 0
                                directionInput = ""
                                showDirectionDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("按原作手法续写")
                        }
                        OutlinedButton(
                            onClick = onOpenWorldview,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("解析档案")
                        }
                    } else {
                        Button(
                            onClick = {
                                pendingIsContinuation = false
                                wordCountInput = 0
                                directionInput = ""
                                showDirectionDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("续写/创作")
                        }
                        OutlinedButton(
                            onClick = onOpenWorldview,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("世界观")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::generateCover,
                        enabled = !uiState.generatingCover,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.generatingCover) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("生成封面")
                    }
                    OutlinedButton(
                        onClick = viewModel::generateVideo,
                        enabled = !uiState.generatingVideo,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.generatingVideo) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("宣传视频")
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "目录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))

                if (uiState.chapters.isEmpty()) {
                    Text(
                        "暂无章节，点击「续写/创作」开始生成",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.chapters.forEach { chapter ->
                        ChapterRow(chapter, onClick = { onOpenReader(chapter.indexInNovel) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            } ?: run {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showDirectionDialog) {
        AlertDialog(
            onDismissRequest = { showDirectionDialog = false },
            title = { Text(if (pendingIsContinuation) "续写设置" else "创作设置") },
            text = {
                Column {
                    Text(
                        if (pendingIsContinuation) {
                            "你希望接下来的剧情往什么方向发展？留空则按原作者风格与情节走向续写。"
                        } else {
                            "你希望后续剧情往什么方向发展？留空则按当前大纲继续创作。"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = directionInput,
                        onValueChange = { directionInput = it },
                        placeholder = { Text("例如：主角解开身世之谜后向帝都进发，遇见新对手…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    if (pendingIsContinuation) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = continuationChapters.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { v -> continuationChapters = v.coerceIn(1, 100) }
                            },
                            label = { Text("续写章节数") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    WordCountDropdown(
                        wordCount = wordCountInput,
                        onSelect = { wordCountInput = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDirectionDialog = false
                        val direction = directionInput.trim()
                        if (pendingIsContinuation) {
                            onStartContinuation(direction, continuationChapters, wordCountInput)
                        } else {
                            onStartCreation(direction, wordCountInput)
                        }
                    }
                ) {
                    Text(if (pendingIsContinuation) "开始续写" else "开始创作")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ChapterRow(chapter: ChapterEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                chapter.indexInNovel.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(chapter.title.ifBlank { "第 ${chapter.indexInNovel} 章" }, style = MaterialTheme.typography.bodyLarge)
                Row {
                    if (chapter.summary != null) {
                        Text(
                            chapter.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (chapter.content.isNotBlank()) {
                        Text(
                            " · ${chapter.content.length} 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: NovelStatus): String = when (status) {
    NovelStatus.DRAFT -> "草稿"
    NovelStatus.WORLDVIEW_DONE -> "世界观完成"
    NovelStatus.OUTLINED -> "大纲完成"
    NovelStatus.WRITING -> "创作中"
    NovelStatus.COMPLETED -> "已完成"
}
