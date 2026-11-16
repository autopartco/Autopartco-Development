# Plan: modelos predictivos en Autopartco

> **Fase 1 implementada (2026-11-15).** Hay tres modelos entrenados en `ml/` (ver `ml/README.md`) y se ejecutan en `core.ml`. Cambios frente a este plan:
> - **Inferencia en Java puro**, sin ONNX Runtime. Los árboles se exportan a JSON y la paridad con la librería es exacta. Así no hay memoria nativa en el VPS ni dependencias nuevas en `pom.xml`.
> - **Se reutiliza el esquema `ml` existente** (`models`, `model_versions`, `batch_runs`, `predictions`) y `payment_risk_assessments`. No hay tablas `ml_*` nuevas.
> - **Modelos:** `demand_category_28d` (LightGBM Poisson, por sucursal × categoría, porque las ventas por SKU son muy escasas), `stockout_14d` (Random Forest calibrado) y `payment_anomaly` (Isolation Forest, nuevo).
> - **Endpoints:** `GET /stores/{storeId}/analytics/demand-forecast` y `GET /stores/{storeId}/analytics/stockout-risk`.
> - **Pendiente:** el resto del plan (fitment, mantenimiento, precio) y la interfaz.

Fecha: 2026-11-13. Estado: propuesto. Secuencia: `database` → `ml` → `backend` → `frontend` → `qa-seguridad`.
SQL nuevo: se aplica en Supabase `autopartco-dev` (no se escribe en el repo). UI: `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md`.

## 1. Objetivo y alcance
Entrenar 6 modelos en Python con los datos de la BD y ejecutarlos **dentro del backend Java**, sin servicio Python en producción.

| # | Clave | Modelo | Algoritmo (para la expo) | Librería | Artefacto |
|---|---|---|---|---|---|
| 1 | `unmet_demand` | Demanda no atendida por categoría × vehículo × municipio | **Gradient Boosting (LightGBM, regresión Poisson)** | lightgbm | ONNX |
| 2 | `replenishment` | Reposición por producto y sucursal | **TSB / Croston-SBA** elegido por **clasificación Syntetos-Boylan** (ADI, CV²) | statsforecast | JSON |
| 3 | `stock_reliability` | Probabilidad de que una venta falle por stock inexistente | **Random Forest** calibrado (isotónica) | scikit-learn | ONNX |
| 4 | `fitment` | Compatibilidad producto ↔ modelo de vehículo | **XGBoost** (clasificador binario sobre pares) | xgboost | ONNX |
| 5 | `maintenance` | Tiempo hasta la próxima compra de un consumible | **Análisis de supervivencia Weibull AFT** | lifelines | JSON |
| 6 | `pricing` | Precio sugerido | **Regresión logística** de conversión + **elasticidad log-log (Ridge)** | scikit-learn | JSON |

Entra: proyecto `ml/` (extracción, entrenamiento, evaluación, exportación), vistas de features en Supabase, inferencia en Java (ONNX Runtime), jobs nocturnos de puntuación, endpoints, **cada modelo integrado en la pantalla del portal o del marketplace donde se toma esa decisión** (§2) y, además, una página «Modelos predictivos» en `/admin` con algoritmo, versión y métricas.
No entra: reentrenamiento automático en producción, servicio Python, ranking de búsqueda aprendido, abandono de almacenes, fraude.

