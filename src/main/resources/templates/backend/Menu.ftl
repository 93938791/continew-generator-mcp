SET @parentId = ${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c};
-- ${businessName}管理菜单
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (@parentId, '${businessName}管理', 1000, 2, '/${apiModuleName}/${apiName}', '${classNamePrefix}', '${apiModuleName}/${apiName}/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW());

-- ${businessName}管理按钮
INSERT INTO `sys_menu`
    (`id`, `title`, `parent_id`, `type`, `permission`, `sort`, `status`, `create_user`, `create_time`)
VALUES
    (${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c}, '列表', @parentId, 3, '${apiModuleName}:${apiName}:list', 1, 1, 1, NOW()),
    (${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c}, '详情', @parentId, 3, '${apiModuleName}:${apiName}:get', 2, 1, 1, NOW()),
    (${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c}, '新增', @parentId, 3, '${apiModuleName}:${apiName}:create', 3, 1, 1, NOW()),
    (${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c}, '修改', @parentId, 3, '${apiModuleName}:${apiName}:update', 4, 1, 1, NOW()),
    (${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c}, '删除', @parentId, 3, '${apiModuleName}:${apiName}:delete', 5, 1, 1, NOW()),
    (${statics["cn.hutool.core.util.IdUtil"].getSnowflakeNextId()?c}, '导出', @parentId, 3, '${apiModuleName}:${apiName}:export', 6, 1, 1, NOW());
