package com.autopartco.backend.shared.security;

/**
 * Expresiones de {@code @PreAuthorize} de la consola de administración. {@code ROLE_ROOT} puede todo; un miembro del
 * equipo ({@code ROLE_STAFF}) necesita el permiso {@code PLATFORM_*} de la sección, que viaja en su token. Gestionar
 * el equipo y sus roles es solo de {@code ROLE_ROOT}.
 */
public final class PlatformAccess {
    public static final String ROOT = "hasAuthority('ROLE_ROOT')";
    public static final String DASHBOARD = "hasAnyAuthority('ROLE_ROOT','PLATFORM_DASHBOARD_READ')";
    public static final String APPLICATIONS = "hasAnyAuthority('ROLE_ROOT','PLATFORM_APPLICATIONS_REVIEW')";
    public static final String STORES = "hasAnyAuthority('ROLE_ROOT','PLATFORM_STORES_READ')";
    public static final String ACCOUNTS_READ = "hasAnyAuthority('ROLE_ROOT','PLATFORM_ACCOUNTS_READ')";
    public static final String ACCOUNTS_MANAGE = "hasAnyAuthority('ROLE_ROOT','PLATFORM_ACCOUNTS_MANAGE')";
    public static final String ORDERS = "hasAnyAuthority('ROLE_ROOT','PLATFORM_ORDERS_READ')";
    public static final String PAYMENT_RISK = "hasAnyAuthority('ROLE_ROOT','PLATFORM_PAYMENT_RISK_REVIEW')";
    public static final String CATALOG = "hasAnyAuthority('ROLE_ROOT','PLATFORM_CATALOG_MANAGE')";
    public static final String PLANS = "hasAnyAuthority('ROLE_ROOT','PLATFORM_PLANS_MANAGE')";
    public static final String PROMOTIONS = "hasAnyAuthority('ROLE_ROOT','PLATFORM_PROMOTIONS_MANAGE')";
    public static final String ADS = "hasAnyAuthority('ROLE_ROOT','PLATFORM_ADS_MANAGE')";
    public static final String MODERATION = "hasAnyAuthority('ROLE_ROOT','PLATFORM_MODERATION')";
    public static final String ANALYTICS = "hasAnyAuthority('ROLE_ROOT','PLATFORM_ANALYTICS_READ')";
    public static final String ANALYTICS_OR_DASHBOARD =
            "hasAnyAuthority('ROLE_ROOT','PLATFORM_ANALYTICS_READ','PLATFORM_DASHBOARD_READ')";
    public static final String AUDIT = "hasAnyAuthority('ROLE_ROOT','PLATFORM_AUDIT_READ')";
    /** Cualquier cuenta de la consola (por ejemplo, {@code GET /admin/me}). */
    public static final String ANY = "hasAnyAuthority('ROLE_ROOT','ROLE_STAFF')";

    private PlatformAccess() {
        throw new UnsupportedOperationException("Esta clase nunca debe ser instanciada");
    }
}
