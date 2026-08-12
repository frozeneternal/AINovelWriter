package com.ainovel.app.ui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    novelId: Long,
    startIndex: Int,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(novelId) {
        viewModel.init(novelId, startIndex)
    }

    LaunchedEffect(uiState.snackbar) {
        uiState.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val currentChapter = uiState.chapters.getOrNull(uiState.currentIndex)
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.currentIndex) {
        if (currentChapter != null) {
            scrollState.scrollTo(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentChapter?.title?.ifBlank { "第 ${uiState.currentIndex + 1} 章" } ?: "阅读") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (currentChapter != null && !uiState.editing) {
                        IconButton(onClick = viewModel::startEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(
                            onClick = viewModel::generateIllustration,
                            enabled = !uiState.generatingIllustration
                        ) {
                            if (uiState.generatingIllustration) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Image, contentDescription = "生成插画")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.editing -> {
                EditChapterContent(
                    content = uiState.draftContent,
                    onContentChange = viewModel::updateDraft,
                    onSave = viewModel::saveEdit,
                    onCancel = viewModel::cancelEdit,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                )
            }
            currentChapter != null -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            currentChapter.content,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            fontSize = 17.sp,
                            lineHeight = 30.sp
                        )
                    }

                    Text(
                        "本章 ${currentChapter.content.length} 字",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    ChapterNavigationBar(
                        currentIndex = uiState.currentIndex,
                        total = uiState.chapters.size,
                        onPrev = { viewModel.navigateTo(uiState.currentIndex - 1) },
                        onNext = { viewModel.navigateTo(uiState.currentIndex + 1) }
                    )
                }
            }
            else -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ChapterNavigationBar(
    currentIndex: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = currentIndex > 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一章", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("上一章")
        }
        Text(
            "${currentIndex + 1} / $total",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onNext, enabled = currentIndex < total - 1) {
            Text("下一章")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一章", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EditChapterContent(
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("取消")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSave) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
        }
    }
}