## 2. Componentes
- **BD (Supabase):** tablas nuevas `customer_vehicles` (mi garaje), `ml_unmet_demand`, `ml_replenishment`, `ml_replenishment_transfers`, `ml_stock_reliability`, `ml_fitment_suggestions`, `ml_maintenance_due`, `ml_price_market` (agregados de mercado por referencia, k ≥ 5); vistas `ml_train_<clave>` (features + objetivo, para Python) y vistas **materializadas** `ml_features_<clave>` (features actuales, para Java). Las dos de un modelo comparten las expresiones de features.
- **`ml/` (Python 3.12, no se despliega):** `autopartco_ml/{db,features,train,export,parity}.py`, un módulo por modelo, CLI `python -m autopartco_ml train <clave>`. Lee de Supabase con `ML_DATABASE_URL` (usuario de solo lectura) y escribe artefactos en `backend/src/main/resources/ml/<clave>/`.
- **Backend `core/ml`:** `ModelRegistry` (carga los artefactos al arrancar), `OnnxModel` y `ParamsModel`, `ModelMetadata`, `MlScoringJob` (`@Scheduled` 02:30 America/Bogota) que llama a un `Scorer` por modelo y reemplaza las filas de `ml_*`. Endpoints de solo lectura sobre esas tablas; el precio sugerido se calcula al pedirlo con coeficientes en memoria y los agregados de `ml_price_market`.
- **Frontend:** los modelos viven dentro de los flujos que ya existen, no en una sección aparte. Cada resultado lleva un enlace discreto «¿Cómo se calcula?» con el algoritmo y la versión. Además, `/admin/modelos` reúne los seis modelos con sus métricas.

| Modelo | Dónde aparece | Qué ve y hace el usuario |
|---|---|---|
| 1 Demanda no atendida | Portal › Inteligencia (nueva pestaña «Demanda») y tarjeta en Portal › Resumen | «En Neiva buscan pastillas para Spark GT y nadie las tiene: lleva 12»; botón para crear el producto o ajustar el stock |
| 2 Reposición | Portal › Inventario (columna «Sugerido» y botón «Descargar lista de compra» (CSV; el módulo de compras sigue fuera de alcance)) e Inventario › Transferencias (transferencias sugeridas) | Acepta, edita o descarta cada sugerencia; las transferencias se crean con el flujo existente |
| 3 Confiabilidad del stock | Portal › Alertas (`STOCK_UNRELIABLE`), insignia en Inventario y, sin mostrarse, en el orden de la búsqueda del marketplace | «Revisa el stock de este producto: es probable que no esté»; al ajustar el inventario el aviso se resuelve |
| 4 Compatibilidad | Portal › Productos › crear/editar, sección de vehículos | Chips «Sugeridos» con su probabilidad; aceptar los agrega a la compatibilidad del producto |
| 5 Mantenimiento | Comprador: `/cuenta/vehiculos` (Mi garaje), inicio del marketplace («Tu Spark necesita filtro de aceite pronto»), notificación y correo | Un clic lleva a la búsqueda filtrada por su vehículo y la categoría |
| 6 Precio sugerido | Portal › Productos › detalle/edición, junto al precio | Rango sugerido y conversión esperada; «Usar precio sugerido» llena el campo |

## 3. Contratos

**Artefactos** (`backend/src/main/resources/ml/<clave>/`):
- `metadata.json`: `{key, name, algorithm, library, version, trainedAt, trainingRows, target, features:[string], metrics:{string:number}, params?:{}}`. `features` fija el orden de columnas.
- ONNX: entrada `features` float32 `[N, F]`; regresor → salida `[N,1]`; clasificador exportado con `zipmap=False` → probabilidades `[N,2]` (se usa la columna 1). Opset 17. Versión de `onnxruntime` igual en Python y Java.
- JSON (`params.json`): TSB `{alphaDemand, alphaProbability, sbaAlpha, classCutoffs:{adi:1.32, cv2:0.49}, serviceLevelZ}`; Weibull AFT `{lambda:{intercept, coef:{feature:number}}, rho:{intercept}}`; precio `{conversion:{intercept, coef:{feature:number}}, elasticity:{byCategory:{categoryId:number}, global:number}, priceGrid:{minRatio:0.8, maxRatio:1.2, steps:21}}`.
- Paridad: `backend/src/test/resources/ml/<clave>/parity.json` = `{rows:[[number]], expected:[number]}` (50 filas generadas por Python).

