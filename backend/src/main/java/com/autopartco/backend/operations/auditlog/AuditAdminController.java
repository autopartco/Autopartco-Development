package com.autopartco.backend.operations.auditlog;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.analytics.Analytics;
import com.autopartco.backend.core.analytics.DateRange;
import com.autopartco.backend.core.audit.AuditQueries;
import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

/** Historial de auditoría de la plataforma (solo root). */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/audit")
@AllArgsConstructor
public class AuditAdminController {
    private final AuditQueries auditQueries;

    /** Rango por defecto: últimos 30 días (máx. 366, si no 400 {@code INVALID_RANGE}). Página desde 1; tamaño máx. 200. */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AuditQueries.Report search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + AuditQueries.DEFAULT_PAGE_SIZE) int size
    ) {
        DateRange range = DateRange.resolve(from, to, Analytics.today());
        return auditQueries.search(new AuditQueries.Filter(
                range.startInstant(), range.endInstant(), action, entityType, actor, page, size));
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public List<AuditQueries.ActionOption> actions() {
        return auditQueries.actions();
    }
}
