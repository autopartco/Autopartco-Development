# Ambientes de Autopartco

- Fecha: 2026-10-14
- Regla permanente: **cada ambiente tiene su archivo `*.example`** con los nombres de variable y su explicación (nunca valores reales). Si una función nueva necesita configuración, claves de API o secretos, se añade a los `*.example` de **cada** ambiente en el mismo PR.

## 1. Matriz

| Ambiente | Para qué sirve | Frontend | Backend | Base de datos | Pagos |
|---|---|---|---|---|---|
| **Local-mock** | Desarrollar UI sin backend | `.env.example` → `.env.local`, fuente `mock` | no se usa | no | simulados en memoria |
| **Local-http** | Desarrollar contra el backend local | `.env.example` → `.env.local`, fuente `http`, API `localhost:8081` | `backend/.env.example` → `backend/.env`, perfil `dev` | PostgreSQL local o de desarrollo | simulador (`APP_CHECKOUT_SIMULATED_PAYMENTS_ENABLED=true`) |
| **Demo** | Mostrar todas las funciones a terceros con datos de prueba | `.env.demo.example` → `.env.demo.local`; `npm run dev:demo` / `build:demo` | `backend/.env.demo.example`, perfil `dev` | BD **aparte** con datos de demostración | simulador + Wompi **sandbox** |
| **Producción** | Usuarios reales | `.env.production.example` (variables del pipeline); `npm run build:prod` | `backend/.env.production.example`, perfil `prod` | BD de producción con respaldos | ver §4, punto 2 |

Los archivos reales (`.env`, `.env.local`, `.env.demo.local`, `.env.production.local`) los ignora git. Solo se versionan los `*.example`.

## 2. Frontend

Todas las variables son públicas (terminan en el bundle). Los secretos nunca van aquí.

| Variable | Local-mock | Local-http | Demo | Producción |
|---|---|---|---|---|
| `VITE_MARKETPLACE_DATA_SOURCE` | `mock` | `http` | `http` | `http` (**obligatorio**) |
| `VITE_API_BASE_URL` | — | `http://localhost:8081/api/v1` | URL de la API de demo | `https://…/api/v1` (**https obligatorio**) |

- El build de producción falla (`frontend/vite.config.ts`) si la fuente no es `http` o la API no es `https://`.
- El backend solo acepta peticiones de los orígenes de CORS. En local son `http://localhost:5173` y `:5174`; `npm run dev:demo` usa el 5174.
- En modo `http` los tokens viven solo en memoria: recargar la página cierra la sesión (limitación conocida, a resolver con una cookie HttpOnly).

## 3. Backend

El perfil se elige con `SPRING_PROFILES_ACTIVE`: `dev` (local y demo) o `prod` (`application-prod.yaml`: `ddl-auto: none`, CORS obligatorio y explícito, sin trazas de error, cabeceras de proxy).

Variables que **exigen decisión por ambiente**:

| Variable | Qué es | Local | Demo | Producción |
|---|---|---|---|---|
| `DB_*` | Conexión a PostgreSQL | local | BD de demo | BD de producción, usuario sin superprivilegios |
| `RSA_PUBLIC_KEY_PATH` / `RSA_PRIVATE_KEY_PATH` | Par que firma los JWT | `classpath:certs/…` | `file:` con par propio de demo | `file:` con par **exclusivo** de producción |
| `SUPABASE_*` | Almacenamiento de imágenes y documentos | proyecto de desarrollo | proyecto/buckets de demo | proyecto/buckets de producción |
| `MAIL_*` | Correo de verificación y recuperación | SMTP de pruebas | SMTP de pruebas o acotado | SMTP real con dominio verificado (SPF/DKIM) |
| `WOMPI_*` | Suscripciones de almacenes | sandbox | sandbox | sandbox hasta la decisión comercial (blueprint §9.4) |
| `APP_PUBLIC_BASE_URL`, `APP_FRONTEND_BASE_URL` | Enlaces en correos y redirecciones | localhost | URLs de demo | URLs públicas con https |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del frontend | (valor fijo en `application.yaml`) | (igual) | **obligatorio**, sin comodines |
| `APP_CHECKOUT_SIMULATED_PAYMENTS_ENABLED` | Habilita pagos simulados | `true` | `true` | `true` por decisión para la demo; `false` antes de clientes reales |

## 4. Lista de verificación antes de publicar en producción

Publicar es un despliegue y requiere autorización explícita (`git tag vX.Y.Z origin/develop` + `git push origin vX.Y.Z`; revisar antes con `DRY_RUN=1 bash tools/release/promote-to-main.sh`).

1. **Secretos:** ninguno en el repositorio; todos inyectados por el entorno. Par RSA nuevo, distinto al de local y demo.
2. **Pagos:** el simulador sigue activo por decisión del usuario (2026-10-14). Mientras lo esté, cualquier usuario autenticado puede crear pedidos "pagados" sin cobro. Desactivarlo (`false`) es obligatorio antes de vender a clientes reales y requiere integrar Wompi para pedidos (hoy fuera del MVP).
3. **Base de datos:** esquema aplicado con `database/init.sql` y migraciones (`database/migrations/`), **no** con `ddl-auto`. Hay migraciones sin aplicar (`U202610130925__tenant_isolation_triggers.sql`, `…0930__append_only_tables.sql`): aplicarlas en un ensayo y verificar el checkout. Respaldos automáticos activados y una restauración probada.
4. **CORS:** `APP_CORS_ALLOWED_ORIGINS` solo con el dominio real del frontend.
5. **HTTPS:** frontend y API con certificado válido; el frontend usa `https://` (el build lo exige).
6. **Correo:** dominio verificado (SPF, DKIM, DMARC); probar verificación de cuenta y recuperación de contraseña.
7. **Frontend:** `npm run build:prod` en verde con las variables reales; servir `dist/` con *fallback* a `index.html` para las rutas de React Router (`/producto/:id`, `/checkout`…).
8. **Observabilidad:** logs sin datos personales, alerta de errores 5xx y revisión de arranque (`/actuator` solo si se añade con autenticación).
9. **Pruebas previas:** `./mvnw -q test -Dtest=<Clase>` de los módulos tocados (nunca sin `-Dtest`: `BackendApplicationTests` modifica la BD de `backend/.env`), `npm run build && npm run lint`, y el recorrido E2E de Playwright.

## 5. Decisiones abiertas

| # | Pregunta | Efecto |
|---|---|---|
| A1 | ¿Dónde se aloja producción (VPS con Docker, Railway/Render/Fly, Vercel + backend aparte)? | Define Dockerfiles, proxy y el workflow de despliegue. Aún no se genera ninguno para no suponer. |
| A2 | ¿Hay ambiente *staging* previo a producción? | Sería otra fila de la matriz (copia de producción con datos de prueba). |
| A3 | ¿Dominios de demo y producción? | Reemplazan los `example.com` de los `*.example`. |
