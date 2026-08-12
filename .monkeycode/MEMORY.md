# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-08-10
- Context: Discovered by Agent while validating domain layer with standalone kotlinc (no Android SDK in devbox)
- Category: Build Methods
- Instructions:
  - 本机无 Android SDK，无法跑 ./gradlew 完整构建；改用 /tmp/ktverify 下的 kotlinc 2.0.21 做命令行编译验证
  - 验证命令必须用 `-Xplugin=kotlinc/lib/kotlinx-serialization-compiler-plugin.jar` 加载序列化插件，用 `-classpath` 指向下载的 jar 目录，不能同时放入两个 kotlinx-coroutines jar（如 -jvm 与 multiplatform 版），否则 Flow/serialization 出现"unresolved reference 'kotlinx'"类环境性误报
  - kotlinx-serialization-core/json、kotlinx-coroutines-core、junit、truth、okhttp、okio、mockwebserver、coroutines-test、atomicfu 已下载到 /tmp/ktverify/lib；测试用 `java -cp out:... org.junit.runner.JUnitCore <TestClass>` 运行
  - 单元测试注意：Truth 1.4.4 的 IterableSubject 没有 anyMatch，用 `assertThat(list.any {...}).isTrue()`；byte[] 比较用 `map { it.toInt() }` 再 containsExactly
  - 测试根目录在 /workspace/app/src/test/java/com/ainovel/app/，共 6 个测试类 26 个用例，运行前先 kotlinc 编译 domain + data/remote + test 到 out/

[Project Knowledge Summary]
- Date: 2026-08-11
- Context: Discovered by Agent while completing full Android APK build with /opt/gradle-8.9
- Category: Build Methods
- Instructions:
  - 本工程构建必须用 `/opt/gradle-8.9/bin/gradle assembleDebug --no-daemon`（无 gradlew 脚本；no-daemon 避免 "Unexpected type tag 22 found" socket 错误），日志重定向到 /tmp/buildN.log
  - 版本锁定成熟组合：AGP 8.7.3、Kotlin 2.0.21、Hilt 2.52、Room 2.6.1、Compose BOM 2024.12.01、JDK 17；KSP 在此环境全部失败（KSP1 NPE 于 Hilt BindsModule、KSP2 缺 kotlin.reflect），必须用 KAPT（kotlin-kapt + `kapt { correctErrorTypes = true }`）
  - Hilt/KAPT 不支持 `suspend () -> ApiConfig` 作 DI 键（生成 Function1 通配符致 MissingBinding）；用 `fun interface ConfigProvider { suspend fun get(): ApiConfig }` 包装，测试尾随 lambda 仍可 SAM 转换，无需改测试
  - suspend DAO 返回 Long 时，Repository 返回实体需要先构建 entity 再 insertAsset，不能把 Long 作为 withContext lambda 末表达式，否则类型不匹配

[Project Knowledge Summary]
- Date: 2026-08-11
- Context: Discovered by Agent while diagnosing "导入小说直接闪退" crash on device
- Category: Troubleshooting & Debugging
- Instructions:
  - 包名含 Java 保留关键字会导致 Hilt 静默失败：Kotlin 编译器容忍 `com.ainovel.app.ui.import` 这类包名，但 Hilt 注解处理器生成 Java 源码时遇到 `import` 关键字无法生成 ViewModel 的 Factory/HiltModules，运行进该页面时 hiltViewModel() 抛 "Cannot create an instance"，即真机闪退。此类问题用 `ls app/build/generated/source/kapt/debug/<pkg>` 对比生成文件可快速定位。包名改用非关键字（ui/importing）即修复
  - Robolectric 不支持 AndroidKeyStore：`KeyStore.getInstance("AndroidKeyStore")` 抛 KeyStoreException。CryptoManager 已加降级路径（AndroidKeyStore 可用则用系统密钥库，否则 lazy 随机 AES 密钥），真机仍走 AndroidKeyStore，仅测试环境走 fallback
  - Robolectric 下跑含 Room suspend 的 ViewModel 测试：用真实线程 `runBlocking` 断言数据库落库（轮询 DB 而非等 state），用 `Shadows.shadowOf(context.contentResolver).registerInputStream(uri, stream)` 模拟文件选择，`shadowOf(Looper.getMainLooper()).idle()` 推进主线程；Room in-memory 需 `setQueryExecutor/setTransactionExecutor` 指定真实线程
  - 冒烟/UI 测试（Robolectric + Compose）需要 testOptions { unitTests { isIncludeAndroidResources = true } }、robolectric 4.14.1、androidx.test core/ext-junit、hilt-android-testing + kaptTest(hilt-compiler)，测试类用 @HiltAndroidTest + HiltTestApplication + @Config(sdk=[34])

