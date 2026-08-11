# Requirements Document

## Introduction

本项目是一款面向 Android 平台的单机 AI 小说创作应用（feature: `ai-novel-writer`）。应用内置多个 AI 专家角色（世界观架构师、作者、连续性编辑、润色编辑等），通过多智能体管线编排，调用用户自配的 OpenAI 兼容大语言模型 API 生成高质量、前后文连贯的小说章节；同时支持调用图片/视频生成 API 为小说生成配图和封面。所有书籍数据、历史记录存储在本地数据库，不依赖任何云端服务。

## Glossary

- **System**: 本 AI 小说创作 Android 应用（ai-novel-writer）
- **User**: 使用本应用的终端用户
- **API Provider**: 用户自行配置的 OpenAI 兼容接口服务商
- **LLM**: 大语言模型（文本生成）
- **Novel**: 一部完整的书籍作品
- **Chapter**: 小说中的一个章节
- **Worldview**: 世界观设定（人物、地点、规则、时间线等）
- **Multi-Agent Pipeline**: 多智能体编排管线
- **Agent / Expert Role**: 具有独立 system prompt 的专家角色
- **Continuity**: 前后文一致性
- **Prompt Template**: 角色提示词模板
- **Conversation Record**: 历史创作对话记录
- **Image Generation API**: 文生图接口
- **Video Generation API**: 文生视频接口

## Requirements

### Requirement 1: 应用基础能力

**User Story:** AS 用户, I want 在 Android 设备上安装并运行本应用, so that 我可以单机使用 AI 小说创作能力。

#### Acceptance Criteria

1. WHEN System 启动, System SHALL 显示主界面, 主界面包含书架、历史记录、新建创作三个入口
2. WHILE System 处于离线状态, System SHALL 保持本地书籍与历史记录可正常浏览
3. IF 设备已安装 Android 8.0 及以上版本, System SHALL 正常安装并运行
4. WHEN User 首次进入应用, System SHALL 引导配置 API 接口信息

### Requirement 2: API 配置管理

**User Story:** AS 用户, I want 自行配置 API 接口, so that 我可以使用自己的大模型密钥生成小说。

#### Acceptance Criteria

1. WHEN User 在设置页填写 API Base URL、API Key、模型名称, System SHALL 保存配置到本地安全存储
2. WHEN User 点击「测试连接」, System SHALL 发起一次最小请求并返回连通性与延迟结果
3. WHEN User 配置多个模型, System SHALL 允许分别配置文本模型、图片模型、视频模型
4. IF API 请求失败, System SHALL 展示明确错误信息（网络错误、鉴权失败、余额不足等）

### Requirement 3: 多智能体专家体系

**User Story:** AS 用户, I want 应用内置多专家角色参与创作, so that 我可以获得高质量、逻辑严谨的小说。

#### Acceptance Criteria

1. WHEN System 创建新小说, System SHALL 自动编排以下专家管线: 世界观架构师 → 大纲规划师 → 章节作者 → 连续性编辑 → 润色编辑
2. WHEN 世界观架构师执行, System SHALL 生成包含人物设定、地理设定、规则体系、时间线的世界观设定文档
3. WHEN 章节作者执行, System SHALL 基于世界观设定、大纲与之前章节内容生成当前章节
4. WHEN 连续性编辑执行, System SHALL 校验当前章节与世界观设定及已写章节的一致性，并输出修正后的章节
5. WHEN 润色编辑执行, System SHALL 优化文笔、节奏与情感表达
6. WHEN User 选择创作模式, System SHALL 支持全自动管线与人工确认每一步两种模式
7. WHILE 管线运行, System SHALL 展示当前执行中的专家角色与进度状态

### Requirement 4: 上下文连贯性

**User Story:** AS 用户, I want 生成的小说上下文连贯, so that 不会出现设定冲突、人物性格漂移、剧情逻辑断裂。

#### Acceptance Criteria

