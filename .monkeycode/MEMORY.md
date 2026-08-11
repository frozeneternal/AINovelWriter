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
