package ${packageName}.${subPackageName};

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

import top.continew.admin.common.base.model.resp.BaseResp;
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

/**
 * ${businessName}信息
 *
 * @author ${author}
 * @since ${datetime}
 */
@Data
@Schema(description = "${businessName}信息")
public class ${className} extends BaseResp {

    @Serial
    private static final long serialVersionUID = 1L;
<#if fieldConfigs??>
<#list fieldConfigs as fieldConfig>
<#if fieldConfig.showInList>

    /**
     * ${fieldConfig.comment!""}
     */
    @Schema(description = "${fieldConfig.comment!""}")
    private ${fieldConfig.fieldType} ${fieldConfig.fieldName};
</#if>
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
    private String ${relation.relationFieldName}${col?replace('_',' ')?capitalize?replace(' ','')};
</#list>
</#list>
</#if>
}
