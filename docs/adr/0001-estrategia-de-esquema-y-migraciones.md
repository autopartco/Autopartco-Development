# ADR 0001 — Estrategia de esquema y migraciones versionadas

- Estado: **Propuesto** (pendiente de aprobación del usuario; no se aplica nada todavía)
- Fecha: 2026-10-13
- Autor: agente `arquitecto`
- Relacionado: `docs/architecture/2026-10-13-database-predictive-data-model.md`, brechas DB-X-1 y DB-X-2 de `docs/architecture/2026-10-13-backend-modular-architecture-design.md` §11

## 1. Contexto

- El esquema vive en un solo archivo, `database/init.sql` (54 tablas). Es "idempotente a base de parches": `CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, bloques `DO $$` que añaden `created_at/updated_at/deleted_at/version` a todas las tablas y un bloque que busca y elimina una restricción `UNIQUE` por nombre. No se sabe qué versión del esquema tiene una base de datos concreta.
- `spring.jpa.hibernate.ddl-auto: update` está activo en `backend/src/main/resources/application.yaml` y en `application-dev.yaml`. Como `application.yaml` es el perfil base, también aplicaría en producción.
- **Deriva comprobada (DB-X-2):** `seller_plans.max_branches`, `max_products`, `max_users` y `capabilities` están mapeadas en `core/billing/internal/Plan.java`, pero no existen en `init.sql`. Solo las crea `ddl-auto`. Una base creada desde `init.sql` falla al usar planes.
- **Tipos de tiempo mixtos:** casi todo es `TIMESTAMP` sin zona. `promotions.starts_at/ends_at` son `TIMESTAMPTZ`. `BaseEntity` usa `LocalDateTime.now()`, que depende de la zona de la JVM, mientras que `DEFAULT now()` depende de la zona de la sesión de PostgreSQL (UTC en Supabase). En la misma columna puede haber filas en hora de Bogotá y filas en UTC. Para series de tiempo esto es inaceptable.
- Nada está desplegado y la BD de `backend/.env` es desechable (decisión U1 del usuario).
- El diseño del modelo predictivo (documento relacionado) añade unas 55 tablas en tres fases (24 en la fase 1), 4 esquemas nuevos, particionamiento y funciones SQL. `ddl-auto` no puede crear nada de eso: ni particiones, ni BRIN, ni vistas materializadas, ni funciones, ni datos semilla. Tampoco puede hacerlo de forma revisable.

## 2. Decisión

### 2.1 Herramienta: Flyway Community (**requiere aprobación**: nueva dependencia)

Adoptar Flyway (edición Community, gratuita y Apache 2.0) como única vía de cambio de esquema.

- Dependencias en `backend/pom.xml`: el starter de Flyway de Spring Boot 4.1.x y `org.flywaydb:flyway-database-postgresql` (obligatorio para PostgreSQL desde Flyway 10). En Spring Boot 4 la autoconfiguración está modularizada. El agente `backend` debe confirmar en la documentación oficial de Spring Boot 4.1 el nombre exacto del artefacto (`spring-boot-starter-flyway` o `spring-boot-flyway`) antes de editar el `pom.xml`, que el hook protege.
- Las migraciones viven en **`database/migrations/`** (área del agente `database`). Para que viajen dentro del jar, `backend/pom.xml` declara ese directorio como recurso con `targetPath` `db/migration`, y se configura `spring.flyway.locations: classpath:db/migration`. En local se puede usar `filesystem:../database/migrations` sin empaquetar.
- Configuración propuesta (`application.yaml`):

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false      # la BD es desechable: se crea desde cero
    validate-on-migrate: true
    out-of-order: false             # true solo en el perfil dev
    clean-disabled: true            # nunca clean fuera de pruebas
    default-schema: public
    schemas: public,tracking,analytics,ml,privacy
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      # sin hibernate.jdbc.time_zone: ver ADR 0002
```

### 2.2 Convención de nombres

| Tipo | Patrón | Ejemplo |
|---|---|---|
| Versionada | `V<AAAAMMDDHHMM>__<descripcion_snake_case>.sql` | `V202610140900__baseline_schema.sql` |
| Rollback | `U<AAAAMMDDHHMM>__<misma_descripcion>.sql` (mismo número que su `V`) | `U202610140900__baseline_schema.sql` |
| Repetible (semillas de referencia, vistas, funciones) | `R__<descripcion>.sql` | `R__seed_holidays_co.sql` |
| Configuración por script | `<archivo>.sql.conf` | `V202610141100__analytics_indexes.sql.conf` con `executeInTransaction=false` |

