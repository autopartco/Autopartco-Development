# Búsqueda inteligente: contrato para el frontend (T6)

Fecha: 2026-11-15. Rama backend: `feature/busqueda-evaluacion`. Plan: `docs/backlog/PLAN-busqueda-inteligente.md`.
Agente: `frontend`. Modelo recomendado: Sonnet. UI según `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md` (§17 y §20.15).

Todos los campos nuevos son **adiciones**: los existentes no cambian. Un frontend sin T6 sigue funcionando.

## 1. `GET /api/v1/marketplace/search` — campos nuevos

```ts
// en SearchResponseDto (frontend/src/features/marketplace/api/dto.ts)
searchId?: string            // UUID; nuevo en cada respuesta (nunca cacheado). Omitido si el servidor no registró la búsqueda.
interpreted: {
  // …campos actuales…
  corrections: { from: string; to: string }[]   // siempre presente ([] si no hubo); palabras normalizadas (minúsculas, sin tildes)
  relaxed: 'NONE' | 'VEHICLE'                    // siempre presente
} | null
fuzzy: boolean               // ya existía; ahora solo es true cuando no hubo coincidencias y se respondió por similitud
```

Semántica:
- `corrections`: palabras de la consulta reemplazadas por el corrector, en orden (p. ej. `{from: "radidor", to: "radiador"}`). `interpreted.text` ya va corregido.
- `relaxed = "VEHICLE"`: se reconoció un vehículo (`makeName`/`modelName`), pero ningún resultado está confirmado para él. Lo que se muestra es universal o no tiene datos de compatibilidad.
- `fuzzy = true`: la búsqueda no encontró coincidencias y estos resultados son aproximados.
- `interpreted` ahora puede venir con solo `corrections` o `relaxed`, sin vehículo, año, ciudad ni referencia. `InterpretedChips` ya oculta el bloque si no hay chips.

## 2. `POST /api/v1/marketplace/search/click` (nuevo)

- Público (comprador anónimo incluido). `Content-Type: application/json`.
- Cuerpo: `{"searchId": "<uuid>", "productId": "<uuid>", "position": <int ≥ 0>}`. Los tres son obligatorios.
- `position`: índice desde 0 del producto en la lista orgánica mostrada, contando la paginación: `page * size + índice`.
- Respuestas:
  - `204` sin cuerpo si el cuerpo es válido. También cuando la búsqueda o el producto no existen y cuando el clic es repetido: se descartan sin error.
  - `400` si un campo falta o no es válido.
  - `429` con `error.code = "SEARCH_CLICK_RATE_LIMITED"`: más de 120 clics por minuto por IP.
- Solo para resultados **orgánicos** (`items`), no para `sponsored`, que ya tienen su propio seguimiento.
- Sin datos personales. No hace falta la sesión.

## 3. Comportamiento esperado en la UI

1. **Enviar el clic** al abrir un producto desde los resultados orgánicos de `/buscar`, solo si la respuesta trajo `searchId`.
   - El envío no bloquea la navegación (`navigator.sendBeacon` con un `Blob` JSON, o `fetch` con `keepalive: true`).
   - Los errores se ignoran: nunca se muestra nada al usuario por un clic fallido.
2. **Correcciones**, si `corrections` no está vacío. Una línea discreta sobre los resultados:
   - Texto: «Mostrando resultados para **<text>**».
   - Enlace: «Buscar exactamente "<q original>"». Repite la búsqueda con el `q` original e `interpret=false`, con el mismo mecanismo que ya usa quitar un chip.
3. **Vehículo no confirmado**, si `relaxed === "VEHICLE"`. Aviso informativo (no de error):
   - Texto: «No encontramos repuestos confirmados para **<makeName> <modelName>**. Estos son universales o sin compatibilidad registrada; verifica antes de comprar».
4. **Resultados aproximados**, si `fuzzy === true`. Aviso informativo:
   - Texto: «No encontramos coincidencias exactas para "<q>". Estos resultados son parecidos».
   - Si `fuzzy` es `true`, no se muestra además el aviso de correcciones.
5. Los tres avisos usan tokens y componentes del sistema de diseño, con `role="status"`, legibles en móvil (375 px) y en modo oscuro. Los textos van en español de Colombia.

## 4. Criterios de aceptación

- `dto.ts` y el mapeo de `http-service.ts` incluyen `searchId`, `corrections` y `relaxed`. Si el servidor no los envía, `corrections` vale `[]` y `relaxed` vale `'NONE'`.
- El clic orgánico envía exactamente el cuerpo del §2, una vez por apertura y con la `position` correcta.
  - No se envía en patrocinados.
  - No se envía sin `searchId`.
  - Lo cubre una prueba del componente o del servicio.
- Hay pruebas o historias para los tres avisos del §3: con correcciones, con `relaxed=VEHICLE` y con `fuzzy`.
- «Buscar exactamente» repite la búsqueda con `interpret=false`.
- Se cumplen los criterios del sistema de diseño (§17 y §20.15): contraste, foco, móvil y modo oscuro.
- No cambia ningún otro comportamiento de `/buscar` (filtros, facetas, paginación, patrocinados).

## 5. Archivos probables

- `frontend/src/features/marketplace/api/dto.ts`
- `frontend/src/features/marketplace/api/http/http-service.ts` (mapeo y `postSearchClick`)
- `frontend/src/features/marketplace/pages/search-results-view.tsx`
- `frontend/src/features/marketplace/components/search/` (avisos)
