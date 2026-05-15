# Jenkins 任务导入导出插件 (Job Import/Export Plugin)

一个功能强大的 Jenkins 插件，支持便捷地导入、导出和更新 Jenkins 任务的 XML 配置，提供批量操作、Dry Run 预演、多层目录支持等企业级功能。

---

## 功能特性

### 核心功能

| 功能 | 说明 | 入口 |
|------|------|------|
| 导出当前配置 | 将当前任务/文件夹配置导出为 XML 文件 | 每个任务/文件夹页面 |
| 更新配置 | 上传 XML 配置文件覆盖当前任务配置 | 每个任务/文件夹页面 |
| 导入新任务 | 在当前文件夹下创建新任务 | 文件夹页面 |
| 全局导入任务 | 从侧边栏直接导入任务 | 左侧边栏 |
| 批量导入任务 | 上传 ZIP 批量导入多个任务，支持多层目录 | 左侧边栏 |
| 导出全部任务 | 导出 Jenkins 根目录下所有任务（仅管理员） | 左侧边栏 |
| 批量导出任务 | 导出当前目录及所有子任务配置 | 文件夹页面 |

### 批量导入/导出增强功能

#### 批量导入
- 支持目录结构和扁平结构：`folder/job/config.xml` 或 `job.xml`
- 支持 UTF-8 编码，中文目录和任务名完美支持
- 冲突处理三模式：**跳过** / **覆盖** / **重命名**
- Dry Run 预演模式，导入前预览结果
- 实时进度显示（SSE 流）
- 导入失败恢复机制（断点续传）
- 插件依赖自动检测
- **根目录 config.xml 处理**：
  - 根目录导入：丢弃 ZIP 根目录的 `config.xml`
  - 子目录导入：使用 ZIP 根目录 `config.xml` 更新当前目录任务配置

#### 批量导出
- ZIP 包名为当前任务名
- 可选包含当前目录配置（默认不包含）
- 不包含当前目录时，子任务直接放在 ZIP 根目录
- 逐级权限检查，无权限任务自动跳过

### 安全特性

| 特性 | 说明 |
|------|------|
| Folder 删除保护 | overwrite 模式下对 Folder 使用 `updateByXml`，避免递归删除子任务 |
| 动态目录保护 | 禁止覆盖 Multibranch、ComputedFolder 等动态生成的目录 |
| 路径状态缓存 | 批量导入时维护任务存在性快照，避免状态断层 |
| 冲突传播机制 | 上游冲突自动阻断后续路径创建，防止级联错误 |
| 覆盖前备份 | 自动备份现有配置为 `config.xml.bak` |
| 权限分级控制 | 菜单和功能按用户权限显示 |

---

## 安装方法

### 方式一：直接安装 HPI 文件

1. 构建插件：
   ```bash
   mvn clean package -Denforcer.skip=true -DskipTests
   ```
2. 生成的插件文件位于：`target/job-import-export-{version}.hpi`
3. 进入 Jenkins → Manage Jenkins → Plugins → Advanced settings
4. 点击 Deploy Plugin，上传 `job-import-export-{version}.hpi` 文件
5. 重启 Jenkins 使插件生效

### 方式二：手动构建

确保本地已安装 JDK 17 和 Maven：

```bash
source ~/.bashrc  # 加载 Maven 环境变量
mvn clean package -Denforcer.skip=true -DskipTests
```

---

## 使用说明

### 1. 导出当前配置

在任意 Job 或 Folder 页面，点击 **导入/导出配置** → **导出配置** 按钮即可下载当前配置的 XML 文件。

> 支持中文文件名，自动处理 Windows 文件系统非法字符（`\\/*?"<>|`）

### 2. 更新配置

1. 在 Job 或 Folder 页面，点击 **导入/导出配置**
2. 选择 **更新配置**
3. 上传 XML 配置文件
4. 点击 **更新配置**

> 如果 XML 配置类型与当前任务类型不匹配，系统会显示友好提示

### 3. 批量导入任务

