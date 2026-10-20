package com.festpass.common;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the React build from classpath:/static and falls back to index.html
 * for client-side routes (e.g. /events/fresher-night), so deep links and
 * page refreshes work. API paths and missing files still return 404.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    private static class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            boolean isClientRoute = !resourcePath.startsWith("api/")
                    && !resourcePath.startsWith("ws")
                    && !resourcePath.contains(".");
            if (!isClientRoute) {
                return null;
            }
            Resource index = location.createRelative("index.html");
            return index.exists() ? index : null;
        }
    }
}
