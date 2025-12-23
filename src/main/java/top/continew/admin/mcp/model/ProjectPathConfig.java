package top.continew.admin.mcp.model;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 项目路径配置
 * <p>用于存储用户配置的前后端代码存放路径</p>
 *
 * @author AI Generator
 */
@Data
@Component
public class ProjectPathConfig {

    /**
     * 后端代码根路径
     * 例如: continew-system/src/main/java/top/continew/admin
     */
    private String backendRootPath = "continew-system/src/main/java/top/continew/admin";

    /**
     * 后端包名前缀
     * 例如: top.continew.admin
     */
    private String backendPackagePrefix = "top.continew.admin";

    /**
     * Mapper XML 路径
     * 例如: continew-system/src/main/resources/mapper
     */
    private String mapperXmlPath = "continew-system/src/main/resources/mapper";

    /**
     * 前端代码根路径
     * 例如: continew-admin-ui/src
     */
    private String frontendRootPath = "continew-admin-ui/src";

    /**
     * 前端页面路径（相对于前端根路径）
     * 例如: views
     */
    private String frontendViewsPath = "views";

    /**
     * 前端 API 路径（相对于前端根路径）
     * 例如: apis
     */
    private String frontendApiPath = "apis";

    /**
     * SQL 输出路径
     * 例如: continew-server/src/main/resources/db/changelog/sql
     */
    private String sqlOutputPath = "continew-server/src/main/resources/db/changelog/sql";

    /**
     * 是否已配置（用户是否确认过路径）
     */
    private boolean configured = false;

    /**
     * 获取完整的后端模块路径
     * @param moduleName 模块名
     * @return 完整路径
     */
    public String getFullBackendPath(String moduleName) {
        return backendRootPath + "/" + moduleName;
    }

    /**
     * 获取完整的前端页面路径
     * @param moduleName 模块名
     * @param apiName API 名称
     * @return 完整路径
     */
    public String getFullFrontendViewPath(String moduleName, String apiName) {
        return frontendRootPath + "/" + frontendViewsPath + "/" + moduleName + "/" + apiName;
    }

    /**
     * 获取完整的前端 API 路径
     * @param moduleName 模块名
     * @return 完整路径
     */
    public String getFullFrontendApiPath(String moduleName) {
        return frontendRootPath + "/" + frontendApiPath + "/" + moduleName;
    }

    /**
     * 获取完整的后端包名
     * @param moduleName 模块名
     * @return 完整包名
     */
    public String getFullPackageName(String moduleName) {
        return backendPackagePrefix + "." + moduleName;
    }

    /**
     * 重置为默认配置
     */
    public void reset() {
        this.backendRootPath = "continew-system/src/main/java/top/continew/admin";
        this.backendPackagePrefix = "top.continew.admin";
        this.mapperXmlPath = "continew-system/src/main/resources/mapper";
        this.frontendRootPath = "continew-admin-ui/src";
        this.frontendViewsPath = "views";
        this.frontendApiPath = "apis";
        this.sqlOutputPath = "continew-server/src/main/resources/db/changelog/sql";
        this.configured = false;
    }
}