**Endpoints** (todos con `modelVersion` en la respuesta; almacén con permiso de lectura de inteligencia; IDs ajenos → 404):
- `GET /stores/{storeId}/intelligence/unmet-demand?municipality=&page=&size=` → `Page<{categoryId, categoryName, makeName, modelName, municipalityCode, municipalityName, expectedSearches4w, storesWithStock, gapScore, suggestedUnits}>`.
- `GET /stores/{storeId}/intelligence/replenishment?branchId=` → `{orders:[{productId, productName, branchId, stock, weeklyForecast, demandClass:'SMOOTH'|'INTERMITTENT'|'ERRATIC'|'LUMPY', method:'TSB'|'SBA'|'SES', safetyStock, suggestedQty}], transfers:[{productId, fromBranchId, toBranchId, quantity}]}`.
- `GET /stores/{storeId}/intelligence/stock-reliability` → `[{inventoryId, productId, branchId, failureProbability, level:'HIGH'|'MEDIUM'|'LOW', topFactors:[string]}]`. Nueva alerta `STOCK_UNRELIABLE` (probabilidad ≥ 0,5). El ranking del marketplace multiplica la relevancia por `1 − 0,5·failureProbability`.
- `GET /stores/{storeId}/products/{productId}/fitment-suggestions` → `[{makeId, modelId, makeName, modelName, yearFrom, yearTo, probability}]` (≥ 0,6, máx. 20). Aceptar = el frontend agrega la regla a `vehicleRules` y guarda con la actualización de producto existente (`ProductUpdateRequest`); rechazar: `POST .../fitment-suggestions/{id}/dismiss` → 204. Nunca se aplican solas.
- `GET/POST /me/vehicles`, `DELETE /me/vehicles/{id}` (`{makeId, modelId, versionId?, year, nickname?}`, máx. 5) · `GET /me/maintenance` → `[{vehicleId, categoryId, categoryName, lastPurchaseAt, dueAt, probabilityWithin30d}]`. Recordatorio por notificación y correo si `preferences.notifications.email.reminders` (nueva, por defecto `true`).
- `GET /stores/{storeId}/products/{productId}/price-suggestion` → `{currentPrice, suggestedPrice, rangeLow, rangeHigh, conversionAtCurrent, conversionAtSuggested, elasticity, marketSampleSize}`. Sin datos de mercado si hay menos de 5 almacenes con la referencia; nunca devuelve precios de otros almacenes.
- `GET /admin/ml/models` (ROOT/ADMIN) → `[ModelMetadata + {loaded:boolean, lastScoredAt, scoredRows}]`.

**Selección de cada modelo** (lo que la expo puede mostrar): partición temporal (últimas 8 semanas = prueba), línea base ingenua y métrica:
1. Poisson deviance y MAE vs. media móvil de 4 semanas. 2. MASE y RMSSE vs. Croston simple. 3. AUC-ROC, Brier vs. tasa histórica del almacén. 4. AUC-PR, precisión@0,6 vs. «misma categoría y fabricante». 5. Índice de concordancia (C-index) vs. mediana por categoría. 6. Log-loss y AUC de conversión vs. tasa media por categoría.

## 4. Decisiones
- **ONNX Runtime embebido en Java** (`com.microsoft.onnxruntime:onnxruntime`). Descartados: microservicio Python (requisito), PMML con jpmml (licencia AGPL), reescribir árboles en Java (frágil), entrenar en Java con Smile/Tribuo (el entrenamiento es en Python).
- **Modelos lineales y estadísticos como parámetros JSON** (TSB, Weibull, regresión logística y elasticidad de precio): su inferencia es una fórmula de pocas líneas en Java; ONNX no aporta nada y no se carga para ellos.
- **Features en vistas SQL compartidas**: Python y Java leen las mismas expresiones; evita el principal riesgo (features distintas al entrenar y al predecir). Descartado: calcular features dos veces (pandas y Java).
- **Puntuación nocturna en lote** y tablas `ml_*`: endpoints rápidos, sin carga en las peticiones. Excepción: precio (21 evaluaciones de una regresión logística en memoria, microsegundos).
- **Artefactos versionados en el repo** dentro de `backend/`: viajan en el JAR y `main` ya los incluye; reentrenar = correr el script, commit, desplegar. Límite 20 MB en total.
- **Agente nuevo `ml`** con área `ml/`, `backend/src/main/resources/ml/` y `backend/src/test/resources/ml/` (requiere tu autorización: cambia `.claude/` y `.opencode/`). Alternativa: que esas tareas las ejecute la sesión principal.

