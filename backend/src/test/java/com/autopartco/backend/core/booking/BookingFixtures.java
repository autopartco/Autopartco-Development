package com.autopartco.backend.core.booking;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.autopartco.backend.core.booking.internal.BookingCatalog.BranchInfo;
import com.autopartco.backend.core.booking.internal.BookingCatalog.ServiceInfo;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.ServiceCategory;

/** Datos comunes de las pruebas de agenda. «Ahora» es el lunes 16-nov-2026 a las 08:00 de Bogotá. */
public final class BookingFixtures {
    public static final Instant NOW = Instant.parse("2026-11-16T13:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, BookingRules.ZONE);
    public static final UUID SERVICE = UUID.fromString("5b1b7f0e-3d21-4c8e-9d52-0a1c6f2e7a11");
    public static final UUID STORE = UUID.fromString("7d3d9b20-5f43-4eaa-9f74-2c3e8b4a9c33");
    public static final UUID OTHER_STORE = UUID.fromString("7d3d9b20-5f43-4eaa-9f74-2c3e8b4a9c34");
    public static final UUID BRANCH = UUID.fromString("8e4eac31-6a54-4fbb-8a85-3d4f9c5bad44");
    public static final UUID BUYER = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000001");
    public static final UUID OTHER_BUYER = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000002");
    public static final UUID STAFF = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000003");
    public static final UUID BOOKING = UUID.fromString("b0b0b0b0-0000-4000-8000-000000000001");
    public static final String BUYER_EMAIL = "comprador@correo.co";
    public static final String STAFF_EMAIL = "taller@correo.co";
    public static final String PHONE = "+573001112233";

    private BookingFixtures() {
    }

    /** Lunes a viernes 08–12 y 14–18; sábado 08–12; domingo cerrado. */
    public static Map<String, Object> hours() {
        Map<String, Object> hours = new LinkedHashMap<>();
        List<List<String>> weekday = List.of(List.of("08:00", "12:00"), List.of("14:00", "18:00"));
        for (String day : List.of("mon", "tue", "wed", "thu", "fri")) {
            hours.put(day, weekday);
        }
        hours.put("sat", List.of(List.of("08:00", "12:00")));
        hours.put("sun", List.of());
        return hours;
    }

    /** Hora local de Bogotá ({@code 2026-11-17T09:00}) con su zona. */
    public static OffsetDateTime at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(BookingRules.ZONE).toOffsetDateTime();
    }

    public static ServiceInfo service() {
        return service(true, 60);
    }

    public static ServiceInfo service(boolean bookingEnabled, int slotMinutes) {
        return new ServiceInfo(SERVICE, STORE, "Alineación y balanceo", ServiceCategory.ALIGNMENT_BALANCING, null,
                new BigDecimal("60000.00"), bookingEnabled, slotMinutes, true, true, "Taller Centro", "/logo");
    }

    public static BranchInfo branch() {
        return branch(hours());
    }

    public static BranchInfo branch(Map<String, Object> hours) {
        return new BranchInfo(BRANCH, "Sede Chapinero", true, hours, "Calle 60 # 10-20", "Bogotá, D.C.", "Bogotá",
                new BigDecimal("4.65"), new BigDecimal("-74.08"), "+576011234567", null);
    }

    public static AccountRef buyer() {
        return new AccountRef(BUYER, UUID.randomUUID(), BUYER_EMAIL, "Ana Pérez", "ACTIVE", "BUYER", "Ana");
    }

    public static AccountRef staff() {
        return new AccountRef(STAFF, UUID.randomUUID(), STAFF_EMAIL, "Luis Taller", "ACTIVE", "SELLER", "Luis");
    }

    /** Cita del comprador {@link #BUYER} en el almacén {@link #STORE}, de 60 minutos, versión 3. */
    public static ServiceBooking booking(BookingStatus status, String localStart) {
        ServiceBooking booking = new ServiceBooking();
        booking.setId(BOOKING);
        booking.setCode("CT-7K3P9Q");
        booking.setServiceId(SERVICE);
        booking.setStoreId(STORE);
        booking.setBranchId(BRANCH);
        booking.setAccountId(BUYER);
        Instant start = at(localStart).toInstant();
        booking.setStartsAt(start);
        booking.setEndsAt(start.plus(Duration.ofMinutes(60)));
        booking.setStatus(status);
        booking.setVehicle(Map.of("makeId", UUID.randomUUID().toString(), "modelId", UUID.randomUUID().toString(),
                "year", 2018, "plate", "ABC123"));
        booking.setContactPhone(PHONE);
        booking.setBuyerNote("Suena la dirección");
        booking.setQuotedPrice(new BigDecimal("60000.00"));
        booking.setCreatedAt(NOW.minus(Duration.ofDays(1)));
        booking.setVersion(3L);
        return booking;
    }
}
