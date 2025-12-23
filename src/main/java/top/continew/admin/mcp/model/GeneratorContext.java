package top.continew.admin.mcp.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 代码生成上下文模型
 * 包含模板渲染所需的所有参数
 *
 * @author AI Generator
 */
@Data
public class GeneratorContext {

    // ============== 基础信息 ==============

    /**
     * 表名
     */
    private String tableName;

    /**
     * 业务名称（中文，用于注释和页面标题）
     */
    private String businessName;

    /**
     * 作者
     */
    private String author = "AI Generator";

    /**
     * 生成日期时间
     */
    private String datetime;

    // ============== 包路径相关 ==============

    /**
     * 主包名，如 top.continew.admin.system
     */
    private String packageName;

    /**
     * 子包名，如 controller, service, mapper, model.entity 等
     */
    private String subPackageName;

    /**
     * 模块名（用于输出目录），如 system, coupon
     */
    private String moduleName;

    // ============== 类名相关 ==============

    /**
     * 类名（完整），如 CouponController
     */
    private String className;

    /**
     * 类名前缀（不含后缀），如 Coupon
     */
    private String classNamePrefix;

    // ============== API 路径相关 ==============

    /**
     * API 模块名，用于路径，如 coupon
     */
    private String apiModuleName;

    /**
     * API 名称，用于路径，如 info
     */
    private String apiName;

    // ============== 字段配置 ==============

    /**
     * 字段配置列表
     */
    private List<FieldConfig> fieldConfigs = new ArrayList<>();

    // ============== 辅助标记 ==============

    /**
     * 需要 import 的类（额外的，如枚举、特殊类型）
     */
    private Set<String> imports = new HashSet<>();

    /**
     * 是否包含时间类型字段
     */
    private boolean hasTimeField = false;

    /**
     * 是否包含 BigDecimal 字段
     */
    private boolean hasBigDecimalField = false;

    /**
     * 是否包含必填字段
     */
    private boolean hasRequiredField = false;

    /**
     * 是否包含字典字段
     */
    private boolean hasDictField = false;

    /**
     * 字典编码列表（前端用）
     */
    private List<String> dictCodes = new ArrayList<>();

    /**
     * 是否有本地选项字段（没有 dictCode 的 SELECT/RADIO）
     */
    private boolean hasLocalOptions = false;

    /**
     * 本地选项变量名列表
     */
    private List<String> localOptionsNames = new ArrayList<>();

    // ============== 关联配置 ==============

    /**
     * 关联配置列表
     */
    private List<RelationConfig> relations = new ArrayList<>();

    /**
     * 是否有 JOIN 关联查询
     */
    private boolean hasJoinRelation = false;

    /**
     * 是否有一对多关联
     */
    private boolean hasOneToManyRelation = false;

    /**
     * 是否有多对多关联
     */
    private boolean hasManyToManyRelation = false;

    /**
     * JOIN 关联列表（方便模板使用）
     */
    private List<RelationConfig> joinRelations = new ArrayList<>();

    /**
     * 一对多关联列表
     */
    private List<RelationConfig> oneToManyRelations = new ArrayList<>();

    /**
     * 多对多关联列表
     */
    private List<RelationConfig> manyToManyRelations = new ArrayList<>();

    /**
     * 根据字段配置自动计算辅助标记
     */
    public void computeFlags() {
        Set<String> dictCodeSet = new HashSet<>();
        Set<String> localOptionsSet = new HashSet<>();
        for (FieldConfig field : fieldConfigs) {
            String type = field.getFieldType();
            if (type != null) {
                if (type.contains("LocalDate") || type.contains("LocalTime") || type.contains("LocalDateTime")) {
                    this.hasTimeField = true;
                }
                if (type.contains("BigDecimal")) {
                    this.hasBigDecimalField = true;
                }
            }
            if (field.getDictCode() != null && !field.getDictCode().isBlank()) {
                this.hasDictField = true;
                dictCodeSet.add(field.getDictCode());
            }
            // 收集本地选项变量名
            if (field.getLocalOptionsName() != null && !field.getLocalOptionsName().isBlank()) {
                this.hasLocalOptions = true;
                localOptionsSet.add(field.getLocalOptionsName());
            }
            if (field.isRequired()) {
                this.hasRequiredField = true;
            }
        }
        this.dictCodes = new ArrayList<>(dictCodeSet);
        this.localOptionsNames = new ArrayList<>(localOptionsSet);

        // 计算关联标记
        for (RelationConfig relation : relations) {
            switch (relation.getType()) {
                case JOIN -> {
                    this.hasJoinRelation = true;
                    this.joinRelations.add(relation);
                }
                case ONE_TO_MANY -> {
                    this.hasOneToManyRelation = true;
                    this.oneToManyRelations.add(relation);
                }
                case MANY_TO_MANY -> {
                    this.hasManyToManyRelation = true;
                    this.manyToManyRelations.add(relation);
                }
            }
        }
    }
}
