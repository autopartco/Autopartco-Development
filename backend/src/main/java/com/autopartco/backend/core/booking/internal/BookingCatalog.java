package com.autopartco.backend.core.booking.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.organization.ServiceCategory;
import com.autopartco.backend.core.organization.StoreBrand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/**
 * Lecturas de la agenda sobre tablas de otros módulos (servicio, almacén, sucursal, dueños y teléfono del perfil), por
 * SQL para no depender de entidades internas de {@code core.organization} ni de {@code core.identity}. Nada de esto se
 * registra en logs.
 */
@Component
@AllArgsConstructor
public class BookingCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> HOURS = new TypeReference<>() {
    };

    static final String SERVICES_SQL = """
            select ss.id, ss.store_id, ss.name, ss.category, ss.image_url, ss.price_from, ss.booking_enabled,
                   ss.slot_minutes, ss.active, s.business_name, s.logo_path, (st.code = 'ACTIVE') as store_active
            from store_services ss
            join store s on s.id = ss.store_id and s.deleted_at is null
            join states st on st.id = s.state
            where ss.id in (:ids)
            """;

    static final String BRANCH_COLUMNS = """
            select b.id, b.name, b.opening_hours::text as opening_hours, (bs.code = 'ACTIVE') as active,
                   l.address_line, l.address_complement, l.city, l.department, l.latitude, l.longitude,
                   b.number_phone, b.whatsapp
            from branches b
            join states bs on bs.id = b.state
            left join locations l on l.id = b.location_id
            """;

    /** Sucursal enlazada al servicio (aunque esté inactiva: quien llama decide). */
    static final String SERVICE_BRANCH_SQL = BRANCH_COLUMNS + """
            join store_service_branches sb on sb.branch_id = b.id and sb.service_id = :serviceId
            where b.id = :branchId and b.deleted_at is null
            """;

    static final String BRANCHES_SQL = BRANCH_COLUMNS + "where b.id in (:ids)";

    static final String PROFILE_PHONE_SQL = """
            select p.number_phone from accounts a join persons p on p.id = a.id_person
            where a.id = :accountId and a.deleted_at is null
            """;

    /** Cuentas de los dueños activos del almacén (mismo criterio que los avisos de preguntas). */
    static final String OWNERS_SQL = """
            select distinct a.id, a.email
            from collaborators c
            join states st on st.id = c.state and st.code = 'ACTIVE'
            join accounts a on a.id_person = c.person_id and a.deleted_at is null
            where c.collab_store_id = :storeId and c.deleted_at is null
              and exists (select 1 from roles_collaborators rc
                          join roles r on r.id = rc.role_id and r.deleted_at is null
                          where rc.collaborator_id = c.id and rc.deleted_at is null and r.code = 'OWNER')
            order by a.id
            """;

    /** Serializa por comprador la verificación de límites y el alta (transacción del llamador). */
    static final String LOCK_ACCOUNT_SQL =
            "select pg_advisory_xact_lock(hashtextextended(:lockKey, 0)) is null as locked";

    /** Servicio con su almacén. {@link #visible()}: activo y almacén publicado. */
    public record ServiceInfo(UUID id, UUID storeId, String name, ServiceCategory category, String imageUrl,
            BigDecimal priceFrom, boolean bookingEnabled, int slotMinutes, boolean active, boolean storeActive,
            String storeName, String logoUrl) {
        public boolean visible() {
            return active && storeActive;
        }
    }

    /** Sucursal con su horario y dirección; {@code phone} en E.164 si son 10 dígitos. */
    public record BranchInfo(UUID id, String name, boolean active, Map<String, Object> openingHours, String address,
            String city, String department, BigDecimal latitude, BigDecimal longitude, String phone,
            String whatsapp) {
    }

    public record Owner(UUID accountId, String email) {
    }

    private final JdbcClient jdbcClient;

    public Optional<ServiceInfo> service(UUID serviceId) {
        return Optional.ofNullable(services(List.of(serviceId)).get(serviceId));
    }

    public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
        Map<UUID, ServiceInfo> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbcClient.sql(SERVICES_SQL).param("ids", ids).query(BookingCatalog::service).list()
                .forEach(info -> result.put(info.id(), info));
        return result;
    }

    /** Sucursal no borrada que presta el servicio; vacío si no está enlazada. */
    public Optional<BranchInfo> serviceBranch(UUID serviceId, UUID branchId) {
        return jdbcClient.sql(SERVICE_BRANCH_SQL).param("serviceId", serviceId).param("branchId", branchId)
                .query(BookingCatalog::branch).optional();
    }

    public Map<UUID, BranchInfo> branches(Collection<UUID> ids) {
        Map<UUID, BranchInfo> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbcClient.sql(BRANCHES_SQL).param("ids", ids).query(BookingCatalog::branch).list()
                .forEach(info -> result.put(info.id(), info));
        return result;
    }

    static final String PROMOTIONS_SQL =
            "select id, name, discount_type, discount_value from promotions where id in (:ids)";

    /** Nombre y etiqueta ({@code -15%}) de las promociones citadas; las borradas también. */
    public Map<UUID, com.autopartco.backend.core.booking.Booking.PromotionRef> promotions(Collection<UUID> ids) {
        Map<UUID, com.autopartco.backend.core.booking.Booking.PromotionRef> result = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbcClient.sql(PROMOTIONS_SQL).param("ids", ids).query((rs, row) -> {
            UUID id = rs.getObject("id", UUID.class);
            String label = com.autopartco.backend.core.pricing.PriceCalculator.label(
                    com.autopartco.backend.core.pricing.PromotionDiscountType.valueOf(rs.getString("discount_type")),
                    rs.getBigDecimal("discount_value"));
            return new com.autopartco.backend.core.booking.Booking.PromotionRef(id, rs.getString("name"), label);
        }).list().forEach(ref -> result.put(ref.id(), ref));
        return result;
    }

    public Optional<String> profilePhone(UUID accountId) {
        return jdbcClient.sql(PROFILE_PHONE_SQL).param("accountId", accountId).query(String.class).optional();
    }

    public List<Owner> owners(UUID storeId) {
        return jdbcClient.sql(OWNERS_SQL).param("storeId", storeId)
                .query((rs, row) -> new Owner(rs.getObject("id", UUID.class), rs.getString("email")))
                .list();
    }

    public void lockAccount(UUID accountId) {
        jdbcClient.sql(LOCK_ACCOUNT_SQL).param("lockKey", "service-booking:" + accountId).query(Boolean.class)
                .single();
    }

    private static ServiceInfo service(ResultSet rs, int row) throws SQLException {
        UUID storeId = rs.getObject("store_id", UUID.class);
        return new ServiceInfo(
                rs.getObject("id", UUID.class),
                storeId,
                rs.getString("name"),
                ServiceCategory.valueOf(rs.getString("category")),
                rs.getString("image_url"),
                rs.getBigDecimal("price_from"),
                rs.getBoolean("booking_enabled"),
                rs.getInt("slot_minutes"),
                rs.getBoolean("active"),
                rs.getBoolean("store_active"),
                rs.getString("business_name"),
                StoreBrand.url(storeId, StoreBrand.LOGO, rs.getString("logo_path")));
    }

    private static BranchInfo branch(ResultSet rs, int row) throws SQLException {
        String line = rs.getString("address_line");
        String complement = rs.getString("address_complement");
        String address = line == null ? null
                : complement == null || complement.isBlank() ? line.strip() : line.strip() + " " + complement.strip();
        return new BranchInfo(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getBoolean("active"),
                hours(rs.getString("opening_hours")),
                address,
                rs.getString("city"),
                rs.getString("department"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                phone(rs.getString("number_phone")),
                rs.getString("whatsapp"));
    }

    static Map<String, Object> hours(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return com.autopartco.backend.core.organization.OpeningHours.readable(JSON.readValue(json, HOURS));
        } catch (com.fasterxml.jackson.core.JacksonException unreadable) {
            return null;
        }
    }

    private static String phone(String numberPhone) {
        if (numberPhone == null || numberPhone.isBlank()) {
            return null;
        }
        String phone = numberPhone.strip();
        return phone.matches("\\d{10}") ? "+57" + phone : phone;
    }
}
