# Plan: planes comerciales justos y atractivos, con integraciones (Alegra, Siigo, facturación)

## Punto de partida
- Modelo listo: `seller_plans` con precio mensual COP, cupos (`max_branches`, `max_products`, `max_users`, `NULL` = sin límite) y `capabilities` (lista de claves). Hoy hay `TRY/FREE`, `BUSINESS` (solo en la semilla de desarrollo, **precios no aprobados, aprobación A1 pendiente**) y `ENTERPRISE` en `PlanService.applyDefaultEntitlements`.
- Solo se hacen cumplir `PROMOTIONS` y `CUSTOM_ROLES` (`PlanEntitlements.requireCapability`) y los tres cupos.
- **No existe aún nada de lo que los planes anuncian**: `CSV_IMPORT`, `INTEGRATIONS`, `ERP_INTEGRATIONS`, `ADVANCED_REPORTS`, `WHATSAPP_NOTIFICATIONS`, listas de precios, compras y alertas de stock están como `package-info` «No implementado».
- Principio: **un plan solo vende lo que ya funciona**. Lo que falta se muestra como «Próximamente» y no cuenta como beneficio contratado.

## Objetivo y alcance
Entra: escalera de 4 planes, catálogo de capacidades con estado, política de cambio de plan, comparador en el portal y en `/vender`, y el orden de construcción de importación CSV, conectores Alegra y Siigo y facturación.
No entra: reemplazar a Alegra/Siigo como sistema contable; facturar directo ante la DIAN (se factura **a través** del ERP o proveedor del almacén); cobro recurrente automático con Wompi (hoy hay cobro simulado; se planifica aparte).

## Escalera propuesta (COP/mes, **propuesta para aprobar**)
| Plan | Precio | Cupos (sedes / productos / usuarios) | Qué incluye |
|---|---|---|---|
| Prueba | 0 (1 mes, una vez) | 1 / 100 / 2 | Todo el flujo de venta; `CSV_IMPORT` limitado a 100 filas para probar. |
| Esencial | 49.900 | 1 / 500 / 2 | Vender en el marketplace, pedidos, inventario por sucursal, `CSV_IMPORT`, analítica básica. |
| Negocio (recomendado) | 149.900 | 3 / 5.000 / 10 | + `ERP_IMPORT` (importar productos y existencias desde Alegra **o** Siigo, a demanda), promociones, reportes avanzados, roles personalizados, notificaciones por WhatsApp. |
| Pro | 299.900 | 10 / 25.000 / 25 | + `ERP_SYNC` (sincronización automática de inventario y precios con Alegra y Siigo), `E_INVOICING` (emitir la factura electrónica de cada pedido a través del ERP conectado), listas de precios por cliente, alertas de stock y compras. |
| Empresarial | a medida | sin límite | + `API_ACCESS`, `WEBHOOKS`, dominio propio, soporte prioritario, acompañamiento de migración. |

Por qué es justo: el límite es por **uso** (sedes, productos, usuarios), no por bloquear lo básico de vender; los almacenes pequeños no pagan por lo que no usan; el salto de Esencial a Negocio coincide con tener varias sedes o un ERP; descuento anual de 2 meses (pagar 10, usar 12); sin permanencia.
Por qué es atractivo: Negocio ya resuelve el dolor principal (no digitar el catálogo a mano); Pro cierra el ciclo (inventario siempre al día y factura emitida sin salir de Autopartco).

## Capacidades (claves) y estado
| Clave | Plan mínimo | Estado hoy |
|---|---|---|
| `BASIC_ANALYTICS` | Prueba | parcial (panel Ventas) |
| `CSV_IMPORT` | Esencial | no implementado |
| `PROMOTIONS`, `CUSTOM_ROLES` | Negocio | implementado |
| `ADVANCED_REPORTS`, `WHATSAPP_NOTIFICATIONS` | Negocio | no implementado |
| `ERP_IMPORT` | Negocio | no implementado (nueva clave; `INTEGRATIONS` queda como alias retirado) |
| `ERP_SYNC`, `E_INVOICING`, `PRICE_LISTS`, `PURCHASING`, `STOCK_ALERTS` | Pro | no implementado |
| `API_ACCESS`, `WEBHOOKS`, `CUSTOM_DOMAIN` | Empresarial | no implementado |
Estado de cada clave: `AVAILABLE` o `COMING_SOON`; el backend lo expone y el frontend lo pinta. Un plan puede **listar** una clave `COMING_SOON` pero no se cobra como beneficio ni se activa hasta pasar a `AVAILABLE`.

## Contratos
- `PlanResponse` añade: `capabilityDetails[{key,label,status}]`, `annualPriceCop` (derivado: 10 × mensual) y `limits{branches,products,users}` (`null` = ilimitado). Mantener los campos actuales (compatibilidad con el portal).
- Catálogo de capacidades como `enum PlanCapability` (clave, etiqueta en español, estado, plan mínimo) en `core.billing`; `requireCapability` acepta el enum.
- Cambio de plan: **subir** aplica de inmediato con prorrateo diario; **bajar** aplica al terminar el período pagado; si el uso supera los nuevos cupos, la tienda **no pierde datos**: queda en solo lectura para crear más (no se borra ni se oculta nada) hasta ajustarse. Aviso con 7 días de antelación.
- Conectores (épica aparte): puerto `ErpConnector` (`importProducts`, `syncStock`, `emitInvoice`) con adaptadores `AlegraConnector` y `SiigoConnector` detrás de `storesuite.integrations`; credenciales cifradas en reposo, mapeo de sedes↔bodegas, identificador externo por producto, autoridad por campo, sincronización idempotente y log por ejecución.

