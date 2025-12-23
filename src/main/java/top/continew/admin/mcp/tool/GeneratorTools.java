package top.continew.admin.mcp.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import top.continew.admin.mcp.model.FieldConfig;
import top.continew.admin.mcp.model.GeneratorContext;
import top.continew.admin.mcp.model.ProjectPathConfig;
import top.continew.admin.mcp.model.RelationConfig;
import top.continew.admin.mcp.service.TemplateService;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 代码生成器 MCP 工具类
 *
 * <p>提供以下能力：</p>
 * <ul>
 *   <li>执行 SQL（建表、加字段、菜单权限等）</li>
 *   <li>获取数据库表列表</li>
 *   <li>获取表结构（字段信息）</li>
 *   <li>预览后端代码</li>
 *   <li>获取 API 接口信息（供 AI 生成前端代码）</li>
 *   <li>生成菜单 SQL</li>
 *   <li>支持关联表生成（JOIN/一对多/多对多）</li>
 * </ul>
 *
 * @author AI Generator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeneratorTools {

    private final JdbcTemplate jdbcTemplate;
    private final TemplateService templateService;
    private final ProjectPathConfig projectPathConfig;

    /**
     * SQL 白名单：只允许执行的 SQL 类型（不区分大小写）
     */
    private static final Set<String> ALLOWED_SQL_PREFIXES = Set.of(
        "INSERT", "CREATE", "ALTER", "UPDATE", "SELECT"
    );

    /**
     * SQL 黑名单：禁止执行的危险关键字
     */
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
        "(?i)(DROP\\s+(TABLE|DATABASE|INDEX)|TRUNCATE\\s+TABLE|DELETE\\s+FROM\\s+\\S+\\s*$|GRANT|REVOKE)"
    );

    // ================== 流程指南 ==================

    /**
     * 获取业务代码生成完整流程指南
     */
    @Tool(description = "【必须首先调用】获取业务代码生成的完整流程指南。当用户要求生成业务代码时，AI 必须先调用此工具了解完整流程，然后按步骤执行")
    public String getGenerationGuide() {
        return """
## 业务代码生成完整流程

### 核心原则（必须遵循）
```
┌─────────────────────────────────────────────────────────────────┐
│  后端：按表拆分生成     │  前端：按业务聚合生成                    │
│  每个表独立 CRUD       │  一个业务一个页面（多表数据聚合展示）       │
│  保持代码解耦          │  通过调用多个 API 聚合数据                │
└─────────────────────────────────────────────────────────────────┘
```

### 第一步：路径配置（首次生成时执行）
```
scanProjectStructure(projectRoot)  →  扫描项目结构，获取推荐路径
configureProjectPaths(...)         →  确认并配置路径
```

### 第二步：分析业务需求
```
listTables()                       →  查看现有表
checkTableExists(tableName)        →  检查表是否存在
getTableDesignRules()              →  获取表设计规范（如需建表）
generateCreateTableSql(...)        →  生成建表SQL（如需建表）
executeSql(sql)                    →  执行建表SQL
```

### 第三步：分析表关系（多表场景）
```
analyzeBusinessRelation(mainTable) →  分析表关系，推荐生成策略

场景判断：
- 单表：直接生成
- JOIN联表：外键关联，查询时自动关联展示
- 一对多：主子表关系（如：订单-订单明细）
```

### 第四步：生成后端代码（每个表独立生成）
```
单表场景：
  writeBackendCode(projectRoot, tableName, businessName, moduleName)

带联表查询场景（推荐）：
  writeBackendCodeWithRelations(projectRoot, tableName, businessName, moduleName, relationsJson)
  → 自动在 Resp 添加关联字段
  → 自动在 Mapper XML 生成 JOIN 查询

多表业务示例（用车管理）：
  writeBackendCodeWithRelations(root, "biz_vehicle", "车辆信息", "vehicle", null)
  writeBackendCodeWithRelations(root, "biz_vehicle_dispatch", "车辆调度", "vehicle", 
    "[{\"type\":\"JOIN\",\"targetTable\":\"biz_vehicle\",\"sourceColumn\":\"vehicle_id\",\"displayColumns\":[\"plate_number\",\"brand\"]}]")
```

### 第五步：生成前端代码（按业务聚合，一个页面）
```
获取业务页面信息（关键！）：
  generateBusinessPageInfo(businessName, moduleName, tablesJson, displayMode)
  → 返回多表聚合的 API 信息、字段配置、页面结构建议

获取规范：
  getApiInfo(tableName, ...)        →  获取单表 API 详情
  getFrontendSpecification()        →  获取前端代码规范

页面展示模式选择：
  - TAB：Tab 分页切换展示多表（如：车辆信息 Tab + 车辆调度 Tab）
  - MASTER_DETAIL：主子表同页展示（如：订单详情页包含订单明细列表）
  - SINGLE：单表或简单聚合

开发页面（AI 手动编写）：
  根据 generateBusinessPageInfo 返回的信息，AI 编写聚合页面：
  - index.vue：业务主页面（聚合展示所有表数据）
  - AddModal.vue：新增/编辑弹窗
  - DetailDrawer.vue：详情抽屉
  - api.ts：聚合多表 API 定义

写入文件：
  writeFile(filePath, content)      →  写入前端文件
```

### 第六步：生成菜单权限（必须正确配置！）
```
listMenus()                        →  获取现有菜单，判断父菜单ID

新模块场景（需要创建一级目录菜单）：
  generateDirectoryMenuSql(菜单名, 模块名, 路由名, 图标, 跳转路径, 排序)  →  生成一级目录菜单
  executeSql(sql)                  →  执行一级目录菜单SQL
  generateMenuSql(表名, 业务名, 模块名, 一级菜单ID)  →  生成二级菜单+按钮
  executeSql(sql)                  →  执行二级菜单SQL

现有模块场景（父菜单已存在）：
  generateMenuSql(表名, 业务名, 模块名, 父菜单ID)  →  生成二级菜单+按钮
  executeSql(sql)                  →  执行菜单SQL

重要提示：
- 一级目录菜单的 component 必须是 'Layout'，否则页面空白
- 二级菜单的 component 必须与 Vue 文件路径匹配（如 bicycle/manage/index）
- 修改菜单后需要重新登录（后端有 Redis 缓存）
```

### 第七步：验证
```
validateGeneratedCode(...)         →  验证生成的代码
```

---
## 重要原则总结

| 维度 | 策略 | 说明 |
|------|------|------|
| 后端代码 | 按表拆分 | 每个表独立的 Entity/Service/Controller，保持解耦 |
| 前端页面 | 按业务聚合 | 一个业务一个页面，页面内调用多个 API 聚合数据 |
| 菜单入口 | 按业务入口 | 只建主业务菜单，子表数据在页面内展示 |
| 权限控制 | 按业务统一 | 同一业务使用相同权限前缀，子表可复用主表权限 |
| 联表查询 | 在后端实现 | 使用 writeBackendCodeWithRelations 自动生成 JOIN 查询 |

## 工具快速参考

| 工具 | 用途 |
|------|------|
| writeBackendCode | 写入单表后端代码 |
| writeBackendCodeWithRelations | 写入带联表查询的后端代码（推荐） |
| generateBusinessPageInfo | 生成业务聚合页面信息（前端开发必用） |
| writeFile | 写入 AI 手动开发的前端代码 |
| generateMenuSql | 生成菜单 SQL（只需主业务） |
""";
    }

    // ================== 路径配置相关工具 ==================

    /**
     * 配置项目路径
     */
    @Tool(description = "配置代码生成的目标路径（后端路径、前端路径、SQL输出路径等）。在开始生成代码前，必须先调用此工具询问用户代码存放位置")
    public String configureProjectPaths(
        @ToolParam(description = "后端代码根路径，如: continew-system/src/main/java/top/continew/admin") String backendRootPath,
        @ToolParam(description = "后端包名前缀，如: top.continew.admin") String backendPackagePrefix,
        @ToolParam(description = "Mapper XML 路径，如: continew-system/src/main/resources/mapper") String mapperXmlPath,
        @ToolParam(description = "前端代码根路径，如: continew-admin-ui/src") String frontendRootPath,
        @ToolParam(description = "SQL 输出路径，如: continew-server/src/main/resources/db/changelog/sql") String sqlOutputPath
    ) {
        log.info("调用 configureProjectPaths，配置项目路径");
        
        if (StrUtil.isNotBlank(backendRootPath)) {
            projectPathConfig.setBackendRootPath(backendRootPath);
        }
        if (StrUtil.isNotBlank(backendPackagePrefix)) {
            projectPathConfig.setBackendPackagePrefix(backendPackagePrefix);
        }
        if (StrUtil.isNotBlank(mapperXmlPath)) {
            projectPathConfig.setMapperXmlPath(mapperXmlPath);
        }
        if (StrUtil.isNotBlank(frontendRootPath)) {
            projectPathConfig.setFrontendRootPath(frontendRootPath);
        }
        if (StrUtil.isNotBlank(sqlOutputPath)) {
            projectPathConfig.setSqlOutputPath(sqlOutputPath);
        }
        projectPathConfig.setConfigured(true);
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目路径配置已更新\n\n");
        sb.append("| 配置项 | 值 |\n");
        sb.append("|--------|-------|\n");
        sb.append("| 后端代码根路径 | `").append(projectPathConfig.getBackendRootPath()).append("` |\n");
        sb.append("| 后端包名前缀 | `").append(projectPathConfig.getBackendPackagePrefix()).append("` |\n");
        sb.append("| Mapper XML 路径 | `").append(projectPathConfig.getMapperXmlPath()).append("` |\n");
        sb.append("| 前端代码根路径 | `").append(projectPathConfig.getFrontendRootPath()).append("` |\n");
        sb.append("| SQL 输出路径 | `").append(projectPathConfig.getSqlOutputPath()).append("` |\n");
        sb.append("\n**注意**: 请确认以上路径正确后再开始生成代码。");
        
        log.info("项目路径配置完成");
        return sb.toString();
    }

    /**
     * 重置路径配置
     */
    @Tool(description = "重置项目路径配置为默认值")
    public String resetPathConfig() {
        log.info("调用 resetPathConfig，重置路径配置");
        projectPathConfig.reset();
        return "项目路径配置已重置为默认值。请重新调用 configureProjectPaths 配置代码存放路径。";
    }

    /**
     * 智能扫描项目结构推荐路径
     */
    @Tool(description = "智能扫描项目目录结构，自动推断并推荐前后端代码存放路径。扫描后返回推荐配置，用户确认后可直接使用")
    public String scanProjectStructure(
        @ToolParam(description = "项目根目录绝对路径，如: C:/projects/continew-admin") String projectRoot
    ) {
        log.info("调用 scanProjectStructure，扫描项目结构: {}", projectRoot);
        
        if (StrUtil.isBlank(projectRoot)) {
            return "请提供项目根目录路径";
        }
        
        java.io.File root = new java.io.File(projectRoot);
        if (!root.exists() || !root.isDirectory()) {
            return "项目目录不存在: " + projectRoot;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目结构扫描结果\n\n");
        
        // 扫描后端模块
        String backendRoot = null;
        String backendPackage = "top.continew.admin";
        String mapperXmlPath = null;
        String sqlOutputPath = null;
        
        // 常见后端模块名称
        String[] backendModules = {"continew-system", "continew-module-system", "src/main/java"};
        for (String module : backendModules) {
            java.io.File moduleDir = new java.io.File(root, module);
            if (moduleDir.exists()) {
                // 查找 java 源码目录
                java.io.File javaDir = new java.io.File(moduleDir, "src/main/java/top/continew/admin");
                if (javaDir.exists()) {
                    backendRoot = module + "/src/main/java/top/continew/admin";
                    // 查找 mapper xml
                    java.io.File mapperDir = new java.io.File(moduleDir, "src/main/resources/mapper");
                    if (mapperDir.exists()) {
                        mapperXmlPath = module + "/src/main/resources/mapper";
                    }
                    break;
                }
            }
        }
        
        // 扫描 SQL 输出路径
        java.io.File serverDir = new java.io.File(root, "continew-server/src/main/resources/db/changelog/sql");
        if (serverDir.exists()) {
            sqlOutputPath = "continew-server/src/main/resources/db/changelog/sql";
        }
        
        // 扫描前端目录
        String frontendRoot = null;
        String[] frontendDirs = {"continew-admin-ui", "admin-ui", "frontend", "web"};
        for (String dir : frontendDirs) {
            java.io.File feDir = new java.io.File(root, dir + "/src");
            if (feDir.exists()) {
                frontendRoot = dir + "/src";
                break;
            }
        }
        
        // 输出扫描结果
        sb.append("### 探测到的路径\n");
        sb.append("| 配置项 | 探测结果 |\n");
        sb.append("|--------|----------|\n");
        sb.append("| 后端代码根路径 | `").append(backendRoot != null ? backendRoot : "未探测到").append("` |\n");
        sb.append("| 后端包名前缀 | `").append(backendPackage).append("` |\n");
        sb.append("| Mapper XML 路径 | `").append(mapperXmlPath != null ? mapperXmlPath : "未探测到").append("` |\n");
        sb.append("| 前端代码根路径 | `").append(frontendRoot != null ? frontendRoot : "未探测到").append("` |\n");
        sb.append("| SQL 输出路径 | `").append(sqlOutputPath != null ? sqlOutputPath : "未探测到").append("` |\n");
        
        // 自动配置探测到的路径
        boolean hasValidPath = false;
        if (backendRoot != null) {
            projectPathConfig.setBackendRootPath(backendRoot);
            projectPathConfig.setBackendPackagePrefix(backendPackage);
            hasValidPath = true;
        }
        if (mapperXmlPath != null) {
            projectPathConfig.setMapperXmlPath(mapperXmlPath);
        }
        if (frontendRoot != null) {
            projectPathConfig.setFrontendRootPath(frontendRoot);
            hasValidPath = true;
        }
        if (sqlOutputPath != null) {
            projectPathConfig.setSqlOutputPath(sqlOutputPath);
        }
        // 如果探测到有效路径，标记为已配置
        if (hasValidPath) {
            projectPathConfig.setConfigured(true);
        }
        
        sb.append("\n### 下一步\n");
        sb.append("请向用户确认以上探测结果是否正确，如需调整请调用 `configureProjectPaths` 修改。\n");
        
        log.info("项目结构扫描完成");
        return sb.toString();
    }

    // ================== 代码写入工具 ==================

    /**
     * 检查路径是否已配置，未配置则返回错误提示
     */
    private String checkPathConfigured() {
        if (!projectPathConfig.isConfigured()) {
            StringBuilder sb = new StringBuilder();
            sb.append("## ⚠️ 路径未配置，无法写入代码\n\n");
            sb.append("请先询问用户代码应该生成到哪个目录，然后调用 `configureProjectPaths` 配置路径。\n\n");
            sb.append("### 当前默认路径（仅供参考，不可直接使用）\n");
            sb.append("| 配置项 | 默认值 |\n");
            sb.append("|--------|--------|\n");
            sb.append("| 后端代码路径 | `").append(projectPathConfig.getBackendRootPath()).append("` |\n");
            sb.append("| Mapper XML 路径 | `").append(projectPathConfig.getMapperXmlPath()).append("` |\n");
            sb.append("| 前端代码路径 | `").append(projectPathConfig.getFrontendRootPath()).append("` |\n");
            sb.append("| SQL 输出路径 | `").append(projectPathConfig.getSqlOutputPath()).append("` |\n\n");
            sb.append("### 下一步\n");
            sb.append("1. 询问用户：\"请问代码要生成到哪个目录？\"\n");
            sb.append("2. 调用 `scanProjectStructure(projectRoot)` 扫描项目结构\n");
            sb.append("3. 向用户确认路径后，调用 `configureProjectPaths(...)` 配置\n");
            sb.append("4. 然后再调用写入方法\n");
            return sb.toString();
        }
        return null;
    }

    /**
     * 将生成的后端代码写入文件
     */
    @Tool(description = "将生成的后端代码写入到项目目录。写入前必须先调用 configureProjectPaths 配置路径")
    public String writeBackendCode(
        @ToolParam(description = "项目根目录绝对路径") String projectRoot,
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文）") String businessName,
        @ToolParam(description = "模块名") String moduleName,
        @ToolParam(description = "作者名（可选）", required = false) String author
    ) {
        log.info("调用 writeBackendCode，表名：{}，模块：{}", tableName, moduleName);
        
        // 强制检查路径配置
        String pathError = checkPathConfigured();
        if (pathError != null) {
            return pathError;
        }
        
        try {
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, author);
            Map<String, String> codes = templateService.previewBackend(ctx);
            
            String backendRoot = projectRoot + "/" + projectPathConfig.getBackendRootPath() + "/" + moduleName;
            String mapperXmlRoot = projectRoot + "/" + projectPathConfig.getMapperXmlPath();
            
            List<String> writtenFiles = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : codes.entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();
                String targetPath = resolveBackendFilePath(backendRoot, mapperXmlRoot, fileName, ctx.getClassNamePrefix());
                
                java.io.File file = new java.io.File(targetPath);
                file.getParentFile().mkdirs();
                java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                writtenFiles.add(targetPath);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("## 后端代码已写入\n\n");
            sb.append("共写入 ").append(writtenFiles.size()).append(" 个文件：\n");
            for (String path : writtenFiles) {
                sb.append("- `").append(path).append("`\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("写入后端代码失败", e);
            return "写入后端代码失败：" + e.getMessage();
        }
    }
    
    private String resolveBackendFilePath(String backendRoot, String mapperXmlRoot, String fileName, String classNamePrefix) {
        if (fileName.endsWith(".xml")) {
            return mapperXmlRoot + "/" + classNamePrefix + "Mapper.xml";
        }
        String subDir = switch (fileName) {
            case "Entity.java" -> "model/entity";
            case "Req.java" -> "model/req";
            case "Resp.java", "DetailResp.java" -> "model/resp";
            case "Query.java" -> "model/query";
            case "Mapper.java" -> "mapper";
            case "Service.java" -> "service";
            case "ServiceImpl.java" -> "service/impl";
            case "Controller.java" -> "controller";
            default -> "";
        };
        String realFileName = fileName.replace(".java", "");
        if (realFileName.equals("Entity")) realFileName = classNamePrefix + "DO";
        else realFileName = classNamePrefix + realFileName;
        return backendRoot + "/" + subDir + "/" + realFileName + ".java";
    }

    /**
     * 将生成的前端代码写入文件（仅用于简单 CRUD 场景，复杂场景建议 AI 手动开发）
     */
    @Tool(description = "【简单 CRUD 场景可用】基于模板生成前端基础代码。对于多表聚合、复杂业务页面，应使用 getApiInfo + getFrontendSpecification 获取信息后，由 AI 根据前端规范手动开发，然后用 writeFile 写入")
    public String writeFrontendCode(
        @ToolParam(description = "项目根目录绝对路径") String projectRoot,
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文）") String businessName,
        @ToolParam(description = "模块名") String moduleName
    ) {
        log.info("调用 writeFrontendCode，表名：{}，模块：{}", tableName, moduleName);
        
        // 强制检查路径配置
        String pathError = checkPathConfigured();
        if (pathError != null) {
            return pathError;
        }
        
        try {
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, null);
            Map<String, String> codes = templateService.previewFrontend(ctx);
            
            String viewsRoot = projectRoot + "/" + projectPathConfig.getFrontendRootPath() + "/views/" + ctx.getApiModuleName() + "/" + ctx.getApiName();
            String apiRoot = projectRoot + "/" + projectPathConfig.getFrontendRootPath() + "/apis/" + ctx.getApiModuleName();
            
            List<String> writtenFiles = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : codes.entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();
                String targetPath;
                
                if (fileName.endsWith(".ts")) {
                    targetPath = apiRoot + "/" + ctx.getApiName() + ".ts";
                } else {
                    targetPath = viewsRoot + "/" + fileName;
                }
                
                java.io.File file = new java.io.File(targetPath);
                file.getParentFile().mkdirs();
                java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                writtenFiles.add(targetPath);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("## 前端代码已写入\n\n");
            sb.append("共写入 ").append(writtenFiles.size()).append(" 个文件：\n");
            for (String path : writtenFiles) {
                sb.append("- `").append(path).append("`\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("写入前端代码失败", e);
            return "写入前端代码失败：" + e.getMessage();
        }
    }

    /**
     * 将菜单 SQL 写入文件
     */
    @Tool(description = "将生成的菜单权限 SQL 写入到项目 SQL 目录")
    public String writeMenuSql(
        @ToolParam(description = "项目根目录绝对路径") String projectRoot,
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文）") String businessName,
        @ToolParam(description = "模块名") String moduleName,
        @ToolParam(description = "父菜单 ID") Long parentMenuId
    ) {
        log.info("调用 writeMenuSql，表名：{}，父菜单ID：{}", tableName, parentMenuId);
        
        // 强制检查路径配置
        String pathError = checkPathConfigured();
        if (pathError != null) {
            return pathError;
        }
        
        try {
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, null);
            String sql = templateService.generateMenuSql(ctx, parentMenuId);
            
            String sqlPath = projectRoot + "/" + projectPathConfig.getSqlOutputPath() + "/menu_" + moduleName + "_" + ctx.getApiName() + ".sql";
            
            java.io.File file = new java.io.File(sqlPath);
            file.getParentFile().mkdirs();
            java.nio.file.Files.writeString(file.toPath(), sql, java.nio.charset.StandardCharsets.UTF_8);
            
            return "## 菜单 SQL 已写入\n\n文件路径: `" + sqlPath + "`\n\n" + "```sql\n" + sql + "\n```";
        } catch (Exception e) {
            log.error("写入菜单 SQL 失败", e);
            return "写入菜单 SQL 失败：" + e.getMessage();
        }
    }

    /**
     * 通用文件写入工具
     */
    @Tool(description = "将 AI 生成的代码内容写入到指定文件。写入前必须先询问用户代码生成位置，并调用 configureProjectPaths 配置路径")
    public String writeFile(
        @ToolParam(description = "文件绝对路径，如: C:/project/src/views/coupon/index.vue") String filePath,
        @ToolParam(description = "文件内容") String content
    ) {
        log.info("调用 writeFile，路径：{}", filePath);
        
        // 强制检查路径配置
        String pathError = checkPathConfigured();
        if (pathError != null) {
            return pathError;
        }
        
        if (StrUtil.isBlank(filePath)) {
            return "文件路径不能为空";
        }
        if (StrUtil.isBlank(content)) {
            return "文件内容不能为空";
        }
        
        try {
            java.io.File file = new java.io.File(filePath);
            
            // 创建父目录
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            
            // 写入文件
            java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            
            log.info("文件写入成功：{}", filePath);
            return "✅ 文件写入成功\n\n文件路径: `" + filePath + "`\n\n内容长度: " + content.length() + " 字符";
        } catch (Exception e) {
            log.error("写入文件失败: {}", filePath, e);
            return "写入文件失败：" + e.getMessage();
        }
    }

    /**
     * 读取文件内容
     */
    @Tool(description = "读取指定文件的内容，用于参考现有代码或检查文件是否存在")
    public String readFile(
        @ToolParam(description = "文件绝对路径") String filePath
    ) {
        log.info("调用 readFile，路径：{}", filePath);
        
        if (StrUtil.isBlank(filePath)) {
            return "文件路径不能为空";
        }
        
        try {
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                return "文件不存在: " + filePath;
            }
            
            String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("文件读取成功：{}，长度: {} 字符", filePath, content.length());
            return content;
        } catch (Exception e) {
            log.error("读取文件失败: {}", filePath, e);
            return "读取文件失败：" + e.getMessage();
        }
    }

    // ================== 代码验证工具 ==================

    /**
     * 验证生成的代码
     */
    @Tool(description = "验证生成的代码是否存在基本错误（检查文件是否存在、基本语法等）")
    public String validateGeneratedCode(
        @ToolParam(description = "项目根目录绝对路径") String projectRoot,
        @ToolParam(description = "模块名") String moduleName,
        @ToolParam(description = "类名前缀，如 Coupon") String classNamePrefix
    ) {
        log.info("调用 validateGeneratedCode，模块：{}，类名前缀：{}", moduleName, classNamePrefix);
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 代码验证结果\n\n");
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> passed = new ArrayList<>();
        
        // 检查后端文件
        String backendRoot = projectRoot + "/" + projectPathConfig.getBackendRootPath() + "/" + moduleName;
        String[][] backendFiles = {
            {"controller", classNamePrefix + "Controller.java"},
            {"service", classNamePrefix + "Service.java"},
            {"service/impl", classNamePrefix + "ServiceImpl.java"},
            {"mapper", classNamePrefix + "Mapper.java"},
            {"model/entity", classNamePrefix + "DO.java"},
            {"model/req", classNamePrefix + "Req.java"},
            {"model/resp", classNamePrefix + "Resp.java"},
            {"model/query", classNamePrefix + "Query.java"}
        };
        
        for (String[] fileInfo : backendFiles) {
            String path = backendRoot + "/" + fileInfo[0] + "/" + fileInfo[1];
            java.io.File file = new java.io.File(path);
            if (file.exists()) {
                passed.add("后端: " + fileInfo[1]);
            } else {
                errors.add("后端文件不存在: " + path);
            }
        }
        
        // 检查 Mapper XML
        String mapperXmlPath = projectRoot + "/" + projectPathConfig.getMapperXmlPath() + "/" + classNamePrefix + "Mapper.xml";
        if (new java.io.File(mapperXmlPath).exists()) {
            passed.add("Mapper XML: " + classNamePrefix + "Mapper.xml");
        } else {
            warnings.add("Mapper XML 不存在: " + mapperXmlPath);
        }
        
        // 检查前端文件
        String apiName = StrUtil.toUnderlineCase(classNamePrefix).replace("_", "-");
        String viewsRoot = projectRoot + "/" + projectPathConfig.getFrontendRootPath() + "/views/" + moduleName + "/" + apiName;
        String[] frontendFiles = {"index.vue", "AddModal.vue", "DetailDrawer.vue"};
        
        for (String fileName : frontendFiles) {
            String path = viewsRoot + "/" + fileName;
            if (new java.io.File(path).exists()) {
                passed.add("前端: " + fileName);
            } else {
                errors.add("前端文件不存在: " + path);
            }
        }
        
        // 检查 API 文件
        String apiPath = projectRoot + "/" + projectPathConfig.getFrontendRootPath() + "/apis/" + moduleName + "/" + apiName + ".ts";
        if (new java.io.File(apiPath).exists()) {
            passed.add("前端 API: " + apiName + ".ts");
        } else {
            errors.add("API 文件不存在: " + apiPath);
        }
        
        // 输出结果
        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("✅ **所有文件验证通过**\n\n");
        } else {
            if (!errors.isEmpty()) {
                sb.append("### ❌ 错误\n");
                for (String error : errors) {
                    sb.append("- ").append(error).append("\n");
                }
                sb.append("\n");
            }
            if (!warnings.isEmpty()) {
                sb.append("### ⚠️ 警告\n");
                for (String warning : warnings) {
                    sb.append("- ").append(warning).append("\n");
                }
                sb.append("\n");
            }
        }
        
        sb.append("### ✅ 已通过 (").append(passed.size()).append(" 个文件)\n");
        for (String p : passed) {
            sb.append("- ").append(p).append("\n");
        }
        
        return sb.toString();
    }

    // ================== 智能业务分析工具 ==================

    /**
     * 智能分析业务场景和表关系
     */
    @Tool(description = "智能分析业务场景和表关系。输入主表名，自动检测外键关系，推断应该使用单表/联表JOIN/主子表一对多哪种生成策略")
    public String analyzeBusinessRelation(
        @ToolParam(description = "主表名，如: biz_coupon") String mainTable,
        @ToolParam(description = "业务名称（中文），如: 优惠券") String businessName
    ) {
        log.info("调用 analyzeBusinessRelation，主表：{}，业务名：{}", mainTable, businessName);
        
        if (StrUtil.isBlank(mainTable)) {
            return "表名不能为空";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 业务关系分析报告\n\n");
        sb.append("主表: `").append(mainTable).append("` (").append(businessName).append(")\n\n");
        
        // 1. 检测主表字段中的外键关联（JOIN场景）
        List<Map<String, Object>> mainColumns = jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
            "AND COLUMN_NAME LIKE '%_id' AND COLUMN_NAME != 'id' " +
            "AND COLUMN_NAME NOT IN ('create_user', 'update_user', 'tenant_id')",
            mainTable
        );
        
        List<Map<String, String>> joinRelations = new ArrayList<>();
        for (Map<String, Object> col : mainColumns) {
            String columnName = (String) col.get("COLUMN_NAME");
            String comment = (String) col.get("COLUMN_COMMENT");
            // 推断可能的关联表
            String possibleTable = guessForeignTable(columnName, mainTable);
            if (possibleTable != null && tableExists(possibleTable)) {
                Map<String, String> rel = new HashMap<>();
                rel.put("column", columnName);
                rel.put("comment", comment != null ? comment : "");
                rel.put("targetTable", possibleTable);
                joinRelations.add(rel);
            }
        }
        
        // 2. 检测子表（一对多场景）- 查找引用主表的其他表
        String mainTableId = mainTable.replace("biz_", "").replace("sys_", "");
        List<Map<String, Object>> childTables = jdbcTemplate.queryForList(
            "SELECT DISTINCT t.TABLE_NAME, t.TABLE_COMMENT, c.COLUMN_NAME " +
            "FROM information_schema.TABLES t " +
            "JOIN information_schema.COLUMNS c ON t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_SCHEMA = c.TABLE_SCHEMA " +
            "WHERE t.TABLE_SCHEMA = DATABASE() " +
            "AND c.COLUMN_NAME LIKE ? " +
            "AND t.TABLE_NAME != ? " +
            "AND t.TABLE_NAME NOT LIKE '%_log'",
            "%" + mainTableId + "_id%", mainTable
        );
        
        // 3. 分析结果
        boolean hasForeignKey = !joinRelations.isEmpty();
        boolean hasChildTable = !childTables.isEmpty();
        
        // 生成建议
        sb.append("### 检测结果\n\n");
        
        if (hasForeignKey) {
            sb.append("#### 发现外键关联 (JOIN 查询场景)\n");
            sb.append("| 字段名 | 关联表 | 备注 |\n");
            sb.append("|--------|--------|------|\n");
            for (Map<String, String> rel : joinRelations) {
                sb.append("| `").append(rel.get("column")).append("` | `");
                sb.append(rel.get("targetTable")).append("` | ");
                sb.append(rel.get("comment")).append(" |\n");
            }
            sb.append("\n");
        }
        
        if (hasChildTable) {
            sb.append("#### 发现子表 (一对多场景)\n");
            sb.append("| 子表名 | 关联字段 | 表注释 |\n");
            sb.append("|--------|----------|--------|\n");
            for (Map<String, Object> child : childTables) {
                sb.append("| `").append(child.get("TABLE_NAME")).append("` | `");
                sb.append(child.get("COLUMN_NAME")).append("` | ");
                sb.append(child.get("TABLE_COMMENT") != null ? child.get("TABLE_COMMENT") : "").append(" |\n");
            }
            sb.append("\n");
        }
        
        // 4. 生成策略建议
        sb.append("### 推荐生成策略\n\n");
        
        if (!hasForeignKey && !hasChildTable) {
            sb.append("✅ **单表模式** - 该表无关联关系，建议使用标准 CRUD 生成\n\n");
            sb.append("调用 `writeBackendCode` 和 `writeFrontendCode` 生成代码\n");
        } else if (hasChildTable && !hasForeignKey) {
            sb.append("📄 **主子表模式** - 发现子表关联，建议生成主子表同页面展示\n\n");
            sb.append("调用 `generateMasterDetailPage` 生成主子表同页面代码\n");
        } else if (hasForeignKey && !hasChildTable) {
            sb.append("🔗 **联表查询模式** - 发现外键关联，建议生成 JOIN 查询代码\n\n");
            sb.append("调用 `generateWithRelations` 并传入关联配置\n");
        } else {
            sb.append("🌐 **复杂关联模式** - 同时存在外键关联和子表\n\n");
            sb.append("建议分步处理：\n");
            sb.append("1. 先调用 `generateMasterDetailPage` 生成主子表页面\n");
            sb.append("2. 再根据需要调整 JOIN 查询\n");
        }
        
        // 5. 生成关联配置 JSON 示例
        if (hasChildTable) {
            sb.append("\n### 一对多关联配置示例\n\n");
            sb.append("```json\n[");
            int i = 0;
            for (Map<String, Object> child : childTables) {
                if (i > 0) sb.append(",");
                String childTable = (String) child.get("TABLE_NAME");
                String childColumn = (String) child.get("COLUMN_NAME");
                String childComment = child.get("TABLE_COMMENT") != null ? (String) child.get("TABLE_COMMENT") : "";
                String childClassName = StrUtil.upperFirst(StrUtil.toCamelCase(childTable.replace("biz_", "").replace("sys_", "")));
                sb.append("\n  {");
                sb.append("\n    \"type\": \"ONE_TO_MANY\",");
                sb.append("\n    \"targetTable\": \"").append(childTable).append("\",");
                sb.append("\n    \"targetBusinessName\": \"").append(childComment.replace("表", "")).append("\",");
                sb.append("\n    \"targetClassNamePrefix\": \"").append(childClassName).append("\",");
                sb.append("\n    \"sourceColumn\": \"id\",");
                sb.append("\n    \"targetColumn\": \"").append(childColumn).append("\",");
                sb.append("\n    \"relationFieldName\": \"").append(StrUtil.toCamelCase(childTable.replace("biz_", "").replace("sys_", ""))).append("List\",");
                sb.append("\n    \"cascadeDelete\": true");
                sb.append("\n  }");
                i++;
            }
            sb.append("\n]\n```\n");
        }
        
        log.info("业务关系分析完成");
        return sb.toString();
    }
    
    /**
     * 推断外键关联的表名
     */
    private String guessForeignTable(String columnName, String mainTable) {
        // 移除 _id 后缀
        String baseName = columnName.replace("_id", "");
        // 试探多种表名形式
        String prefix = mainTable.startsWith("biz_") ? "biz_" : (mainTable.startsWith("sys_") ? "sys_" : "");
        String[] candidates = {
            prefix + baseName,
            "biz_" + baseName,
            "sys_" + baseName,
            baseName
        };
        for (String candidate : candidates) {
            if (tableExists(candidate)) {
                return candidate;
            }
        }
        return null;
    }
    
    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成主子表同页面代码（一对多场景）
     */
    @Tool(description = "生成主子表同页面展示的代码（一对多场景）。主表列表页面包含子表数据展示，如：优惠券+核销记录、订单+订单明细")
    public String generateMasterDetailPage(
        @ToolParam(description = "主表名，如: biz_coupon") String masterTable,
        @ToolParam(description = "主表业务名称，如: 优惠券") String masterBusinessName,
        @ToolParam(description = "子表名，如: biz_coupon_verify") String detailTable,
        @ToolParam(description = "子表业务名称，如: 核销记录") String detailBusinessName,
        @ToolParam(description = "子表关联主表的字段名，如: coupon_id") String detailForeignKey,
        @ToolParam(description = "模块名，如: coupon") String moduleName,
        @ToolParam(description = "是否在详情页展示子表（true=详情抽屉中展示，false=Tab分页展示）", required = false) Boolean showInDetail
    ) {
        log.info("调用 generateMasterDetailPage，主表：{}，子表：{}", masterTable, detailTable);
        
        try {
            boolean showInDetailPage = showInDetail != null ? showInDetail : true;
            
            StringBuilder sb = new StringBuilder();
            sb.append("## 主子表同页面生成方案\n\n");
            sb.append("主表: `").append(masterTable).append("` (").append(masterBusinessName).append(")\n");
            sb.append("子表: `").append(detailTable).append("` (").append(detailBusinessName).append(")\n");
            sb.append("关联字段: `").append(detailForeignKey).append("`\n");
            sb.append("展示方式: ").append(showInDetailPage ? "详情抽屉中展示子表" : "Tab 分页展示").append("\n\n");
            
            // 获取子表字段信息
            List<FieldConfig> detailFields = templateService.getTableColumns(detailTable);
            
            sb.append("### 子表字段信息\n");
            sb.append("| 字段名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");
            for (FieldConfig field : detailFields) {
                if (!field.getColumnName().equals(detailForeignKey)) {
                    sb.append("| `").append(field.getFieldName()).append("` | ");
                    sb.append(field.getFieldType()).append(" | ");
                    sb.append(field.getComment() != null ? field.getComment() : "").append(" |\n");
                }
            }
            
            // 子表 API 接口信息
            String masterApiName = StrUtil.toUnderlineCase(masterTable.replace("biz_", "").replace("sys_", "")).replace("_", "-");
            String detailApiName = StrUtil.toUnderlineCase(detailTable.replace("biz_", "").replace("sys_", "")).replace("_", "-");
            
            sb.append("\n### 子表 API 接口\n");
            sb.append("| 方法 | 路径 | 说明 |\n");
            sb.append("|------|------|------|\n");
            sb.append("| GET | `/").append(moduleName).append("/").append(detailApiName).append("?" + detailForeignKey + "={id}` | 根据主表ID查询子表列表 |\n");
            sb.append("| POST | `/").append(moduleName).append("/").append(detailApiName).append("` | 新增子表记录 |\n");
            sb.append("| PUT | `/").append(moduleName).append("/").append(detailApiName).append("/{id}` | 修改子表记录 |\n");
            sb.append("| DELETE | `/").append(moduleName).append("/").append(detailApiName).append("` | 删除子表记录 |\n");
            
            sb.append("\n### 生成步骤\n\n");
            sb.append("1. **生成主表后端代码**: 调用 `writeBackendCode(masterTable=\"").append(masterTable).append("\", businessName=\"").append(masterBusinessName).append("\", moduleName=\"").append(moduleName).append("\")`\n");
            sb.append("2. **生成子表后端代码**: 调用 `writeBackendCode(tableName=\"").append(detailTable).append("\", businessName=\"").append(detailBusinessName).append("\", moduleName=\"").append(moduleName).append("\")`\n");
            sb.append("3. **生成主表前端代码**: 调用 `writeFrontendCode(tableName=\"").append(masterTable).append("\", businessName=\"").append(masterBusinessName).append("\", moduleName=\"").append(moduleName).append("\")`\n");
            sb.append("4. **生成子表前端 API**: 调用 `writeDetailTableApi(masterTable=\"").append(masterTable).append("\", detailTable=\"").append(detailTable).append("\", moduleName=\"").append(moduleName).append("\")`\n");
            sb.append("5. **修改主表详情页**: 在 DetailDrawer.vue 中添加子表展示\n");
            sb.append("6. **生成菜单 SQL**: 只需要主表菜单，子表不需要单独菜单\n\n");
            
            sb.append("‼️ **重要**: 主子表场景下，子表不需要单独的菜单，子表数据在主表详情页中展示和管理\n");
            
            log.info("主子表方案生成完成");
            return sb.toString();
        } catch (Exception e) {
            log.error("生成主子表方案失败", e);
            return "生成失败：" + e.getMessage();
        }
    }

    /**
     * 生成子表 API 定义文件（用于主子表场景）
     */
    @Tool(description = "生成子表的前端 API 定义文件（用于主子表场景），子表不需要单独页面，只需要 API 定义")
    public String writeDetailTableApi(
        @ToolParam(description = "项目根目录绝对路径") String projectRoot,
        @ToolParam(description = "主表名") String masterTable,
        @ToolParam(description = "子表名") String detailTable,
        @ToolParam(description = "子表业务名称") String detailBusinessName,
        @ToolParam(description = "模块名") String moduleName
    ) {
        log.info("调用 writeDetailTableApi，子表：{}", detailTable);
        
        // 强制检查路径配置
        String pathError = checkPathConfigured();
        if (pathError != null) {
            return pathError;
        }
        
        try {
            GeneratorContext ctx = templateService.buildContext(detailTable, detailBusinessName, moduleName, null);
            Map<String, String> codes = templateService.previewFrontend(ctx);
            
            // 只写入 API 文件
            String apiRoot = projectRoot + "/" + projectPathConfig.getFrontendRootPath() + "/apis/" + ctx.getApiModuleName();
            String apiContent = codes.get("api.ts");
            
            if (apiContent != null) {
                String targetPath = apiRoot + "/" + ctx.getApiName() + ".ts";
                java.io.File file = new java.io.File(targetPath);
                file.getParentFile().mkdirs();
                java.nio.file.Files.writeString(file.toPath(), apiContent, java.nio.charset.StandardCharsets.UTF_8);
                
                return "✅ 子表 API 文件已写入\n\n文件路径: `" + targetPath + "`";
            }
            return "未找到 API 模板";
        } catch (Exception e) {
            log.error("写入子表 API 失败", e);
            return "写入失败：" + e.getMessage();
        }
    }

    // ================== 业务需求分析工具 ==================

    /**
     * 分析业务需求生成建表SQL
     */
    @Tool(description = "根据业务需求分析并生成建表SQL。AI 应根据用户描述的业务需求，结合项目表设计规范，设计合适的数据库表结构")
    public String generateCreateTableSql(
        @ToolParam(description = "业务名称（中文），如：优惠券管理") String businessName,
        @ToolParam(description = "表名，如：biz_coupon") String tableName,
        @ToolParam(description = "表字段定义JSON数组，如：[{\"name\":\"name\",\"type\":\"VARCHAR(100)\",\"comment\":\"优惠券名称\",\"nullable\":false}]") String fieldsJson,
        @ToolParam(description = "表注释") String tableComment
    ) {
        log.info("调用 generateCreateTableSql，业务名：{}，表名：{}", businessName, tableName);
        
        StringBuilder sql = new StringBuilder();
        sql.append("-- ").append(businessName).append("表\n");
        sql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");
        sql.append("    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',\n");
        
        // 解析字段
        if (StrUtil.isNotBlank(fieldsJson)) {
            JSONArray fields = JSONUtil.parseArray(fieldsJson);
            for (int i = 0; i < fields.size(); i++) {
                JSONObject field = fields.getJSONObject(i);
                String name = field.getStr("name");
                String type = field.getStr("type", "VARCHAR(255)");
                String comment = field.getStr("comment", "");
                boolean nullable = field.getBool("nullable", true);
                String defaultVal = field.getStr("default");
                
                sql.append("    `").append(name).append("` ").append(type);
                if (!nullable) sql.append(" NOT NULL");
                if (defaultVal != null) sql.append(" DEFAULT ").append(defaultVal);
                sql.append(" COMMENT '").append(comment).append("',\n");
            }
        }
        
        // 基础字段（必须包含，与 TenantBaseDO 基类字段对应）
        sql.append("    `create_user` BIGINT COMMENT '创建人',\n");
        sql.append("    `create_time` DATETIME COMMENT '创建时间',\n");
        sql.append("    `update_user` BIGINT COMMENT '修改人',\n");
        sql.append("    `update_time` DATETIME COMMENT '修改时间',\n");
        sql.append("    `deleted` BIGINT DEFAULT 0 COMMENT '是否删除（0-否，其他-是）',\n");
        sql.append("    `tenant_id` BIGINT DEFAULT 0 COMMENT '租户ID',\n");
        sql.append("    PRIMARY KEY (`id`),\n");
        sql.append("    INDEX `idx_tenant` (`tenant_id`)\n");
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='").append(tableComment).append("';\n");
        
        return "## 建表 SQL\n\n```sql\n" + sql.toString() + "```\n\n请确认表结构后，调用 `executeSql` 执行建表。";
    }

    // ================== 工具方法 ==================

    /**
     * 在当前数据源上执行 SQL（仅限安全操作）
     */
    @Tool(description = "在当前数据源上执行 SQL（仅支持 INSERT/CREATE/ALTER/UPDATE/SELECT，禁止 DROP/TRUNCATE/DELETE 全表等危险操作）")
    public String executeSql(
        @ToolParam(description = "需要执行的 SQL，可以是单条或多条（以分号分隔）") String sql
    ) {
        log.info("调用 executeSql，待执行 SQL：\n{}", sql);
        if (sql == null || sql.isBlank()) {
            return "SQL 为空，未执行任何语句。";
        }
        List<String> statements = Arrays.stream(sql.split(";"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        // 安全校验
        List<String> rejectedStatements = new ArrayList<>();
        for (String statement : statements) {
            String validationResult = validateSqlSecurity(statement);
            if (validationResult != null) {
                rejectedStatements.add(validationResult);
            }
        }
        if (!rejectedStatements.isEmpty()) {
            String errorMsg = "SQL 安全校验失败，已拒绝执行：\n" + String.join("\n", rejectedStatements);
            log.warn(errorMsg);
            return errorMsg;
        }

        // 执行 SQL
        int total = 0;
        for (String statement : statements) {
            jdbcTemplate.execute(statement);
            total++;
        }
        String result = "本次成功执行 SQL 语句数量：" + total;
        log.info("executeSql 执行完成，{}", result);
        return result;
    }

    /**
     * 校验 SQL 安全性
     *
     * @param sql SQL 语句
     * @return 如果不安全，返回错误原因；安全则返回 null
     */
    private String validateSqlSecurity(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        String trimmedSql = sql.trim().toUpperCase();

        // 检查是否包含危险操作
        if (DANGEROUS_SQL_PATTERN.matcher(sql).find()) {
            return "[危险操作] " + sql.substring(0, Math.min(50, sql.length())) + "...";
        }

        // 检查是否在白名单内
        boolean isAllowed = ALLOWED_SQL_PREFIXES.stream()
            .anyMatch(prefix -> trimmedSql.startsWith(prefix));
        if (!isAllowed) {
            return "[不支持的操作] " + sql.substring(0, Math.min(50, sql.length())) + "...（仅支持 INSERT/CREATE/ALTER/UPDATE/SELECT）";
        }

        return null;
    }

    /**
     * 获取系统菜单列表（供 AI 判断父菜单）
     */
    @Tool(description = "获取系统菜单列表（包含 ID、标题、父菜单 ID、类型），用于 AI 判断新菜单应该放在哪个父菜单下")
    public String listMenus() {
        log.info("调用 listMenus，获取系统菜单列表");
        String sql = "SELECT id, title, parent_id, type, path, name FROM sys_menu WHERE type IN (1, 2) AND status = 1 ORDER BY parent_id, sort";
        List<Map<String, Object>> menus = jdbcTemplate.queryForList(sql);
        log.info("查询到 {} 个菜单", menus.size());
        return JSONUtil.toJsonPrettyStr(menus);
    }

    /**
     * 获取系统字典列表（供 AI 判断字段是否使用字典）
     */
    @Tool(description = "获取系统字典列表（包含字典编码、名称、字典项），用于 AI 判断字段是否应该使用字典")
    public String listDicts() {
        log.info("调用 listDicts，获取系统字典列表");
        String sql = "SELECT d.id, d.code, d.name, d.description, " +
            "(SELECT GROUP_CONCAT(CONCAT(di.label, ':', di.value) SEPARATOR ', ') " +
            " FROM sys_dict_item di WHERE di.dict_id = d.id AND di.status = 1 ORDER BY di.sort) AS items " +
            "FROM sys_dict d WHERE d.status = 1 ORDER BY d.sort";
        List<Map<String, Object>> dicts = jdbcTemplate.queryForList(sql);
        log.info("查询到 {} 个字典", dicts.size());
        return JSONUtil.toJsonPrettyStr(dicts);
    }

    /**
     * 检查表是否存在
     */
    @Tool(description = "检查指定表名是否已存在于数据库中，用于建表前验证避免重复")
    public String checkTableExists(
        @ToolParam(description = "要检查的表名") String tableName
    ) {
        log.info("调用 checkTableExists，表名：{}", tableName);
        if (StrUtil.isBlank(tableName)) {
            return "表名不能为空";
        }
        String sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
        boolean exists = count != null && count > 0;
        log.info("表 {} {}", tableName, exists ? "已存在" : "不存在");
        return exists ? "表 " + tableName + " 已存在" : "表 " + tableName + " 不存在，可以创建";
    }

    /**
     * 获取项目关键路径配置
     */
    @Tool(description = "获取项目关键路径配置（后端模块路径、前端路径、SQL 输出路径等），供 AI 判断代码应放置的位置")
    public String getProjectPaths() {
        log.info("调用 getProjectPaths，获取项目关键路径");
        StringBuilder sb = new StringBuilder();
        
        // 检查是否已配置
        if (!projectPathConfig.isConfigured()) {
            sb.append("## \u26a0\ufe0f 警告：路径未配置\n\n");
            sb.append("用户尚未确认代码存放路径，请先询问用户并调用 `configureProjectPaths` 配置。\n\n");
            sb.append("以下为默认路径配置：\n\n");
        } else {
            sb.append("## 项目关键路径配置（已确认）\n\n");
        }
        
        sb.append("### 后端代码路径\n");
        sb.append("- **代码根路径**: `").append(projectPathConfig.getBackendRootPath()).append("`\n");
        sb.append("- **包名前缀**: `").append(projectPathConfig.getBackendPackagePrefix()).append("`\n");
        sb.append("- **业务模块示例**: `").append(projectPathConfig.getBackendRootPath()).append("/{moduleName}/`\n");
        sb.append("- **Mapper XML**: `").append(projectPathConfig.getMapperXmlPath()).append("`\n\n");
        
        sb.append("### 后端包结构\n");
        sb.append("```\n");
        sb.append(projectPathConfig.getBackendPackagePrefix()).append(".{moduleName}/\n");
        sb.append("├── controller/          # Controller 层\n");
        sb.append("├── service/             # Service 接口\n");
        sb.append("│   └── impl/            # Service 实现\n");
        sb.append("├── mapper/              # Mapper 接口\n");
        sb.append("└── model/               # 数据模型\n");
        sb.append("    ├── entity/          # 实体类 (DO)\n");
        sb.append("    ├── req/             # 请求参数\n");
        sb.append("    ├── resp/            # 响应参数\n");
        sb.append("    └── query/           # 查询参数\n");
        sb.append("```\n\n");
        
        sb.append("### 前端代码路径\n");
        sb.append("- **前端根路径**: `").append(projectPathConfig.getFrontendRootPath()).append("`\n");
        sb.append("- **页面组件**: `").append(projectPathConfig.getFrontendRootPath()).append("/").append(projectPathConfig.getFrontendViewsPath()).append("/{moduleName}/{apiName}/`\n");
        sb.append("- **API 定义**: `").append(projectPathConfig.getFrontendRootPath()).append("/").append(projectPathConfig.getFrontendApiPath()).append("/{moduleName}/{apiName}.ts`\n\n");
        
        sb.append("### SQL 输出路径\n");
        sb.append("- **菜单权限 SQL**: `").append(projectPathConfig.getSqlOutputPath()).append("`\n");
        sb.append("- 命名规范: `menu_{moduleName}_{apiName}.sql`\n");
        
        return sb.toString();
    }

    /**
     * 获取表设计规范
     */
    @Tool(description = "获取项目表设计规范（命名规范、字段规范、索引规范等），用于 AI 设计建表 SQL")
    public String getTableDesignRules() {
        log.info("调用 getTableDesignRules，获取表设计规范");
        StringBuilder sb = new StringBuilder();
        sb.append("## 表设计规范\n\n");
        
        sb.append("### 命名规范\n");
        sb.append("- **表名**: 小写字母 + 下划线，前缀按模块区分\n");
        sb.append("  - 系统模块: `sys_xxx`\n");
        sb.append("  - 业务模块: `biz_xxx`\n");
        sb.append("  - 日志模块: `log_xxx`\n");
        sb.append("- **字段名**: 小写字母 + 下划线，如 `create_time`\n");
        sb.append("- **索引名**: `idx_{表名缩写}_{字段名}`\n\n");
        
        sb.append("### 基础字段（必须包含，与 TenantBaseDO 基类对应）\n");
        sb.append("所有业务表必须包含以下基础字段：\n");
        sb.append("```sql\n");
        sb.append("id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',\n");
        sb.append("create_user  BIGINT       COMMENT '创建人',\n");
        sb.append("create_time  DATETIME     COMMENT '创建时间',\n");
        sb.append("update_user  BIGINT       COMMENT '修改人',\n");
        sb.append("update_time  DATETIME     COMMENT '修改时间',\n");
        sb.append("deleted      BIGINT       DEFAULT 0 COMMENT '是否删除（0-否，其他-是）',\n");
        sb.append("tenant_id    BIGINT       DEFAULT 0 COMMENT '租户 ID',\n");
        sb.append("PRIMARY KEY (id)\n");
        sb.append("```\n\n");
        
        sb.append("### 常用字段类型\n");
        sb.append("| 场景 | MySQL 类型 | Java 类型 | 说明 |\n");
        sb.append("|------|-----------|----------|------|\n");
        sb.append("| 主键 | BIGINT | Long | 雪花算法生成 |\n");
        sb.append("| 状态 | TINYINT | Integer | 1-启用, 2-禁用 |\n");
        sb.append("| 布尔 | BIT(1) | Boolean | b'1'-true, b'0'-false |\n");
        sb.append("| 短文本 | VARCHAR(n) | String | n 根据实际需求设置 |\n");
        sb.append("| 长文本 | TEXT | String | 备注、描述等 |\n");
        sb.append("| 日期时间 | DATETIME | LocalDateTime | - |\n");
        sb.append("| 日期 | DATE | LocalDate | - |\n");
        sb.append("| 金额 | DECIMAL(12,2) | BigDecimal | 精确计算 |\n");
        sb.append("| 排序 | INT | Integer | 默认 0 |\n\n");
        
        sb.append("### 索引规范\n");
        sb.append("- 外键关联字段必须建索引\n");
        sb.append("- 高频查询字段建索引\n");
        sb.append("- tenant_id 字段建索引（多租户）\n");
        sb.append("- 逻辑删除时，索引需包含 deleted 字段\n\n");
        
        sb.append("### 建表模板\n");
        sb.append("```sql\n");
        sb.append("CREATE TABLE IF NOT EXISTS `{table_name}` (\n");
        sb.append("    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',\n");
        sb.append("    -- 业务字段\n");
        sb.append("    `name`        VARCHAR(100) NOT NULL COMMENT '名称',\n");
        sb.append("    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态（1-启用, 2-禁用）',\n");
        sb.append("    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',\n");
        sb.append("    `remark`      VARCHAR(500) COMMENT '备注',\n");
        sb.append("    -- 基础字段（必须包含，与 TenantBaseDO 基类对应）\n");
        sb.append("    `create_user` BIGINT       COMMENT '创建人',\n");
        sb.append("    `create_time` DATETIME     COMMENT '创建时间',\n");
        sb.append("    `update_user` BIGINT       COMMENT '修改人',\n");
        sb.append("    `update_time` DATETIME     COMMENT '修改时间',\n");
        sb.append("    `deleted`     BIGINT       DEFAULT 0 COMMENT '是否删除（0-否，其他-是）',\n");
        sb.append("    `tenant_id`   BIGINT       DEFAULT 0 COMMENT '租户 ID',\n");
        sb.append("    PRIMARY KEY (`id`),\n");
        sb.append("    INDEX `idx_tenant` (`tenant_id`)\n");
        sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{table_comment}';\n");
        sb.append("```\n");
        
        return sb.toString();
    }

    /**
     * 获取前端代码规范和示例（AI 手动开发前端时必须参考）
     */
    @Tool(description = "获取前端代码规范和完整示例（包含页面结构、组件用法、Hooks、权限指令、路由配置等）。AI 开发前端页面时必须先调用此工具获取规范，确保生成的代码符合项目风格")
    public String getFrontendSpecification() {
        log.info("调用 getFrontendSpecification，获取前端代码规范");
        StringBuilder sb = new StringBuilder();
        
        sb.append("## 前端代码规范（从项目模板读取）\n\n");
        sb.append("框架：Vue 3 + TypeScript + Arco Design + 项目封装组件\n\n");
        
        // 路由机制说明（关键！）
        sb.append("### \u2757 路由机制（关键）\n\n");
        sb.append("本项目使用**动态路由**机制，路由由数据库菜单表加载，不是静态配置文件！\n\n");
        sb.append("**路由加载原理**：\n");
        sb.append("1. 前端自动扫描 `src/views/**/*.vue` 文件\n");
        sb.append("2. 后端返回菜单列表，包含 `component` 字段\n");
        sb.append("3. 前端根据菜单的 `component` 字段匹配 Vue 组件\n\n");
        sb.append("**component 路径映射规则**：\n");
        sb.append("```\n");
        sb.append("数据库 component 字段:  vehicle/vehicle/index\n");
        sb.append("映射到文件:            src/views/vehicle/vehicle/index.vue\n");
        sb.append("\n");
        sb.append("数据库 component 字段:  system/user/index\n");
        sb.append("映射到文件:            src/views/system/user/index.vue\n");
        sb.append("```\n\n");
        sb.append("**页面显示不出来的常见原因**：\n");
        sb.append("1. 菜单SQL未执行 → 需执行 `executeSql` 插入菜单记录\n");
        sb.append("2. 用户无权限 → 需给用户/角色分配菜单权限\n");
        sb.append("3. 路径不匹配 → 检查 `component` 字段与实际文件路径是否一致\n");
        sb.append("4. 未刷新页面 → 修改菜单后需重新登录或刷新\n\n");
        
        // 文件结构规范
        sb.append("### 文件结构规范\n");
        sb.append("```\n");
        sb.append("src/views/{moduleName}/{apiName}/\n");
        sb.append("├── index.vue          # 列表页面（使用 GiTable 组件）\n");
        sb.append("├── AddModal.vue       # 新增/编辑弹窗\n");
        sb.append("└── DetailDrawer.vue   # 详情抽屉\n");
        sb.append("src/apis/{moduleName}/{apiName}.ts  # API 接口定义\n");
        sb.append("```\n\n");
        
        sb.append("**路径对应关系示例**：\n");
        sb.append("| 表名 | 模块名 | apiName | 视图目录 | 菜单 component |\n");
        sb.append("|------|--------|---------|----------|----------------|\n");
        sb.append("| biz_vehicle | vehicle | vehicle | views/vehicle/vehicle/ | vehicle/vehicle/index |\n");
        sb.append("| biz_coupon | coupon | coupon | views/coupon/coupon/ | coupon/coupon/index |\n");
        sb.append("| sys_user | system | user | views/system/user/ | system/user/index |\n\n");
        
        // 读取模板文件作为规范
        sb.append("### 列表页模板 (index.vue)\n");
        sb.append("\u2139\ufe0f 以下为项目真实模板文件内容：\n");
        sb.append("```vue\n");
        sb.append(templateService.getTemplateContent("frontend/index.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### 新增/编辑弹窗模板 (AddModal.vue)\n");
        sb.append("```vue\n");
        sb.append(templateService.getTemplateContent("frontend/AddModal.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### 详情抽屉模板 (DetailDrawer.vue)\n");
        sb.append("```vue\n");
        sb.append(templateService.getTemplateContent("frontend/DetailDrawer.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### API 定义模板 (api.ts)\n");
        sb.append("```typescript\n");
        sb.append(templateService.getTemplateContent("frontend/api.ftl"));
        sb.append("\n```\n\n");
        
        // 关键规范说明
        sb.append("### 关键规范说明\n");
        sb.append("1. **页面布局**: 使用 `GiPageLayout` 包裹页面\n");
        sb.append("2. **表格组件**: 使用 `GiTable` 而非原生 `a-table`\n");
        sb.append("3. **HTTP 请求**: 使用 `@/utils/http` 而非原生 axios\n");
        sb.append("4. **API 路径**: `@/apis/{moduleName}/{apiName}`\n");
        sb.append("5. **权限指令**: `v-permission=['xxx:xxx:action']`\n");
        sb.append("6. **Hooks**: 使用 `useTable`, `useDict`, `useDownload` 等封装\n");
        sb.append("7. **字典组件**: 使用 `GiCellTag` 显示字典标签\n\n");
        
        // Tab 组件数据加载说明（重要！）
        sb.append("### ⚠️ Tab 组件数据加载（重要！）\n\n");
        sb.append("使用 Tab 分页模式时，每个 Tab 内的表格组件需要正确处理数据加载：\n\n");
        sb.append("**方式一（推荐）**: 使用 `immediate: true` 自动加载\n");
        sb.append("```typescript\n");
        sb.append("const { tableData, loading, pagination, search } = useTable(\n");
        sb.append("  (page) => listData({ ...queryForm, ...page }), \n");
        sb.append("  { immediate: true }  // 组件挂载时自动加载数据\n");
        sb.append(")\n");
        sb.append("```\n\n");
        sb.append("**方式二**: 使用 `immediate: false` + `onMounted` 手动触发\n");
        sb.append("```typescript\n");
        sb.append("const { tableData, loading, pagination, search } = useTable(\n");
        sb.append("  (page) => listData({ ...queryForm, ...page }), \n");
        sb.append("  { immediate: false }\n");
        sb.append(")\n\n");
        sb.append("onMounted(() => {\n");
        sb.append("  search()  // 必须手动调用 search() 加载数据！\n");
        sb.append("})\n");
        sb.append("```\n\n");
        sb.append("**常见错误**: 设置 `immediate: false` 但忘记添加 `onMounted` 调用 `search()`，导致页面空白无数据！\n\n");
        
        // 菜单配置说明（重要！）
        sb.append("### ❗ 菜单配置说明（重要！）\n\n");
        sb.append("生成前端页面后，必须配置菜单才能在导航中显示：\n\n");
        sb.append("**菜单类型说明**：\n");
        sb.append("| 类型 | type值 | component | 说明 |\n");
        sb.append("|------|--------|-----------|------|\n");
        sb.append("| 一级目录 | 1 | `Layout` | 导航中的顶级菜单，parent_id=0 |\n");
        sb.append("| 二级菜单 | 2 | `xxx/xxx/index` | 实际页面，component 指向 Vue 文件 |\n");
        sb.append("| 按钮权限 | 3 | 无 | 按钮级权限控制 |\n\n");
        sb.append("**新模块菜单配置流程**：\n");
        sb.append("1. 调用 `listMenus()` 查看现有菜单结构\n");
        sb.append("2. 判断是否需要创建**一级目录菜单**（如新模块）\n");
        sb.append("3. 调用 `generateMenuSql(...)` 生成菜单SQL\n");
        sb.append("4. 调用 `executeSql(sql)` 执行菜单SQL\n");
        sb.append("5. **重新登录**清除菜单缓存\n\n");
        sb.append("**一级目录菜单 SQL 示例**（新模块必须先创建）：\n");
        sb.append("```sql\n");
        sb.append("INSERT INTO sys_menu (id, title, parent_id, type, path, name, component, redirect, icon, is_external, is_cache, is_hidden, sort, status, create_user, create_time)\n");
        sb.append("VALUES (5000, '自行车管理', 0, 1, '/bicycle', 'Bicycle', 'Layout', '/bicycle/manage', 'swap', b'0', b'0', b'0', 50, 1, 1, NOW());\n");
        sb.append("```\n\n");
        sb.append("**二级菜单 SQL 示例**：\n");
        sb.append("```sql\n");
        sb.append("INSERT INTO sys_menu (id, title, parent_id, type, path, name, component, icon, is_external, is_cache, is_hidden, sort, status, create_user, create_time)\n");
        sb.append("VALUES (5010, '自行车管理', 5000, 2, '/bicycle/manage', 'BicycleManage', 'bicycle/manage/index', NULL, b'0', b'0', b'0', 1, 1, 1, NOW());\n");
        sb.append("```\n\n");
        sb.append("**菜单表关键字段**：\n");
        sb.append("| 字段 | 一级目录 | 二级菜单 | 说明 |\n");
        sb.append("|------|----------|----------|------|\n");
        sb.append("| type | 1 | 2 | 菜单类型 |\n");
        sb.append("| parent_id | 0 | 一级菜单ID | 父菜单 |\n");
        sb.append("| path | /bicycle | /bicycle/manage | 路由路径 |\n");
        sb.append("| component | `Layout` | `bicycle/manage/index` | **关键！** |\n");
        sb.append("| redirect | /bicycle/manage | 无 | 一级菜单需要 |\n");
        sb.append("| name | Bicycle | BicycleManage | 路由名称（唯一） |\n\n");
        sb.append("⚠️ **常见错误**：\n");
        sb.append("- 一级目录菜单缺少 `component='Layout'` → 页面空白\n");
        sb.append("- 二级菜单 `component` 路径与 Vue 文件不匹配 → 路由找不到组件\n");
        sb.append("- 修改菜单后未重新登录 → 后端有 Redis 缓存\n\n");
        
        log.info("前端代码规范获取完成（从模板读取）");
        return sb.toString();
    }

    /**
     * 获取后端代码规范和示例
     */
    @Tool(description = "获取后端代码规范和完整示例代码（从项目模板文件读取，包含 Entity、Controller、Service、Mapper 等），AI 生成后端代码时必须参考此规范")
    public String getBackendSpecification() {
        log.info("调用 getBackendSpecification，获取后端代码规范");
        StringBuilder sb = new StringBuilder();
        
        sb.append("## 后端代码规范（从项目模板读取）\n\n");
        sb.append("框架：Spring Boot 3 + MyBatis Plus + Sa-Token\n\n");
        
        // 文件结构规范
        sb.append("### 文件结构规范\n");
        sb.append("```\n");
        sb.append(projectPathConfig.getBackendPackagePrefix()).append(".{moduleName}/\n");
        sb.append("├── controller/          # Controller 层\n");
        sb.append("│   └── {ClassName}Controller.java\n");
        sb.append("├── service/             # Service 接口\n");
        sb.append("│   ├── {ClassName}Service.java\n");
        sb.append("│   └── impl/\n");
        sb.append("│       └── {ClassName}ServiceImpl.java\n");
        sb.append("├── mapper/              # Mapper 接口\n");
        sb.append("│   └── {ClassName}Mapper.java\n");
        sb.append("└── model/               # 数据模型\n");
        sb.append("    ├── entity/          # 实体类 {ClassName}DO.java\n");
        sb.append("    ├── req/             # 请求参数 {ClassName}Req.java\n");
        sb.append("    ├── resp/            # 响应参数 {ClassName}Resp.java\n");
        sb.append("    └── query/           # 查询参数 {ClassName}Query.java\n");
        sb.append("```\n\n");
        
        // 读取模板文件作为规范
        sb.append("### Entity 实体类模板\n");
        sb.append("ℹ️ 以下为项目真实模板文件内容：\n");
        sb.append("```java\n");
        sb.append(templateService.getTemplateContent("backend/Entity.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### Controller 控制器模板\n");
        sb.append("```java\n");
        sb.append(templateService.getTemplateContent("backend/Controller.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### Service 接口模板\n");
        sb.append("```java\n");
        sb.append(templateService.getTemplateContent("backend/Service.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### ServiceImpl 实现类模板\n");
        sb.append("```java\n");
        sb.append(templateService.getTemplateContent("backend/ServiceImpl.ftl"));
        sb.append("\n```\n\n");
        
        sb.append("### Mapper 接口模板\n");
        sb.append("```java\n");
        sb.append(templateService.getTemplateContent("backend/Mapper.ftl"));
        sb.append("\n```\n\n");
        
        // 关键规范说明
        sb.append("### 关键规范说明\n");
        sb.append("1. **实体类**: 继承 `TenantBaseDO`，使用 `@TableName` 注解\n");
        sb.append("2. **Controller**: 继承 `BaseController`，使用 `@CrudApi` 注解\n");
        sb.append("3. **Service**: 继承 `BaseService`，注意泛型顺序\n");
        sb.append("4. **Mapper**: 继承 `DataPermissionMapper`\n");
        sb.append("5. **命名规范**: \n");
        sb.append("   - 实体类后缀 `DO`\n");
        sb.append("   - 请求参数后缀 `Req`\n");
        sb.append("   - 响应参数后缀 `Resp`\n");
        sb.append("   - 查询参数后缀 `Query`\n");
        sb.append("6. **校验注解**: 使用 Jakarta Validation (`@NotBlank`, `@NotNull`, `@Size` 等)\n");
        
        log.info("后端代码规范获取完成（从模板读取）");
        return sb.toString();
    }

    /**
     * 获取数据库中所有表的列表
     */
    @Tool(description = "获取当前数据库中所有表的列表（表名和注释）")
    public String listTables() {
        log.info("调用 listTables，获取数据库表列表");
        List<Map<String, Object>> tables = templateService.listTables();
        String result = JSONUtil.toJsonPrettyStr(tables);
        log.info("查询到 {} 张表", tables.size());
        return result;
    }

    /**
     * 获取指定表的字段信息
     */
    @Tool(description = "获取指定表的字段结构信息（字段名、类型、注释等）")
    public String getTableColumns(
        @ToolParam(description = "表名") String tableName
    ) {
        log.info("调用 getTableColumns，表名：{}", tableName);
        if (StrUtil.isBlank(tableName)) {
            return "表名不能为空";
        }
        List<FieldConfig> fields = templateService.getTableColumns(tableName);
        String result = JSONUtil.toJsonPrettyStr(fields);
        log.info("查询到 {} 个字段（已排除基类字段）", fields.size());
        return result;
    }

    /**
     * 预览后端代码
     */
    @Tool(description = "根据表结构预览生成的后端代码（Entity, Controller, Service, Mapper 等）")
    public String previewBackendCode(
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文，如'优惠券'）") String businessName,
        @ToolParam(description = "模块名（如 coupon, system）") String moduleName,
        @ToolParam(description = "作者名（可选）", required = false) String author
    ) {
        log.info("调用 previewBackendCode，表名：{}，业务名：{}，模块：{}", tableName, businessName, moduleName);
        try {
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, author);
            Map<String, String> codes = templateService.previewBackend(ctx);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : codes.entrySet()) {
                sb.append("\n========== ").append(entry.getKey()).append(" ==========").append("\n");
                sb.append(entry.getValue()).append("\n");
            }
            log.info("后端代码预览生成完成，共 {} 个文件", codes.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("预览后端代码失败", e);
            return "预览后端代码失败：" + e.getMessage();
        }
    }

    /**
     * 获取 API 接口信息和字段配置（AI 开发前端时必须获取）
     */
    @Tool(description = "获取指定表的 API 接口信息、字段配置（包含接口路径、权限、字段类型、表单类型、字典等）。AI 开发前端页面时必须先调用此工具获取业务信息，支持多表聚合页面")
    public String getApiInfo(
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文，如'优惠券'）") String businessName,
        @ToolParam(description = "模块名（如 coupon, system）") String moduleName
    ) {
        log.info("调用 getApiInfo，表名：{}，业务名：{}，模块：{}", tableName, businessName, moduleName);
        try {
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, null);
            List<FieldConfig> fields = ctx.getFieldConfigs();

            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(businessName).append("管理 API 接口信息\n\n");

            // 基础信息
            String basePath = "/" + ctx.getApiModuleName() + "/" + ctx.getApiName();
            sb.append("### 基础信息\n");
            sb.append("- 接口前缀: `").append(basePath).append("`\n");
            sb.append("- 权限前缀: `").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append("`\n");
            sb.append("- 实体类名: `").append(ctx.getClassNamePrefix()).append("DO`\n\n");

            // API 接口列表
            sb.append("### API 接口\n");
            sb.append("| 方法 | 路径 | 说明 | 权限 |\n");
            sb.append("|------|------|------|------|\n");
            sb.append("| GET | `").append(basePath).append("` | 分页查询").append(businessName).append("列表 | ").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append(":list |\n");
            sb.append("| GET | `").append(basePath).append("/{id}` | 查询").append(businessName).append("详情 | ").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append(":get |\n");
            sb.append("| POST | `").append(basePath).append("` | 新增").append(businessName).append(" | ").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append(":create |\n");
            sb.append("| PUT | `").append(basePath).append("/{id}` | 修改").append(businessName).append(" | ").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append(":update |\n");
            sb.append("| DELETE | `").append(basePath).append("` | 删除").append(businessName).append(" | ").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append(":delete |\n");
            sb.append("| GET | `").append(basePath).append("/export` | 导出").append(businessName).append(" | ").append(ctx.getApiModuleName()).append(":").append(ctx.getApiName()).append(":export |\n");
            sb.append("| GET | `").append(basePath).append("/dict` | 获取").append(businessName).append("字典 | - |\n\n");

            // 字段信息
            sb.append("### 字段信息\n");
            sb.append("| 字段名 | 类型 | 说明 | 列表显示 | 表单显示 | 查询条件 | 必填 | 表单类型 |\n");
            sb.append("|--------|------|------|----------|----------|----------|------|----------|\n");
            for (FieldConfig field : fields) {
                sb.append("| ").append(field.getFieldName())
                    .append(" | ").append(field.getFieldType())
                    .append(" | ").append(field.getComment() != null ? field.getComment() : "")
                    .append(" | ").append(field.isShowInList() ? "✓" : "")
                    .append(" | ").append(field.isShowInForm() ? "✓" : "")
                    .append(" | ").append(field.isShowInQuery() ? "✓" : "")
                    .append(" | ").append(field.isRequired() ? "✓" : "")
                    .append(" | ").append(field.getFormType())
                    .append(" |\n");
            }

            // 字典信息
            if (ctx.isHasDictField()) {
                sb.append("\n### 字典字段\n");
                for (FieldConfig field : fields) {
                    if (field.getDictCode() != null && !field.getDictCode().isBlank()) {
                        sb.append("- `").append(field.getFieldName()).append("`: 使用字典 `").append(field.getDictCode()).append("`\n");
                    }
                }
            }
            
            // 提示调用其他工具获取更多信息
            sb.append("\n### 下一步\n");
            sb.append("- 调用 `getFrontendSpecification` 获取完整的前端代码规范和模板\n");
            sb.append("- 调用 `getProjectPaths` 查看代码生成路径配置\n");
            
            log.info("API 信息获取完成");
            return sb.toString();
        } catch (Exception e) {
            log.error("获取 API 信息失败", e);
            return "获取 API 信息失败：" + e.getMessage();
        }
    }

    /**
     * 生成菜单 SQL（支持 AI 指定父菜单，支持自定义 component 路径）
     */
    @Tool(description = "生成二级菜单和按钮权限的 SQL 语句。注意：如果是新模块，需先调用 generateDirectoryMenuSql 生成一级目录菜单")
    public String generateMenuSql(
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文，如'优惠券'）") String businessName,
        @ToolParam(description = "模块名（如 coupon, system）") String moduleName,
        @ToolParam(description = "父菜单 ID，由 AI 根据 listMenus 结果判断应该放在哪个菜单下") Long parentMenuId
    ) {
        log.info("调用 generateMenuSql，表名：{}，业务名：{}，模块：{}，父菜单ID：{}", tableName, businessName, moduleName, parentMenuId);
        try {
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, null);
            String sql = templateService.generateMenuSql(ctx, parentMenuId);
            
            log.info("菜单 SQL 生成完成");
            
            StringBuilder sb = new StringBuilder();
            sb.append(sql);
            sb.append("\n-- ❗ 重要提示：\n");
            sb.append("-- 菜单 component 路径: ").append(ctx.getApiModuleName()).append("/").append(ctx.getApiName()).append("/index\n");
            sb.append("-- Vue 文件应放在: src/views/").append(ctx.getApiModuleName()).append("/").append(ctx.getApiName()).append("/index.vue\n");
            sb.append("-- 如果文件路径不一致，需要手动修改 SQL 中的 component 字段！\n");
            
            return sb.toString();
        } catch (Exception e) {
            log.error("生成菜单 SQL 失败", e);
            return "生成菜单 SQL 失败：" + e.getMessage();
        }
    }

    /**
     * 生成一级目录菜单 SQL（新模块必须先创建）
     */
    @Tool(description = "生成一级目录菜单的 SQL 语句（新模块必须先创建）。一级目录菜单是导航栏的顶级菜单，component 固定为 'Layout'")
    public String generateDirectoryMenuSql(
        @ToolParam(description = "一级目录菜单名称（中文，如'自行车管理'）") String menuTitle,
        @ToolParam(description = "模块名（英文，如 bicycle、vehicle）") String moduleName,
        @ToolParam(description = "路由名称（大驼峰，如 Bicycle、Vehicle）") String routeName,
        @ToolParam(description = "菜单图标（Arco Design 图标名，如 swap、car、shopping-cart、settings）") String icon,
        @ToolParam(description = "默认跳转的子菜单路径（如 /bicycle/manage）") String redirectPath,
        @ToolParam(description = "排序号（数字越大越靠后）") Integer sort
    ) {
        log.info("调用 generateDirectoryMenuSql，菜单名：{}，模块：{}，图标：{}", menuTitle, moduleName, icon);
        
        long menuId = cn.hutool.core.util.IdUtil.getSnowflakeNextId();
        StringBuilder sb = new StringBuilder();
        
        sb.append("-- ").append(menuTitle).append("一级目录菜单\n");
        sb.append("INSERT INTO `sys_menu`\n");
        sb.append("    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `sort`, `status`, `create_user`, `create_time`)\n");
        sb.append("VALUES\n");
        sb.append("    (").append(menuId).append(", '").append(menuTitle).append("', 0, 1, '/").append(moduleName).append("', '");
        sb.append(routeName).append("', 'Layout', '").append(redirectPath).append("', '").append(icon != null ? icon : "menu");
        sb.append("', b'0', b'0', b'0', ").append(sort != null ? sort : 50).append(", 1, 1, NOW());\n\n");
        
        sb.append("-- ❗ 重要提示：\n");
        sb.append("-- 1. 一级目录菜单 ID：").append(menuId).append("\n");
        sb.append("-- 2. 生成二级菜单时，将此 ID 作为 parentMenuId 传入 generateMenuSql\n");
        sb.append("-- 3. component 必须是 'Layout'，否则页面会空白\n");
        sb.append("-- 4. redirect 指向默认子菜单路径\n");
        
        return sb.toString();
    }

    /**
     * 生成带关联关系的后端代码（菜单需单独生成）
     */
    @Tool(description = "生成带关联关系的后端代码（支持 JOIN 查询、一对多、多对多），菜单 SQL 需单独调用 generateMenuSql 生成")
    public String generateWithRelations(
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文，如'商品'）") String businessName,
        @ToolParam(description = "模块名（如 product, order）") String moduleName,
        @ToolParam(description = "关联配置 JSON 数组，如: [{\"type\":\"JOIN\",\"targetTable\":\"biz_category\",\"targetBusinessName\":\"分类\",\"targetClassNamePrefix\":\"Category\",\"sourceColumn\":\"category_id\",\"targetColumn\":\"id\",\"displayColumns\":[\"name\"],\"relationFieldName\":\"category\"}]") String relationsJson,
        @ToolParam(description = "作者名（可选）", required = false) String author
    ) {
        log.info("调用 generateWithRelations，表名：{}，业务名：{}，模块：{}，关联：{}", tableName, businessName, moduleName, relationsJson);
        try {
            // 解析关联配置
            List<RelationConfig> relations = parseRelations(relationsJson);

            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, author, relations);

            StringBuilder sb = new StringBuilder();

            // 后端代码
            sb.append("\n\n================ 后端代码（含关联） ================").append("\n");
            Map<String, String> backendCodes = templateService.previewBackend(ctx);
            for (Map.Entry<String, String> entry : backendCodes.entrySet()) {
                sb.append("\n========== ").append(entry.getKey()).append(" ==========").append("\n");
                sb.append(entry.getValue()).append("\n");
            }

            // API 接口信息和前端开发规则
            sb.append("\n\n================ API 接口信息和前端开发规则 ================").append("\n");
            sb.append(getApiInfo(tableName, businessName, moduleName));

            // 关联信息
            sb.append("\n\n================ 关联关系 ================").append("\n");
            for (RelationConfig rel : relations) {
                sb.append("- ").append(rel.getType()).append(": ")
                    .append(tableName).append(".").append(rel.getSourceColumn())
                    .append(" -> ").append(rel.getTargetTable()).append(".").append(rel.getTargetColumn())
                    .append(" (").append(rel.getTargetBusinessName()).append(")\n");
            }

            // 提示 AI 还需要生成菜单
            sb.append("\n\n================ 下一步 ================").append("\n");
            sb.append("请调用 listMenus 查看现有菜单结构，判断新菜单应放在哪个父菜单下，然后调用 generateMenuSql 生成菜单 SQL\n");

            log.info("关联代码生成完成，共 {} 个文件，{} 个关联关系", backendCodes.size(), relations.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("生成关联代码失败", e);
            return "生成关联代码失败：" + e.getMessage();
        }
    }

    /**
     * 将带关联关系的后端代码写入文件
     */
    @Tool(description = "将带关联关系的后端代码写入项目目录（含 JOIN 查询、一对多等）。这是 generateWithRelations 的写入版本")
    public String writeBackendCodeWithRelations(
        @ToolParam(description = "项目根目录绝对路径") String projectRoot,
        @ToolParam(description = "表名") String tableName,
        @ToolParam(description = "业务名称（中文）") String businessName,
        @ToolParam(description = "模块名") String moduleName,
        @ToolParam(description = "关联配置 JSON 数组") String relationsJson,
        @ToolParam(description = "作者名（可选）", required = false) String author
    ) {
        log.info("调用 writeBackendCodeWithRelations，表名：{}，模块：{}", tableName, moduleName);
        
        // 强制检查路径配置
        String pathError = checkPathConfigured();
        if (pathError != null) {
            return pathError;
        }
        
        try {
            List<RelationConfig> relations = parseRelations(relationsJson);
            GeneratorContext ctx = templateService.buildContext(tableName, businessName, moduleName, author, relations);
            Map<String, String> codes = templateService.previewBackend(ctx);
            
            String backendRoot = projectRoot + "/" + projectPathConfig.getBackendRootPath() + "/" + moduleName;
            String mapperXmlRoot = projectRoot + "/" + projectPathConfig.getMapperXmlPath();
            
            List<String> writtenFiles = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : codes.entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();
                String targetPath = resolveBackendFilePath(backendRoot, mapperXmlRoot, fileName, ctx.getClassNamePrefix());
                
                java.io.File file = new java.io.File(targetPath);
                file.getParentFile().mkdirs();
                java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
                writtenFiles.add(targetPath);
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("## 带关联的后端代码已写入\n\n");
            sb.append("共写入 ").append(writtenFiles.size()).append(" 个文件：\n");
            for (String path : writtenFiles) {
                sb.append("- `").append(path).append("`\n");
            }
            sb.append("\n### 关联关系\n");
            for (RelationConfig rel : relations) {
                sb.append("- ").append(rel.getType()).append(": ")
                    .append(tableName).append(".").append(rel.getSourceColumn())
                    .append(" -> ").append(rel.getTargetTable()).append(".").append(rel.getTargetColumn())
                    .append(" (").append(rel.getTargetBusinessName()).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("写入关联后端代码失败", e);
            return "写入关联后端代码失败：" + e.getMessage();
        }
    }

    /**
     * 生成业务聚合前端页面信息（一个业务一个页面）
     */
    @Tool(description = "【重要】生成业务聚合前端页面信息（一个业务一个页面）。输入业务涉及的多个表，返回聚合页面的 API 信息、字段配置和前端开发指南。前端页面由 AI 根据此信息手动开发")
    public String generateBusinessPageInfo(
        @ToolParam(description = "业务名称（中文），如: 用车管理") String businessName,
        @ToolParam(description = "模块名，如: vehicle") String moduleName,
        @ToolParam(description = "业务表配置 JSON 数组，如: [{\"tableName\":\"biz_vehicle\",\"tableBusinessName\":\"车辆信息\",\"isMain\":true},{\"tableName\":\"biz_vehicle_dispatch\",\"tableBusinessName\":\"车辆调度\",\"isMain\":false,\"foreignKey\":\"vehicle_id\",\"joinFields\":[\"plate_number\",\"brand\"]}]") String tablesJson,
        @ToolParam(description = "页面展示模式：TAB-Tab分页切换, MASTER_DETAIL-主子表同页, SINGLE-单表聚合") String displayMode
    ) {
        log.info("调用 generateBusinessPageInfo，业务：{}，模块：{}，模式：{}", businessName, moduleName, displayMode);
        
        try {
            JSONArray tables = JSONUtil.parseArray(tablesJson);
            
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(businessName).append(" - 业务聚合页面信息\n\n");
            sb.append("展示模式: **").append(displayMode).append("**\n\n");
            
            // 表信息汇总
            sb.append("### 业务表概览\n");
            sb.append("| 表名 | 业务名 | 类型 | 关联字段 |\n");
            sb.append("|------|--------|------|----------|\n");
            
            String mainTable = null;
            List<JSONObject> subTables = new ArrayList<>();
            
            for (int i = 0; i < tables.size(); i++) {
                JSONObject table = tables.getJSONObject(i);
                String tblName = table.getStr("tableName");
                String tblBusinessName = table.getStr("tableBusinessName");
                boolean isMain = table.getBool("isMain", false);
                String foreignKey = table.getStr("foreignKey", "-");
                
                sb.append("| `").append(tblName).append("` | ")
                    .append(tblBusinessName).append(" | ")
                    .append(isMain ? "主表" : "子表/关联表").append(" | ")
                    .append(foreignKey).append(" |\n");
                
                if (isMain) {
                    mainTable = tblName;
                } else {
                    subTables.add(table);
                }
            }
            
            // 各表 API 信息
            sb.append("\n### API 接口汇总\n");
            for (int i = 0; i < tables.size(); i++) {
                JSONObject table = tables.getJSONObject(i);
                String tblName = table.getStr("tableName");
                String tblBusinessName = table.getStr("tableBusinessName");
                String apiName = StrUtil.toUnderlineCase(tblName.replace("biz_", "").replace("sys_", "")).replace("_", "-");
                
                sb.append("\n#### ").append(tblBusinessName).append("\n");
                sb.append("- 接口前缀: `/").append(moduleName).append("/").append(apiName).append("`\n");
                sb.append("- 权限前缀: `").append(moduleName).append(":").append(apiName).append("`\n");
                
                // 字段信息
                List<FieldConfig> fields = templateService.getTableColumns(tblName);
                sb.append("- 字段：");
                List<String> fieldNames = fields.stream().map(FieldConfig::getFieldName).collect(Collectors.toList());
                sb.append(String.join(", ", fieldNames)).append("\n");
                
                // 如果有联表查询字段
                JSONArray joinFields = table.getJSONArray("joinFields");
                if (joinFields != null && !joinFields.isEmpty()) {
                    sb.append("- 关联查询字段：");
                    List<String> joinFieldList = new ArrayList<>();
                    for (int j = 0; j < joinFields.size(); j++) {
                        joinFieldList.add(joinFields.getStr(j));
                    }
                    sb.append(String.join(", ", joinFieldList)).append("\n");
                }
            }
            
            // 前端页面结构建议
            sb.append("\n### 前端页面结构建议\n");
            sb.append("```\n");
            sb.append("src/views/").append(moduleName).append("/\n");
            sb.append("├── index.vue              # 业务主页面（聚合展示）\n");
            
            if ("TAB".equalsIgnoreCase(displayMode)) {
                sb.append("├── components/\n");
                for (int i = 0; i < tables.size(); i++) {
                    JSONObject table = tables.getJSONObject(i);
                    String tblBusinessName = table.getStr("tableBusinessName");
                    String className = StrUtil.upperFirst(StrUtil.toCamelCase(table.getStr("tableName").replace("biz_", "").replace("sys_", "")));
                    sb.append("│   ├── ").append(className).append("Tab.vue      # ").append(tblBusinessName).append(" Tab 内容\n");
                }
            } else if ("MASTER_DETAIL".equalsIgnoreCase(displayMode)) {
                sb.append("├── AddModal.vue           # 主表新增/编辑\n");
                sb.append("├── DetailDrawer.vue       # 主表详情（包含子表展示）\n");
            }
            
            sb.append("└── ... 其他组件\n");
            sb.append("```\n");
            
            // 前端开发指南
            sb.append("\n### 前端开发指南\n\n");
            sb.append("1. **一个业务一个页面**: 所有表数据在同一页面展示\n");
            sb.append("2. **后端按表拆分**: 每个表有独立 API，前端调用多个 API 聚合数据\n");
            sb.append("3. **菜单只建一个**: 整个业务只需要一个菜单入口\n");
            sb.append("4. **权限统一管理**: 子表操作可使用主表权限或单独权限\n\n");
            
            sb.append("### 下一步\n");
            sb.append("1. 调用 `getFrontendSpecification` 获取前端代码规范\n");
            sb.append("2. AI 根据以上信息手动开发聚合页面\n");
            sb.append("3. 调用 `writeFile` 写入前端文件\n");
            
            log.info("业务聚合页面信息生成完成");
            return sb.toString();
        } catch (Exception e) {
            log.error("生成业务聚合页面信息失败", e);
            return "生成失败：" + e.getMessage();
        }
    }

    /**
     * 解析关联配置 JSON
     */
    private List<RelationConfig> parseRelations(String relationsJson) {
        if (StrUtil.isBlank(relationsJson)) {
            return new ArrayList<>();
        }
        List<RelationConfig> result = new ArrayList<>();
        JSONArray jsonArray = JSONUtil.parseArray(relationsJson);
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            RelationConfig rel = new RelationConfig();
            rel.setType(RelationConfig.RelationType.valueOf(obj.getStr("type", "JOIN")));
            rel.setTargetTable(obj.getStr("targetTable"));
            rel.setTargetBusinessName(obj.getStr("targetBusinessName"));
            rel.setTargetClassNamePrefix(obj.getStr("targetClassNamePrefix"));
            rel.setSourceColumn(obj.getStr("sourceColumn"));
            rel.setSourceFieldName(StrUtil.toCamelCase(obj.getStr("sourceColumn", "")));
            rel.setTargetColumn(obj.getStr("targetColumn", "id"));
            rel.setRelationFieldName(obj.getStr("relationFieldName"));
            rel.setCascadeDelete(obj.getBool("cascadeDelete", false));
            // 解析 displayColumns
            JSONArray cols = obj.getJSONArray("displayColumns");
            if (cols != null) {
                String[] displayCols = new String[cols.size()];
                for (int j = 0; j < cols.size(); j++) {
                    displayCols[j] = cols.getStr(j);
                }
                rel.setDisplayColumns(displayCols);
            } else {
                rel.setDisplayColumns(new String[]{"name"});
            }
            result.add(rel);
        }
        return result;
    }
}
