package com.ainovel.app

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