## Decisiones y alternativas descartadas
- Límites por uso, no por «bloqueo de funciones básicas»: descartado un plan gratis eterno con comisión alta (castiga al que vende más).
- Un solo ERP incluido en Negocio, ambos en Pro: descartado dar los dos en Negocio (el costo de soporte sube sin beneficio claro).
- Facturar vía el ERP del almacén y no integrarse directo con la DIAN: menor riesgo regulatorio y reutiliza lo que el almacén ya paga.
- Capacidades con estado explícito: descartado listar beneficios aún no construidos como si estuvieran activos.

## Tareas (secuencia database → backend → frontend → qa-seguridad)

### T1 — Opus — agente `arquitecto`
Archivo: `docs/adr/0004-planes-capacidades-y-cambio-de-plan.md`. Contexto: este plan. Aceptación: ADR con el catálogo de capacidades, la política de cambio de plan y la lista cerrada de decisiones del usuario (ver abajo). **Bloquea T2–T6.**

### T2 — Sonnet — agente `database`
Archivos: `database/migrations/V…__seed_plans_comerciales.sql` (+ su `U…`), `database/migrations/README.md`. Contexto: insertar `BASIC`/`PRO` y ajustar `BUSINESS`/`ENTERPRISE` de forma idempotente (`ON CONFLICT (code) DO UPDATE` solo si el plan no tiene suscripciones activas con precio distinto). **Solo con precios aprobados (A1).** Aceptación: aplicar y revertir en BD de prueba (con autorización del usuario); `seller_plans` con el `CHECK code <> 'TRY' OR price = 0` intacto.

### T3 — Sonnet — agente `backend`
Archivos: `core/billing/PlanCapability.java` (nuevo), `PlanEntitlements.java`, `PlanService.java`, `PlanResponse.java`, `PlanRequest.java`, `storesuite/subscription/PlanCatalogController.java`, pruebas. Aceptación (`-Dtest=`): una capacidad `COMING_SOON` no se concede; sustituir las claves en texto por el enum sin cambiar el comportamiento de `PROMOTIONS`/`CUSTOM_ROLES`; bajar de plan con uso por encima del cupo devuelve solo lectura y no borra datos; `ModularityTests` y `WebSecurityConfigTest` pasan.

### T4 — Sonnet — agente `frontend`
Archivos: `features/portal/pages/portal-plan-page.tsx`, `components/plan-card.tsx`, `features/marketplace/pages/sell-page.tsx`, tipos y mock. Aceptación: comparador de planes (mensual/anual) con insignia «Próximamente», estado «Disponible en Pro» con CTA de subir de plan donde se bloquea una función, AA, móvil; `tsc -b` y lint sin avisos nuevos; probado en navegador (mock).

### T5 — Sonnet — agente `backend` + `frontend` (épica 1: importación CSV)
Primera función real que justifica Esencial. Un plan propio (`docs/architecture/…-importacion-csv-plan.md`) con previsualización, errores por fila y reintento, respetando cupos de productos. No depende de servicios externos.

### T6 — Opus → Sonnet — épicas 2 y 3 (conectores y facturación)
Un plan por épica, cada una tras T1–T5: (a) `AlegraConnector` importación; (b) `SiigoConnector` importación; (c) sincronización automática; (d) `E_INVOICING`. **Requieren tu autorización y cuentas sandbox/credenciales de Alegra y Siigo** (servicios externos) y revisión de seguridad de almacenamiento de credenciales.

### T7 — Opus — agente `qa-seguridad`
Revisar que ninguna ruta de pago ni de cambio de plan se pueda saltar desde el cliente, que `requireCapability` se aplique en el servidor en cada caso de uso gateado y que las credenciales de ERP no salgan en logs ni respuestas.

## Riesgos y puntos a verificar
- Vender lo que no existe: mitigado con `COMING_SOON`; revisar que `/vender` y el correo de aprobación no lo prometan.
- Suscripciones existentes en `BUSINESS` con precio de desarrollo: migrar sin cobrar de más ni cambiar cupos en caliente.
- Cobro: hoy es simulado; el cobro real y recurrente (Wompi) y las notas de factura de la propia suscripción son un plan aparte.
- Costos de terceros (API de Alegra/Siigo, WhatsApp, factura electrónica por documento): validar márgenes antes de fijar precios.

## Decisiones abiertas para el usuario
1. Aprobar o ajustar los precios y cupos de la tabla (aprobación A1).
2. ¿Hay plan gratis permanente después de la prueba, o la prueba termina y se elige un plan pago?
3. ¿Comisión por venta en el marketplace además de la suscripción? (hoy no existe en el modelo).
4. Descuento anual (propuesto 2 meses) y si se ofrece desde el inicio.
5. ¿Qué ERP va primero, Alegra o Siigo?
6. ¿Se muestran las funciones «Próximamente» en los planes públicos?
