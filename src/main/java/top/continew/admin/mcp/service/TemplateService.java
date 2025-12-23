package top.continew.admin.mcp.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.continew.admin.mcp.model.FieldConfig;
import top.continew.admin.mcp.model.GeneratorContext;
import top.continew.admin.mcp.model.RelationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 模板渲染服务
 * 负责：1. 从数据库获取表结构 2. 将表结构转换为生成上下文 3. 使用 FreeMarker 渲染模板
 *
 * @author AI Generator
 */
@Slf4j
@Service
public class TemplateService {

    private final JdbcTemplate jdbcTemplate;
    private final Configuration freemarkerConfig;

    public TemplateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // 使用原生 FreeMarker Configuration
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        this.freemarkerConfig.setClassLoaderForTemplateLoading(
            getClass().getClassLoader(), "templates");
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
        // 异常处理：抛出异常而不是忽略
        this.freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        // 不记录模板异常（已经通过 RETHROW 处理）
        this.freemarkerConfig.setLogTemplateExceptions(false);
        // 包装空值以避免空指针
        this.freemarkerConfig.setWrapUncheckedExceptions(true);
    }

    /**
     * 获取模板文件原始内容（不渲染）
     */
    public String getTemplateContent(String templatePath) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("templates/" + templatePath);
            if (is == null) {
                log.warn("模板文件不存在: {}", templatePath);
                return "模板文件不存在: " + templatePath;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取模板文件失败: {}", templatePath, e);
            return "读取模板文件失败: " + templatePath;
        }
    }

    /**
     * 获取数据库中所有表的列表
     */
    public List<Map<String, Object>> listTables() {
        String sql = "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 获取指定表的字段信息
     */
    public List<FieldConfig> getTableColumns(String tableName) {
        // 获取系统字典编码列表，用于自动匹配
        Set<String> dictCodes = loadDictCodes();

        String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, COLUMN_COMMENT, COLUMN_KEY, IS_NULLABLE, EXTRA " +
            "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tableName);

        List<FieldConfig> fields = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String columnName = (String) row.get("COLUMN_NAME");
            // 跳过基类字段（BaseDO 中已有的字段）
            if (isBaseField(columnName)) {
                continue;
            }

            FieldConfig field = new FieldConfig();
            field.setColumnName(columnName);
            field.setFieldName(StrUtil.toCamelCase(columnName));
            field.setColumnType((String) row.get("COLUMN_TYPE"));
            field.setFieldType(mapDbTypeToJava((String) row.get("DATA_TYPE")));
            field.setColumnSize(parseColumnSize((String) row.get("COLUMN_TYPE")));
            field.setComment((String) row.get("COLUMN_COMMENT"));
            field.setPrimaryKey("PRI".equals(row.get("COLUMN_KEY")));
            field.setRequired("NO".equals(row.get("IS_NULLABLE")));

            // 自动匹配字典编码（如果匹配到，同时设置 formType 为 SELECT）
            String matchedDictCode = matchDictCode(columnName, dictCodes);
            if (matchedDictCode != null) {
                field.setDictCode(matchedDictCode);
                field.setFormType("SELECT");
            } else {
                // 根据字段类型推断表单类型
                field.setFormType(inferFormType(field));
            }

            // 推断查询配置
            inferQueryConfig(field);

            // 设置 TypeScript 类型
            field.setTsType(mapJavaTypeToTs(field.getFieldType()));

            fields.add(field);
        }
        return fields;
    }

    /**
     * 加载系统字典编码列表
     */
    private Set<String> loadDictCodes() {
        try {
            String sql = "SELECT code FROM sys_dict WHERE status = 1";
            List<String> codes = jdbcTemplate.queryForList(sql, String.class);
            return new HashSet<>(codes);
        } catch (Exception e) {
            log.warn("加载系统字典失败，跳过自动匹配: {}", e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * 根据字段名自动匹配字典编码
     * 匹配规则：
     * 1. status 字段 -> sys_status
     * 2. gender 字段 -> sys_gender
     * 3. 其他字段名包含字典编码的情况
     */
    private String matchDictCode(String columnName, Set<String> dictCodes) {
        String lowerName = columnName.toLowerCase();
        
        // 精确匹配：字段名完全等于字典编码
        if (dictCodes.contains(lowerName)) {
            return lowerName;
        }
        
        // 常见映射：字段名 -> 字典编码
        Map<String, String> commonMappings = Map.of(
            "status", "sys_status",
            "gender", "sys_gender"
        );
        for (Map.Entry<String, String> entry : commonMappings.entrySet()) {
            if (lowerName.equals(entry.getKey()) && dictCodes.contains(entry.getValue())) {
                return entry.getValue();
            }
        }
        
        // 模糊匹配：字段名包含字典编码
        for (String dictCode : dictCodes) {
            if (lowerName.contains(dictCode.replace("_", ""))) {
                return dictCode;
            }
        }
        
        return null;
    }

    /**
     * 判断是否为基类字段（TenantBaseDO 中已定义的）
     */
    private boolean isBaseField(String columnName) {
        Set<String> baseFields = Set.of("id", "create_user", "create_time", "update_user", "update_time", "tenant_id");
        return baseFields.contains(columnName.toLowerCase());
    }

    /**
     * 数据库类型映射到 Java 类型
     */
    private String mapDbTypeToJava(String dbType) {
        if (dbType == null) return "String";
        dbType = dbType.toLowerCase();
        return switch (dbType) {
            case "bigint" -> "Long";
            case "int", "integer", "tinyint", "smallint", "mediumint" -> "Integer";
            case "decimal", "numeric" -> "BigDecimal";
            case "float" -> "Float";
            case "double" -> "Double";
            case "bit", "boolean" -> "Boolean";
            case "date" -> "LocalDate";
            case "time" -> "LocalTime";
            case "datetime", "timestamp" -> "LocalDateTime";
            default -> "String";
        };
    }

    /**
     * Java 类型映射到 TypeScript 类型
     */
    private String mapJavaTypeToTs(String javaType) {
        if (javaType == null) return "string";
        return switch (javaType) {
            case "Integer", "Long", "Float", "Double", "BigDecimal" -> "number";
            case "Boolean" -> "boolean";
            default -> "string";
        };
    }

    /**
     * 从 columnType 中解析字段长度，如 varchar(255) -> 255
     */
    private Integer parseColumnSize(String columnType) {
        if (columnType == null) return null;
        // 匹配括号中的数字，如 varchar(255), int(11)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(columnType);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * 根据字段类型推断表单类型
     * 注意：SELECT/RADIO 类型需要有 dictCode 才能正常工作，
     * 因此这里不自动推断为 SELECT，而是由 inferDictCode 来设置
     */
    private String inferFormType(FieldConfig field) {
        String javaType = field.getFieldType();
        String columnName = field.getColumnName().toLowerCase();

        // 备注/描述/内容类字段，使用文本域
        if (columnName.contains("remark") || columnName.contains("description") || columnName.contains("content")) {
            return "TEXTAREA";
        }

        // 根据类型推断
        return switch (javaType) {
            case "LocalDate" -> "DATE";
            case "LocalDateTime" -> "DATE_TIME";
            case "Boolean" -> "SWITCH";
            default -> "INPUT";
        };
    }

    /**
     * 推断字段的查询配置
     */
    private void inferQueryConfig(FieldConfig field) {
        String columnName = field.getColumnName().toLowerCase();
        String javaType = field.getFieldType();

        // 默认不显示在查询条件中
        field.setShowInQuery(false);
        field.setQueryType("EQ");

        // 状态、类型等枚举字段，适合等值查询
        if (columnName.contains("status") || columnName.contains("type")
            || columnName.contains("gender") || columnName.contains("state")
            || columnName.contains("level") || columnName.contains("category")) {
            field.setShowInQuery(true);
            field.setQueryType("EQ");
            return;
        }

        // 名称类字段，适合模糊查询
        if (columnName.contains("name") || columnName.contains("title")) {
            field.setShowInQuery(true);
            field.setQueryType("LIKE");
            return;
        }

        // 时间类型字段，适合范围查询
        if ("LocalDateTime".equals(javaType) || "LocalDate".equals(javaType)) {
            // 不默认开启时间查询，但设置正确的查询类型
            field.setQueryType("BETWEEN");
        }
    }

    /**
     * 构建生成上下文
     *
     * @param tableName    表名
     * @param businessName 业务名称（中文）
     * @param moduleName   模块名
     * @param author       作者
     */
    public GeneratorContext buildContext(String tableName, String businessName, String moduleName, String author) {
        return buildContext(tableName, businessName, moduleName, author, null);
    }

    /**
     * 构建生成上下文（含关联配置）
     *
     * @param tableName    表名
     * @param businessName 业务名称（中文）
     * @param moduleName   模块名
     * @param author       作者
     * @param relations    关联配置列表
     */
    public GeneratorContext buildContext(String tableName, String businessName, String moduleName, String author, List<RelationConfig> relations) {
        GeneratorContext ctx = new GeneratorContext();

        // 基础信息
        ctx.setTableName(tableName);
        ctx.setBusinessName(businessName);
        ctx.setAuthor(author != null ? author : "AI Generator");
        ctx.setDatetime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 从表名生成类名前缀（去掉前缀，转大驼峰）
        String classNamePrefix = StrUtil.upperFirst(StrUtil.toCamelCase(removeTablePrefix(tableName)));
        ctx.setClassNamePrefix(classNamePrefix);

        // 包名和模块名
        ctx.setModuleName(moduleName);
        ctx.setPackageName("top.continew.admin." + moduleName);

        // API 路径
        ctx.setApiModuleName(moduleName);
        ctx.setApiName(StrUtil.toUnderlineCase(classNamePrefix).replace("_", "-"));

        // 获取字段配置
        List<FieldConfig> fields = getTableColumns(tableName);
        ctx.setFieldConfigs(fields);

        // 设置关联配置
        if (relations != null && !relations.isEmpty()) {
            ctx.setRelations(relations);
        }

        // 计算标记
        ctx.computeFlags();

        return ctx;
    }

    /**
     * 移除表名前缀（如 sys_, t_, biz_ 等）
     */
    private String removeTablePrefix(String tableName) {
        String[] prefixes = {"sys_", "t_", "biz_", "gen_"};
        for (String prefix : prefixes) {
            if (tableName.toLowerCase().startsWith(prefix)) {
                return tableName.substring(prefix.length());
            }
        }
        return tableName;
    }

    /**
     * 渲染单个模板
     */
    public String render(String templatePath, GeneratorContext context) {
        try {
            Template template = freemarkerConfig.getTemplate(templatePath);
            // 构建数据模型
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("packageName", context.getPackageName());
            dataModel.put("subPackageName", context.getSubPackageName());
            dataModel.put("className", context.getClassName());
            dataModel.put("classNamePrefix", context.getClassNamePrefix());
            dataModel.put("businessName", context.getBusinessName());
            dataModel.put("author", context.getAuthor());
            dataModel.put("datetime", context.getDatetime());
            dataModel.put("tableName", context.getTableName());
            dataModel.put("moduleName", context.getModuleName());
            dataModel.put("apiModuleName", context.getApiModuleName());
            dataModel.put("apiName", context.getApiName());
            dataModel.put("fieldConfigs", context.getFieldConfigs());
            dataModel.put("imports", context.getImports());
            dataModel.put("hasTimeField", context.isHasTimeField());
            dataModel.put("hasBigDecimalField", context.isHasBigDecimalField());
            dataModel.put("hasRequiredField", context.isHasRequiredField());
            dataModel.put("hasDictField", context.isHasDictField());
            dataModel.put("dictCodes", context.getDictCodes());
            // 本地选项相关
            dataModel.put("hasLocalOptions", context.isHasLocalOptions());
            dataModel.put("localOptionsNames", context.getLocalOptionsNames());
            // 关联配置
            dataModel.put("relations", context.getRelations());
            dataModel.put("hasJoinRelation", context.isHasJoinRelation());
            dataModel.put("hasOneToManyRelation", context.isHasOneToManyRelation());
            dataModel.put("hasManyToManyRelation", context.isHasManyToManyRelation());
            dataModel.put("joinRelations", context.getJoinRelations());
            dataModel.put("oneToManyRelations", context.getOneToManyRelations());
            dataModel.put("manyToManyRelations", context.getManyToManyRelations());

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            log.error("模板渲染失败: {}", templatePath, e);
            throw new RuntimeException("模板渲染失败: " + templatePath, e);
        }
    }

    /**
     * 预览所有后端代码
     */
    public Map<String, String> previewBackend(GeneratorContext ctx) {
        Map<String, String> result = new LinkedHashMap<>();

        // Entity
        ctx.setSubPackageName("model.entity");
        ctx.setClassName(ctx.getClassNamePrefix() + "DO");
        result.put("Entity.java", render("backend/Entity.ftl", ctx));

        // Req
        ctx.setSubPackageName("model.req");
        ctx.setClassName(ctx.getClassNamePrefix() + "Req");
        result.put("Req.java", render("backend/Req.ftl", ctx));

        // Resp
        ctx.setSubPackageName("model.resp");
        ctx.setClassName(ctx.getClassNamePrefix() + "Resp");
        result.put("Resp.java", render("backend/Resp.ftl", ctx));

        // DetailResp
        ctx.setClassName(ctx.getClassNamePrefix() + "DetailResp");
        result.put("DetailResp.java", render("backend/DetailResp.ftl", ctx));

        // Query
        ctx.setSubPackageName("model.query");
        ctx.setClassName(ctx.getClassNamePrefix() + "Query");
        result.put("Query.java", render("backend/Query.ftl", ctx));

        // Mapper
        ctx.setSubPackageName("mapper");
        ctx.setClassName(ctx.getClassNamePrefix() + "Mapper");
        result.put("Mapper.java", render("backend/Mapper.ftl", ctx));

        // Mapper XML
        result.put("Mapper.xml", render("backend/MapperXml.ftl", ctx));

        // Service
        ctx.setSubPackageName("service");
        ctx.setClassName(ctx.getClassNamePrefix() + "Service");
        result.put("Service.java", render("backend/Service.ftl", ctx));

        // ServiceImpl
        ctx.setSubPackageName("service.impl");
        ctx.setClassName(ctx.getClassNamePrefix() + "ServiceImpl");
        result.put("ServiceImpl.java", render("backend/ServiceImpl.ftl", ctx));

        // Controller
        ctx.setSubPackageName("controller");
        ctx.setClassName(ctx.getClassNamePrefix() + "Controller");
        result.put("Controller.java", render("backend/Controller.ftl", ctx));

        return result;
    }

    /**
     * 预览前端代码
     */
    public Map<String, String> previewFrontend(GeneratorContext ctx) {
        Map<String, String> result = new LinkedHashMap<>();

        result.put("index.vue", render("frontend/index.ftl", ctx));
        result.put("AddModal.vue", render("frontend/AddModal.ftl", ctx));
        result.put("DetailDrawer.vue", render("frontend/DetailDrawer.ftl", ctx));
        result.put("api.ts", render("frontend/api.ftl", ctx));

        return result;
    }

    /**
     * 生成菜单 SQL（支持 AI 指定父菜单 ID）
     *
     * @param ctx          生成上下文
     * @param parentMenuId 父菜单 ID，由 AI 根据现有菜单结构判断
     */
    public String generateMenuSql(GeneratorContext ctx, Long parentMenuId) {
        long menuId = IdUtil.getSnowflakeNextId();
        StringBuilder sb = new StringBuilder();

        sb.append("-- ").append(ctx.getBusinessName()).append("管理菜单\n");
        sb.append("INSERT INTO `sys_menu`\n");
        sb.append("    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)\n");
        sb.append("VALUES\n");
        sb.append("    (").append(menuId).append(", '").append(ctx.getBusinessName()).append("管理', ").append(parentMenuId).append(", 2, '/")
            .append(ctx.getApiModuleName()).append("/").append(ctx.getApiName()).append("', '")
            .append(ctx.getClassNamePrefix()).append("', '").append(ctx.getApiModuleName()).append("/")
            .append(ctx.getApiName()).append("/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());\n\n");

        sb.append("-- ").append(ctx.getBusinessName()).append("管理按钮\n");
        sb.append("INSERT INTO `sys_menu`\n");
        sb.append("    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)\n");
        sb.append("VALUES\n");

        String perm = ctx.getApiModuleName() + ":" + ctx.getApiName();
        sb.append("    (").append(IdUtil.getSnowflakeNextId()).append(", '列表', ").append(menuId).append(", 3, '").append(perm).append(":list', 1, 1, 1, NOW()),\n");
        sb.append("    (").append(IdUtil.getSnowflakeNextId()).append(", '详情', ").append(menuId).append(", 3, '").append(perm).append(":get', 2, 1, 1, NOW()),\n");
        sb.append("    (").append(IdUtil.getSnowflakeNextId()).append(", '新增', ").append(menuId).append(", 3, '").append(perm).append(":create', 3, 1, 1, NOW()),\n");
        sb.append("    (").append(IdUtil.getSnowflakeNextId()).append(", '修改', ").append(menuId).append(", 3, '").append(perm).append(":update', 4, 1, 1, NOW()),\n");
        sb.append("    (").append(IdUtil.getSnowflakeNextId()).append(", '删除', ").append(menuId).append(", 3, '").append(perm).append(":delete', 5, 1, 1, NOW()),\n");
        sb.append("    (").append(IdUtil.getSnowflakeNextId()).append(", '导出', ").append(menuId).append(", 3, '").append(perm).append(":export', 6, 1, 1, NOW());\n");

        return sb.toString();
    }
}
