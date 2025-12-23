<template>
  <a-drawer v-model:visible="visible" title="${businessName}详情" :width="width >= 600 ? 600 : '100%'" :footer="false">
    <a-descriptions :column="2" size="large" class="general-description">
      <#list fieldConfigs as fieldConfig>
      <#if fieldConfig.dictCode?? && fieldConfig.dictCode != "">
      <a-descriptions-item label="${fieldConfig.comment}">
        <GiCellTag :value="dataDetail?.${fieldConfig.fieldName}" :dict="${fieldConfig.dictCode}" />
      </a-descriptions-item>
      <#else>
      <a-descriptions-item label="${fieldConfig.comment}">{{ dataDetail?.${fieldConfig.fieldName} }}</a-descriptions-item>
      </#if>
      <#if fieldConfig.fieldName = 'createUser'>
      <a-descriptions-item label="创建人">{{ dataDetail?.createUserString }}</a-descriptions-item>
      <#elseif fieldConfig.fieldName = 'updateUser'>
      <a-descriptions-item label="修改人">{{ dataDetail?.updateUserString }}</a-descriptions-item>
      </#if>
      </#list>
      <#-- JOIN 关联字段 -->
      <#if hasJoinRelation>
      <#list joinRelations as relation>
      <#list relation.displayColumns as col>
      <a-descriptions-item label="${relation.targetBusinessName}">{{ dataDetail?.${relation.relationFieldName}${col?replace('_',' ')?capitalize?replace(' ','')} }}</a-descriptions-item>
      </#list>
      </#list>
      </#if>
    </a-descriptions>
<#-- 一对多子表展示 -->
<#if hasOneToManyRelation>
<#list oneToManyRelations as relation>
    <a-divider orientation="left">${relation.targetBusinessName}列表</a-divider>
    <a-table :data="dataDetail?.${relation.relationFieldName}List" :pagination="false" size="small">
      <template #columns>
        <a-table-column title="ID" data-index="id" />
        <a-table-column title="创建时间" data-index="createTime" />
      </template>
    </a-table>
</#list>
</#if>
  </a-drawer>
</template>

<script setup lang="ts">
import { useWindowSize } from '@vueuse/core'
import { type ${classNamePrefix}DetailResp, get${classNamePrefix} as getDetail } from '@/apis/${apiModuleName}/${apiName}'
<#if hasDictField>
import { useDict } from '@/hooks/app'
</#if>

<#if hasDictField>
const { <#list dictCodes as dictCode>${dictCode}<#if dictCode_has_next>,</#if></#list> } = useDict(<#list dictCodes as dictCode>'${dictCode}'<#if dictCode_has_next>,</#if></#list>)
</#if>

const { width } = useWindowSize()

const dataId = ref('')
const dataDetail = ref<${classNamePrefix}DetailResp>()
const visible = ref(false)

// 查询详情
const getDataDetail = async () => {
  const { data } = await getDetail(dataId.value)
  dataDetail.value = data
}

// 打开
const onOpen = async (id: string) => {
  dataId.value = id
  await getDataDetail()
  visible.value = true
}

defineExpose({ onOpen })
</script>

<style scoped lang="scss"></style>
