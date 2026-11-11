package com.autopartco.backend.storesuite.store;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Registra {@link StaffTwoFactorInterceptor} en las rutas del portal de un almacén. */
@Configuration
@AllArgsConstructor
public class StaffTwoFactorWebConfig implements WebMvcConfigurer {
    private final StaffTwoFactorInterceptor staffTwoFactorInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(staffTwoFactorInterceptor).addPathPatterns(ApiPaths.STORES + "/*", ApiPaths.STORES + "/*/**");
    }
}