## 5. Rendimiento
Regla: **ningún modelo se ejecuta en una petición del usuario**, salvo el precio (fórmula en memoria). Las pantallas leen resultados ya calculados.

| Punto | Medida | Presupuesto |
|---|---|---|
| Endpoints de §3 | Leen tablas `ml_*` por índice (`store_id`, `branch_id`, `product_id`), paginados (máx. 50) y con caché de 10 min (`AppCaches`, se invalida al terminar el job) | p95 < 150 ms |
| Búsqueda del marketplace | La confiabilidad entra en `ListingCache` como un número precalculado por inventario; la búsqueda no consulta `ml_*` ni carga modelos | + < 5 ms en p95 |
| Precio sugerido | Coeficientes en memoria + una fila de `ml_price_market`; caché 1 h por producto | p95 < 50 ms |
| Features | Vistas materializadas `ml_features_<clave>`, `REFRESH … CONCURRENTLY` dentro del job; solo ventanas de 52 semanas; índices en las tablas origen (`marketplace_searches(occurred_at, municipality_code, model_id)`, `inventory_movements(inventory_id, occurred_at)`) | refresco < 60 s por vista |
| Job nocturno | 02:30 America/Bogota, un modelo tras otro (no en paralelo), en un `ThreadPoolTaskExecutor` propio de 1 hilo; lectura por lotes de 5 000 filas (`fetchSize`) e inferencia vectorizada `[5 000, F]`; escritura con inserción por lotes en tabla temporal e intercambio en una transacción corta (las pantallas nunca ven datos a medias); `statement_timeout` 120 s; si un modelo falla o excede 10 min se conservan sus predicciones anteriores | total < 15 min |
| Varias instancias | `pg_try_advisory_lock` por modelo: solo una instancia puntúa | — |
| ONNX Runtime | Un `OrtEnvironment` y una `OrtSession` por modelo, creadas una vez y compartidas (son seguras entre hilos); `intraOpNumThreads = 1` para no quitarle CPU a las peticiones web; carga en segundo plano al arrancar (el backend arranca aunque falte un modelo) | +150 MB de memoria como máximo |
| Tamaño de modelos | Límites al entrenar: ≤ 300 árboles, profundidad ≤ 8 (LightGBM, XGBoost, Random Forest); artefactos ≤ 5 MB cada uno | inferencia < 50 ms por lote de 1 000 filas |
| Correos de mantenimiento | Por lotes de 100 con la cola de correo existente; máx. 1 recordatorio por vehículo y categoría cada 14 días | — |
| Frontend | Datos de los modelos con carga diferida y esqueleto; nunca bloquean el resto de la pantalla; metadatos de «¿Cómo se calcula?» pedidos una vez y en caché | — |
| Observabilidad | Métricas de Actuator `ml.scoring.duration`, `ml.inference.duration`, `ml.rows.scored` por modelo | — |

