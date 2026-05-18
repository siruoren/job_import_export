# 变更日志

本项目的所有重要变更都将记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/spec/v2.0.0.html)。

## [2.0.3] - 2026-05-18

### 新增

- **API 单元测试**：新增 `JobImportExportApiTest` 测试类，包含 8 个测试方法，覆盖 REST API 的主要功能点
  - 验证 API 类实例化
  - 验证通过 SidebarLink 获取 API 实例
  - 验证所有 API 端点方法存在（list、exportJob、exportFolder、exportAll、progress、updateJob、import、preview）
  - 验证 URL 路由配置

### 测试覆盖

- **测试用例总数**：从 67 个增加到 75 个
- **新增测试类**：`JobImportExportApiTest`（8 个测试方法）

### 验证

- **REST API 接口验证通过**：所有 8 个 API 端点已在实际 Jenkins 环境（`http://localhost:8080/jenkins`）测试通过
  - `list` (GET)：列出任务列表，返回树形结构
  - `progress` (GET)：查询导入进度，支持异步状态跟踪
  - `exportJob` (POST)：导出单个任务配置XML
  - `exportFolder` (POST)：导出文件夹（含所有子任务）
  - `exportAll` (POST)：导出全部任务为ZIP
  - `import` (POST)：批量导入任务（异步执行）
  - `preview` (POST)：预演导入（dry-run模式）
  - `updateJob` (POST)：更新任务配置（需使用 xmlFile 参数上传文件）

### 文档更新

- **API 使用说明增强**：README 中新增 Python requests 库调用示例，提供更可靠的认证和请求方式
- **curl 注意事项说明**：curl 命令在 POST 请求中 crumb 验证存在兼容性问题，建议使用 Python requests 库

## [2.0.2] - 2026-05-17

### 新增功能

- **REST API 接口**：新增 `JobImportExportApi` 类，提供完整的 REST API 端点，使外部工具可以程序化地调用插件功能
  - API 路径前缀：`/jobImportExport/api/`
  - 所有接口统一返回 JSON 格式，包含 `success`、`message`、`errorCode` 字段
  - 支持 `exportJob`、`exportFolder`、`exportAll`、`import`、`preview`、`updateJob`、`progress`、`list` 等端点
  - 支持 Base64 编码的 ZIP 数据传输，便于与外部系统集成
  - 导入接口支持异步执行，返回 `batchId` 用于进度查询
  - 所有接口均包含权限检查，确保安全性
  - **API 文档页面**：新增 Swagger 风格的可视化 API 文档页面，可通过侧边栏「API 文档」入口访问
    - 支持在线「Try it out」功能，直接填写参数并发送请求查看响应
    - 包含完整的参数说明、cURL 示例、响应示例
    - 权限徽章显示各端点所需权限
    - 支持中英双语自动切换

### 改进

- **API 权限检查与 Web 页面统一**：所有 API 端点使用 `checkPermission()` 进行权限校验，与 Web 页面行为完全一致
  - `preview` 端点权限从 `Item.READ` 修正为 `Item.CREATE`，与 Web 页面导入预览保持一致
  - 权限不足时由 Jenkins 框架统一处理（返回 403），而非自定义 JSON 错误
- **API 响应信息完善**：各端点 JSON 输出补充了更丰富的上下文信息
  - `exportJob`：新增 `fullName`、`jobType`（folder/job）、`jobUrl` 字段
  - `exportFolder`/`exportAll`：新增 `sourceFolder`、`includeCurrentConfig` 字段
  - `import`：即时返回新增 `targetFolder`、`overwrite`、`rename`、`dryRun` 参数回显及 `progressUrl` 进度查询链接
  - `preview`：新增 `targetFolder`、`overwrite`、`rename` 参数回显
  - `updateJob`：新增 `jobType`、`jobUrl`、`redirect` 字段
  - `progress`：完成时新增 `total` 汇总字段
  - `list`：新增 `totalCount` 字段

### 新增 API 端点

