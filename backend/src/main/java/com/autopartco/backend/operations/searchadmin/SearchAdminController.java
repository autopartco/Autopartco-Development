package com.autopartco.backend.operations.searchadmin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.search.SearchIndexJobs;
import com.autopartco.backend.operations.auditlog.AdminAudit;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Mantenimiento del índice de búsqueda (solo root). */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/search")
@AllArgsConstructor
public class SearchAdminController {
    private final SearchIndexJobs searchIndexJobs;
    private final AdminAudit adminAudit;

    /** Encola una reconstrucción completa de {@code search_listings} y responde 202 sin esperar. */
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public ResponseEntity<Void> reindex(Authentication authentication) {
        searchIndexJobs.requestFullReindex();
        adminAudit.record(authentication, "SEARCH_REINDEX_REQUESTED", "SEARCH_INDEX", null, Map.of());
        return ResponseEntity.accepted().build();
    }
}