Reglas:

1. **El timestamp se toma en UTC al crear el archivo** y nunca se reutiliza. Así dos ramas no colisionan por número. El orden de aplicación es el orden numérico.
2. **Una migración aplicada es inmutable.** Si hay que corregirla, se escribe otra `V` (Flyway valida el checksum).
3. Cada `V` que crea o altera estructura tiene su `U`, que la revierte de forma exacta. Si el rollback perdería datos (por ejemplo, `DROP TABLE` de una tabla con datos), la `U` empieza con el comentario `-- DESTRUCTIVO: requiere aprobación` y aborta salvo que se fije a mano la variable de sesión `autopartco.allow_destructive = 'on'`. Para eso usa un bloque `DO` que lanza `RAISE EXCEPTION` si no está fijada.
4. **Undo es una función de pago de Flyway (Teams/Enterprise).** En Community los `U` no se ejecutan automáticamente. Son scripts de rollback que se aplican a mano con `psql` en entornos no productivos y quedan versionados junto a su `V`. Al adoptar Flyway, el agente `backend` debe verificar que Community los ignora sin fallar en `validate`. Si fallara, se mueven a `database/rollback/` con el mismo nombre. Ese cambio requiere aprobación porque toca la convención.
5. Cada migración empieza con `SET lock_timeout = '5s'; SET statement_timeout = '15min';`. Las operaciones que no admiten transacción (`CREATE INDEX CONCURRENTLY`, `ALTER TYPE ... ADD VALUE`) van en un archivo propio con su `.sql.conf` (`executeInTransaction=false`).
6. Los cambios se escriben en el orden **expandir, migrar datos, contraer**. Primero se añade lo nuevo (columna nullable, tabla nueva). Después se rellena con datos en una migración aparte o con un job. Por último, en una versión posterior, se endurece (`NOT NULL`, eliminación de lo viejo). La fase de contracción siempre está marcada como "requiere aprobación".
7. No se usan `IF NOT EXISTS` ni `IF EXISTS` en migraciones versionadas: el historial de Flyway garantiza el estado previo. Sí se usan en `R__` y en `U`.
8. Los datos semilla de catálogo (estados, métodos de pago, motivos tipificados) van en `V` cuando el código depende de ellos. Los datos de referencia voluminosos o actualizables (festivos, DIVIPOLA) van en `R__` con `INSERT ... ON CONFLICT DO UPDATE`.

### 2.3 Línea base

- `V202610140900__baseline_schema.sql` es la transcripción determinista de `init.sql` con estos cambios:
  - Elimina los parches idempotentes: deja cada tabla en su forma final, con las columnas comunes ya declaradas y sin bloques `DO`.
  - Incorpora la deriva real: `seller_plans.max_branches INTEGER`, `max_products INTEGER`, `max_users INTEGER`, `capabilities JSONB NOT NULL DEFAULT '[]'::jsonb`. El agente `database` debe comparar todas las entidades JPA con la línea base para no dejar más deriva. El arranque con `ddl-auto: validate` es la prueba de aceptación.
  - Mantiene nombres, tipos y erratas existentes (`store`, `bussnies_name`, `products_compatibilies`). Renombrarlos queda fuera y **requiere aprobación**.
- Como la BD es desechable, **se recrea desde cero** y no se usa `baseline-on-migrate`. Recrearla es una operación destructiva contra una base de datos: **requiere aprobación** y la ejecuta el usuario o la sesión principal.
- `database/init.sql` se congela. Se le añade un encabezado "OBSOLETO: ver database/migrations/", que es una edición de `database/` y por tanto la hace el agente `database`. Borrarlo **requiere aprobación**.

### 2.4 `ddl-auto: validate`

- Se pasa de `update` a `validate` en `application.yaml` y en `application-dev.yaml` cuando la línea base esté aplicada y el arranque de humo pase (ya autorizado como U2 contra la BD desechable).
- Las pruebas de integración usan una BD aislada migrada por Flyway. Hasta que exista esa aislación sigue vigente la regla de AGENTS.md: pruebas solo con `-Dtest=<Clase>`.
- Hibernate `validate` no comprueba particiones, índices, `CHECK` ni funciones. La fuente de verdad es Flyway. `validate` solo detecta deriva entre entidades y columnas.

