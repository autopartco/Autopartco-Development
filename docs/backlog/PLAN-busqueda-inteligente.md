# Plan: búsqueda de repuestos precisa y barata

Fecha: 2026-11-15. Estado: propuesto (v2). Secuencia: `database` → `backend` → `frontend` → `qa-seguridad`.
SQL nuevo: se aplica en Supabase `autopartco-dev`. Sin servicios nuevos ni llamadas externas por búsqueda.

## 1. Objetivo y alcance
Que cualquier consulta (producto, vehículo, referencia, fabricante, con o sin errores de ortografía) devuelva primero lo que contiene **todo lo que pidió el usuario**, con un costo por búsqueda **igual o menor** al actual.
Entra: índice, parser, SQL, corrector, registro de clics, aviso en resultados. No entra: embeddings ni LLM en la ruta de la búsqueda, motor externo (Elastic/Meili/Typesense), ranking aprendido.

## 2. Problemas generales (no de un ejemplo)
Código: `core/search/` (`QueryParser`, `SearchSql`, `SearchService.organic`).
1. **Al no encontrar nada, la búsqueda se relaja de golpe.** Pasa de exigir *todas* las palabras a aceptar *una cualquiera*, y mantiene la pista del vehículo. Cualquier consulta de 3 o más palabras sin coincidencia exacta termina en resultados que comparten una sola palabra.
2. **El orden no prioriza cuánto de la consulta cubre cada producto.** Las pistas (vehículo ×2, modelo ×1,5, año ×1,2) multiplican el puntaje. Por eso un producto que cubre la mitad de las palabras y menciona la marca supera a uno que las cubre todas.
3. **Los errores ortográficos solo se corrigen en marcas y modelos.** Para el resto de palabras (repuesto, fabricante, posición) solo queda el respaldo por trigramas, que se ejecuta al final y casi nunca llega a correr (el punto 1 ya encontró algo).
4. **El vehículo se reconoce de forma frágil.** Fallan los modelos numéricos tras una marca corregida («masda 3»), los nombres pegados («mazda3», «cx5») y los rangos o años cortos («2010-2015», «08»).

## 3. Costo actual por búsqueda (a reducir)
- Hasta **3 consultas** en cascada (estricta → cualquier palabra → trigramas) y, además, las facetas (`grouping sets`) y los anuncios.
- Por fila candidata se evalúan varios `document @@ to_tsquery(...)` en `CASE` (pistas de marca y modelo, una cobertura por palabra) y `word_similarity` en el respaldo. Son costos lineales en el número de filas candidatas, sin tope.
- La caché (`SearchResultCache`) evita repetir la misma consulta, pero no abarata los fallos de caché.

**Presupuesto objetivo:**
- **1 consulta** principal por búsqueda, más las facetas, que solo se calculan en la página 1 y se cachean.
- Candidatos acotados por índice (máximo 500 filas puntuadas).
- Cero comparaciones de texto por fila para las pistas.
- Corrección ortográfica en memoria: menos de 1 ms y sin ida a la BD.

## 4. Diseño
**Mover el trabajo de la consulta al indexado:**
- `search_listings.mentioned_make_ids uuid[]` y `mentioned_model_ids uuid[]`, extraídos al indexar del nombre y la descripción con el diccionario de vehículos. Las pistas pasan de `to_tsquery` por fila a un cruce de arreglos (`&&`, índice GIN).
- Pesos del documento: nombre y referencia en A, categoría y fabricante en B, vehículos en C, descripción en D. La categoría entra al texto con su nombre visible, así «bomba de agua» o «pastillas de freno» coinciden por categoría aunque el vendedor escriba un nombre pobre.

**Corrector ortográfico en memoria (SymSpell):**
- Léxico construido automáticamente (sin lista manual): palabras de `search_text`, nombres de categorías, fabricantes, marcas, modelos y sinónimos, con su frecuencia.
- Se recarga junto a `SearchDictionary` (mismo ciclo de vida). Tamaño estimado: decenas de miles de palabras, pocos MB.
- Corrige solo palabras de 4 letras o más que no estén en el léxico. Distancia máxima: 1 hasta 5 letras, 2 desde 6. Desempata por frecuencia.
- No toca referencias ni números. Sustituye el respaldo por trigramas, que se elimina.

**Consulta única con cobertura como criterio principal:**
1. Candidatos: `document @@ (g1 | g2 | … )` con los términos ya corregidos (índice GIN existente), `order by ts_rank_cd desc limit 500`.
2. Sobre esos ≤500: `cobertura = palabras cubiertas / palabras con contenido`, calculada con una sola expresión. Se filtra `cobertura ≥ 0,7`, o `= 1` cuando hay 2 palabras o menos.
3. Orden lexicográfico, sin multiplicaciones que mezclen niveles:
   1. Cobertura, en tramos (1 / ≥0,85 / ≥0,7).
   2. Nivel de vehículo: compatible por datos > lo menciona > universal > sin datos > menciona otra marca (cuando no menciona la buscada).
   3. Categoría aprendida (§4, clics).
   4. `ts_rank_cd`.
   5. Stock, calificación y distancia.
4. El vehículo interpretado **ordena, no filtra**. Si ninguno del primer tramo es compatible, la respuesta trae `relaxed: "VEHICLE"`.

**Aprendizaje barato de los clics (por lotes, nocturno):**
- Registrar el clic del resultado: `search_clicks(search_id, product_id, position)`, de forma asíncrona como `SearchLog`.
- El job nocturno agrega `search_query_category(normalized_query, category_id, clicks, impressions)`. En la búsqueda es una búsqueda por clave en memoria que sube la categoría con más clics para esa consulta.
- Sinónimos candidatos: una consulta con 0 resultados o sin clic, seguida en menos de 60 s por otra con clic. Se proponen en `/admin` para aprobarlos; no se aplican solos.

