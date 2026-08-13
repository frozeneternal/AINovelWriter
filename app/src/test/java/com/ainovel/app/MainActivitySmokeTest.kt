package com.ainovel.app

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HiltTestApplication::class)
class MainActivitySmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var novelRepository: com.ainovel.app.data.repository.NovelRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun appLaunches_toBookshelf() {
        composeRule.onNodeWithText("我的书架").assertIsDisplayed()
    }

    @Test
    fun importButton_navigatesToImportScreen() {
        composeRule.onNodeWithText("导入小说").performClick()
        composeRule.onNodeWithText("支持导入 TXT / Markdown 文本小说").assertIsDisplayed()
    }

    @Test
    fun fullImportFlow_selectedFile_reachesImportStart() {
        val context = composeRule.activity
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(
                Uri.parse("content://com.example/novel.txt"),
                "第一章\n\n在一个遥远的王国里，有一位年轻的骑士。\n\n第二章\n\n骑士踏上了冒险的旅程。".byteInputStream()
            )

        composeRule.onNodeWithText("导入小说").performClick()
        composeRule.onNodeWithText("选择小说文件").assertIsDisplayed()
    }

    @Test
    fun settingsButton_navigatesToSettings() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun newNovelButton_navigatesToCreationSetup() {
        composeRule.onNodeWithText("新建小说").performClick()
        composeRule.onNodeWithText("书名").assertIsDisplayed()
    }

    @Test
    fun nonEmptyBookshelf_keepsImportAndNewButtons() {
        runBlocking {
            novelRepository.importNovel("已上传小说.txt", "第一章\n\n内容")
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("我的书架").assertIsDisplayed()
        composeRule.onNodeWithText("导入小说").assertIsDisplayed()
        composeRule.onNodeWithText("新建小说").assertIsDisplayed()
        composeRule.onNodeWithText("已上传小说").assertIsDisplayed()
    }

    @Test
    fun backFromImport_returnsToBookshelf() {
        composeRule.onNodeWithText("导入小说").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("我的书架").assertIsDisplayed()
    }

    @Test
    fun bookDetail_importedNovel_showsContinuationEntries() {
        runBlocking {
            novelRepository.importNovel("书架书.txt", "第一章\n\n正文内容")
        }
        // 书架列表异步刷新，等待书卡片出现
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("书架书").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("书架书").performClick()
        // 导入的书展示"按原作手法续写"+"解析档案"，而非"续写/创作"
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("按原作手法续写").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("按原作手法续写").assertIsDisplayed()
        composeRule.onNodeWithText("解析档案").assertIsDisplayed()
    }

    @Test
    fun bookDetail_opensReader_fromChapterRow() {
        val id = runBlocking {
            val novelId = novelRepository.importNovel("可读书.txt", "第一章\n\n正文内容")
            novelRepository.saveImportedChapters(
                novelId,
                listOf(1 to ("第一章" to "这是第一章的正文内容，足够长以便阅读。"))
            )
            novelId
        }
        composeRule.waitForIdle()

        // 书架列表异步刷新，等待书卡片出现
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("可读书").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("可读书").performClick()
        composeRule.waitForIdle()

        // 章节行可能在可视区下方，先滚动到可见再点击
        composeRule.onNodeWithText("第一章").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 进入阅读页，翻章控件可见（导航为异步，等待其出现）
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("上一章").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("上一章").assertIsDisplayed()
        composeRule.onNodeWithText("下一章").assertIsDisplayed()
    }
}
