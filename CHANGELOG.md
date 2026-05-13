# 变更日志

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/spec/v2.0.0.html)。

## [1.0.3-SNAPSHOT] - 2026-05-12

### 新增功能
- **批量导入功能**
  - 支持上传包含多个任务配置的 ZIP 文件
  - 自动从 ZIP 结构中检测任务名称
    - 支持目录结构：`job/config.xml`
    - 支持扁平结构：`job.xml`
  - UTF-8 编码支持中文目录名
  - **路径级联重命名**：父目录重命名后，子任务自动同步更新目标路径
  - **路径状态缓存**：批量导入时维护任务存在性快照，避免状态断层

- **Dry Run 模式**
  - 预检查模式，验证任务但不导入
  - 验证任务名称、插件依赖和冲突
  - 显示导入结果详细预览（包含完整 ZIP 路径）
  - 默认启用以确保安全
  - **虚拟目录状态缓存**：预演模式下模拟目录存在状态，支持多层级路径正确传递
  - **冲突传播机制**：上游冲突自动阻断后续路径创建

- **两阶段确认流程**
  - Dry Run 预览 → 确认对话框 → 实际导入
  - 通过显式确认防止意外导入
  - 最终导入前显示任务列表

- **插件依赖检测**
  - 从 XML 自动检测所需插件
  - 导入前报告缺失插件
  - 防止因缺少插件导致的导入失败

- **插件建议系统**
  - 集成 Jenkins 更新中心 API
  - 自动检索插件元数据（名称、版本、描述）
  - 一键插件安装功能
  - PluginSuggestion 类支持元数据

- **导入跟踪检查点系统**
  - ImportCheckpoint 类跟踪导入状态
  - 检查点持久化存储到磁盘
  - CheckpointManager 管理检查点
  - 支持 STARTED、DONE、FAILED、ROLLED_BACK、RECOVERED 状态
  - **checkpoint 路径保存**：保存 folderPath 和 fullName，确保恢复导入正确

- **导入恢复机制**
  - 通过 doResumeImport 端点恢复失败的导入
  - 自动重试失败的任务
  - 批量导入回滚能力
  - 完成后清理检查点数据
  - **正确恢复目录层级**：支持深层任务恢复

- **SSE 进度流**
  - ImportProgress 类跟踪进度
  - ProgressManager 实时进度更新
  - doProgress 端点支持服务器发送事件
  - 实时进度流式传输到前端

- **冲突处理选项**
  - **跳过模式（默认）**：跳过已存在的任务
  - **覆盖模式**：覆盖已存在的任务并自动备份
  - **重命名模式**：自动重命名冲突（test → test_1 → test_2）
  - 优先级：覆盖 > 重命名 > 跳过

- **覆盖前备份**
  - 自动备份现有 config.xml 为 config.xml.bak
  - 防止覆盖操作期间数据丢失

### 变更
- 更新参数解析使用 Boolean.parseBoolean 以提高健壮性
- 简化 JobImportExportSidebarLink 布局为两栏（导入 + 批量导入）
- 从侧边栏链接移除导出功能（无文件夹级上下文）
- **多层目录支持**：批量导入支持多层嵌套目录结构
- **当前目录隔离**：导入任务到当前页面所在目录，而非 Jenkins 根目录

### 修复
- **XML 解析错误**
  - 修复 Jelly 文件中 JavaScript 代码的 `&&` 为 `&amp;&amp;`
  - 修复 `<br>` 为 `<br />` 以符合 XML 规范
  - 修复 `<hr>` 为 `<hr />` 以符合 XML 规范
  - 解决 Jelly XML 实体引用错误

- **缺失导入**
  - 为两个 Java 文件添加 `ByteArrayInputStream` 导入
  - 为 CheckpointManager 添加 Jenkins 导入
  - 修复 PluginSuggestionManager 中的 UpdateSite.Plugin API 调用

- **详细导入结果**
  - 综合结果报告包含：
    - 任务总数
    - 成功数量
    - 失败数量
    - 跳过数量
    - 每个任务状态（OK、ERROR、SKIP、RENAME、OVERWRITE）
    - 最终任务名称（显示重命名映射）
    - 缺失插件列表
    - 错误消息
  - 带图标的彩色状态指示器（✔ ✘ ⏭ ↻ ⚠）
  - **新增字段**：`sourcePath`（ZIP 原始路径）、`displayPath`（UI 展示路径）

- **前端增强**
  - Dry Run 复选框（默认选中）
  - 覆盖选项及备份通知
  - 重命名选项及命名模式描述
  - **自适应结果模态框**：宽度改为 `max-width: 90vw`、`max-height: 90vh`，支持内容滚动
  - 四列结果表（任务、最终名称、状态、消息）
  - 缺失插件以红色高亮显示

- **目录创建修复**
  - 修复 `createProjectFromXML` 使用 `targetGroup` 而非 `itemGroup`，确保任务创建到正确目录
  - 新增 `ensureFolderPath` 方法支持多层目录自动创建
  - **dryRun 模式修复**：不再提前 return，继续遍历层级确保完整路径传递

- **路径规范化**
  - 修复 ZIP 路径解析，支持 Windows ZIP 文件
  - 统一路径分隔符处理（`\` → `/`）
  - 移除开头多余斜杠和连续斜杠

- **重名检测优化**
  - 修复 `generateUniqueJobName` 基于目标目录检测重名
  - 支持深层目录重名检测和自动重命名
  - **路径级联重命名**：父目录 rename 后自动传播到子任务

- **Folder 插件兼容**
  - 新增 `hasFolderPlugin()` 检测方法
  - 缺失 Folder 插件时显示友好错误提示，而非 NoClassDefFoundError

- **安全修复**
  - **禁止删除 Folder**：overwrite 模式下对 Folder 使用 `updateByXml` 而非 `delete`，避免递归删除子任务
  - **路径状态缓存**：批量导入时维护 `existingJobsCache`，避免状态断层导致的误判
  - **动态目录保护**：禁止覆盖 Multibranch、ComputedFolder 等动态生成的目录类型
  - **根目录与子目录功能同步**：确保 `JobImportExportAction` 和 `JobImportExportSidebarLink` 使用相同的安全逻辑

- **许可证变更**：将自定义非商业许可改为 MIT License，允许自由用于商业用途

## [1.0.2] - 上一版本

### 新增功能
- 单任务导入/导出功能
- 任务配置 XML 文件上传
- 任务名称验证
- 文件夹支持组织任务

### 功能
- 导出当前任务/文件夹配置为 XML
- 更新现有任务/文件夹配置
- 导入新任务到当前目录
- 创建/导入操作的权限检查

## [1.0.1] - 初始版本

### 新增功能
- 基础任务导入/导出插件
- Jenkins 侧边栏链接全局访问
- 单个任务的项目级操作
