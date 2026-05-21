# Changelog

All notable changes to this project will be documented in this file.

## [1.0.2] - 2026-05-21

### 新增

- 添加共享线程池管理类 `JobImportExportThreadPool`
- 优化并发性能，支持多页面同时导入/导出/更新操作

### 优化

- `JobImportExportAction` 的 `doExport`、`doUpdate`、`doImport` 方法改为通过线程池执行
- `JobImportExportSidebarLink` 的 `doExport`、`doImport` 方法改为通过线程池执行

### 线程池配置

| 参数 | 值 | 说明 |
|------|-----|------|
| 核心线程数 | 4 | 常驻线程，避免频繁创建销毁 |
| 最大线程数 | 8 | 高峰期可扩容 |
| 队列容量 | 64 | 有界队列，防止内存溢出 |
| 空闲回收 | 60s | 超出核心数的线程空闲后自动回收 |
| 拒绝策略 | CallerRunsPolicy | 队列满时由调用线程执行 |
| 守护线程 | true | JVM 退出时自动终止 |

### 性能改进

- 避免线程无限增长导致的内存泄漏
- 防止多个用户同时操作时的线程阻塞
- 统一管理并发任务，提高系统稳定性

## [1.0.1] - 2026-05-XX

### 修复

- 修复中文任务名导入/导出问题
- 修复 XML 控制字符清理逻辑
- 修复权限检查逻辑

### 优化

- 改进错误处理机制
- 统一 JSON 响应格式

## [1.0.0] - 2026-05-XX

### 初始版本

- 支持任务配置导出
- 支持任务配置更新
- 支持任务导入
- 支持中文任务名
- 支持多级文件夹导入
