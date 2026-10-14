# ADR 0002 — Zona horaria de la JVM y `LocalDateTime` sobre `timestamptz`

- Estado: **Aceptado** (decisión del usuario: Opción A)
- Fecha: 2026-10-14
- Autor: agente `arquitecto`
- Relacionado: `docs/adr/0001-estrategia-de-esquema-y-migraciones.md` §2.5 (corregido por este ADR), `database/migrations/pending-approval/PA_V202610141110__timestamptz_existing_tables.sql`, `docs/architecture/2026-10-14-backend-preparacion-migraciones-pendientes-plan.md` (D2, T4)

## 1. Contexto

La migración A3 convierte todas las columnas `TIMESTAMP` de `public` a `TIMESTAMPTZ`. Su cabecera, el ADR 0001 §2.5 y `database/migrations/README.md` afirmaban que, con `ddl-auto: validate`, Hibernate fallaría con `LocalDateTime` y recomendaban `OffsetDateTime`/`Instant` más `hibernate.jdbc.time_zone: UTC`. **Esa afirmación es falsa** (ver §2).

El backend usa `LocalDateTime` en 135 sitios de 42 archivos, y `BaseEntity` usa `LocalDateTime.now()`. Cambiarlo todo altera el contrato JSON público.

## 2. Experimentos

Spike: `backend/src/test/java/com/autopartco/backend/spike/TimestamptzSpikeTest.java` (10 pruebas OK). Hibernate 7.4.5, pgjdbc 42.7.13, PostgreSQL 16.

| # | Prueba | Resultado |
|---|---|---|
| 1 | `LocalDateTime` sobre `timestamptz` con `validate` | **Valida**. Control negativo: una columna `text` sí falla con `wrong column type ... found [text (Types#VARCHAR)], but expecting [timestamp(6) (Types#TIMESTAMP)]` |
| 2 | Efecto de `hibernate.jdbc.time_zone` sobre `timestamptz` | **Ninguno**: el `LocalDateTime` se interpreta siempre en la zona de la JVM. `2026-10-14T08:00` se guarda como 08:00Z con JVM en UTC y como 13:00Z con JVM en Bogotá |
| 3 | `LocalDateTime.now()` | Guarda el instante correcto en cualquier zona de JVM |
| 4 | `hibernate.jdbc.time_zone` sobre columnas `timestamp` (no migradas) | Sí influye: JVM Bogotá + `time_zone=UTC` guarda 13:00 (desfase de 5 h) |
| 5 | JDBC nativo `getTimestamp().toLocalDateTime()` | Hora de pared en la zona de la JVM (08:00Z da 08:00 con JVM UTC y 03:00 con Bogotá); coherente con Hibernate, así que `SaleQueries.localDateTime` y `NotificationService` siguen correctos |
| 6 | JDBC `getObject(col, LocalDateTime.class)` | Lanza `PSQLException`; `getObject(col, OffsetDateTime.class)` devuelve 08:00Z |
| 7 | `Instant`/`OffsetDateTime` | Validan y no dependen de la zona de la JVM |
| 8 | `@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)` sobre `LocalDateTime` | Valida pero **falla al persistir** |
| 9 | `preferred_instant_jdbc_type`, `default_storage` | Solo afectan a `Instant`/`OffsetDateTime`/`ZonedDateTime` |
| 10 | Zona de sesión de pgjdbc | pgjdbc fija la `TimeZone` de la sesión a la de la JVM, así que `now()` de SQL coincide con la zona de la JVM |

Conclusión: con `LocalDateTime` sobre `timestamptz`, la única variable que importa es la zona por defecto de la JVM.

## 3. Decisión (Opción A)

Conservar `LocalDateTime` y **fijar la zona de la JVM en `America/Bogota` desde el código**.

- `BackendApplication.main` ejecuta `TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))` antes de `SpringApplication.run` (lo implementa el agente `backend`).
- Opcional y redundante: `-Duser.timezone=America/Bogota` o `TZ=America/Bogota` en el despliegue.
- **No** se fija `hibernate.jdbc.time_zone`.
- `ddl-auto: validate` (lo aplica el usuario en `application.yaml`).
- La migración A3 se ejecuta con `autopartco.legacy_source_tz = 'America/Bogota'`, porque los datos existentes están en hora de Bogotá.

Razones: el mercado es solo Colombia y no hay horario de verano; el JSON no cambia; `date_trunc('month', now())` de `SaleQueries` calcula meses de Colombia; coincide con `legacy_source_tz`; es 1 línea frente a ~135 sitios.

## 4. Configuración exacta

```java
// BackendApplication.main
TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
SpringApplication.run(BackendApplication.class, args);
```

```yaml
# application.yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    # sin hibernate.jdbc.time_zone
```

```sql
-- antes de ejecutar A3
SET autopartco.legacy_source_tz = 'America/Bogota';
```

Orden: backend añade la línea y la prueba que la verifica; el usuario cambia el yaml; `database` ejecuta A3 con la app detenida.

## 5. Alternativas descartadas

- **B: `Instant`/`OffsetDateTime`.** Robusta e independiente de la zona de la JVM, pero cambia el contrato JSON y afecta a 7 entidades (`BaseEntity`, `StoreSubscription`, `AccountSession`, `AccountToken`, `LoginAttempt`, `StaffInvitation`, `StoreApplication`), `SaleQueries.localDateTime`, `NotificationService`, `AccountSessionRepository.insertSession`, ~20 servicios/mapeadores, ~13 DTO públicos y 57 apariciones en 6 archivos de test. Queda como camino para módulos nuevos o migración gradual.
- **A': JVM en UTC + `hibernate.jdbc.time_zone: UTC`.** El JSON saldría en UTC y el frontend mostraría +5 h.

## 6. Consecuencias y riesgos

- La corrección depende de que la zona de la JVM se fije en código. Un contenedor en UTC sin esa línea desplazaría 5 h las escrituras nuevas con `LocalDateTime` fijo (no las de `now()`).
- Las consultas SQL ad hoc y de reportes deben usar `AT TIME ZONE 'America/Bogota'` para fechas de negocio.
- Las tablas nuevas siguen siendo `TIMESTAMPTZ` (ADR 0001 §2.5); el código nuevo preferirá `Instant`/`OffsetDateTime`.
- Si el mercado se amplía fuera de Colombia, este ADR se revisa y se migra a la Opción B.
- Verificación: prueba que fije la zona y compruebe escritura/lectura sin desfase; arranque con `validate` sin diferencias.
