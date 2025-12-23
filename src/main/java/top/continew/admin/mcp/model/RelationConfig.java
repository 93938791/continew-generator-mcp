package top.continew.admin.mcp.model;

import lombok.Data;

/**
 * 表关联配置模型
 *
 * @author AI Generator
 */
@Data
public class RelationConfig {

    /**
     * 关联类型
     */
    private RelationType type;

    /**
     * 目标表名
     */
    private String targetTable;

    /**
     * 目标表业务名称（中文）
     */
    private String targetBusinessName;

    /**
     * 目标表类名前缀（如 Category, Brand）
     */
    private String targetClassNamePrefix;

    /**
     * 当前表的关联字段（如 category_id）
     */
    private String sourceColumn;

    /**
     * 目标表的关联字段（如 id）
     */
    private String targetColumn;

    /**
     * 要显示的目标表字段列表（如 name, code）
     */
    private String[] displayColumns;

    /**
     * 关联字段的 Java 属性名（如 categoryId）
     */
    private String sourceFieldName;

    /**
     * 关联对象的属性名（如 category, categoryName）
     */
    private String relationFieldName;

    /**
     * 是否级联删除
     */
    private boolean cascadeDelete = false;

    /**
     * 关联类型枚举
     */
    public enum RelationType {
        /**
         * 关联查询（多对一）：当前表有外键，查询时 JOIN 目标表获取显示字段
         * 例如：商品表的 category_id 关联分类表，列表显示分类名称
         */
        JOIN,

        /**
         * 一对多：当前表是主表，目标表有外键指向当前表
         * 例如：订单表 -> 订单明细表
         */
        ONE_TO_MANY,

        /**
         * 多对多：通过中间表关联
         * 例如：文章表 <-> 标签表，通过 article_tag 中间表
         */
        MANY_TO_MANY
    }
}
