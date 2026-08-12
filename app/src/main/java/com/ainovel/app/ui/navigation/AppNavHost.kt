package com.ainovel.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ainovel.app.ui.analysis.AnalysisRunScreen
import com.ainovel.app.ui.bookdetail.BookDetailScreen
import com.ainovel.app.ui.bookshelf.BookshelfScreen
import com.ainovel.app.ui.creation.CreationRunScreen
import com.ainovel.app.ui.creation.CreationSetupScreen
import com.ainovel.app.ui.history.HistoryScreen
import com.ainovel.app.ui.importing.ImportScreen
import com.ainovel.app.ui.reading.ReaderScreen
import com.ainovel.app.ui.settings.SettingsScreen
import com.ainovel.app.ui.worldview.WorldviewScreen

object Routes {
    const val BOOKSHELF = "bookshelf"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CREATION_SETUP = "creation_setup"
    const val CREATION_RUN = "creation_run/{novelId}?continuation={continuation}&resume={resume}&direction={direction}&chapters={chapters}&wordCount={wordCount}"
    const val BOOK_DETAIL = "book_detail/{novelId}"
    const val READER = "reader/{novelId}/{chapterIndex}"
    const val WORLDVIEW = "worldview/{novelId}"
    const val IMPORT = "import"
    const val ANALYSIS_RUN = "analysis_run/{novelId}"

    fun creationRun(
        novelId: Long,
        continuation: Boolean = false,
        resume: Boolean = false,
        direction: String = "",
        chapters: Int = 0,
        wordCount: Int = 0
    ) =
        "creation_run/$novelId?continuation=$continuation&resume=$resume&chapters=$chapters&wordCount=$wordCount&direction=" +
            java.net.URLEncoder.encode(direction, "UTF-8")
    fun bookDetail(novelId: Long) = "book_detail/$novelId"
    fun reader(novelId: Long, chapterIndex: Int) = "reader/$novelId/$chapterIndex"
    fun worldview(novelId: Long) = "worldview/$novelId"
    fun analysisRun(novelId: Long) = "analysis_run/$novelId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.BOOKSHELF
    ) {
        composable(Routes.BOOKSHELF) {
            BookshelfScreen(
                onOpenNovel = { id -> navController.navigate(Routes.bookDetail(id)) },
                onNewNovel = { navController.navigate(Routes.CREATION_SETUP) },
                onImportNovel = { navController.navigate(Routes.IMPORT) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onStartAnalysis = { novelId ->
                    navController.navigate(Routes.analysisRun(novelId)) {
                        popUpTo(Routes.BOOKSHELF)
                    }
                }
            )
        }
        composable(
            route = Routes.ANALYSIS_RUN,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType })
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: 0L
            AnalysisRunScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Routes.bookDetail(novelId)) {
                        popUpTo(Routes.BOOKSHELF)
                    }
                }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenNovel = { id -> navController.navigate(Routes.bookDetail(id)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CREATION_SETUP) {
            CreationSetupScreen(
                onBack = { navController.popBackStack() },
                onStart = { novelId, wordCount ->
                    navController.navigate(Routes.creationRun(novelId, wordCount = wordCount)) {
                        popUpTo(Routes.BOOKSHELF)
                    }
                }
            )
        }
        composable(
            route = Routes.CREATION_RUN,
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("continuation") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("resume") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("direction") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("chapters") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("wordCount") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: 0L
            val isContinuation = entry.arguments?.getBoolean("continuation") ?: false
            val resume = entry.arguments?.getBoolean("resume") ?: false
            val direction = entry.arguments?.getString("direction") ?: ""
            val chapters = entry.arguments?.getInt("chapters") ?: 0
            val wordCount = entry.arguments?.getInt("wordCount") ?: 0
            CreationRunScreen(
                novelId = novelId,
                isContinuation = isContinuation,
                resume = resume,
                direction = direction,
                chapters = chapters,
                wordCount = wordCount,
                onBack = { navController.popBackStack() },
                onOpenNovel = { id ->
                    navController.navigate(Routes.bookDetail(id)) {
                        popUpTo(Routes.BOOKSHELF)
                    }
                }
            )
        }
        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType })
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: 0L
            BookDetailScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() },
                onOpenReader = { chapterIndex ->
                    navController.navigate(Routes.reader(novelId, chapterIndex))
                },
                onOpenWorldview = { navController.navigate(Routes.worldview(novelId)) },
                onStartCreation = { direction, wordCount ->
                    navController.navigate(Routes.creationRun(novelId, direction = direction, wordCount = wordCount))
                },
                onStartContinuation = { direction, chapters, wordCount ->
                    navController.navigate(
                        Routes.creationRun(
                            novelId,
                            continuation = true,
                            direction = direction,
                            chapters = chapters,
                            wordCount = wordCount
                        )
                    )
                },
                onResumeCreation = {
                    navController.navigate(Routes.creationRun(novelId, resume = true))
                }
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("chapterIndex") { type = NavType.IntType }
            )
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: 0L
            val chapterIndex = entry.arguments?.getInt("chapterIndex") ?: 0
            ReaderScreen(
                novelId = novelId,
                startIndex = chapterIndex,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.WORLDVIEW,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType })
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: 0L
            WorldviewScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
