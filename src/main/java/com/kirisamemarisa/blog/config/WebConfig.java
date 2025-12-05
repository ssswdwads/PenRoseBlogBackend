package com.kirisamemarisa.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${resource.avatar-location}")
    private String avatarLocation;

    @Value("${resource.background-location}")
    private String backgroundLocation;

    @Value("${resource.blogpostcover-location}")
    private String blogpostcoverLocation;

    @Value("${resource.blogpostcontent-location}")
    private String blogpostcontentLocation;

    @Value("${resource.sources-location}")
    private String sourcesLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations(avatarLocation);
        registry.addResourceHandler("/background/**")
                .addResourceLocations(backgroundLocation);
        // 博客文章封面资源映射
        registry.addResourceHandler("/sources/blogpostcover/**")
                .addResourceLocations(blogpostcoverLocation);
        // 博客正文图片、gif等资源映射
        registry.addResourceHandler("/sources/blogpostcontent/**")
                .addResourceLocations(blogpostcontentLocation);
        // 通配符映射整个 sources 目录，兼容 StaticResourceConfig.java 的功能
        registry.addResourceHandler("/sources/**")
                .addResourceLocations(sourcesLocation);
    }
}
