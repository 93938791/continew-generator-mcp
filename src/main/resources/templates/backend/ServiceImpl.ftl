package ${packageName}.${subPackageName};

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import top.continew.admin.common.base.service.BaseServiceImpl;
import ${packageName}.mapper.${classNamePrefix}Mapper;
import ${packageName}.model.entity.${classNamePrefix}DO;
import ${packageName}.model.query.${classNamePrefix}Query;
import ${packageName}.model.req.${classNamePrefix}Req;
import ${packageName}.model.resp.${classNamePrefix}DetailResp;
import ${packageName}.model.resp.${classNamePrefix}Resp;
import ${packageName}.service.${classNamePrefix}Service;
<#if hasJoinRelation || hasOneToManyRelation>
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.continew.starter.extension.crud.model.query.PageQuery;
import top.continew.starter.extension.crud.model.resp.PageResp;
</#if>
<#if hasOneToManyRelation>
import java.util.List;
<#list oneToManyRelations as relation>
import ${packageName}.model.resp.${relation.targetClassNamePrefix}Resp;
</#list>
</#if>

/**
 * ${businessName}业务实现
 *
 * @author ${author}
 * @since ${datetime}
 */
@Service
@RequiredArgsConstructor
public class ${className} extends BaseServiceImpl<${classNamePrefix}Mapper, ${classNamePrefix}DO, ${classNamePrefix}Resp, ${classNamePrefix}DetailResp, ${classNamePrefix}Query, ${classNamePrefix}Req> implements ${classNamePrefix}Service {
<#if hasJoinRelation>

    @Override
    public PageResp<${classNamePrefix}Resp> page(${classNamePrefix}Query query, PageQuery pageQuery) {
        IPage<${classNamePrefix}Resp> page = baseMapper.selectPageWithJoin(
            new Page<>(pageQuery.getPage(), pageQuery.getSize()), query);
        return PageResp.build(page);
    }
</#if>
<#if hasJoinRelation || hasOneToManyRelation>

    @Override
    public ${classNamePrefix}DetailResp get(Long id) {
    <#if hasJoinRelation>
        ${classNamePrefix}DetailResp detail = baseMapper.selectDetailById(id);
    <#else>
        ${classNamePrefix}DetailResp detail = super.get(id);
    </#if>
    <#if hasOneToManyRelation>
        // 查询子表数据
    <#list oneToManyRelations as relation>
        List<${relation.targetClassNamePrefix}Resp> ${relation.relationFieldName}List = baseMapper.select${relation.targetClassNamePrefix}ListBy${classNamePrefix}Id(id);
        detail.set${relation.relationFieldName?cap_first}List(${relation.relationFieldName}List);
    </#list>
    </#if>
        return detail;
    }
</#if>
}
