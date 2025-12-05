
package com.kirisamemarisa.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    // 私信媒体物理存储目录，默认 `uploads/messages`
    @Value("${resource.message-media-location:uploads/messages}")
    private String messageMediaLocation;

    // 对外访问前缀，默认 `/files/messages`（不以 `/` 结尾）
    @Value("${resource.message-media-access-prefix:/files/messages}")
    private String messageMediaAccessPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 规范化前缀：以 `/` 开头，不以 `/` 结尾
        String prefix = messageMediaAccessPrefix;
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        // 例如 handlerPattern = `/files/messages/**`
        String handlerPattern = prefix + "/**";

        // 物理路径转为 `file:` URL
        String dir = Paths.get(messageMediaLocation).toAbsolutePath().toString();
        if (!dir.endsWith("/") && !dir.endsWith("\\")) {
            dir = dir + "/";
        }
        String location = "file:" + dir;

        registry.addResourceHandler(handlerPattern)
                .addResourceLocations(location);
    }
}