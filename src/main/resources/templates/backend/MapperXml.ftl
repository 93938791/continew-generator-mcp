<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${packageName}.mapper.${classNamePrefix}Mapper">
<#if hasJoinRelation>

    <!-- 分页查询${businessName}列表（含关联字段） -->
    <select id="selectPageWithJoin" resultType="${packageName}.model.resp.${classNamePrefix}Resp">
        SELECT
            t.*
            <#list joinRelations as relation>
            <#list relation.displayColumns as col>
            , ${relation.relationFieldName}.${col} AS ${relation.relationFieldName}${col?replace('_',' ')?capitalize?replace(' ','')}
            </#list>
            </#list>
        FROM ${tableName} t
        <#list joinRelations as relation>
        LEFT JOIN ${relation.targetTable} ${relation.relationFieldName} ON t.${relation.sourceColumn} = ${relation.relationFieldName}.${relation.targetColumn}
        </#list>
        <where>
            t.deleted = 0
            <#list fieldConfigs as fieldConfig>
            <#if fieldConfig.showInQuery && !fieldConfig.primaryKey>
            <if test="query != null and query.${fieldConfig.fieldName} != null<#if fieldConfig.fieldType == 'String'> and query.${fieldConfig.fieldName} != ''</#if>">
                <#if fieldConfig.queryType == 'LIKE'>
                AND t.${fieldConfig.columnName} LIKE CONCAT('%', ${r"#{query."}${fieldConfig.fieldName}${r"}"}, '%')
                <#elseif fieldConfig.queryType == 'BETWEEN'>
                <#-- BETWEEN 需要特殊处理，此处简化为 EQ -->
                AND t.${fieldConfig.columnName} = ${r"#{query."}${fieldConfig.fieldName}${r"}"}
                <#else>
                AND t.${fieldConfig.columnName} = ${r"#{query."}${fieldConfig.fieldName}${r"}"}
                </#if>
            </if>
            </#if>
            </#list>
        </where>
        ORDER BY t.create_time DESC
    </select>

    <!-- 查询${businessName}详情（含关联字段） -->
    <select id="selectDetailById" resultType="${packageName}.model.resp.${classNamePrefix}DetailResp">
        SELECT
            t.*
            <#list joinRelations as relation>
            <#list relation.displayColumns as col>
            , ${relation.relationFieldName}.${col} AS ${relation.relationFieldName}${col?replace('_',' ')?capitalize?replace(' ','')}
            </#list>
            </#list>
        FROM ${tableName} t
        <#list joinRelations as relation>
        LEFT JOIN ${relation.targetTable} ${relation.relationFieldName} ON t.${relation.sourceColumn} = ${relation.relationFieldName}.${relation.targetColumn}
        </#list>
        WHERE t.id = ${r"#{id}"} AND t.deleted = 0
    </select>
</#if>
<#if hasOneToManyRelation>
<#list oneToManyRelations as relation>

    <!-- 根据${businessName}ID查询${relation.targetBusinessName}列表 -->
    <select id="select${relation.targetClassNamePrefix}ListBy${classNamePrefix}Id" resultType="${packageName}.model.resp.${relation.targetClassNamePrefix}Resp">
        SELECT * FROM ${relation.targetTable}
        WHERE ${relation.targetColumn} = ${'#'}{${classNamePrefix?uncap_first}Id}
          AND deleted = 0
        ORDER BY create_time DESC
    </select>
</#list>
</#if>

</mapper>
