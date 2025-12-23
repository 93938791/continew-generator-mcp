package ${packageName}.${subPackageName};

import org.apache.ibatis.annotations.Mapper;
<#if hasJoinRelation || hasOneToManyRelation>
import org.apache.ibatis.annotations.Param;
</#if>
<#if hasJoinRelation>
import com.baomidou.mybatisplus.core.metadata.IPage;
import ${packageName}.model.resp.${classNamePrefix}Resp;
import ${packageName}.model.resp.${classNamePrefix}DetailResp;
</#if>
<#if hasOneToManyRelation>
import java.util.List;
<#list oneToManyRelations as relation>
import ${packageName}.model.resp.${relation.targetClassNamePrefix}Resp;
</#list>
</#if>
import ${packageName}.model.entity.${classNamePrefix}DO;
import top.continew.starter.data.mapper.BaseMapper;
import ${packageName}.model.query.${classNamePrefix}Query;

/**
* ${businessName} Mapper
*
* @author ${author}
* @since ${datetime}
*/
@Mapper
public interface ${className} extends BaseMapper<${classNamePrefix}DO> {
<#if hasJoinRelation>

    /**
     * 分页查询${businessName}列表（含关联字段）
     */
    IPage<${classNamePrefix}Resp> selectPageWithJoin(IPage<${classNamePrefix}DO> page, @Param("query") ${classNamePrefix}Query query);

    /**
     * 查询${businessName}详情（含关联字段）
     */
    ${classNamePrefix}DetailResp selectDetailById(@Param("id") Long id);
</#if>
<#if hasOneToManyRelation>
<#list oneToManyRelations as relation>

    /**
     * 根据${businessName}ID查询${relation.targetBusinessName}列表
     */
    List<${relation.targetClassNamePrefix}Resp> select${relation.targetClassNamePrefix}ListBy${classNamePrefix}Id(@Param("${classNamePrefix?uncap_first}Id") Long ${classNamePrefix?uncap_first}Id);
</#list>
</#if>
}
