package com.autopartco.backend.core.identity.internal.social;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SocialAuthProperties.class)
public class SocialAuthConfig {
}
