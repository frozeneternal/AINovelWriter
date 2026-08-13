# Requirements Document

## Introduction

针对 AI 小说写作应用（AINovelWriter）新增三项书籍管理能力：

1. **最近删除**：删除书籍不再直接销毁，而是进入"最近删除"列表，可恢复或彻底删除。
2. **创作提示词回显**：在创作/续写时，展示该书上次创作所使用的提示词（方向、章节数、每章字数等），便于复用与回顾。
3. **章节独立删除**：书籍详情内可单独删除某一章节。

## Glossary

- **System**: AINovelWriter Android 应用
- **Book**: 一本小说（NovelEntity）
- **Chapter**: 书内的一个章节（ChapterEntity）
- **Recently Deleted**: 被标记删除但尚未彻底清除的书籍集合
- **Creation Prompt**: 创作/续写时用户输入的提示词（创作方向、章节数、每章字数）

## Requirements

### Requirement 1: 最近删除（软删除与恢复）

**User Story:** AS 用户, I want 删除书籍后能进入最近删除列表并可恢复, so that 误删的书籍可以找回。

#### Acceptance Criteria

1. WHEN 用户在书架长按书籍并确认删除, the system SHALL 将该书籍标记为已删除并从书架隐藏。
2. WHEN 用户打开最近删除列表, the system SHALL 展示所有已标记删除的书籍及其删除时间。
3. WHEN 用户在最近删除列表点击恢复, the system SHALL 将该书籍移回书架并移除删除标记。
4. WHEN 用户在最近删除列表确认彻底删除, the system SHALL 连同书籍的章节、世界观、大纲、导入文本、聊天记录等关联数据一并清除。
5. IF 最近删除列表为空, the system SHALL 展示空态提示文案。

### Requirement 2: 创作提示词回显

**User Story:** AS 用户, I want 在创作/续写时看到该书上次使用的提示词, so that 我可以回顾或复用之前的创作设置。

#### Acceptance Criteria

1. WHEN 用户为某本书打开新建创作设置页, the system SHALL 回显该书上次创作时输入的创作方向与章节数、每章字数。
2. WHEN 用户为某本书打开续写设置对话框, the system SHALL 回显该书上次续写时输入的续写方向、章节数与每章字数。
3. WHEN 用户确认创作/续写并成功开始, the system SHALL 将本次的提示词（方向、章节数、每章字数）持久化到该书。
4. IF 该书从未进行过创作或续写, the system SHALL 展示默认空值。

### Requirement 3: 章节独立删除

**User Story:** AS 用户, I want 在书籍详情中单独删除某一章节, so that 我可以移除不需要的章节。

#### Acceptance Criteria

1. WHEN 用户在书籍详情长按某一章节行, the system SHALL 展示删除确认对话框。
2. WHEN 用户确认删除该章节, the system SHALL 删除该章节并更新书的章节总数。
3. IF 删除后书不再有章节, the system SHALL 将书的完成状态重置为草稿。
4. IF 删除章节后剩余章节序号不连续, the system SHALL 重排剩余章节的序号。
