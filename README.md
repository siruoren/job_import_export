# Jenkins 任务导入导出插件 (Job Import/Export Plugin)

一个 Jenkins 插件，用于便捷地导入、导出和更新 Jenkins 任务的 XML 配置。

---

## 功能概览

| 功能 | 入口 | 说明 | 权限要求 |
|------|------|------|---------|
| **导出配置** | 每个任务/文件夹页面 | 将当前任务或文件夹的配置导出为 XML 文件 | `Item.READ` |
| **更新配置** | 每个任务/文件夹页面 | 上传新的 XML 配置文件覆盖当前配置 | `Item.CONFIGURE` |
| **导入新任务** | 文件夹页面 | 在当前文件夹下创建新任务（支持子文件夹） | `Item.CREATE` |
| **全局导入任务** | 左侧边栏 | 从侧边栏直接导入任务，支持指定路径 | `Item.CREATE` |

---

## 安装方法

### 方式一：直接安装 HPI 文件

1. 在项目根目录下执行：
   ```bash
   mvn clean package -Denforcer.skip=true -DskipTests
   ```
2. 生成的插件文件位于：`target/job-import-export-{version}.hpi`（如 `target/job-import-export-1.0.2.hpi`）
3. 进入 Jenkins **Manage Jenkins** → **Plugins** → **Advanced settings**
4. 点击 **Deploy Plugin**，上传 `job-import-export-{version}.hpi` 文件
5. 重启 Jenkins 使插件生效

### 方式二：手动构建

确保本地已安装 JDK 17 和 Maven：

```bash
source ~/.bashrc  # 加载 Maven 环境变量
mvn clean package -Denforcer.skip=true -DskipTests
```

构建产物：`target/job-import-export-{version}.hpi`（带版本号）

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
> **任务名自动清洗**：输入的任务名会自动去除前后空格、全角空格（`\u3000`）和不间断空格（`\u00A0`），并进行合法性校验（仅禁止文件系统危险字符和控制字符，**完全支持中文任务名**）。不合法字符会提示"任务名称不合法"。
>
> **权限要求**：导入新任务需要当前用户具有 `Item.CREATE` 权限，权限不足时会提示"请更换具有相应权限的登录用户"。
>
> **重复任务名**：如果该目录下已存在同名任务，页面会提示"任务名称已存在"，并提供「返回重新命名」和「进入任务更新配置」两个选项。

### 4. 全局导入任务（侧边栏）

通过 Jenkins 左侧边栏的 **任务导入/导出** 入口，可以在任意页面直接导入新任务：
- 支持使用 `"folder/job"` 格式指定目标路径
- 支持自动创建父文件夹
- 上传 XML 配置文件后 Jenkins 会自动重载

> **权限要求**：导出需要 `Item.READ` 权限，全局导入需要 Jenkins 根目录的 `Item.CREATE` 权限。无权限时页面不显示对应功能入口。
>
> **任务名自动清洗**：输入的任务名会自动去除前后空格、全角空格和不间断空格，并进行合法性校验（仅禁止危险字符和控制字符，**完全支持中文任务名**）。
>
> **重复任务名**：如果指定路径已存在同名任务，会显示友好提示并提供「返回重新命名」和「进入任务更新配置」选项。

---

## 功能显示规则

### 按页面类型

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

### 按用户权限

| 功能 | 所在页面 | 所需权限 | 无权限时的表现 |
|------|---------|---------|--------------|
| 更新配置 | Job/Folder 页面 | `Item.CONFIGURE` | 页面不显示「更新配置」区域 |
| 导入新任务 | Folder 页面 | `Item.CREATE` | 页面不显示「导入新任务」区域 |
| 全局导入任务 | 侧边栏页面 | `Item.CREATE` | 页面不显示「导入任务配置」区域 |
| 导出配置 | 所有页面 | `Item.READ` | 始终显示（后端接口仍做权限校验）|

**兼容性**：
| 浏览器/部署方式       | 中文文件名 | 非 ROOT 部署 |
| ------------------- | ----- | ----------- |
| Chrome              | ✅     | ✅          |
| Edge                | ✅     | ✅          |
| Safari              | ✅     | ✅          |
| Firefox             | ✅     | ✅          |
| Jenkins 内嵌 Jetty  | ✅     | ✅          |
| Tomcat 部署         | ✅     | ✅          |
| Windows             | ✅     | ✅          |
| Linux               | ✅     | ✅          |
| macOS               | ✅     | ✅          |


**行为规则**：
| 场景            | 行为             |
| ------------- | -------------- |
| 更新普通 Job      | 自动 reload + 跳转 |
| 更新 Folder     | 自动 reload + 跳转 |
| 强制替换类型        | 自动 reload + 跳转 |
| 更新后 rename    | 自动进入新名称页面      |
| 中文任务名         | 正常             |
| Folder 内 Job  | 正常             |
| Pipeline Job  | 正常             |
| Freestyle Job | 正常             |

---

## 技术架构

### 后端统一 JSON 协议

本插件采用统一的 JSON 响应协议，确保前后端通信一致：

