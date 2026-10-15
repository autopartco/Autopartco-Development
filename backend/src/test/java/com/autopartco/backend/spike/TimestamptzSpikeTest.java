package com.autopartco.backend.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.hibernate.SessionFactory;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Spike T2 (ADR 0002): comportamiento de LocalDateTime / Instant / OffsetDateTime
 * sobre columnas TIMESTAMPTZ con Hibernate 7, hibernate.jdbc.time_zone=UTC y
 * ddl-auto=validate.
 *
 * <p>Usa un PostgreSQL desechable (no la BD de backend/.env). Se auto-omite si no
 * conecta. URL configurable con -Dspike.db.url / -Dspike.db.user / -Dspike.db.password.
 * No arranca Spring: Hibernate se inicia de forma mínima con entidades propias.
 *
 * <p>Hallazgos (Hibernate 7.4.5, pgjdbc 42.7.13, PostgreSQL 16), ver docs/adr/0002:
 * LocalDateTime sobre timestamptz valida; se interpreta en la zona por defecto de la JVM
 * (jdbc.time_zone no lo afecta); getTimestamp().toLocalDateTime() es coherente con ello;
 * Instant/OffsetDateTime validan y son independientes de la JVM.
 */
class TimestamptzSpikeTest {

    private static final String URL = System.getProperty("spike.db.url",
            "jdbc:postgresql://localhost:55432/spike");
    private static final String USER = System.getProperty("spike.db.user", "postgres");
    private static final String PASSWORD = System.getProperty("spike.db.password", "spike");
    private static final String SCHEMA = "tz_spike";

