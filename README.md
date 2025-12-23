# ContiNew Generator MCP

<div align="center">

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M1-blue)
![Java](https://img.shields.io/badge/Java-17+-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-green)

**基于 Spring AI MCP 协议的智能代码生成器**

[English](#english) | [中文](#中文)

</div>

---

## 中文

### 简介

ContiNew Generator MCP 是一个基于 **Spring AI MCP (Model Context Protocol)** 协议的智能代码生成服务。它允许 AI 助手（如 Claude、Cursor 等）通过标准化的 MCP 协议调用代码生成能力，实现：

- 自动分析数据库表结构
- 生成完整的后端 CRUD 代码（Controller、Service、Mapper、Entity 等）
- 生成前端页面代码（Vue3 + TypeScript + Arco Design）
- 支持单表、联表查询、主子表（一对多）等复杂场景
- 自动生成菜单权限 SQL

### 特性

- **MCP 协议支持**：与任何支持 MCP 协议的 AI 客户端无缝集成
- **模板驱动**：使用 FreeMarker 模板引擎，易于定制和扩展
- **多场景支持**：
  - 单表 CRUD
  - 联表查询（JOIN）
  - 主子表一对多
  - 多表业务聚合
- **完全独立**：零业务依赖，可服务于任何 Java + Vue 技术栈项目

### 快速开始

#### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

#### 2. 配置数据库

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

#### 3. 启动服务

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/continew-generator-mcp-1.0.0.jar
```

服务默认运行在 `http://localhost:8091`

#### 4. 配置 AI 客户端

以 Cursor 为例，在 MCP 配置中添加：

```json
{
  "mcpServers": {
    "continew-generator": {
      "url": "http://localhost:8091/sse"
    }
  }
}
```

### 可用工具列表

| 工具名称 | 说明 |
|---------|------|
| `listTables` | 获取数据库所有表列表 |
| `getTableColumns` | 获取表字段结构 |
| `getTableDesignRules` | 获取表设计规范 |
| `generateCreateTableSql` | 生成建表 SQL |
| `executeSql` | 执行 SQL 语句 |
| `checkTableExists` | 检查表是否存在 |
| `analyzeBusinessRelation` | 分析表关系 |
| `previewBackendCode` | 预览后端代码 |
| `writeBackendCode` | 写入后端代码 |
| `writeBackendCodeWithRelations` | 写入带关联的后端代码 |
| `writeFrontendCode` | 写入前端代码 |
| `generateMenuSql` | 生成菜单权限 SQL |
| `generateDirectoryMenuSql` | 生成一级目录菜单 SQL |
| `listMenus` | 获取系统菜单列表 |
| `listDicts` | 获取系统字典列表 |
| `configureProjectPaths` | 配置项目路径 |
| `scanProjectStructure` | 扫描项目结构 |
| `getProjectPaths` | 获取当前路径配置 |
| `getFrontendSpecification` | 获取前端代码规范 |
| `getBackendSpecification` | 获取后端代码规范 |
| `getGenerationGuide` | 获取完整生成指南 |
| `getApiInfo` | 获取 API 接口信息 |
| `generateBusinessPageInfo` | 生成业务聚合页面信息 |
| `generateMasterDetailPage` | 生成主子表页面 |
| `validateGeneratedCode` | 验证生成的代码 |
| `writeFile` | 写入文件 |
| `readFile` | 读取文件 |

### 项目结构

```
continew-generator-mcp/
├── src/main/java/top/continew/admin/mcp/
│   ├── McpServerApplication.java      # 启动类
│   ├── config/
│   │   └── McpToolConfig.java         # MCP 工具配置
│   ├── model/
│   │   ├── FieldConfig.java           # 字段配置
│   │   ├── GeneratorContext.java      # 生成上下文
│   │   ├── ProjectPathConfig.java     # 项目路径配置
│   │   └── RelationConfig.java        # 关联配置
│   ├── service/
│   │   └── TemplateService.java       # 模板服务
│   └── tool/
│       └── GeneratorTools.java        # MCP 工具实现
├── src/main/resources/
│   ├── application.yml                # 应用配置
│   └── templates/                     # 代码模板
│       ├── backend/                   # 后端模板
│       │   ├── Controller.ftl
│       │   ├── Service.ftl
│       │   ├── ServiceImpl.ftl
│       │   ├── Mapper.ftl
│       │   ├── Entity.ftl
│       │   └── ...
│       └── frontend/                  # 前端模板
│           ├── index.ftl
│           ├── AddModal.ftl
│           ├── DetailDrawer.ftl
│           └── api.ftl
└── pom.xml
```

### 自定义模板

模板文件位于 `src/main/resources/templates/` 目录，使用 FreeMarker 语法。

**可用变量**：

| 变量名 | 说明 |
|--------|------|
| `${tableName}` | 表名 |
| `${businessName}` | 业务名称（中文） |
| `${classNamePrefix}` | 类名前缀 |
| `${moduleName}` | 模块名 |
| `${packagePrefix}` | 包名前缀 |
| `${fieldConfigs}` | 字段配置列表 |
| `${author}` | 作者 |
| `${date}` | 生成日期 |

### 技术栈

- **后端框架**：Spring Boot 3.5.0
- **AI 协议**：Spring AI MCP 2.0.0-M1
- **模板引擎**：FreeMarker 2.3.34
- **工具库**：Hutool 5.8.35
- **数据库**：MySQL 8.0+

### 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

---

## English

### Introduction

ContiNew Generator MCP is an intelligent code generation service based on the **Spring AI MCP (Model Context Protocol)** protocol. It enables AI assistants (such as Claude, Cursor, etc.) to invoke code generation capabilities through the standardized MCP protocol.

### Features

- **MCP Protocol Support**: Seamlessly integrates with any AI client supporting MCP protocol
- **Template-Driven**: Uses FreeMarker template engine, easy to customize and extend
- **Multiple Scenarios**:
  - Single table CRUD
  - Join queries
  - Master-detail (one-to-many)
  - Multi-table business aggregation
- **Fully Independent**: Zero business dependencies, can serve any Java + Vue tech stack project

### Quick Start

#### 1. Requirements

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

#### 2. Configure Database

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

#### 3. Run Service

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/continew-generator-mcp-1.0.0.jar
```

Service runs on `http://localhost:3000` by default.

#### 4. Configure AI Client

For Cursor, add to MCP configuration:

```json
{
  "mcpServers": {
    "continew-generator": {
      "url": "http://localhost:3000/sse"
    }
  }
}
```

### License

This project is licensed under the [Apache License 2.0](LICENSE).

---

<div align="center">

**基于 [ContiNew](https://github.com/continew-org/continew-admin) 项目打造**

[![GitHub](https://img.shields.io/badge/GitHub-continew--admin-blue?logo=github)](https://github.com/continew-org/continew-admin)

</div>
