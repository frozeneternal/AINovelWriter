# AI Novel Writer 实施任务清单

## 1. 工程骨架

- [x] 1.1 创建 Gradle 工程结构（settings.gradle.kts, build.gradle.kts, gradle 配置, AndroidManifest）
- [x] 1.2 配置依赖（Compose, Room, Hilt, Retrofit/OkHttp, Coil, DataStore, kotlinx-serialization）
- [x] 1.3 创建基础包结构与 Application 类、主题资源

## 2. 数据层

- [x] 2.1 定义 Room 实体（Novel, Chapter, Worldview, Outline, HistoryRecord, GeneratedAsset, ChatMessage）
- [x] 2.2 定义 DAO 接口与数据库类
- [x] 2.3 定义加密配置存储（Keystore + SharedPreferences）
- [x] 2.4 实现 Repository 层（NovelRepository, HistoryRepository, SettingRepository, AssetRepository）

## 3. 领域层：多智能体编排

- [x] 3.1 定义专家角色模型与提示词模板（世界观架构师、大纲规划师、章节作者、连续性编辑、润色编辑）
- [x] 3.2 实现上下文管理器（上下文组装、token 估算、摘要压缩）
- [x] 3.3 实现创作会话状态机（IDLE/RUNNING/WAITING_CONFIRM）
- [x] 3.4 实现 Agent 编排引擎（管线执行、流式事件、取消）

## 4. LLM 客户端

- [x] 4.1 实现 OpenAI 兼容 Chat Completions 客户端（流式+非流式）
- [x] 4.2 实现图片/视频生成客户端与错误映射
- [x] 4.3 实现 API 配置管理与连接测试

## 5. UI 层

- [x] 5.1 书架页面（书籍卡片网格、创建书籍入口）
- [x] 5.2 书籍详情页（信息展示、封面、目录、生成封面/视频入口）
- [x] 5.3 创作配置页（题材、风格、章节数、专家管线确认模式）
- [x] 5.4 创作运行页（管线进度、专家角色状态、流式内容展示、停止/重试）
- [x] 5.5 章节阅读页（阅读、编辑、生成配图、生成下一章）
- [x] 5.6 历史记录页（记录列表、详情、继续创作）
- [x] 5.7 设置页（API 配置、测试连接、模型管理）
- [x] 5.8 世界观设定展示页（人物/地理/规则/时间线）

## 6. 测试与验证

- [x] 6.1 单元测试（上下文组装、token 估算、错误映射、状态机）
- [x] 6.2 Repository 测试（Room 内存库 CRUD 与级联删除）
- [x] 6.3 集成测试（MockWebServer 模拟 OpenAI API）
- [x] 6.4 命令行编译与单测验证（kotlinc 2.0.21 编译 domain/data/remote/test，37 用例全部通过）
- [x] 6.5 Android Studio 完整构建验证（`/opt/gradle-8.9/bin/gradle assembleDebug --no-daemon`，BUILD SUCCESSFUL，产出 app-debug.apk 20M，包名 com.ainovel.app，minSdk 26/targetSdk 35）

## 7. 小说导入与原作者手法续写（见 specs/novel-import-continuation/）

- [x] 7.1 数据层：NovelSource 枚举、ImportedTextEntity 表、WorldviewEntity 新增 plotSummary/styleProfile、DB 迁移 1→2、DAO/Repository 扩展
- [x] 7.2 解析层：AnalysisPhase/AnalysisSession/AnalysisEvent、ChapterSplitter 规则切分、3 个解析专家提示词、NovelAnalyzer 四阶段串联
- [x] 7.3 续写管线：PipelineRequest 扩展（styleProfile/plotSummary/skipSetup/existingWorldview/existingChapters）、ContextManager 注入手法指令、NovelCreationUseCase.runContinuation
- [x] 7.4 UI：ImportScreen、AnalysisRunScreen、WorldviewScreen 四页签+手法画像可编辑、BookDetail 续写入口、AppNavHost 路由
- [x] 7.5 测试：ChapterSplitterTest、NovelAnalyzerTest、续写 skipSetup 路径测试（37 用例通过）
- [x] 7.6 Android Studio 构建验证（assembleDebug 成功，Hilt DI 采用 ConfigProvider 包装类解决 suspend lambda 键问题；AssetRepository 生成函数返回实体而非 Long）