    private static final TimeZone BOGOTA = TimeZone.getTimeZone("America/Bogota");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 10, 14, 8, 0);

    private static TimeZone originalDefault;

    // ---------------------------------------------------------------- entidades

    @Entity(name = "LdtOnTimestamptz")
    @Table(name = "ldt_tz")
    public static class LdtOnTimestamptz {
        @Id
        Long id;
        @Column(name = "at")
        LocalDateTime at;
    }

    @Entity(name = "InstantOnTimestamptz")
    @Table(name = "instant_tz")
    public static class InstantOnTimestamptz {
        @Id
        Long id;
        @Column(name = "at")
        Instant at;
    }

    @Entity(name = "OdtOnTimestamptz")
    @Table(name = "odt_tz")
    public static class OdtOnTimestamptz {
        @Id
        Long id;
        @Column(name = "at")
        OffsetDateTime at;
    }

    /** Control: el estado actual (LocalDateTime sobre TIMESTAMP sin zona). */
    @Entity(name = "LdtOnTimestamp")
    @Table(name = "ldt_ts")
    public static class LdtOnTimestamp {
        @Id
        Long id;
        @Column(name = "at")
        LocalDateTime at;
    }

    /** Control negativo: demuestra que validate sí compara tipos (LocalDateTime sobre TEXT). */
    @Entity(name = "LdtOnText")
    @Table(name = "ldt_text")
    public static class LdtOnText {
        @Id
        Long id;
        @Column(name = "at")
        LocalDateTime at;
    }

    /** Variante del experimento 5: LocalDateTime forzado a TIMESTAMP_WITH_TIMEZONE. */
    @Entity(name = "LdtForcedTz")
    @Table(name = "ldt_forced_tz")
    public static class LdtForcedTz {
        @Id
        Long id;
        @Column(name = "at")
        @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
        LocalDateTime at;
    }

    // ---------------------------------------------------------------- ciclo de vida

    @BeforeAll
    static void createSchema() {
        originalDefault = TimeZone.getDefault();
        boolean reachable;
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            reachable = c.isValid(2);
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "PostgreSQL del spike no disponible en " + URL + "; se omite");

        sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE",
                "CREATE SCHEMA " + SCHEMA,
                "CREATE TABLE " + SCHEMA + ".ldt_tz (id BIGINT PRIMARY KEY, at TIMESTAMPTZ)",
                "CREATE TABLE " + SCHEMA + ".instant_tz (id BIGINT PRIMARY KEY, at TIMESTAMPTZ)",
                "CREATE TABLE " + SCHEMA + ".odt_tz (id BIGINT PRIMARY KEY, at TIMESTAMPTZ)",
                "CREATE TABLE " + SCHEMA + ".ldt_forced_tz (id BIGINT PRIMARY KEY, at TIMESTAMPTZ)",
                "CREATE TABLE " + SCHEMA + ".ldt_ts (id BIGINT PRIMARY KEY, at TIMESTAMP)",
                "CREATE TABLE " + SCHEMA + ".ldt_text (id BIGINT PRIMARY KEY, at TEXT)");
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalDefault);
    }

    @AfterAll
    static void dropSchema() {
        if (originalDefault != null) {
            TimeZone.setDefault(originalDefault);
        }
        try {
            sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        } catch (RuntimeException ignored) {
            // BD no disponible: nada que limpiar
        }
    }

    // ---------------------------------------------------------------- experimento 1

    @Test
    void exp1_localDateTimeOnTimestamptz_validate() {
        String error = validate(LdtOnTimestamptz.class);
        report("EXP1 LocalDateTime sobre timestamptz, validate", error == null ? "VALIDA" : "FALLA: " + error);
        assertThat(error).as("validate de LocalDateTime sobre timestamptz").isNull();
    }

    @Test
    void exp1_control_validateRejectsWrongType() {
        String error = validate(LdtOnText.class);
        report("EXP1 control LocalDateTime sobre text, validate", error == null ? "VALIDA" : "FALLA: " + error);
        assertThat(error).contains("Schema validation: wrong column type");
    }

    // ---------------------------------------------------------------- experimento 2

    @Test
    void exp2_localDateTimeRoundTrip_jvmUtc() {
        roundTripLdt(UTC, 21L);
    }

    @Test
    void exp2_localDateTimeRoundTrip_jvmBogota() {
        roundTripLdt(BOGOTA, 22L);
    }

    private void roundTripLdt(TimeZone jvm, long id) {
        TimeZone.setDefault(jvm);
        try (SessionFactory sf = sessionFactory("none", LdtOnTimestamptz.class)) {
            sf.inTransaction(s -> {
                LdtOnTimestamptz e = new LdtOnTimestamptz();
                e.id = id;
                e.at = SAMPLE;
                s.persist(e);
            });
            LocalDateTime readBack = sf.fromSession(s -> s.find(LdtOnTimestamptz.class, id).at);
            String utcWall = scalar("SELECT (at AT TIME ZONE 'UTC')::text FROM " + SCHEMA + ".ldt_tz WHERE id = " + id);
            report("EXP2 JVM=" + jvm.getID(), "escrito=" + SAMPLE + " leido=" + readBack + " BD(UTC)=" + utcWall);
            // Ida y vuelta estable en cualquier zona.
            assertThat(readBack).isEqualTo(SAMPLE);
            // Hallazgo: sobre timestamptz, jdbc.time_zone=UTC NO aplica; el LocalDateTime se
            // interpreta en la zona por defecto de la JVM (Bogotá 08:00 -> 13:00Z).
            String expected = jvm == UTC ? "2026-10-14 08:00:00" : "2026-10-14 13:00:00";
            assertThat(utcWall).isEqualTo(expected);
        }
    }

    /** Lo que hace BaseEntity hoy: LocalDateTime.now() con la zona de la JVM. */
    @Test
    void exp2b_localDateTimeNow_inBogotaJvm_isStoredAsCorrectInstant() {
        TimeZone.setDefault(BOGOTA);
        long id = 23L;
        Instant realInstant = Instant.now();
        try (SessionFactory sf = sessionFactory("none", LdtOnTimestamptz.class)) {
            sf.inTransaction(s -> {
                LdtOnTimestamptz e = new LdtOnTimestamptz();
                e.id = id;
                e.at = LocalDateTime.ofInstant(realInstant, BOGOTA.toZoneId()); // == LocalDateTime.now() en Bogotá
                s.persist(e);
            });
            String diff = scalar("SELECT round(extract(epoch FROM (to_timestamp(" + realInstant.toEpochMilli() / 1000.0
                    + ") - at)) / 3600)::text FROM " + SCHEMA + ".ldt_tz WHERE id = " + id);
            report("EXP2b LocalDateTime.now() con JVM Bogota", "desfase (instante real - guardado) en horas=" + diff);
            assertThat(diff).isEqualTo("0");
        }
    }

    /**
     * Matriz diagnóstica: escribe LocalDateTime 08:00 vía Hibernate y mira qué queda en la BD
     * según tipo de columna, hibernate.jdbc.time_zone y zona de la JVM.
     */
    @Test
    void exp2c_matrix() {
        long id = 100;
        for (boolean tz : new boolean[] {true, false}) {
            for (String jdbcTz : new String[] {"UTC", null}) {
                for (TimeZone jvm : new TimeZone[] {UTC, BOGOTA}) {
                    TimeZone.setDefault(jvm);
                    long rowId = id++;
                    String table = tz ? "ldt_tz" : "ldt_ts";
                    try (SessionFactory sf = sessionFactory("none", jdbcTz, tz ? LdtOnTimestamptz.class : LdtOnTimestamp.class)) {
                        LocalDateTime readBack;
                        if (tz) {
                            sf.inTransaction(s -> {
                                LdtOnTimestamptz e = new LdtOnTimestamptz();
                                e.id = rowId;
                                e.at = SAMPLE;
                                s.persist(e);
                            });
                            readBack = sf.fromSession(s -> s.find(LdtOnTimestamptz.class, rowId).at);
                        } else {
                            sf.inTransaction(s -> {
                                LdtOnTimestamp e = new LdtOnTimestamp();
                                e.id = rowId;
                                e.at = SAMPLE;
                                s.persist(e);
                            });
                            readBack = sf.fromSession(s -> s.find(LdtOnTimestamp.class, rowId).at);
                        }
                        String stored = scalar(tz
                                ? "SELECT (at AT TIME ZONE 'UTC')::text || ' (UTC)' FROM " + SCHEMA + "." + table + " WHERE id = " + rowId
                                : "SELECT at::text || ' (sin zona)' FROM " + SCHEMA + "." + table + " WHERE id = " + rowId);
                        report("EXP2c col=" + (tz ? "timestamptz" : "timestamp") + " jdbc.time_zone=" + jdbcTz
                                + " JVM=" + jvm.getID(), "escrito=" + SAMPLE + " leido=" + readBack + " BD=" + stored);
                        assertThat(readBack).isEqualTo(SAMPLE);
                        // timestamptz: siempre el instante "08:00 en la zona de la JVM", con o sin jdbc.time_zone.
                        // timestamp: jdbc.time_zone=UTC guarda la hora de pared UTC; sin ella, la de la JVM.
                        boolean shifted = jvm == BOGOTA && (tz || jdbcTz != null);
                        assertThat(stored).startsWith(shifted ? "2026-10-14 13:00:00" : "2026-10-14 08:00:00");
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- experimento 3

    @Test
    void exp3_nativeJdbc_getTimestamp_onTimestamptz() {
        sql("INSERT INTO " + SCHEMA + ".ldt_tz (id, at) VALUES (31, TIMESTAMPTZ '2026-10-14 08:00:00+00')");
        for (TimeZone jvm : new TimeZone[] {UTC, BOGOTA}) {
            TimeZone.setDefault(jvm);
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT at FROM " + SCHEMA + ".ldt_tz WHERE id = 31")) {
                rs.next();
                Timestamp ts = rs.getTimestamp("at");
                LocalDateTime viaTimestamp = ts.toLocalDateTime(); // SaleQueries.localDateTime / NotificationService
                OffsetDateTime viaOdt = rs.getObject("at", OffsetDateTime.class);
                String viaLdt;
                try {
                    viaLdt = String.valueOf(rs.getObject("at", LocalDateTime.class));
                } catch (Exception e) {
                    viaLdt = "EXCEPCION " + e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                report("EXP3 JDBC JVM=" + jvm.getID(),
                        "getTimestamp().toLocalDateTime()=" + viaTimestamp
                                + " | getObject(OffsetDateTime)=" + viaOdt
                                + " | getObject(LocalDateTime)=" + viaLdt);
                if (jvm == UTC) {
                    assertThat(viaTimestamp).isEqualTo(SAMPLE);
                } else {
                    assertThat(viaTimestamp).isEqualTo(SAMPLE.minusHours(5));
                }
                assertThat(viaOdt.toInstant()).isEqualTo(SAMPLE.toInstant(ZoneOffset.UTC));
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ---------------------------------------------------------------- experimento 4

    @Test
    void exp4_instantOnTimestamptz_validateAndRoundTrip() {
        String error = validate(InstantOnTimestamptz.class);
        report("EXP4 Instant sobre timestamptz, validate", error == null ? "VALIDA" : "FALLA: " + error);
        assertThat(error).isNull();

        TimeZone.setDefault(BOGOTA);
        Instant value = SAMPLE.toInstant(ZoneOffset.UTC);
        try (SessionFactory sf = sessionFactory("none", InstantOnTimestamptz.class)) {
            sf.inTransaction(s -> {
                InstantOnTimestamptz e = new InstantOnTimestamptz();
                e.id = 41L;
                e.at = value;
                s.persist(e);
            });
            Instant readBack = sf.fromSession(s -> s.find(InstantOnTimestamptz.class, 41L).at);
            String utcWall = scalar("SELECT (at AT TIME ZONE 'UTC')::text FROM " + SCHEMA + ".instant_tz WHERE id = 41");
            report("EXP4 Instant JVM=Bogota", "escrito=" + value + " leido=" + readBack + " BD(UTC)=" + utcWall);
            assertThat(readBack).isEqualTo(value);
            assertThat(utcWall).isEqualTo("2026-10-14 08:00:00");
        }
    }

    @Test
    void exp4_offsetDateTimeOnTimestamptz_validateAndRoundTrip() {
        String error = validate(OdtOnTimestamptz.class);
        report("EXP4 OffsetDateTime sobre timestamptz, validate", error == null ? "VALIDA" : "FALLA: " + error);
        assertThat(error).isNull();

        TimeZone.setDefault(BOGOTA);
        OffsetDateTime value = OffsetDateTime.of(2026, 10, 14, 3, 0, 0, 0, ZoneOffset.ofHours(-5));
        try (SessionFactory sf = sessionFactory("none", OdtOnTimestamptz.class)) {
            sf.inTransaction(s -> {
                OdtOnTimestamptz e = new OdtOnTimestamptz();
                e.id = 42L;
                e.at = value;
                s.persist(e);
            });
            OffsetDateTime readBack = sf.fromSession(s -> s.find(OdtOnTimestamptz.class, 42L).at);
            String utcWall = scalar("SELECT (at AT TIME ZONE 'UTC')::text FROM " + SCHEMA + ".odt_tz WHERE id = 42");
            report("EXP4 OffsetDateTime JVM=Bogota", "escrito=" + value + " leido=" + readBack + " BD(UTC)=" + utcWall);
            assertThat(readBack.toInstant()).isEqualTo(value.toInstant());
            assertThat(utcWall).isEqualTo("2026-10-14 08:00:00");
        }
    }

    // ---------------------------------------------------------------- experimento 5

    @Test
    void exp5_localDateTimeForcedToTimestampWithTimezone() {
        String error = validate(LdtForcedTz.class);
        report("EXP5 LocalDateTime + @JdbcTypeCode(TIMESTAMP_WITH_TIMEZONE), validate",
                error == null ? "VALIDA" : "FALLA: " + error);
        assertThat(error).isNull();

        // Valida, pero no sirve: Hibernate no sabe convertir LocalDateTime a OffsetDateTime al escribir.
        TimeZone.setDefault(BOGOTA);
        try (SessionFactory sf = sessionFactory("none", LdtForcedTz.class)) {
            assertThatThrownBy(() -> sf.inTransaction(s -> {
                LdtForcedTz e = new LdtForcedTz();
                e.id = 51L;
                e.at = SAMPLE;
                s.persist(e);
            }))
                    .satisfies(t -> report("EXP5 persist JVM=Bogota", "EXCEPCION " + t.getMessage()))
                    .hasMessageContaining("Could not convert 'java.time.LocalDateTime' to 'java.time.OffsetDateTime'");
        }
    }

    // ---------------------------------------------------------------- utilidades

    private static String validate(Class<?> entity) {
        try (SessionFactory ignored = sessionFactory("validate", entity)) {
            return null;
        } catch (Exception e) {
            Throwable t = e;
            StringBuilder sb = new StringBuilder(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            while (t.getCause() != null && t.getCause() != t) {
                t = t.getCause();
                sb.append(" <- ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            }
            return sb.toString();
        }
    }

    private static SessionFactory sessionFactory(String hbm2ddl, Class<?>... entities) {
        return sessionFactory(hbm2ddl, "UTC", entities);
    }

    private static SessionFactory sessionFactory(String hbm2ddl, String jdbcTimeZone, Class<?>... entities) {
        Properties p = new Properties();
        p.put("hibernate.connection.url", URL);
        p.put("hibernate.connection.username", USER);
        p.put("hibernate.connection.password", PASSWORD);
        p.put("hibernate.connection.pool_size", "2");
        p.put("hibernate.default_schema", SCHEMA);
        p.put("hibernate.hbm2ddl.auto", hbm2ddl);
        if (jdbcTimeZone != null) {
            p.put("hibernate.jdbc.time_zone", jdbcTimeZone);
        }
        p.put("hibernate.show_sql", "false");
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(p).build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : entities) {
                sources.addAnnotatedClass(entity);
            }
            return sources.buildMetadata().buildSessionFactory();
        } catch (RuntimeException e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw e;
        }
    }

    private static void sql(String... statements) {
        withUtcConnection(c -> {
            try (Statement st = c.createStatement()) {
                for (String s : statements) {
                    st.execute(s);
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static String scalar(String query) {
        String[] out = new String[1];
        withUtcConnection(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(query)) {
                rs.next();
                out[0] = rs.getString(1);
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        return out[0];
    }

    private static void withUtcConnection(Consumer<Connection> work) {
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            try (Statement st = c.createStatement()) {
                st.execute("SET TIME ZONE 'UTC'");
            }
            work.accept(c);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void report(String experiment, String result) {
        System.out.println("[SPIKE] " + experiment + " -> " + result);
    }
}