## 6. Datos que debes poblar
| Modelo | Tablas y campos imprescindibles | Volumen mínimo útil |
|---|---|---|
| 1 | `marketplace_searches` con `make_id`, `model_id`, `municipality_code`, `category_id`, `results_count`, `occurred_at`; `inventory` con stock por sucursal; `branches` con municipio | 26 semanas, ≥ 20 municipios, búsquedas con 0 resultados |
| 2 | `inventory_movements` (ventas, ajustes, transferencias) por producto y sucursal; plazo de entrega del proveedor (si no existe: 1 semana fijo) | 52 semanas, mezcla de productos de alta y baja rotación |
| 3 | Ventas con `cancellation_reason_code` que distinga **sin stock** (p. ej. `OUT_OF_STOCK`); ajustes de inventario con fecha | ≥ 5 % de ventas canceladas por stock |
| 4 | `compatibilities` / `product_vehicle_rules` existentes, productos con nombre, fabricante y referencia OEM; búsquedas con vehículo que terminaron en compra | ≥ 2 000 pares producto-vehículo |
| 5 | `customer_vehicles` (nueva) y pedidos entregados de categorías consumibles (filtros, pastillas, aceite, bujías) de esas cuentas | ≥ 300 compradores con 2+ compras de la misma categoría |
| 6 | Vistas de producto (`VisibilityRecorder`) y compras con el precio de ese momento; misma referencia en ≥ 5 almacenes | ≥ 50 000 vistas |

## 7. Tareas

**T0 — Autorizaciones.** Tú · instalar `onnxruntime` en `backend/pom.xml` y las dependencias Python de `ml/`; crear el agente `ml`; usuario de solo lectura en Supabase para `ML_DATABASE_URL`. Aceptación: las tres decisiones registradas en este plan.

**T1 — Esquema base.** Sonnet · `database` (Supabase) · Crear `customer_vehicles(id, account_id, make_id, model_id, version_id, year, nickname, created_at, deleted_at)` con máx. 5 por cuenta, tablas `ml_*` de §2 (cada una con `model_version`, `scored_at`) y el código de cancelación `OUT_OF_STOCK` si no existe. Aceptación: `list_tables` muestra las tablas; índices por `store_id` en las `ml_*` de almacén.

**T2 — Vistas de features.** Opus · `database` · Para cada clave, `ml_train_<clave>` (features + objetivo, con fecha de corte) y `ml_features_<clave>` (vista materializada con índice único para `REFRESH CONCURRENTLY`, features a hoy) con las mismas expresiones (CTE común); índices de §5 en las tablas origen; `ml_price_market` con k ≥ 5. Contexto: §3, §5 y §6. Aceptación: para una fecha de corte, las filas de `ml_features_<clave>` coinciden con `ml_train_<clave>` sin el objetivo; cada refresco < 60 s con los datos poblados (`EXPLAIN ANALYZE` sin escaneos secuenciales de las tablas grandes).

**T3 — Proyecto `ml/`.** Sonnet · `ml` · `ml/pyproject.toml` (python 3.12, pandas, scikit-learn, lightgbm, xgboost, statsforecast, lifelines, skl2onnx, onnxmltools, onnxruntime, psycopg), `autopartco_ml/{db,export,parity,cli}.py`. `export` escribe `metadata.json` y el modelo; `parity` escribe `parity.json` y verifica con onnxruntime. Aceptación: `python -m autopartco_ml --help` lista `train <clave>`; un modelo de prueba se exporta y su paridad pasa.

**T4–T9 — Entrenar cada modelo.** Sonnet (T6, T7, T9: Opus para selección y validación) · `ml` · `ml/autopartco_ml/models/<clave>.py`. Leer `ml_train_<clave>`, partición temporal, línea base de §3, búsqueda de hiperparámetros dentro de los límites de tamaño de §5, exportar artefactos y `parity.json`. Aceptación por modelo: supera la línea base en su métrica, `metadata.json` completo, paridad < 1e-4, artefacto ≤ 5 MB.
- T4 `unmet_demand` (LightGBM Poisson). T5 `replenishment` (clasificación SBC + TSB/SBA, parámetros por clase). T6 `stock_reliability` (Random Forest + calibración isotónica). T7 `fitment` (XGBoost, candidatos: mismos fabricante y categoría, vehículos buscados con compra). T8 `maintenance` (Weibull AFT con censura). T9 `pricing` (regresión logística de conversión + Ridge log-log).

