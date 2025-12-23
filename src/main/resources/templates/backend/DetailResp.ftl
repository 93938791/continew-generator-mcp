package ${packageName}.${subPackageName};

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;

import top.continew.admin.common.base.model.resp.BaseDetailResp;
import top.continew.starter.excel.converter.ExcelBaseEnumConverter;
<#if imports??>
<#list imports as className>
import ${className};
</#list>
</#if>
import java.io.Serial;
<#if hasTimeField>
import java.time.*;
</#if>
<#if hasBigDecimalField>
import java.math.BigDecimal;
</#if>
<#if hasOneToManyRelation>
import java.util.List;
</#if>

/**
 * ${businessName}详情信息
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@ExcelIgnoreUnannotated
@Schema(description = "${businessName}详情信息")
public class ${className} extends BaseDetailResp {

    @Serial
    private static final long serialVersionUID = 1L;
<#if fieldConfigs??>
<#list fieldConfigs as fieldConfig>

    /**
     * ${fieldConfig.comment!""}
     */
    @Schema(description = "${fieldConfig.comment!""}")
<#if fieldConfig.fieldType?ends_with("Enum")>
    @ExcelProperty(value = "${fieldConfig.comment!""}", converter = ExcelBaseEnumConverter.class)
<#else>
    @ExcelProperty(value = "${fieldConfig.comment!""}")
</#if>
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
</#list>
</#if>
<#-- JOIN 关联字段 -->
<#if hasJoinRelation>
<#list joinRelations as relation>
<#list relation.displayColumns as col>

    /**
     * ${relation.targetBusinessName}名称
     */
    @Schema(description = "${relation.targetBusinessName}名称")
    @ExcelProperty(value = "${relation.targetBusinessName}名称")
    private String ${relation.relationFieldName}${col?replace('_',' ')?capitalize?replace(' ','')};
</#list>
</#list>
</#if>
<#-- 一对多子表列表 -->
<#if hasOneToManyRelation>
<#list oneToManyRelations as relation>

    /**
     * ${relation.targetBusinessName}列表
     */
    @Schema(description = "${relation.targetBusinessName}列表")
    private List<${relation.targetClassNamePrefix}Resp> ${relation.relationFieldName}List;
</#list>
</#if>
}