1. WHEN 章节作者生成章节, System SHALL 将世界观设定、大纲、最近 N 个章节的摘要或全文注入上下文
2. WHEN 连续性编辑校验章节, System SHALL 对照世界观设定与角色卡检查设定一致性
3. WHILE 小说累计章节数超过上下文窗口, System SHALL 使用摘要压缩历史章节后注入
4. WHEN System 检测到前后文矛盾, System SHALL 标记冲突并提示 User 选择接受修改或重新生成

### Requirement 5: 书架管理

**User Story:** AS 用户, I want 管理我的小说书架, so that 我可以组织并快速找到我的作品。

#### Acceptance Criteria

1. WHEN User 创建小说, System SHALL 在书架中新增书籍卡片（封面、书名、简介、进度）
2. WHEN User 点击书籍卡片, System SHALL 进入书籍详情页，展示目录与章节列表
3. WHEN User 对书籍执行重命名或删除, System SHALL 同步更新本地数据库
4. WHEN User 删除书籍, System SHALL 弹出二次确认对话框
5. WHEN System 展示书架, System SHALL 支持按最近更新排序

### Requirement 6: 历史记录

**User Story:** AS 用户, I want 查看创作历史记录, so that 我可以回溯或继续之前的创作。

#### Acceptance Criteria

1. WHEN User 发起任何一次 AI 生成请求, System SHALL 记录该次请求的专家角色、输入摘要、输出结果、时间戳与状态
2. WHEN User 打开历史记录页, System SHALL 按时间倒序展示全部记录
3. WHEN User 点击某条历史记录, System SHALL 展示完整输入输出内容
4. WHEN User 从历史记录选择「继续创作」, System SHALL 恢复对应的书籍与上下文状态
5. WHEN User 删除单条或清空历史记录, System SHALL 更新本地数据库

### Requirement 7: 小说内容展示与编辑

**User Story:** AS 用户, I want 阅读与编辑生成的小说内容, so that 我可以调整并导出作品。

#### Acceptance Criteria

1. WHEN User 打开章节阅读页, System SHALL 以舒适的排版展示章节正文
2. WHEN User 阅读到章节末尾, System SHALL 提供「生成下一章」入口
3. WHEN User 切换章节, System SHALL 记录阅读进度并在重新打开时恢复到该位置
4. WHEN User 编辑章节正文, System SHALL 允许本地修改并保存
5. WHEN User 请求导出, System SHALL 将全书导出为 TXT/Markdown 格式文件

### Requirement 8: 图文与视频生成

**User Story:** AS 用户, I want 为小说生成配图与视频, so that 我可以获得封面、插画和宣传素材。

#### Acceptance Criteria

1. WHEN User 在书籍详情点击「生成封面」, System SHALL 基于书名与简介调用图片生成 API 生成封面
2. WHEN User 在章节页点击「生成配图」, System SHALL 基于章节内容摘要调用图片生成 API 生成插画
3. WHEN User 在书籍详情点击「生成宣传视频」, System SHALL 调用视频生成 API 生成视频素材
4. WHEN 图片或视频生成成功, System SHALL 保存到本地并关联到对应书籍
5. IF 未配置图片或视频 API, System SHALL 提示 User 配置后再使用
6. WHEN User 查看生成素材, System SHALL 支持全屏预览图片

### Requirement 9: 数据持久化

**User Story:** AS 用户, I want 我的创作数据持久保存, so that 我可以安全地长期使用。

#### Acceptance Criteria

1. WHEN System 保存书籍、章节、历史记录、设定文档, System SHALL 写入本地 Room 数据库
2. WHEN System 保存配置信息, System SHALL 使用 Android Keystore 加密后存储
3. WHEN User 关闭应用并重新打开, System SHALL 恢复书架、历史记录与阅读进度
4. WHEN User 卸载应用, System SHALL 删除全部本地数据

### Requirement 10: 流式生成与取消

**User Story:** AS 用户, I want 实时查看生成内容并控制生成过程, so that 我获得即时反馈。

#### Acceptance Criteria

1. WHEN System 调用 LLM, System SHALL 使用流式（stream）输出实时展示生成文本
2. WHEN User 点击停止, System SHALL 取消当前生成任务并保留已生成部分
3. WHILE 生成进行中, System SHALL 提供重试与重新生成功能
