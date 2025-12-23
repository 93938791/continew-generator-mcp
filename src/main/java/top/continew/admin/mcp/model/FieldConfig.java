package top.continew.admin.mcp.model;

import lombok.Data;

/**
 * 字段配置模型
 *
 * @author AI Generator
 */
@Data
public class FieldConfig {

    /**
     * 列名（数据库字段名）
     */
    private String columnName;

    /**
     * 字段名（Java 属性名，驼峰）
     */
    private String fieldName;

    /**
     * 字段类型（Java 类型，如 String, Long, LocalDateTime）
     */
    private String fieldType;

    /**
     * 字段注释
     */
    private String comment;

    /**
     * 是否在列表中展示
     */
    private boolean showInList = true;

    /**
     * 是否在表单中展示
     */
    private boolean showInForm = true;

    /**
     * 是否在查询条件中展示
     */
    private boolean showInQuery = false;

    /**
     * 是否必填
     */
    private boolean required = false;

    /**
     * 表单类型：INPUT, TEXTAREA, SELECT, RADIO, CHECKBOX, DATE, DATE_TIME, SWITCH 等
     */
    private String formType = "INPUT";

    /**
     * 查询类型：EQ, NE, GT, GE, LT, LE, LIKE, BETWEEN 等
     */
    private String queryType = "EQ";

    /**
     * 字典编码（如果是字典字段）
     */
    private String dictCode;

    /**
     * 是否为主键
     */
    private boolean primaryKey = false;

    /**
     * 数据库字段类型
     */
    private String columnType;

    /**
     * 字段长度（用于字符串校验）
     */
    private Integer columnSize;

    /**
     * TypeScript 类型（用于前端 API 定义）
     * 如：string, number, boolean
     */
    private String tsType = "string";

    /**
     * 本地选项变量名（当 SELECT/RADIO 没有 dictCode 时使用）
     * 如：statusOptions, typeOptions
     */
    private String localOptionsName;
}
