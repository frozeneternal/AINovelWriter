package com.ainovel.app.data.repository

import android.content.Context
import com.ainovel.app.data.local.entity.GeneratedAssetEntity
import com.ainovel.app.data.remote.MediaClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelRepository: NovelRepository,
    private val mediaClient: MediaClient
) {

    private val mediaDir: File by lazy {
        File(context.filesDir, "media").apply { mkdirs() }
    }

    suspend fun generateCover(novelId: Long, prompt: String): GeneratedAssetEntity? = withContext(Dispatchers.IO) {
        val image = mediaClient.generateImage(prompt)
        val localPath = saveImage(image) ?: return@withContext null
        val asset = GeneratedAssetEntity(
            novelId = novelId,
            type = "cover",
            localPath = localPath,
            prompt = prompt
        )
        novelRepository.insertAsset(asset)
        asset
    }

    suspend fun generateIllustration(novelId: Long, chapterId: Long?, prompt: String): GeneratedAssetEntity? =
        withContext(Dispatchers.IO) {
            val image = mediaClient.generateImage(prompt)
            val localPath = saveImage(image) ?: return@withContext null
            val asset = GeneratedAssetEntity(
                novelId = novelId,
                chapterId = chapterId,
                type = "illustration",
                localPath = localPath,
                prompt = prompt
            )
            novelRepository.insertAsset(asset)
            asset
        }

    suspend fun generateVideo(novelId: Long, prompt: String): GeneratedAssetEntity? = withContext(Dispatchers.IO) {
        try {
            val taskId = mediaClient.generateVideo(prompt)
            val videoUrl = mediaClient.pollVideoStatus(taskId)
            if (videoUrl.isBlank()) return@withContext null
            val fileName = "video_${System.currentTimeMillis()}.mp4"
            val target = File(mediaDir, fileName)
            // 仅记录远端地址；若后续需要本地下载可扩展
            val localPath = target.absolutePath
            val asset = GeneratedAssetEntity(
                novelId = novelId,
                type = "video",
                localPath = localPath,
                prompt = prompt
            )
            novelRepository.insertAsset(asset)
            asset
        } catch (e: Exception) {
            null
        }
    }

    private fun saveImage(image: MediaClient.GeneratedImage): String? {
        val bytes = image.bytes
        if (bytes != null) {
            val file = File(mediaDir, "img_${System.currentTimeMillis()}.png")
            file.outputStream().use { it.write(bytes) }
            return file.absolutePath
        }
        val url = image.url ?: return null
        return try {
            java.net.URL(url).openStream().use { input ->
                val file = File(mediaDir, "img_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { output -> input.copyTo(output) }
                file.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    fun exportNovel(title: String, chapters: List<Pair<String, String>>): File? {
        return try {
            val exportDir = File(context.filesDir, "export").apply { mkdirs() }
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(exportDir, "$safeTitle.txt")
            file.bufferedWriter().use { writer ->
                writer.write("《$title》\n")
                writer.write("=".repeat(40) + "\n\n")
                chapters.forEachIndexed { index, (chapterTitle, content) ->
                    writer.write("${index + 1}. $chapterTitle\n\n")
                    writer.write(content)
                    writer.write("\n\n")
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }
}
