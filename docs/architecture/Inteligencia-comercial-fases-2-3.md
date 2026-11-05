# Inteligencia comercial — fases 2 y 3

Estado: propuesta (2026-11-05). La fase 1 está en producción (v1.2.0): `core.analytics`, `/stores/{id}/analytics/*`, `/admin/analytics/*`, registro anónimo en `marketplace_searches` y permiso `ANALYTICS_READ`.

Reglas de ejecución:
- Esquema y datos solo en Supabase `autopartco-dev` (`hwojkaguqmegvdxlcysd`) con `apply_migration`, desde la sesión orquestadora. Ningún archivo SQL en el repo.
- Producto lanzado: textos en español de Colombia, sin «demo» ni «próximamente».
- Pruebas backend solo con `-Dtest=<Clases>`. QA, si se hace, solo sobre el diff de la fase.
- Ramas `feature/*` desde `develop`, PR a `develop`, publicación con tag `vX.Y.Z`.

## 1. Alcance

| Fase | Entra | No entra |
|---|---|---|
| 2 · Acción comercial | Promociones con audiencia (por segmento de cliente) y su aplicación en precio y checkout; pantalla de promociones del portal; medición de resultados; productos que se compran juntos y combos; alertas automáticas (caída de ventas, agotamiento estimado, cliente frecuente en riesgo) en el portal y por correo | Campañas por WhatsApp, cupones con código, precios por cliente individual |
| 3 · Inteligencia automotriz | Demanda no atendida por almacén; demanda por marca, modelo, año y ciudad; ubicación y año del vehículo en la búsqueda; recomendaciones de catálogo; modelos de ML con volumen (probabilidad de recompra y pronóstico por producto), con conmutación automática según historial | Diagnóstico de fallas, identificación de piezas por foto |

## 2. Componentes

| Capa | Componente | Responsabilidad |
|---|---|---|
| BD | `promotions.audience`, `promotion_exposures`, `product_associations`, `store_alerts`, `alert_preferences`, `analytics_model_runs` | Datos nuevos de fases 2 y 3 |
| core.analytics | `CustomerSegments` (ya existe) + `SegmentMembership` | Responde «¿a qué segmentos pertenece esta persona para este almacén y en la plataforma?» con caché corta |
| core.pricing | `PriceCalculator`, `PromotionRule`, `PricingSubject` | Evaluar la audiencia: necesita el comprador (`BuyerContext`) |
| core.analytics | `PromotionResults`, `ProductAssociations`, `AlertRules`, `UnmetDemand`, `VehicleDemand` | Cálculos de las fases |
| storesuite | `promotions` (cambia), `analytics` (amplía), `automation` (alertas) | Casos de uso del portal |
| operations | `promotions` (plataforma), `metrics` (amplía) | Administración |
| marketplace | `catalog`, `checkout`, `vehiclesearch` | Precio por audiencia, combos, búsqueda con año y ubicación |
| intelligence | `demandforecast` | Modelos de ML y su conmutación |
| frontend | portal (promociones, alertas, demanda), marketplace (beneficios, combos), admin (promociones de plataforma, demanda) | UI |

## 3. Contratos

### 3.1 BD (Supabase)

- `promotions` + `audience VARCHAR(20) NOT NULL DEFAULT 'ALL' CHECK IN ('ALL','PLATFORM_FREQUENT','STORE_FREQUENT','STORE_AT_RISK','STORE_NEW','STORE_DORMANT')`. `PLATFORM_FREQUENT` solo con capability `ADVANCED_REPORTS` (almacén) o `scope='PLATFORM'`.
- `promotion_exposures (id, promotion_id FK, person_id FK NULL, session_hash VARCHAR(64) NULL, product_id, shown_at)`, solo inserción, índice `(promotion_id, shown_at)`, retención 18 meses. Sirve para alcance y grupo de control.
- `product_associations (store_id NULL, antecedent_product_id, consequent_product_id, support NUMERIC, confidence NUMERIC, lift NUMERIC, source VARCHAR(20) CHECK IN ('ORDERS','COMPATIBILITY','CATEGORY'), computed_at)`, PK `(coalesce(store_id), antecedent, consequent)`; se reescribe cada noche.
- `store_alerts (id, store_id, type VARCHAR(40), severity CHECK IN ('INFO','WARNING','CRITICAL'), title, message, payload JSONB, dedupe_key VARCHAR(160), created_at, read_at, resolved_at)`, `UNIQUE (store_id, dedupe_key)`.
- `alert_preferences (store_id, type, in_app BOOL, email BOOL, threshold JSONB)`, PK `(store_id, type)`.
- `marketplace_searches` ya tiene `vehicle_year` y `municipality_code`; en la fase 3 se rellenan.
- `analytics_model_runs (id, model VARCHAR(40), scope_store_id NULL, trained_at, rows_used, metric_name, metric_value, params JSONB, active BOOL)`.
- RLS activado sin políticas y GRANT a `autopartco_app` (patrón existente). Permiso nuevo `ALERT_MANAGE` («Configurar alertas») asignado a OWNER.