**Alternativas descartadas:**
- *Embeddings o búsqueda semántica:* costo por consulta (inferencia y vector) y fallan con referencias y modelos numéricos. Se reconsideran solo si las métricas de T0 se estancan.
- *Motor externo:* un servicio más y sincronización; los problemas del §2 son de lógica.
- *Diccionario manual de tipos de repuesto:* no escala. Lo reemplazan las categorías del catálogo y lo aprendido de los clics.

## 5. Contratos
- **BD:**
  - `search_listings`: columnas nuevas `mentioned_make_ids uuid[]` y `mentioned_model_ids uuid[]`, con sus índices GIN.
  - `search_clicks(id bigserial, search_log_id, product_id uuid, position int, created_at)`.
  - `search_query_category(normalized_query text, category_id uuid, clicks int, impressions int, pk(normalized_query, category_id))`.
  - `search_log` debe devolver su `id` para enlazar el clic.
- **Backend:**
  - Clases nuevas:
    - `SpellCorrector.correct(String token) → Optional<String>`.
    - `QueryLearning.categoryFor(String normalizedQuery) → Optional<UUID>`, en memoria y recargado por el job.
  - Cambios:
    - `SearchSql.page`: consulta única de §4.
    - `SearchService.organic`: sin cascada.
    - `SearchInterpretation`: añade `corrections: [{from, to}]` y `relaxed: "NONE" | "VEHICLE" | "PARTIAL"`.
  - Endpoint nuevo: `POST /api/v1/search/click {searchId, productId, position}`, con límite de tasa.
- **Frontend:** enviar el clic; mostrar «Buscamos **X** (corregido de **Y**)» con opción de repetir con `interpret=false`, y el aviso de vehículo no confirmado.

## 6. Tareas
**T0 — Evaluación de calidad y costo.** Modelo: Sonnet. Agente: `backend`.
- Archivos: `src/test/resources/search/golden-queries.csv`, `SearchRelevanceTest.java`.
- Contexto: más de 100 consultas variadas. Sacarlas de `search_log` (las más frecuentes y las de 0 resultados) y completarlas con variantes con errores, vehículos y referencias. Cada una lleva los `product_id` esperados en el top 5, sobre un catálogo fijo de prueba.
- Aceptación: imprime precisión@5, MRR, % sin resultados y **número de consultas SQL y tiempo por búsqueda**. Falla si una tarea posterior empeora cualquiera de ellas.

**T1 — Esquema.** Modelo: Haiku. Agente: `database`.
- Aplicar en Supabase el §5 BD.
- Aceptación: columnas e índices creados; `explain` de `mentioned_make_ids && array[...]` usa el GIN.

**T2 — Indexador.** Modelo: Sonnet. Agente: `backend`. Archivos: `SearchIndexer.java`, `SearchIndexJobs.java`.
- Llena `mentioned_*` y los pesos de §4.
- Aceptación: un producto que nombra «Mazda 3» en la descripción queda con la marca y el modelo; la reindexación completa no tarda más de 1,3× lo actual.

**T3 — Corrector y parser de vehículo.** Modelo: Opus. Agente: `backend`. Archivos: `SpellCorrector.java` (nuevo), `QueryParser.java`, `SearchDictionary.java`, tests.
- Aceptación en tests: corrige errores de 1 y 2 ediciones en palabras del léxico y no toca palabras válidas, referencias ni números.
- Reconoce marca+modelo pegados, modelos numéricos tras una marca corregida y años en rango o cortos.
- Corrección de menos de 1 ms por consulta (microbenchmark) y heap del léxico de menos de 20 MB con el catálogo actual.

**T4 — Consulta única.** Modelo: Opus. Agente: `backend`. Archivos: `SearchSql.java`, `SearchService.java`, `SearchCriteria.java`, `SearchInterpretation.java`, tests.
- Aceptación: T0 mejora en calidad; 1 consulta SQL principal por búsqueda; p95 menor o igual al actual; las facetas solo se calculan en la página 1.

**T5 — Clics y aprendizaje.** Modelo: Sonnet. Agente: `backend`. Archivos: `SearchController.java`, `core/analytics/`, job nocturno, `QueryLearning.java`.
- Aceptación: el clic se registra sin bloquear la respuesta; el job llena `search_query_category`; una consulta con categoría aprendida la sube en el orden; endpoint con límite de tasa.

**T6 — Interfaz.** Modelo: Sonnet. Agente: `frontend`.
- Contexto: contrato §5 y `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md`.
- Aceptación: envía el clic; muestra las correcciones con «buscar exactamente» y el aviso de vehículo no confirmado.

**T7 — QA.** Modelo: Sonnet. Agente: `qa-seguridad`. Solo sobre el diff: inyección en `tsquery`, abuso del endpoint de clics, tiempos y T0 en verde.

## 7. Riesgos
- El tope de 500 candidatos puede dejar fuera algún resultado en consultas muy genéricas. Se mide en T0 y se ajusta el tope.
- Una corrección equivocada cambia la intención: se muestra siempre y se puede deshacer.
- Los clics sesgan hacia lo que ya se muestra arriba: el aprendizaje se usa como desempate, no como criterio principal.
- Cobertura ≥ 0,7 en consultas largas con palabras de relleno («original», «bueno», «barato»): ampliar `STOPWORDS` con datos de `search_log`.
