package org.kendar.sync.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the application.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Configure static resource handlers.
     *
     * @param registry The resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Configure static resources location
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * Configure view controllers for single-page application behavior.
     *
     * @param registry The view controller registry
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward requests to root to index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
