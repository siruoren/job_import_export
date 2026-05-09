# Jenkins 任务导入导出插件 (Job Import/Export Plugin)

一个 Jenkins 插件，用于便捷地导入、导出和更新 Jenkins 任务的 XML 配置。

---

## 功能概览

| 功能 | 入口 | 说明 |
|------|------|------|
| **导出配置** | 每个任务/文件夹页面 | 将当前任务或文件夹的配置导出为 XML 文件 |
| **更新配置** | 每个任务/文件夹页面 | 上传新的 XML 配置文件覆盖当前配置 |
| **导入新任务** | 文件夹页面 | 在当前文件夹下创建新任务（支持子文件夹） |
| **全局导入任务** | 左侧边栏 | 从侧边栏直接导入任务，支持指定路径 |

---

## 安装方法

### 方式一：直接安装 HPI 文件

1. 在项目根目录下执行：
   ```bash
   mvn clean package -Denforcer.skip=true -DskipTests
   ```
2. 生成的插件文件位于：`target/job-import-export.hpi`
3. 进入 Jenkins **Manage Jenkins** → **Plugins** → **Advanced settings**
4. 点击 **Deploy Plugin**，上传 `job-import-export.hpi` 文件
5. 重启 Jenkins 使插件生效

### 方式二：手动构建

确保本地已安装 JDK 17 和 Maven：

```bash
source ~/.bashrc  # 加载 Maven 环境变量
mvn clean package -Denforcer.skip=true -DskipTests
```

构建产物：`target/job-import-export.hpi`

---

## 使用说明

### 1. 导出当前配置

在任意 **Job** 或 **Folder** 页面，点击 **导入/导出配置** 菜单，选择 **导出配置** 按钮即可下载当前配置的 XML 文件。

> **中文文件名支持**：导出的 XML 文件名会保留任务完整名称，支持中文文件名（如 `测试Pipeline.xml`、`发布-生产环境.xml`），并自动处理 Windows 文件系统非法字符。

### 2. 更新配置

在任意 **Job** 或 **Folder** 页面：
1. 点击 **导入/导出配置**
2. 选择 **更新配置**
3. 上传 XML 配置文件
4. 可选：勾选 **强制替换**（当 XML 文件类型与当前任务类型不匹配时使用）
5. 点击 **更新配置**
6. 弹窗确认：点击「确认」提交更新，点击「取消」返回页面

> **注意**：如果 XML 配置的类型与当前任务类型不匹配（例如尝试用 Freestyle 的配置覆盖 Pipeline 任务），系统会显示友好提示页面。勾选「强制替换」可绕过类型检查直接写入配置文件。
>
> **权限要求**：更新配置需要当前用户具有 `Item.CONFIGURE` 权限，权限不足时会提示"请更换具有相应权限的登录用户"。
>
> **重复任务名**：如果导入的任务名称已存在，页面会提示"任务名称已存在"，并提供「返回重新命名」和「进入任务更新配置」两个选项。

### 3. 导入新任务到当前目录

仅在 **Folder 页面** 显示此功能：
1. 进入目标文件夹
2. 点击 **导入/导出配置**
3. 在 **导入新任务到当前目录** 区域：
   - 输入新任务的名称
   - 上传 XML 配置文件
4. 点击 **导入任务**
5. 弹窗确认：点击「确认」提交创建，点击「取消」返回页面

> **中文任务名支持**：完全支持中文任务名称（如 `测试Pipeline`、`发布-生产环境`、`服务_订单中心`）。插件内部使用 RFC 5987 标准处理 URL 编码，确保中文路径在浏览器、Jenkins 内嵌 Jetty 和 Folder 嵌套场景下均能正确工作。
>
> **权限要求**：导入新任务需要当前用户具有 `Item.CREATE` 权限，权限不足时会提示"请更换具有相应权限的登录用户"。
>
> **重复任务名**：如果该目录下已存在同名任务，页面会提示"任务名称已存在"，并提供「返回重新命名」和「进入任务更新配置」两个选项。

### 4. 全局导入任务（侧边栏）

通过 Jenkins 左侧边栏的 **任务导入/导出** 入口，可以在任意页面直接导入新任务：
- 支持使用 `"folder/job"` 格式指定目标路径
- 支持自动创建父文件夹
- 上传 XML 配置文件后 Jenkins 会自动重载

> **权限要求**：导出需要 `Item.READ` 权限，导入需要目标目录的 `Item.CREATE` 权限。
>
> **重复任务名**：如果指定路径已存在同名任务，会显示友好提示并提供「返回重新命名」和「进入任务更新配置」选项。

---

## 功能显示规则

| 类型                 | 导入/导出菜单 | 导入新任务 |
| ------------------ | ------- | ----- |
| 所有 AbstractItem    | ✅       | 按下面规则 |
| 根目录 Folder         | ✅       | ✅     |
| 子 Folder           | ✅       | ✅     |
| Multibranch        | ✅       | ❌     |
| OrganizationFolder | ✅       | ❌     |
| ComputedFolder     | ✅       | ❌     |
| 根目录 Job            | ✅       | ❌     |
| Folder 内 Job       | ✅       | ❌     |
| Freestyle          | ✅       | ❌     |
| Pipeline           | ✅       | ❌     |
| Matrix             | ✅       | ❌     |