### 2.5 Política de tiempo (**requiere aprobación** para tablas existentes)

- **Tablas nuevas:** siempre `TIMESTAMPTZ` y fechas de negocio en `DATE` calculadas en `America/Bogota`.
- **Tablas existentes:** conversión `TIMESTAMP → TIMESTAMPTZ` en una migración propia (`V..__timestamptz_existing_tables.sql`) con `ALTER COLUMN ... TYPE timestamptz USING <col> AT TIME ZONE 'UTC'`. Es un **cambio de tipo y reescritura de tablas: requiere aprobación**. **Corrección (ADR [0002](0002-zona-horaria-y-timestamptz.md)):** no hace falta cambiar `LocalDateTime` (valida con `validate` sobre `timestamptz`) ni fijar `hibernate.jdbc.time_zone`, que no tiene efecto sobre `timestamptz`; se fija la zona de la JVM en `America/Bogota` y la migración usa `autopartco.legacy_source_tz = 'America/Bogota'`. Como la BD es desechable, no hace falta corregir datos históricos mal zonificados.

## 3. Alternativas consideradas

| Alternativa | A favor | En contra | Veredicto |
|---|---|---|---|
| Mantener `init.sql` + `ddl-auto: update` | Cero esfuerzo | Deriva ya demostrada; no soporta particiones, funciones ni semillas; sin historial; peligroso en producción | Rechazada |
| **Flyway Community** | SQL puro (el agente `database` escribe PostgreSQL nativo: particiones, BRIN, funciones), integración nativa con Spring Boot, `R__` para semillas, checksums | Undo solo en edición de pago; requiere disciplina de inmutabilidad | **Elegida** |
| Liquibase | Rollback incluido en la edición libre, changelogs XML/YAML/SQL | Más ceremonia; el rollback automático de SQL nativo igualmente hay que escribirlo; menos directo para DDL específico de PostgreSQL | Rechazada (viable) |
| Scripts propios + tabla de control | Sin dependencia | Reinventar checksums, bloqueo y orden; frágil | Rechazada |
| Migraciones fuera de la app (CLI/CI) en lugar de al arrancar | Separa despliegue de arranque, permisos DDL mínimos para la app | Requiere pipeline que hoy no existe | Diferida: aplicar en el primer despliegue (la app correría con un rol sin DDL; **requiere aprobación** por ser cambio de seguridad) |

## 4. Consecuencias

**Positivas**
- El esquema es reproducible y revisable, y cada BD sabe en qué versión está (`flyway_schema_history`).
- Se pueden crear particiones, BRIN, vistas materializadas, funciones de refresco y datos de referencia, que el modelo predictivo necesita.
- `validate` hace fallar el arranque ante la deriva, en vez de "arreglarla" en silencio.

**Negativas / costes**
- Cada cambio de entidad JPA exige su migración. El agente `backend` ya no puede añadir columnas solo con anotaciones.
- Los rollback son manuales en Community.
- Hay que recrear la BD de desarrollo una vez.

**Orden de implementación**
1. Usuario: aprueba la dependencia Flyway, la recreación de la BD desechable y, por separado, la conversión a `timestamptz`.
2. `database`: `V202610140900__baseline_schema.sql` + `U...`, encabezado de obsolescencia en `init.sql`.
3. `backend`: dependencias y recursos en `pom.xml`, configuración `spring.flyway.*`, `ddl-auto: validate`, zona de la JVM fija (ADR 0002); arranque de humo.
4. `qa-seguridad`: comprueba que `clean-disabled: true` está activo, que no hay credenciales en las migraciones y que el arranque falla ante deriva (columna eliminada a propósito en una BD de prueba).

## 5. Decisiones que requieren aprobación (resumen)

1. Añadir Flyway (dependencia) y declarar `database/migrations` como recurso en `pom.xml`.
2. Recrear la BD de `backend/.env` desde la línea base (comando destructivo contra una BD).
3. Convertir las columnas `TIMESTAMP` existentes a `TIMESTAMPTZ` (cambio de tipo).
4. Borrar `database/init.sql` (por defecto solo se congela).
5. Más adelante, ejecutar migraciones con un rol sin privilegios DDL para la app (cambio de seguridad).
