package com.autopartco.backend.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "supabase")
public record SupabaseStorageProperties(
        String url,
        String serviceKey,
        String bucket,
        String businessBucket
) {
}