### 3.2 Precio por audiencia

```java
record BuyerContext(UUID personId /* null si anónimo */, Set<String> platformSegments, Map<UUID, Set<String>> storeSegments) {}
PriceQuote bestPrice(PricingSubject subject, List<PromotionRule> promotions, BuyerContext buyer);
```
- `PromotionRule` añade `audience`. Regla con audiencia ≠ `ALL` solo aplica si el comprador pertenece al segmento; anónimo solo ve `ALL`.
- El checkout recalcula con el mismo `BuyerContext` (nunca confía en el precio del cliente) y guarda `order_details.promotion_id`.
- `SegmentMembership` calcula los segmentos de una persona en una consulta y cachea 10 minutos.

### 3.3 Endpoints

| Método y ruta | Respuesta | Permiso |
|---|---|---|
| `GET/POST/PATCH/DELETE /stores/{id}/promotions` (existe) | añade `audience` y `results` en el detalle | `PROMOTION_*` + capability `PROMOTIONS` |
| `GET /stores/{id}/promotions/{pid}/results` | `{exposures, buyers, orders, revenue, unitsSold, baseline:{revenuePerDay, ordersPerDay}, lift, controlConversion, exposedConversion, status:"RUNNING"\|"FINISHED"\|"INSUFFICIENT_DATA"}` | `PROMOTION_READ` |
| `GET /marketplace/products/{id}/frequently-bought` | `[{productId, name, price, image, reason:"ORDERS"\|"COMPATIBILITY"\|"CATEGORY"}]` (≤6, solo publicados con stock) | público |
| `GET /marketplace/me/benefits` | promociones vigentes para el comprador autenticado por segmento | autenticado |
| `GET /stores/{id}/analytics/associations` | pares con soporte, confianza y lift + combo sugerido | `ANALYTICS_READ` + `ADVANCED_REPORTS` |
| `GET /stores/{id}/alerts?status=` · `PATCH /alerts/{aid}` (`read`/`resolved`) | alertas del almacén | `STORE_READ` |
| `GET/PUT /stores/{id}/alert-preferences` | preferencias por tipo | `ALERT_MANAGE` |
| `GET /stores/{id}/analytics/unmet-demand?from&to` | `{queries:[{query, searches, municipalities}], vehicles:[{make, model, year, searches}], suggestedCategories:[…]}` filtrado a la ciudad de sus sucursales y a sus categorías | `ANALYTICS_READ` + `ADVANCED_REPORTS` |
| `GET /admin/analytics/vehicle-demand?from&to&municipality?` | demanda por marca, modelo, año y ciudad: búsquedas, compras y conversión | admin |
| `POST/PATCH /admin/promotions` (existe `PlatformPromotionController`) | añade `audience` | admin |

### 3.4 Alertas (tarea nocturna 05:00 America/Bogota y al registrar una venta)

| Tipo | Regla por defecto |
|---|---|
| `SALES_DROP` | ventas de los últimos 7 días < 65 % de la media de las 8 semanas anteriores (requiere ≥8 semanas) |
| `STOCKOUT_SOON` | cobertura < 7 días en un producto clase A o B |
| `STOCKOUT` | producto publicado con stock 0 y ventas en los últimos 30 días |
| `CUSTOMER_AT_RISK` | un cliente Frecuente pasa a En riesgo |
| `DEAD_STOCK` | producto con stock sin ventas en 90 días (resumen semanal) |
Deduplicación por `dedupe_key` (tipo + objeto + semana). El correo usa `EmailService` y solo sale si la preferencia está activa.

## 4. Decisiones

- **Audiencia evaluada en el servidor y en el checkout**: no basta con ocultar la promoción en la UI.
- **Almacén nunca ve la identidad de los «frecuentes de Autopartco»**: solo alcance y resultados agregados (Ley 1581).
- **Medición con grupo de control**: compradores del mismo segmento sin exposición; si hay menos de 30 expuestos → `INSUFFICIENT_DATA`. Descartado: antes/después sin control (confunde estacionalidad).
- **Asociaciones en tres niveles**: pedidos reales cuando hay ≥20 pedidos con ≥2 productos; si no, compatibilidad por vehículo y luego categoría. El campo `reason` lo explica.
- **ML solo con volumen**: `analytics_model_runs` registra el entrenamiento y la métrica; el modelo solo se activa si supera al método estadístico de la fase 1 en validación (MAPE o AUC). Primero en Java (regresión logística, gradient boosting ligero) sin servicio Python; un servicio aparte solo si la métrica lo justifica.
- **Ubicación de la búsqueda**: municipio del comprador autenticado (dirección principal) o el elegido en el marketplace; nunca IP.

## 5. Tareas

Secuencia: Fase 2 (T1 → T2 → T3 → T4 → T5 → T6 → T7) y luego Fase 3 (T8 → T9 → T10 → T11 → T12). Dentro de cada fase, BD → backend → frontend.

### Fase 2