1. **准备 ZIP 文件**：
   - 支持目录结构：`folder/subfolder/job/config.xml`
   - 支持扁平结构：`job.xml`
   - 支持中文目录和任务名

2. **选择导入选项**：
   - Dry Run（预演）：默认开启，验证任务但不实际创建
   - 冲突处理：
     - 不处理冲突（默认）：跳过已存在的任务
     - 覆盖模式：覆盖已存在的任务并自动备份
     - 重命名模式：自动重命名冲突任务（`test` → `test_1`）

3. **导入流程**：
   - Dry Run 预览 → 确认对话框 → 实际导入
   - 实时显示导入结果（成功/失败/跳过数量）

### 4. 根目录 config.xml 处理

| 导入场景 | ZIP 根目录 config.xml 处理方式 |
|---------|------------------------------|
| 根目录导入 | 直接丢弃 |
| 子目录导入 | 使用此配置更新当前目录任务配置 |

### 5. 批量导出任务配置

1. 在 Folder 页面，点击 **导入/导出配置** → **批量导出任务**
2. 可选：勾选「包含当前目录配置」（默认不勾选）
   - 勾选：导出内容包括当前目录本身和所有子任务
   - 不勾选：仅导出子任务，ZIP 路径不包含当前目录文件夹
3. 点击 **执行导出**，自动下载 ZIP 文件

### 6. 导出全部任务（管理员）

通过左侧边栏 **任务导入/导出** 入口，管理员可以导出 Jenkins 根目录下所有任务。

---

## 技术架构

### 架构设计

本插件采用 **Tree + DAG + Execution Engine + State Machine** 架构模式：

```
┌─────────────────────────────────────────────────────────────────┐
│                      ImportEngine（统一入口）                    │
│  ┌──────────────┐    ┌──────────────────┐                      │
│  │ ZipTreeBuilder│───▶│  ExecutionEngine │                      │
│  │ （结构构建）   │    │   （递归执行）     │                      │
│  └──────────────┘    └────────┬─────────┘                      │
│                               │                                │
│         ┌─────────────────────┼─────────────────────┐          │
│         ▼                     ▼                     ▼          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │ TypeResolver │    │ PathResolver │    │ ImportContext│     │
│  │ （类型解析）   │    │ （路径解析）   │    │ （状态管理）   │     │
│  └──────────────┘    └──────────────┘    └──────────────┘     │
└─────────────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 职责 |
|------|------|
| ImportEngine | 统一导入入口，协调 ZipTreeBuilder 和 ExecutionEngine |
| ExportEngine | 导出引擎，支持包含/排除当前目录配置选项 |
| ExecutionEngine | 递归执行引擎，深度优先遍历树节点 |
| PreviewEngine | 预览引擎，通过 dryRun 模式实现预演 |
| ZipTreeBuilder | 将 ZIP 条目转换为树形结构 |
| TypeResolver | 根据 `hasConfigXml` 判断节点是 JOB 还是 FOLDER |
| PathResolver | 处理重命名映射，支持级联传播 |
| ImportContext | 集中管理 renameMap、createdFolders、dryRun 等状态 |

### 关键设计原则

- **TreeBuilder（结构）**：将线性 ZIP 条目转换为树形结构
- **Engine（执行）**：统一的递归执行引擎
- **Context（状态）**：集中式状态管理
- **Preview == Import**：预览和导入复用同一引擎，通过 `dryRun` 模式区分
- **Checkpoint（断点）**：支持导入失败后的恢复重试

---

## 项目结构

```
job_import_export/
├── pom.xml                                    # Maven 构建配置
├── README.md                                  # 本文档
├── CHANGELOG.md                               # 变更日志
└── src/
    └── main/
        ├── java/com/siruoren/jobimportexport/
        │   ├── JobImportExportAction.java     # 任务导入导出 Action
        │   ├── JobImportExportSidebarLink.java # 侧边栏全局入口
        │   └── engine/
        │       ├── ImportEngine.java          # 统一导入入口
        │       ├── ExportEngine.java          # 导出引擎
        │       ├── ExecutionEngine.java       # 执行引擎
        │       ├── PreviewEngine.java         # 预览引擎
        │       ├── diff/
        │       │   └── DryRunDiffEngine.java # 差异计算
        │       ├── model/                     # 数据模型
        │       │   ├── Action.java
        │       │   ├── Diff.java
        │       │   ├── DiffResult.java
        │       │   ├── DryRunResult.java
        │       │   ├── ExportResult.java
        │       │   ├── ImportContext.java
        │       │   ├── ImportResult.java
        │       │   ├── MissingConfigReport.java
        │       │   ├── Node.java
        │       │   ├── NodeAction.java
        │       │   ├── NodeType.java
        │       │   ├── RenameRule.java
        │       │   ├── Status.java
        │       │   └── TreeNode.java
        │       ├── tree/
        │       │   ├── TreeBuilder.java
        │       │   └── ZipTreeBuilder.java
        │       ├── resolver/
        │       │   ├── PathResolver.java
        │       │   ├── RenameDAGResolver.java
        │       │   └── TypeResolver.java
        │       ├── scanner/
        │       │   └── ConfigScanner.java
        │       └── state/
        │           └── ImportStateStore.java
        └── resources/
            ├── index.jelly
            └── com/siruoren/jobimportexport/
                ├── JobImportExportAction/
                │   └── index.jelly
                └── JobImportExportSidebarLink/
                    └── index.jelly
