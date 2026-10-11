package com.autopartco.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase")
public record SupabaseStorageProperties(
        String url,
        String serviceKey,
        String bucket,
        String businessBucket
) {
}