**T10 — Infraestructura de inferencia.** Sonnet · `backend` · `core/ml/*`, `pom.xml`. `ModelRegistry` (carga en segundo plano), `OnnxModel.predict(float[][])` con sesión compartida e `intraOpNumThreads=1`, `ParamsModel`, `MlScoringJob` con ejecutor de 1 hilo, `pg_try_advisory_lock`, lotes de 5 000, intercambio atómico y métricas de §5; `GET /admin/ml/models`; si falta un artefacto el modelo queda `loaded:false` y su endpoint responde con lista vacía. Aceptación: `-Dtest=ModelRegistryTest,ParityTest,MlScoringJobTest` (paridad de los 6 modelos; un fallo conserva las predicciones anteriores; un segundo job concurrente no corre); test de inferencia de 1 000 filas < 50 ms por modelo ONNX.

**T11 — Scorers y endpoints 1–3.** Sonnet · `backend` · `UnmetDemandScorer`, `ReplenishmentScorer` (TSB + stock de seguridad `z·σ·√plazo` + transferencias de sedes con cobertura > 8 semanas a sedes con déficit), `StockReliabilityScorer`, alerta `STOCK_UNRELIABLE`, penalización en el ranking vía `ListingCache` (sin consultas extra en la búsqueda); caché de endpoints de §5. Aceptación: tests de cada scorer con features fijas; IDOR → 404; la búsqueda no ejecuta consultas nuevas (test de conteo de consultas).

**T12 — Scorers y endpoints 4–6.** Sonnet · `backend` · `FitmentScorer` + dismiss, `/me/vehicles`, `MaintenanceScorer` + recordatorio (respeta preferencia), `PriceSuggestionService` (coeficientes en memoria, `ml_price_market`, k ≥ 5 almacenes, caché 1 h); correos por lotes con tope de §5. Aceptación: tests de propiedad, máximo de 5 vehículos, el precio nunca expone datos de un almacén concreto.

**T13 — Frontend.** Sonnet · `frontend` · integrar cada modelo en las pantallas existentes de la tabla de §2 (sin sección aparte), con el enlace «¿Cómo se calcula?»; `/admin/modelos` con la tabla de modelos y métricas. Contexto: contratos §3 (el frontend se trabaja desde otra cuenta: este plan es el contrato). Aceptación: lint y build en verde; con datos poblados, cada fila de la tabla de §2 se ve y su acción funciona (crear producto, descargar lista de compra, transferir, ajustar stock, aceptar compatibilidad, ir a la búsqueda del vehículo, usar precio sugerido).

**T14 — QA acotado.** Sonnet · `qa-seguridad` · solo el diff de T1–T13: autorización de endpoints de almacén y `/me/vehicles`, que el precio no filtre datos de competidores, tamaño de artefactos, manejo de modelo no cargado y presupuestos de §5 (p95 de endpoints y de la búsqueda con y sin modelos, duración del job, memoria). Aceptación: informe sin hallazgos altos abiertos y presupuestos cumplidos.

## 8. Riesgos
- Features distintas entre entrenamiento y predicción: mitigado con vistas compartidas y paridad; cualquier cambio de vista exige reentrenar.
- Memoria nativa de ONNX Runtime (~60–100 MB) en el contenedor `eclipse-temurin:26-jre`: está fuera del heap (`MaxRAMPercentage=75` deja ~25 %); revisar el límite de RAM del hosting antes de T10.
- Refresco de vistas y job sobre la misma BD de producción: se corre de madrugada y por lotes; si crece el volumen, mover el refresco a una réplica de lectura.
- Con datos poblados a mano, las métricas muestran que el pipeline funciona, no rendimiento real: decirlo así en la expo.
- Precio: riesgo de competencia ante la SIC; solo agregados con k ≥ 5.
- Mi garaje y recordatorios: datos personales (Ley 1581); preferencia de recordatorios y borrado del vehículo.