**核心规则**：
- ✅ 所有任务/文件夹都显示 **导入/导出配置** 菜单
- ✅ 只有 **Folder**（根目录和子目录）才显示 **导入新任务** 功能
- ❌ 所有 **Job** 类型页面均不显示 **导入新任务**
- ❌ **特殊 Folder**（Multibranch、OrganizationFolder、ComputedFolder）不显示 **导入新任务**

**兼容性**：
| 浏览器              | 中文文件名 |
| ---------------- | ----- |
| Chrome           | ✅     |
| Edge             | ✅     |
| Safari           | ✅     |
| Firefox          | ✅     |
| Jenkins 内嵌 Jetty | ✅     |
| Windows          | ✅     |
| Linux            | ✅     |
| macOS            | ✅     |



---

## 技术栈

- **Jenkins 版本**：2.479.2
- **JDK 版本**：17
- **构建工具**：Maven 3.x
- **打包格式**：`.hpi`（Jenkins 插件标准格式）
- **核心依赖**：Jenkins Core API、Stapler Web 框架

---

## 项目结构

```
job_import_export/
├── pom.xml                                    # Maven 构建配置
├── README.md                                  # 本文档
└── src/
    └── main/
        ├── java/com/example/jobimportexport/
        │   ├── JobImportExportAction.java     # 任务导入导出 Action（页面级）
        │   └── JobImportExportSidebarLink.java # 侧边栏全局入口
        └── resources/
            └── com/example/jobimportexport/
                ├── JobImportExportAction/
                │   └── index.jelly             # 任务/文件夹页面 UI
                └── JobImportExportSidebarLink/
                    └── index.jelly             # 侧边栏全局导入页面 UI
```

---

## 核心类说明

### `JobImportExportAction`

绑定到每个 `AbstractItem`（Job/Folder）页面的 Action，提供以下功能：
- `doExport()` — 导出当前配置的 XML 文件
- `doUpdate()` — 更新当前配置，支持类型不匹配时的友好提示
- `doImport()` — 在父目录下创建新任务
- `canImportJobs()` — 控制「导入新任务」区域的显示

### `JobImportExportSidebarLink`

Jenkins 根级别的 `RootAction`，在左侧边栏提供全局入口：
- 支持直接导入任务到指定路径（如 `folder/subfolder/job`）
- 导入后自动触发 Jenkins 重载

---

## 常见问题

### Q: 为什么某些页面看不到「导入新任务」？

只有 **Folder** 类型的页面才会显示「导入新任务」功能。Job 页面（包括 Freestyle、Pipeline 等）仅显示导出和更新功能。这是设计上的安全限制，避免在 Job 页面误操作创建新任务。

### Q: 为什么导入/导出支持中文任务名？

本插件针对 Jenkins 中文场景做了专项优化：
- **HTTP Header 编码**：导出文件名使用 RFC 5987 标准（`filename*=`），兼容现代浏览器和 IE
- **URL 重定向编码**：导入后重定向使用 `Util.rawEncode()` 处理中文路径
- **请求参数解码**：使用 `ISO-8859-1` → `UTF-8` 重新转码（ Stapler 默认解析问题）
- **Windows 文件名安全**：自动替换 `\\\\/*?\"<>|` 等非法字符
- **Jelly 页面编码**：所有表单添加 `accept-charset="UTF-8"`，Jelly 页面添加 `escape-by-default`

这些修复确保在 Tomcat、Jetty、Windows Jenkins、Folder 嵌套等复杂环境下中文均能正常工作。

### Q: 更新配置时提示「任务类型不匹配」怎么办？

如果确认要强制覆盖（例如将 Freestyle Job 改为 Pipeline Job），请勾选 **强制替换** 选项后重试。系统会直接替换 `config.xml` 文件并刷新页面。

### Q: 提示「当前用户无权限」怎么办？

本插件对所有操作都进行了权限检查：

| 操作 | 所需权限 | 提示信息 |
|------|---------|---------|
| 导出配置 | `Item.READ` | 请更换具有 Item.READ 权限的登录用户 |
| 更新配置 | `Item.CONFIGURE` | 请更换具有 Item.CONFIGURE 权限的登录用户 |
| 导入新任务 | `Item.CREATE` | 请更换具有 Item.CREATE 权限的登录用户 |

请联系 Jenkins 管理员为您分配相应权限，或切换到有权限的用户账号进行操作。

### Q: 导入时提示「任务名称已存在」怎么办？

如果目标目录下已存在同名任务，页面会显示友好提示，提供两个选项：
- **返回重新命名** — 使用新的任务名称重新导入
- **进入任务更新配置** — 跳转到已有任务的导入/导出页面，通过「更新配置」功能覆盖其配置

---

## 构建与开发

```bash
# 清理并构建
mvn clean package -Denforcer.skip=true -DskipTests

# 仅编译
mvn compile

# 运行测试（需要完整 Jakarta Servlet API 环境）
mvn test
```

---

## 许可证

MIT License

---

## 维护者

- 项目归属：`com.example:job-import-export`
- 版本：`1.0.0-SNAPSHOT`