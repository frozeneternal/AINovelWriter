# AI 小说家（AINovelWriter）

单机 Android AI 小说创作应用。内置 5 位 AI 专家角色，通过多智能体管线编排，调用用户自配的 OpenAI 兼容 API 生成前后文连贯的小说；支持图片/视频生成、书架、历史记录、流式输出与本地加密存储。

## 核心特性

- **多智能体专家管线**：世界观架构师 → 大纲规划师 → 章节作者 → 连续性编辑 → 润色编辑
- **上下文连贯**：世界观/大纲/前文注入 + 旧章节摘要压缩，防止设定冲突与人物漂移
- **书架管理**：书籍卡片、封面、进度、目录
- **历史记录**：全部 AI 调用记录，支持继续创作
- **图文视频**：封面、章节插画、宣传视频生成
- **流式输出**：章节实时生成、可中途停止
- **安全存储**：API Key 经 Android Keystore 加密保存

## 技术栈

- Kotlin 2.0 + Jetpack Compose（Material 3）
- Room（本地数据库）
- Hilt（依赖注入）
- OkHttp + kotlinx.serialization（OpenAI 兼容 API 客户端）
- Coil（图片加载）
- 架构：Compose UI → ViewModel → UseCase → Repository → Room/DataStore

## 环境要求

- JDK 17
- Android SDK 35
- Android Studio（Ladybug 或更新）

## 构建运行

```bash
# 在 Android Studio 中打开本工程，等待 Gradle 同步后运行
# 或使用命令行构建调试包
./gradlew assembleDebug
```

```bash
# 运行单元测试
./gradlew testDebugUnitTest
```

## 使用说明

1. 进入「设置」配置文本模型 API（Base URL / API Key / 模型名，OpenAI 兼容接口），可点「测试连接」验证
2. 可选配置图片、视频生成 API
3. 在书架点击「新建小说」，填写书名、题材、主题、章节数，选择创作模式
4. 创作运行页实时展示专家管线进度与章节流式生成
5. 完成后进入书籍详情查看目录、生成封面/宣传视频；章节页可生成插画、编辑正文

## 目录结构

```
app/src/main/java/com/ainovel/app/
├── data/                 # Room 实体/DAO、LLM/媒体客户端、Repository、Keystore 加密
├── domain/
│   ├── agent/            # 多智能体编排引擎、专家提示词、上下文管理、摘要压缩、会话状态机
│   ├── model/            # 领域模型与枚举
│   └── usecase/          # 创作管线用例
├── di/                   # Hilt 模块
└── ui/                   # 书架/详情/创作/阅读/历史/设置/世界观 页面
```

## 设计文档

- 需求：`.monkeycode/specs/ai-novel-writer/requirements.md`
- 设计：`.monkeycode/specs/ai-novel-writer/design.md`
- 任务：`.monkeycode/specs/ai-novel-writer/tasklist.md`