```

---

## 权限说明

| 功能 | 所需权限 |
|------|---------|
| 导出配置 | Item.READ |
| 更新配置 | Item.CONFIGURE |
| 导入新任务 | Item.CREATE |
| 全局批量导入 | Item.CREATE |
| 导出全部任务 | Jenkins.ADMINISTER |
| 批量导出任务 | Item.READ（每个任务）|

### 侧边栏权限分级

| 用户权限 | 显示内容 |
|---------|---------|
| Jenkins.ADMINISTER | 导出全部任务 + 批量导入任务 |
| Item.CREATE（非管理员） | 仅批量导入任务 |
| 无权限 | 菜单不显示 |

---

## 常见问题

### Q: 为什么批量导入支持多层目录结构？

插件采用 Tree + ExecutionEngine 架构，将 ZIP 文件解析为树形结构后深度优先遍历执行导入：
- 支持任意深度的嵌套目录
- 支持父目录重命名后自动级联传播到子任务路径
- 支持目录类型检测（有无 config.xml）
- 支持预演模式下的虚拟目录状态缓存

### Q: 覆盖已存在的任务会备份吗？

是的，覆盖模式会自动备份现有配置。备份文件命名为 `config.xml.bak`，与原配置文件在同一目录下。

### Q: 导入时提示「任务类型不匹配」怎么办？

当导入的目录（无 config.xml）与已存在的普通任务同名时，系统会报告类型不匹配错误。这是为了防止将普通任务误覆盖为目录，或反之。

### Q: 批量导入的结果统计中，「成功」「跳过」「失败」是如何定义的？

| 分类 | 包含的状态 |
|------|-----------|
| 成功 | CREATE_FOLDER、CREATE_JOB、OVERWRITE_FOLDER、OVERWRITE_JOB、UPDATE_CONFIG |
| 跳过 | SKIP_EXISTS、SKIP_EMPTY、REUSE_FOLDER、RENAME_FOLDER、RENAME_JOB |
| 失败 | ERROR |

---

## 技术栈

- Jenkins 版本：2.479.2
- JDK 版本：17
- 构建工具：Maven 3.x
- 打包格式：`.hpi`（Jenkins 插件标准格式）

---

## 构建与开发

```bash
# 清理并构建
mvn clean package -Denforcer.skip=true -DskipTests

# 仅编译
mvn compile

# 运行测试
mvn test
```

---

## 许可证

MIT License

---

## 维护者

- 项目：com.siruoren:job-import-export
- 版本：2.0.0