| 端点 | 方法 | 功能 | 权限 |
|------|------|------|------|
| `/jobImportExport/api/exportJob` | POST | 导出单个Job的config.xml | Item.READ |
| `/jobImportExport/api/exportFolder` | POST | 导出文件夹为ZIP（Base64） | Item.READ |
| `/jobImportExport/api/exportAll` | POST | 导出所有Job为ZIP（Base64） | Jenkins.ADMINISTER |
| `/jobImportExport/api/import` | POST | 从ZIP导入Job | Item.CREATE |
| `/jobImportExport/api/preview` | POST | 预演导入（dry-run） | Item.CREATE |
| `/jobImportExport/api/updateJob` | POST | 更新Job的config.xml | Item.CONFIGURE |
| `/jobImportExport/api/progress` | GET | 查询导入进度 | - |
| `/jobImportExport/api/list` | GET | 列出Job/文件夹 | Item.READ |

## [2.0.1] - 2026-05-17

### 新增功能

- **进度显示全称路径**：进度条中显示任务的全称路径（如 `myFolder/subFolder/myJob`），而非相对路径（如 `subFolder/myJob`）

### 修复

- **异步任务 Locale 丢失导致状态/消息未汉化**：异步导入任务在后台线程执行时，`Messages.XXX()` 调用无法获取用户 Locale，导致所有状态文本和消息都返回英文（如 "Skip (Exists)"、"Directory job already exists, skipped"）
  - 新增 `LocaleHolder` 工具类，安装自定义 `LocaleProvider`，通过 `ThreadLocal` 在异步线程中保存用户 Locale
  - 修改 `JobImportExportAction.doBatchImport` 和 `JobImportExportSidebarLink` 异步任务入口，捕获 `req.getLocale()` 并在子线程中通过 `LocaleHolder.setLocale()` 恢复
  - 统一所有 `ImportResult.status` 赋值方式，通过 `setStatusEnum()` / `setStatus()` 方法自动调用 `StatusUtil.getLocalizedStatus()` 汉化
  - 移除所有直接 `result.status = "XXX"` 赋值（共 45 处）
  - `ExportResult` 构造函数中自动通过 `StatusUtil.getLocalizedStatus()` 汉化 status 字段
  - JSON 响应新增 `statusCode` 字段，前端使用 `statusCode` 判断状态样式，`status` 字段直接显示汉化文本
- **导出结果本地化**：修复 `ExportResult` 状态未汉化问题，导出弹窗中 "EXPORTED" / "SKIPPED" 等状态正确显示为中文
- **扩展状态码支持**：新增 `BLOCKED`、`CONFLICT`、`OK`、`EXPORTED`、`SKIPPED`、`ERROR_INVALID_NAME`、`ERROR_PLUGIN`、`REUSE`、`SKIP_FOLDER_MISSING` 等状态码的本地化支持

### UI 优化

- **结果弹窗表格自适应**：
  - 减小单元格 padding：`8px` → `4px 6px`
  - 添加 `table-layout:fixed` 固定列宽
  - 设置列宽比例（任务 28% / 最终名称 28% / 状态 14% / 消息 30%）
  - 长路径自动换行：`word-break:break-all`
  - 紧凑行高：`line-height:1.4`
  - 状态列 `white-space:nowrap` 防止状态图标和文字被拆行
- **前端 switch 状态码映射补全**：补充 `ERROR_INVALID_NAME`、`ERROR_PLUGIN`、`SKIP_EMPTY`、`SKIP_FOLDER_MISSING`、`OVERWRITE_FOLDER`、`CREATE_JOB`、`UPDATE_CONFIG`、`REUSE`、`BLOCKED`、`CONFLICT` 等状态码的颜色/图标映射

### 性能优化

- **线程池限流机制**：新增 `ImportExecutor` 类，统一管理导入任务线程池
  - 核心线程数：`max(2, CPU核心数)`
  - 最大线程数：`CPU核心数 * 2`
  - 队列容量：100
  - 服务器繁忙时优雅拒绝任务，返回友好提示
- **轮询间隔优化**：将前端轮询间隔从 500ms 调整为 800ms，减少服务器请求压力，同时用户体验几乎不受影响
- **内存泄漏防护**：在 `ProgressManager` 中添加自动清理机制，当结果准备好 10 分钟后自动删除该进度记录，防止长时间运行后内存泄漏

### 测试覆盖

