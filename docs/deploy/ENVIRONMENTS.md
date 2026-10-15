# Ambientes de Autopartco

- Fecha: 2026-10-15
- Regla permanente: **un solo archivo de configuración**, `.env` en la raíz del repo (git lo ignora), con su plantilla versionada `.env.example`. Si una función nueva necesita configuración, claves de API o secretos, se añade a `.env.example` en el mismo PR (nunca valores reales).

## 1. Arranque local

```bash
cp .env.example .env        # una sola vez; completa los valores
cd backend && mvn spring-boot:run
cd frontend && npm run dev  # en otra terminal → http://localhost:5173
```

- El backend importa `../.env` (`application.yaml`) y Vite lo lee con `envDir: '..'` (`frontend/vite.config.ts`).
- Por defecto corre **como producción** (`SPRING_PROFILES_ACTIVE=prod`, frontend con fuente `http`) contra la BD local. Para depurar con `ddl-auto: update` y trazas de error, cambia a `dev`.
- Para UI sin backend: `VITE_MARKETPLACE_DATA_SOURCE=mock`.
- Spring no convierte `SPRING_PROFILES_ACTIVE` de un archivo importado en perfil por sí solo; `application.yaml` lo hace con `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`.

## 2. Producción

En el servidor no se usa el `.env` del repo: las mismas variables se inyectan como variables de entorno (gestor de secretos o del orquestador) con valores de producción. Las del frontend (`VITE_*`) se fijan en el build.

- `npm run build` falla (`frontend/vite.config.ts`) si la fuente no es `http` o la API no es `https://` (se admite `http://localhost` para probar el build en la máquina).
- Todas las variables `VITE_*` son públicas (terminan en el bundle). Los secretos nunca llevan ese prefijo.

## 3. Variables que cambian entre local y producción

El perfil `prod` (`application-prod.yaml`) aplica `ddl-auto: none`, CORS obligatorio y explícito, sin trazas de error y cabeceras de proxy.

| Variable | Qué es | Local (`.env`) | Producción |
|---|---|---|---|
| `DB_*` | Conexión a PostgreSQL | BD local | BD de producción, usuario sin superprivilegios |
| `RSA_PUBLIC_KEY_PATH` / `RSA_PRIVATE_KEY_PATH` | Par que firma los JWT | `classpath:certs/…` | `file:` con par **exclusivo** de producción |
| `SUPABASE_*` | Almacenamiento de imágenes y documentos | proyecto de desarrollo | proyecto/buckets de producción |
| `MAIL_*` | Correo de verificación y recuperación | SMTP de pruebas | SMTP real con dominio verificado (SPF/DKIM) |
| `WOMPI_*` | Suscripciones de almacenes | sandbox o vacío | sandbox hasta la decisión comercial (blueprint §9.4) |
| `APP_PUBLIC_BASE_URL`, `APP_FRONTEND_BASE_URL` | Enlaces en correos y redirecciones | localhost | URLs públicas con https |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del frontend (obligatorio con `prod`) | `http://localhost:5173` | dominio real, sin comodines |
| `AUTH_COOKIE_SECURE` | Cookie del refresh token | `false` (sin HTTPS) | `true` |
| `APP_CHECKOUT_SIMULATED_PAYMENTS_ENABLED`, `SIMULATED_PAYMENTS_ENABLED` | Pagos simulados | `true` | `true` por decisión para la demo; `false` antes de clientes reales |
| `VITE_MARKETPLACE_DATA_SOURCE` | Origen de datos del frontend | `http` (o `mock`) | `http` (**obligatorio**) |
| `VITE_API_BASE_URL` | API con prefijo de versión | `http://localhost:8081/api/v1` | `https://…/api/v1` |

## 4. Lista de verificación antes de publicar en producción

Publicar es un despliegue y requiere autorización explícita (`git tag vX.Y.Z origin/develop` + `git push origin vX.Y.Z`; revisar antes con `DRY_RUN=1 bash tools/release/promote-to-main.sh`).

1. **Secretos:** ninguno en el repositorio; todos inyectados por el entorno. Par RSA nuevo, distinto al de local.
2. **Pagos:** el simulador sigue activo por decisión del usuario (2026-10-14). Mientras lo esté, cualquier usuario autenticado puede crear pedidos "pagados" sin cobro. Desactivarlo (`false`) es obligatorio antes de vender a clientes reales y requiere integrar Wompi para pedidos (hoy fuera del MVP).
3. **Base de datos:** esquema aplicado con `database/init.sql` y migraciones (`database/migrations/`), **no** con `ddl-auto`. Hay migraciones sin aplicar (`U202610130925__tenant_isolation_triggers.sql`, `…0930__append_only_tables.sql`): aplicarlas en un ensayo y verificar el checkout. Respaldos automáticos activados y una restauración probada.
4. **CORS:** `APP_CORS_ALLOWED_ORIGINS` solo con el dominio real del frontend.
5. **HTTPS:** frontend y API con certificado válido; el frontend usa `https://` (el build lo exige).
6. **Correo:** dominio verificado (SPF, DKIM, DMARC); probar verificación de cuenta y recuperación de contraseña.
7. **Frontend:** `npm run build` en verde con las variables reales; servir `dist/` con *fallback* a `index.html` para las rutas de React Router (`/producto/:id`, `/checkout`…).
8. **Observabilidad:** logs sin datos personales, alerta de errores 5xx y revisión de arranque (`/actuator` solo si se añade con autenticación).
9. **Pruebas previas:** `./mvnw -q test -Dtest=<Clase>` de los módulos tocados (nunca sin `-Dtest`), `npm run build && npm run lint`, y el recorrido E2E de Playwright.

## 5. Decisiones abiertas

| # | Pregunta | Efecto |
|---|---|---|
| A1 | ¿Dónde se aloja producción (VPS con Docker, Railway/Render/Fly, Vercel + backend aparte)? | Define Dockerfiles, proxy y el workflow de despliegue. Aún no se genera ninguno para no suponer. |
| A2 | ¿Hay ambiente *staging* previo a producción? | Sería otra fila en §3. |
| A3 | ¿Dominio de producción? | Define `APP_CORS_ALLOWED_ORIGINS`, las URLs públicas y `VITE_API_BASE_URL`. |
