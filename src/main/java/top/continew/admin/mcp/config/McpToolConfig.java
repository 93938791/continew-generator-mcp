/*
 * Copyright (c) 2024-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.mcp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.continew.admin.mcp.tool.GeneratorTools;

import java.util.List;

/**
 * MCP 工具配置类
 * 负责将带有 @Tool 注解的工具类注册为 MCP 可用的工具
 *
 * @author Charles7c
 * @since 2025/12/22
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpToolConfig {

    private final GeneratorTools generatorTools;

    /**
     * 注册代码生成器工具
     * 通过 MethodToolCallbackProvider 将工具类中的方法注册为 MCP Tool
     */
    @Bean
    public List<ToolCallback> generatorToolCallbacks() {
        log.info("Registering GeneratorTools with MethodToolCallbackProvider");
        
        ToolCallback[] callbackArray = MethodToolCallbackProvider.builder()
            .toolObjects(generatorTools)
            .build()
            .getToolCallbacks();
        
        List<ToolCallback> callbacks = List.of(callbackArray);
        
        log.info("Registered {} tool callbacks from GeneratorTools", callbacks.size());
        
        return callbacks;
    }
}