- **新增单元测试**：共 76 个测试用例，覆盖本地化核心功能和 REST API
  - `StatusUtilTest`：21 个测试，覆盖状态码本地化、null/empty 处理、未知状态码
  - `ImportResultTest`：18 个测试，覆盖 `setStatusEnum()` / `setStatus()` 方法、双字段一致性
  - `ExportResultTest`：14 个测试，覆盖构造器本地化、statusCode/status 分离
  - `LocaleHolderTest`：14 个测试，覆盖 ThreadLocal 存储、线程隔离
  - `JobImportExportApiTest`：9 个测试，覆盖 REST API 接口（list、exportJob、exportFolder、exportAll、progress、updateJob）
- **测试框架**：使用 JUnit 5（junit-jupiter-api/junit-jupiter-engine 5.10.0）

### 修复

- **进度条不动（致命错误）**：
  - 修复 `ExecutionEngine.addResult()` 方法内部递归调用自己导致 `StackOverflowError`，后台线程崩溃后进度永远不会更新
  - 修复 `JobImportExportAction/index.jelly` 中 SSE URL 路径重复问题（`${it.getUrlName()}/progress` 在已包含 `urlName` 的页面路径下导致 404）
  - 修复 `ImportProgress` 所有字段缺少 `volatile`，导致跨线程写入后读线程看不到新值
  - 修复 `doProgress` SSE 端点用 while 循环阻塞 Jetty 请求线程 120 秒，多页面同时导入时线程池耗尽
  - 将 SSE 长连接改为短轮询 JSON 接口，前端用 `setInterval` 每 800ms 轮询，每次请求立即返回不阻塞线程
- **No permission to create job**：修复后台线程中 `Authentication` 上下文丢失问题，在 `new Thread()` 启动前保存当前用户认证，线程内恢复，线程结束时清理
- **预演阶段卡住**：修复 `completeProgress()` 和 `setResult()` 之间的竞态条件——`completeProgress` 先设置 `status="DONE"`，前端轮询命中 `status==DONE` 但 `resultReady==false`，执行提前停止轮询；将 `status`、`overallProgress`、`resultReady` 合并到 `setResult()` 的同一个 `synchronized` 方法中原子设置
- **预演阶段显示跳转按钮**：修复预演（dryRun）完成后也显示跳转按钮的问题，增加 `!result.dryRun` 条件
- **跳转链接包含多余路径**：修复 `redirectUrl` 使用相对路径导致前端 `location.href` 解析错误的问题，改为使用 `Jenkins.get().getRootUrl() + url` 绝对路径；redirect 指向导入目标 `target` 而非 `item`

## [2.0.0] - 2026-05-16

### 新增功能

- **国际化支持（i18n）**：页面语言兼容英语与中文，根据浏览器语言自动适配当前页面语言显示
  - 新增 `index.properties` / `index_zh_CN.properties` 页面资源文件
  - 新增 `Messages.properties` / `Messages_zh_CN.properties` Java 代码资源文件
  - 前端 JavaScript i18n 对象支持动态语言切换
- **导入进度条显示**：导入任务弹窗中加入动画进度条，实时显示导入进度
- **导出时间戳**：导出文件名后追加导出时间（格式：`任务名_yyyy-MM-dd_HH-mm-ss.zip`），由后端生成服务器本地时间
- **子目录批量导出优化**：导出的 ZIP 包名为当前任务名，当前任务的配置导出为 `config.xml` 文件存放在 ZIP 包中当前任务的目录名下
- **批量导入功能增强**：
  - 根目录下批量导入：如果 ZIP 包根目录有 `config.xml` 直接丢弃
  - 子目录任务下批量导入：如果 ZIP 包根目录存在 `config.xml`，使用这个配置更新当前所在的目录任务配置
  - 在预演和导入结果中显示目录任务配置更新条目（`UPDATE_CONFIG` 状态）
- **权限分级显示**：根目录菜单功能按权限显示，`Item.CREATE` 权限显示批量导入，`Jenkins.ADMINISTER` 权限显示所有功能，无权限则不显示菜单
- **导出计数优化**：子目录下批量导出时，当前目录的配置也作为成功条目计入导出结果
- **批量导出选项**：新增「包含当前目录配置」复选框（默认不勾选），不勾选时仅导出子任务，且 ZIP 包路径不包含当前目录文件夹