```json
{
  "success": true,
  "message": "操作成功",
  "redirect": "/job/myjob"
}
```

**响应字段说明**：
- `success` - 操作是否成功（布尔值）
- `message` - 提示信息（字符串）
- `redirect` - 重定向 URL（成功时返回，失败时为 null）

**后端实现原则**：
- 所有接口统一返回 JSON 格式，错误信息通过 Body 返回，**绝不写入 HTTP Header**
- `sendError()` 已全面替换为 `writeJson()`（避免 Tomcat 将中文错误信息塞入 HTTP Header 导致 `Unicode字符无法编码` 异常）
- 所有 Action 方法（`doExport`/`doUpdate`/`doImport`）外层均有 `try-catch(Exception e)` 兜底，确保任何异常都不会冒泡到 Jenkins 默认错误处理（`AbstractModelObject/error.jelly`），防止 Stapler 自动往 Header 写入中文异常信息
- `writeJson()` 封装响应前显式调用 `rsp.setCharacterEncoding("UTF-8")`，确保 `getWriter()` 使用 UTF-8 而非容器默认的 ISO-8859-1
- 请求端统一调用 `req.setCharacterEncoding("UTF-8")`，直接从 Stapler 获取 UTF-8 参数，不再进行 ISO-8859-1 中转码

### 前端防御性解析

前端采用防御性 JSON 解析策略，确保即使后端返回非 JSON 内容也能优雅处理：

```javascript
async function safePost(form) {
    const res = await fetch(form.action, {
        method: 'POST',
        body: new FormData(form)
    });
    
    const text = await res.text();
    
    let data;
    try {
        data = JSON.parse(text);
    } catch (e) {
        showToast('error', '服务器返回非JSON：' + text);
        return;
    }
    
    showToast(data.success ? 'success' : 'error', data.message);
    
    if (data.success && data.redirect) {
        setTimeout(() => {
            window.location.href = data.redirect;
        }, 300);
    }
}
```

### Toast 组件

采用标准可控生命周期的悬浮提示组件：
- ✅ 自动消失（默认 10 秒）
- ✅ 可点击手动关闭
- ✅ 平滑动画过渡
- ✅ 支持成功/错误/普通三种类型

### 页面布局

采用横向三栏布局设计：

**任务/文件夹页面**（JobImportExportAction）：
- 第一栏：导出当前配置
- 第二栏：更新当前配置（需 `Item.CONFIGURE` 权限，无权限显示空白）
- 第三栏：导入新任务（仅 Folder 且需 `Item.CREATE` 权限，无权限显示空白）

**侧边栏全局页面**（JobImportExportSidebarLink）：
- 第一栏：空白
- 第二栏：导入任务配置（需 `Item.CREATE` 权限，无权限显示空白）
- 第三栏：空白

布局特性：
- 使用 Flexbox 布局，三栏等宽分配
- 有功能显示内容，无功能显示空白占位
- 自适应容器宽度，响应式设计

### 任务创建后的安全流程

创建任务后执行以下三步确保 Jenkins 完全就绪：

1. **Save** - 确保配置持久化到磁盘
2. **Sync Reload** - 调用 `Jenkins.get().reload()` 同步重新加载（确保路由注册完成）
3. **Safe Redirect** - 使用 `Jenkins.get().getRootUrl() + item.getUrl()` 生成完整的绝对重定向 URL（Jenkins 内部已处理编码、路径规则和 context path，同时兼容反向代理和 HTTPS）

### 中文任务名处理机制

插件对中文任务名采用特殊处理，确保正确传递到 Jenkins：

1. **编码转换**：获取任务名后强制进行 ISO-8859-1 → UTF-8 转换，解决 Servlet 默认解码问题
2. **有效性验证**：通过 UTF-8 编码往返验证确保任务名编码正确
3. **兼容性处理**：兼容浏览器发送 UTF-8 但 Servlet 默认用 ISO-8859-1 解码的场景

**处理流程**：
```
浏览器输入中文 → UTF-8 编码发送 → Servlet 用 ISO-8859-1 解码（乱码）→ 强制 ISO-8859-1→UTF-8 转换 → 恢复正确中文 → Jenkins 创建任务
```

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
- `doUpdate()` — 更新当前配置，支持类型不匹配时的友好提示；成功后使用 `Jenkins.get().getRootUrl() + refreshedItem.getUrl()` 生成安全的重定向 URL
- `doImport()` — 在父目录下创建新任务；成功后使用 `Jenkins.get().getRootUrl() + newItem.getUrl()` 生成安全的重定向 URL
- `canImportJobs()` — 控制「导入新任务」区域的显示（按类型）
- `canCreateJob()` — 控制「导入新任务」区域的显示（按 `Item.CREATE` 权限）
- `hasPermission()` — 控制「更新配置」区域的显示（按 `Item.CONFIGURE` 权限）
- `writeJson()` — 统一 JSON 响应封装

### `JobImportExportSidebarLink`