**T1 — BD fase 2** · sesión orquestadora (Supabase)
Aplicar `promotions.audience`, `promotion_exposures`, `product_associations`, `store_alerts`, `alert_preferences`, permiso `ALERT_MANAGE`.
Aceptación: consultas de lectura muestran columnas, restricciones, RLS y GRANT.

**T2 — Precio por audiencia** · Opus · `backend`
Archivos: `core/pricing/*`, `core/analytics/SegmentMembership.java`, `marketplace/catalog`, `marketplace/checkout`, `storesuite/promotions`, `operations/promotions`.
Contexto: §3.2, §3.3 (promociones). Registrar exposición al mostrar una promoción con audiencia.
Aceptación: pruebas de cada audiencia (anónimo, frecuente plataforma, frecuente almacén, fuera de segmento), checkout que recalcula y rechaza precio manipulado; `-Dtest=PriceCalculatorTest,SegmentMembershipTest,<checkout>,ModularityTests` en verde.

**T3 — Resultados de promociones** · Sonnet · `backend`
Archivos: `core/analytics/PromotionResults.java`, endpoint `…/promotions/{pid}/results`.
Aceptación: lift con control, `INSUFFICIENT_DATA` con <30 expuestos; pruebas unitarias en verde.

**T4 — Productos que se compran juntos** · Sonnet · `backend`
Archivos: `core/analytics/ProductAssociations.java` (tarea nocturna 04:00), endpoints `frequently-bought` y `analytics/associations`.
Aceptación: soporte/confianza/lift correctos con datos conocidos; respaldo por compatibilidad y categoría; solo productos publicados con stock.

**T5 — Alertas** · Sonnet · `backend`
Archivos: `core/analytics/AlertRules.java`, `storesuite/automation/*`, plantillas de correo en `EmailService`.
Contexto: §3.4.
Aceptación: cada regla con su prueba, deduplicación, correo solo con preferencia activa.

**T6 — Portal y marketplace fase 2** · Sonnet · `frontend`
Archivos: página de promociones del portal (no existe: lista, crear y editar con audiencia y resultados), centro de alertas en el encabezado del portal y preferencias, combos en la ficha del producto, «Tus beneficios» y etiqueta «Precio para clientes frecuentes» en el marketplace, audiencia en promociones de administración, enlace desde Inteligencia › Clientes a «Crear promoción para este segmento».
Aceptación: `npm run lint` y `npm run build` en verde; el mock reproduce audiencias y alertas.

**T7 — Publicación fase 2** · Haiku · sesión orquestadora
QA acotado al diff si el usuario lo pide, `graphify update .`, commits por capa, PR, merge y tag `v1.3.0`; verificar Vercel y Render.

### Fase 3

**T8 — Búsqueda con año y ubicación** · Sonnet · `backend` → `frontend`
Backend: parámetros `year` y `municipality` en la búsqueda del marketplace y su registro; municipio por defecto desde la dirección principal del comprador autenticado. Frontend: selector de año del vehículo y ciudad en el marketplace (recordado en el navegador).
Aceptación: filas de `marketplace_searches` con `vehicle_year` y `municipality_code`.

**T9 — Demanda no atendida y demanda por vehículo** · Sonnet · `backend`
Archivos: `core/analytics/UnmetDemand.java`, `VehicleDemand.java`, endpoints de §3.3.
Aceptación: un almacén solo ve demanda de los municipios de sus sucursales y de sus categorías, sin datos personales; agregados con mínimo 5 búsquedas por fila (k-anonimato).

**T10 — Pantallas de demanda** · Sonnet · `frontend`
Portal: pestaña «Demanda» en Inteligencia (búsquedas sin resultado, vehículos más buscados, acción «Crear producto» prellenado). Admin: mapa o tabla por ciudad y ranking de vehículos con conversión.
Aceptación: lint y build en verde.

**T11 — Modelos de ML** · Opus · `backend`
Archivos: `intelligence/demandforecast/*`, `analytics_model_runs` (BD por la sesión orquestadora).
Contexto: probabilidad de recompra a 30 días (regresión logística con RFM y categorías) y pronóstico por producto; entrenamiento semanal; activación solo si mejora la métrica frente a la fase 1; la API de `/analytics/forecast` y `/customers` añade `method` y `confidence` sin romper el contrato.
Aceptación: pruebas de entrenamiento con datos sintéticos en tests, conmutación correcta, sin cambio de contrato para el frontend.

**T12 — Publicación fase 3** · Haiku · sesión orquestadora
Igual que T7, tag `v1.4.0`.

## 6. Riesgos

- **Rendimiento del precio por audiencia**: calcular segmentos en cada listado del marketplace; mitigado con `SegmentMembership` en caché y solo para compradores autenticados.
- **Pocos datos**: medición, asociaciones y ML caen a estados `INSUFFICIENT_DATA` o a métodos de respaldo; las pantallas lo explican.
- **Privacidad**: exposiciones y búsquedas sin datos personales del comprador hacia el almacén; correos de marketing solo con `MARKETING_EMAIL` vigente.
- **Clientes frecuentes de admin en memoria** (fase 1): pasar a SQL paginado antes de la fase 3 si crecen las ventas.