### 性能优化

- **大幅优化导入性能**：删除了 `ExecutionEngine.java` 中每个任务创建后的 `Jenkins.get().reload()` 调用，改为只在批量导入完成后调用一次，导入大量任务时性能显著提升

### 修复

- **Jelly 文件语法错误**：修复了 `JobImportExportSidebarLink/index.jelly` 中多余的 `</div>` 标签导致的 XML 解析失败
- **页面 404 问题**：添加了 `getIndex()` 方法确保 Stapler 正确解析视图路径
- **Jelly XML 解析失败**：修复 `<script>` 标签内裸露的 `<` 比较运算符导致 XML 解析失败，页面显示 404 的问题
- **缺失 Java 方法**：补充 `JobImportExportAction` 中缺失的 `canImportJobs()`、`canCreateJob()`、`hasAdminPermission()` 方法，修复 Jelly 渲染异常
- **导出文件名时间戳重复**：修复前端和后端同时添加时间戳导致导出 ZIP 文件名出现两次时间的问题
- **代码冗余清理**：删除了重复的 `PathResolver.java` 文件（保留 `/engine/resolver/PathResolver.java`）

### 变更

- **权限控制细化**：`JobImportExportSidebarLink.isVisible()` 改为只在用户有 `Item.CREATE` 或 `Jenkins.ADMINISTER` 权限时显示菜单
- **新增状态枚举**：添加 `UPDATE_CONFIG` 状态用于显示目录任务配置更新操作
- **新增导入上下文字段**：`applyRootConfigToCurrentFolder`、`currentFolderItem`、`rootConfigResults` 用于控制根目录 config.xml 的处理逻辑

### 架构重构（ImportEngine v2）

将原有的 `if/else + zip遍历脚本` 模式升级为 **Tree + DAG + Execution Engine + State Machine** 架构：

**新增核心组件：**
- **ImportEngine** - 统一导入入口，协调 TreeBuilder 和 ExecutionEngine
- **ExecutionEngine** - 核心执行引擎，深度优先遍历树节点
- **PreviewEngine** - 预览引擎，复用同一引擎实现 `preview == import`
- **ZipTreeBuilder** - 树形结构构建器，将 ZIP 条目转换为树形结构
- **TypeResolver** - 类型解析器，统一判断 JOB/FOLDER 类型
- **PathResolver** - 路径解析器，处理重命名映射和级联传播
- **ImportStateStore** - 断点恢复状态存储

**新增模型类：**
- `Node.java` - 树节点结构（替换所有 ZipEntry 逻辑）
- `NodeType.java` - 节点类型枚举（FOLDER/JOB）
- `RenameRule.java` - 重命名规则
- `DiffResult.java` - 预览差异结果

**删除旧代码：**
- `ZipImportService`
- `JobImportExportService`
- `PreviewService`
- `FolderUtil`
- 旧 model/* 文件

**关键设计原则：**
- **TreeBuilder（结构）**：将线性 ZIP 条目转换为树形结构
- **Resolver（类型）**：统一的类型和路径解析入口
- **Engine（执行）**：统一的递归执行引擎
- **Context（状态）**：集中式状态管理（renameMap、createdFolders、dryRun）
- **Preview == Import**：预览和导入复用同一引擎，通过 `dryRun` 模式区分

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

- **预演阶段路径传播修复**
  - 修复预演阶段父任务重命名后子任务路径未同步更新的问题
  - 修复 `finalName` 字段生成逻辑，确保使用应用重命名映射后的完整路径
  - 修复预演模式下虚拟目录状态缓存，支持父任务重命名后的子任务正确检测
  - 修复后预演弹窗正确显示：`test/jobs/test → test_6/jobs/test`

## [1.0.2] - 上一版本

### 新增功能
- 单任务导入/导出功能
- 任务配置 XML 文件上传
- 任务名称验证
- 文件夹支持组织任务

### 功能
- 导出当前任务/文件夹配置为 XML
- 更新现有任务/文件夹配置
- 批量导入任务（支持 ZIP 文件）
- 创建/导入操作的权限检查

## [1.0.1] - 初始版本

### 新增功能
- 基础任务导入/导出插件
- Jenkins 侧边栏链接全局访问
- 单个任务的项目级操作
