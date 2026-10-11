package com.autopartco.backend.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.config.SupabaseStorageProperties;
import com.autopartco.backend.exception.StorageException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class SupabaseStorageService {
    private final RestClient.Builder restClientBuilder;
    private final SupabaseStorageProperties properties;

    public void upload(String path, byte[] content, String contentType, boolean upsert) {
        uploadToBucket(properties.bucket(), path, content, contentType, upsert);
    }

    public void uploadToBucket(
            String bucket,
            String path,
            byte[] content,
            String contentType,
            boolean upsert
    ) {
        requireConfiguration(bucket);
        try {
            restClientBuilder.build()
                    .put()
                    .uri(objectUri(bucket, path))
                    .headers(headers -> {
                        addAuthentication(headers);
                        headers.setContentType(MediaType.parseMediaType(contentType));
                        headers.set("x-upsert", Boolean.toString(upsert));
                    })
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.error("Supabase Storage upload failed for object path {}", path, exception);
            throw new StorageException("No se pudo guardar la imagen en Supabase Storage.", exception);
        }
    }

    public void delete(String path) {
        deleteFromBucket(properties.bucket(), path);
    }

    public void deleteFromBucket(String bucket, String path) {
        requireConfiguration(bucket);
        try {
            restClientBuilder.build()
                    .method(HttpMethod.DELETE)
                    .uri(bucketObjectUri(bucket))
                    .headers(this::addAuthentication)
                    .body(Map.of("prefixes", List.of(path)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.error("Supabase Storage delete failed for object path {}", path, exception);
            throw new StorageException("No se pudo eliminar la imagen de Supabase Storage.", exception);
        }
    }

    public StoredObject download(String path) {
        return downloadFromBucket(properties.bucket(), path);
    }

    public StoredObject downloadFromBucket(String bucket, String path) {
        requireConfiguration(bucket);
        try {
            var response = restClientBuilder.build()
                    .get()
                    .uri(objectUri(bucket, path))
                    .headers(this::addAuthentication)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new StorageException("Supabase Storage devolvió una imagen vacía.");
            }
            MediaType mediaType = response.getHeaders().getContentType();
            return new StoredObject(body, mediaType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : mediaType.toString());
        } catch (RestClientException exception) {
            log.error("Supabase Storage download failed for object path {}", path, exception);
            throw new StorageException("No se pudo recuperar la imagen de Supabase Storage.", exception);
        }
    }

    public String publicUrl(String path) {
        requireConfiguration(properties.bucket());
        List<String> pathSegments = new ArrayList<>(
                List.of("storage", "v1", "object", "public", properties.bucket())
        );
        pathSegments.addAll(List.of(path.split("/")));
        return UriComponentsBuilder.fromUriString(baseUrl())
                .pathSegment(pathSegments.toArray(String[]::new))
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private URI objectUri(String bucket, String path) {
        List<String> pathSegments = new ArrayList<>(List.of("storage", "v1", "object", bucket));
        pathSegments.addAll(List.of(path.split("/")));
        return UriComponentsBuilder.fromUriString(baseUrl())
                .pathSegment(pathSegments.toArray(String[]::new))
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private URI bucketObjectUri(String bucket) {
        return UriComponentsBuilder.fromUriString(baseUrl())
                .pathSegment("storage", "v1", "object", bucket)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private String baseUrl() {
        return properties.url().replaceAll("/+$", "");
    }

    private void addAuthentication(org.springframework.http.HttpHeaders headers) {
        headers.set("apikey", properties.serviceKey());
        headers.setBearerAuth(properties.serviceKey());
    }

    private void requireConfiguration(String bucket) {
        if (properties.url() == null || properties.url().isBlank()
                || properties.serviceKey() == null || properties.serviceKey().isBlank()
                || bucket == null || bucket.isBlank()) {
            throw new StorageException(
                    "Supabase Storage no está configurado. Define SUPABASE_URL, SUPABASE_SECRET_KEY y los buckets requeridos."
            );
        }
    }

    public record StoredObject(byte[] content, String contentType) {
    }
}