[Project Knowledge Summary]
- Date: 2026-08-11
- Context: Discovered by Agent while diagnosing "创建小说卡死在准备中" stuck-on-准备中 bug
- Category: Troubleshooting & Debugging
- Instructions:
  - 创作管线 UI 卡在"准备中"的典型根因：AgentOrchestrator.run 的 flow 里只调用 session.update { phase = ... } 更新 CreationSession 内部状态，但从不 emit PipelineEvent.StateChanged；而 CreationRunViewModel.handleEvent 只有收到 StateChanged 才更新 UI 的 _state.phase。结果 UI phase 永远停在初始 IDLE（"准备中"），进度条/章节进度不更新，直到流结束才被 Completed 覆盖
  - 修复模式：在 flow 内定义 `suspend fun FlowCollector<PipelineEvent>.update(session, transform)` 局部扩展，先 session.update(transform) 再 emit(PipelineEvent.StateChanged(session.state.value))，所有 phase 变更点都走它；streamingText 由 Token 事件驱动，不要在 update() 里带 streamingText，并在每章开始清空 session 的 streamingText、streaming 结束后再清空一次，避免 StateChanged 携带上一章全文覆盖 UI
  - 排查此类"状态不更新"问题时，先比对"状态生产端（orchestrator/session）"与"状态消费端（ViewModel.handleEvent 对事件的 case 分支）"是否一致，StateChanged 定义了但从未 emit 是最常见盲区

[Project Knowledge Summary]
- Date: 2026-08-12
- Context: Discovered by Agent while fixing "翻页停在底部" and strengthening continuation writing
- Category: Troubleshooting & Debugging
- Instructions:
  - 阅读页翻章后停在底部：Compose `verticalScroll(rememberScrollState())` 在重组时保留滚动位置，切章不会自动回到顶部。修复：在 Composable 顶部创建 `val scrollState = rememberScrollState()`，用 `LaunchedEffect(uiState.currentIndex) { if (currentChapter != null) scrollState.scrollTo(0) }` 在章节索引变化时重置到顶部
  - 续写链路手法画像/prompt 文案相关测试断言注意：NovelAnalyzerTest 与 NovelCreationUseCaseTest 断言 `toUserPrompt()`/章节 prompt 包含特定文案（如"写作手法指令"）。若改动 ContextManager.toUserPrompt 的文案标签（改为"续写要求"），必须同步更新这两处测试断言，否则误报失败

[Project Knowledge Summary]
- Date: 2026-08-12
- Context: Discovered by Agent while strengthening continuation writing to match previous plot & writing style
- Category: Workflow & Collaboration
- Instructions:
  - 续写场景的"贴合前文情节与写作手法"由三层组成：① 导入时 NovelAnalyzer 的 plot-style-analyzer 生成 styleProfile/plotSummary 持久化到 WorldviewEntity；② runContinuation 读取它们注入 PipelineRequest(styleProfile/plotSummary/skipSetup=true)；③ orchestrator 每章构建章节作者 prompt 时带上手法画像，并在【续写要求】指令中明确要求承接前文结尾、延续人物状态、不推翻已有剧情
  - continuity-editor 校验时携带最近 3 章结尾+手法画像（防止只查世界观/大纲而漏掉情节与文风连贯性）；polish-editor 润色时携带手法画像（防止润色后风格走样）。若后续再调上下文组装，这两处 userMessage 是 buildString 拼装，别只传 rawChapter

[Project Knowledge Summary]
- Date: 2026-08-12
- Context: Discovered by Agent while making creation/continuation run in background
- Category: Workflow & Collaboration
- Instructions:
  - 创作/续写后台化模式：管线收集从 ViewModel 的 viewModelScope 移入 NovelCreationUseCase（@Singleton）内部的应用级 `CoroutineScope(SupervisorJob() + Dispatchers.Default)`。useCase 提供 startPipelineInBackground/startContinuationInBackground（isRunning 去重）+ events(novelId) 共享流 + currentState(novelId) 快照恢复 + observeRunning(novelId)。ViewModel 只订阅 events，onCleared 只取消订阅 Job 不 cancel 管线，退出页面后后台继续生成、章节实时落库，书架/详情页 observe 自动刷新
  - CreationRunViewModel 重新进入时用 currentState(id) 先恢复 phase 快照再订阅 events，避免进度闪回"准备中"
  - runPipeline 是普通函数（runContinuation 是 suspend）：需要收集时才执行的操作用 `.onStart { suspendOp() }` 挂到 flow 上，不能直接在函数体调用 suspend 方法；markWriting 置 novel.status=WRITING 就在管线 onStart 时执行，让书架立即显示"创作中"
  - 测试续写后台行为（NovelCreationUseCaseTest）：fake.completeHandler 是非 suspend lambda，延时用 Thread.sleep 而非 delay；协程内订阅共享流用 import kotlinx.coroutines.launch 的 launch{} + collect { event -> ... }（`collect { events += it }` 会触发类型推断失败）；等待后台完成用 withTimeout + 轮询 isRunning

