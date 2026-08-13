package com.ainovel.app.ui.recentlydeleted

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainovel.app.data.local.entity.NovelEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    onBack: () -> Unit,
    viewModel: RecentlyDeletedViewModel = hiltViewModel()
) {
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    var novelToPurge by remember { mutableStateOf<NovelEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近删除") },
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
        if (novels.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.padding(top = 12.dp))
                    Text(
                        "最近删除为空",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        "删除的书籍会出现在这里，可随时恢复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(novels, key = { it.id }) { novel ->
                    DeletedNovelCard(
                        novel = novel,
                        onRestore = { viewModel.restore(novel.id) },
                        onPurge = { novelToPurge = novel }
                    )
                }
            }
        }
    }

    novelToPurge?.let { novel ->
        AlertDialog(
            onDismissRequest = { novelToPurge = null },
            title = { Text("彻底删除") },
            text = { Text("确定彻底删除《${novel.title}》吗？该书及其全部章节、世界观、大纲等数据将永久清除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purge(novel.id)
                    novelToPurge = null
                }) {
                    Text("彻底删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { novelToPurge = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DeletedNovelCard(
    novel: NovelEntity,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    novel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    "删除于 ${formatTime(novel.deletedAt ?: novel.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Filled.Restore, contentDescription = "恢复")
            }
            IconButton(onClick = onPurge) {
                Icon(Icons.Filled.Delete, contentDescription = "彻底删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