Jenkins 根级别的 `RootAction`，在左侧边栏提供全局入口：
- `doExport()` — 全局导出任务配置
- `doImport()` — 全局导入任务，支持指定路径；成功后使用 `Jenkins.get().getRootUrl() + newItem.getUrl()` 生成安全的重定向 URL
- `canCreateJob()` — 控制「导入任务配置」区域的显示（按 `Item.CREATE` 权限）
- `writeJson()` — 统一 JSON 响应封装

---

## 常见问题

### Q: 为什么某些页面看不到「导入新任务」？

「导入新任务」的显示受两个条件限制：
1. **页面类型**：只有 **Folder** 类型的页面才会显示此功能。Job 页面（包括 Freestyle、Pipeline 等）不显示。
2. **用户权限**：当前用户必须拥有目标目录的 `Item.CREATE` 权限。权限不足时，即使 Folder 页面也不会显示该功能入口。

同理，「更新配置」功能需要当前用户对目标任务拥有 `Item.CONFIGURE` 权限，无权限时页面不会显示该区域。

### Q: 为什么导入/导出支持中文任务名？

本插件针对 Jenkins 中文场景做了专项优化：
- **HTTP Header 编码**：导出文件名使用 RFC 5987 标准（`filename*=`），兼容现代浏览器和 IE
- **URL 重定向**：导入/更新后重定向统一使用 `Jenkins.get().getRootUrl() + item.getUrl()` 生成完整绝对 URL（Jenkins 内部已处理编码、路径规则、context path 和反向代理），避免手动拼接导致的重复编码或特殊字符丢失
- **请求参数编码**：`doImport` 显式调用 `req.setCharacterEncoding("UTF-8")`，直接从 Stapler 获取 UTF-8 参数，不再进行 ISO-8859-1 → UTF-8 中转码
- **响应编码安全**：`writeJson()` 封装响应前显式调用 `rsp.setCharacterEncoding("UTF-8")`，确保 `getWriter()` 使用 UTF-8 而非容器默认的 ISO-8859-1，彻底避免中文乱码
- **HTTP Header 中文隔离**：所有错误提示统一通过 JSON Body 返回，**绝不往 HTTP Header 写入中文**，避免 Tomcat 因 Header 仅支持 ISO-8859-1 而抛出 `Unicode字符无法编码` 异常
- **Windows 文件名安全**：自动替换 `\\/*?"<>|` 等非法字符
- **Jelly 页面编码**：所有表单添加 `accept-charset="UTF-8"`，Jelly 页面添加 `escape-by-default`

这些修复确保在 Tomcat、Jetty、Windows Jenkins、Folder 嵌套、Multibranch 等复杂环境下中文均能正常工作。

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

### Q: 导入时提示「任务名称不合法」怎么办？

插件在导入新任务时会自动清洗和校验任务名称：
- **自动去除**：前后普通空格、全角空格（中文输入法空格）、不间断空格（`&nbsp;`）
- **合法性校验**：使用插件内置安全规则校验，**完全支持中文任务名**，仅禁止以下危险字符和模式：
  - 文件系统危险字符：`\`、`/`、`*`、`?`、`"`、`>`、`<`、`|`、`:`
  - 控制字符（ASCII 0-31）
  - 长度超过 200 个字符
  - 空字符串或纯空格

**解决方法**：使用符合常规文件命名规范的任务名称。中文、字母、数字、下划线、连字符、点号等均为安全字符，可正常使用。

### Q: 导入时提示「任务名称已存在」怎么办？

如果目标目录下已存在同名任务，页面会显示友好提示，提供两个选项：
- **重新命名** — 使用新的任务名称重新导入
- **进入任务更新配置** — 跳转到已有任务的导入/导出页面，通过「更新配置」功能覆盖其配置

### Q: 为什么导入后有时会出现 404？

Jenkins 创建任务后，路由注册存在异步延迟。本插件已实现企业级安全流程：
1. 创建任务后调用 `save()` 确保持久化
2. 调用 `Jenkins.get().reload()` 同步重新加载（确保 Jenkins 完全注册新任务路由）
3. 使用 `Jenkins.get().getRootUrl() + newItem.getUrl()` 生成完整绝对重定向 URL（Jenkins 内部已处理编码、路径规则和 context path，同时兼容反向代理和 HTTPS）

前端收到重定向后会延迟 300ms 再跳转，确保 Jenkins 完全就绪。

早期版本手动拼接 `/job/xxx`、使用 `req.getContextPath()` 或对 URL 进行二次编码，导致中文任务名在 Tomcat 下出现 `%25` 双重编码或路径错误，现已统一使用 `Jenkins.get().getRootUrl() + item.getUrl()` 修复。

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

本插件采用**自定义非商业许可**：

- ✅ 允许自由使用、复制、修改和二次分发
- ❌ **禁止用于任何商业用途**（包括但不限于产生收入、盈利或以商业目的的活动）
- 使用本插件须保留原始版权声明

如需商业使用授权，请联系项目维护者。

---

## 维护者

- 项目归属：`com.example:job-import-export`
- 版本：`1.0.2`