[Project Knowledge Summary]
- Date: 2026-08-12
- Context: Discovered by Agent while fixing "暂停生成不生效" (pause had no visible effect)
- Category: Troubleshooting & Debugging
- Instructions:
  - 后台创作"暂停不生效"的根因：暂停检查若只放在章节循环边界，暂停时若正处于某次 LLM 调用（章节流式/连续性/润色）或世界观/大纲阶段，管线会跑完当前调用甚至整章才挂起，用户点击后看不到停止，误以为暂停无效
  - 修复模式：AgentOrchestrator.run 的 flow 内定义 `suspend fun FlowCollector<PipelineEvent>.awaitResume(session, message)`——若 session.state.paused 则 update 到 PAUSED（含 currentAgent=null、streamingText=""）并 delay(200) 轮询等待 resume；恢复后再 update 回 WRITE_CHAPTER。把 awaitResume 插入到每次 LLM 调用之前（世界观、大纲、章节 streamChat、连续性 complete、润色 complete 各一处），暂停最坏只等当前这一次调用结束即挂起，体验即时
  - 详情页"后台创作"卡片状态不能只靠按钮点击时写本地 UiState：重进页面会丢失。useCase 增加 observePaused(novelId) StateFlow（getOrPut 时用 sessions[novelId]?.state?.value?.paused 初始化，pause/resume 调 setPaused，registerJob 的 invokeOnCompletion 与 cancel 里复位 false），详情页订阅它刷新 creationPaused 卡片与暂停/继续按钮
  - 暂停期间章节数不变的验证：暂停发生在章节流式调用中时，流式跑完即挂起，连续性/润色不会再执行，dao.getChapters 仍为原数量；resume 后跑完剩余章节
  - "暂停后不能继续"的根因：resume() 之前只清 paused 标记、不还原 phase。若用户快速暂停后立刻恢复（管线尚未到达 awaitResume 挂起点），管线执行到 awaitResume 时 paused 已是 false 直接 return 跳过，phase 永远停在 PAUSED，UI 持续显示"继续生成"按钮，用户误以为无法继续。修复：resume() 里若 phase==PAUSED 则同时恢复为 WRITE_CHAPTER 并 emit StateChanged，并新增断言 resume 后 phase 立即离开 PAUSED

[Project Knowledge Summary]
- Date: 2026-08-12
- Context: Discovered by Agent while adding auto-retry when generation hits content policy (违禁词/敏感内容) review
- Category: Troubleshooting & Debugging
- Instructions:
  - 内容合规违规（违禁词/敏感内容）与普通错误要用独立异常区分：新增 ContentPolicyException(IOException 子类)。LlmClient 在 HTTP 错误响应中识别 content_policy_violation / content_policy / content_filter / inappropriate_content / unsafe_content / moderation 等英文 code，以及"敏感词/违禁/违规内容/不合规内容/sensitive content/inappropriate/unsafe content/violated our policy/safety system"等关键词，命中则抛 ContentPolicyException，否则仍抛 IOException
  - AgentOrchestrator 的统一重试模式：`withContentComplianceRetry(systemPrompt, userMessage) { sys, user -> llm.xxx(...) }`，捕获 ContentPolicyException 后给 userMessage 末尾追加【内容合规要求】指令（要求用含蓄/隐喻/间接表达规避违禁词、保持剧情与人设完整），最多重试 2 次，重试耗尽抛原异常由调用方决定降级或 FAILED。世界观/大纲/章节/连续性/润色 5 处调用都要包这层；连续性/润色本身有 catch 降级（回退原文），违规重试耗尽后仍走原降级，不中断管线
  - 流式章节生成（streamChat）的违规发生在 collect 阶段，重试 lambda 里要包含整个 collect 收集逻辑（每次重试重建 StringBuilder），awaitResume 也要放进重试 lambda 内，避免重试期间跳过暂停检查
  - 测试模拟违规：FakeLlmGateway 增加 contentPolicyFailForSystemPrompt（按 systemPrompt 匹配）+ contentPolicyFailRemaining（剩余失败次数，首次抛、重试成功用=1）+ recordedUserMessages（记录每次调用 userMessage，用于断言重试追加了合规指令）

[Project Knowledge Summary]
- Date: 2026-08-12
- Context: Discovered by Agent while optimizing generation speed (每章 3 次完整章节生成的浪费)
- Category: Troubleshooting & Debugging
- Instructions:
  - 创作管线每章的时间瓶颈：章节作者(流式全文) → 连续性编辑(强制完整重写一章) → 润色编辑(完整重写一章)，一章正文最多被完整生成 3 遍。最大可优化点是连续性编辑——绝大多数章节无设定冲突，但旧 prompt 强制输出【修正后章节】完整正文，白耗一次完整 LLM 生成
  - 修复模式：continuity-editor prompt 改为"仅发现问题时才输出【修正后章节】正文，无问题只输出【一致性报告】- 无设定冲突"；parseContinuityOutput 在无修正章节时回退原章节正文（fallback），绝不能把报告文本当作正文返回（旧代码 `return issues to output.trim()` 会把报告当章节，是新 prompt 生效后的隐患）
  - 润色编辑是质量保证的最后一道，不要跳过或降级；若后续还想提速，考虑上下文裁剪（recentChaptersInContext 全文注入）而非削弱润色
  - 上下文裁剪落地：ContextManager 默认 recentChaptersInContext 从 5 降到 3，每章只保留最近 3 章全文（连贯性核心），更早章节经 SummaryCompressor 压缩为摘要注入，显著减小输入 token 规模、加快每次 LLM 生成的首 token
