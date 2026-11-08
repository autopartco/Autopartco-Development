# Rediseño visual: estado y pendientes

Norma: `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md`.

## Publicado (v1.6.3, PR #122)

- Base: tokens `ap-*` en `frontend/src/index.css`, IBM Plex Sans/Mono, radios 6/8/10 px, una sola sombra (`shadow-overlay`), componentes `ui/` y `common/` y cabecera y pie.
- Vistas: sitio público y catálogo (inicio, resultados, ficha técnica, tiendas, categorías, vender), acceso, carrito, checkout, pedidos, vendedor y beneficios.

## Hecho en `feature/rediseno-portal-admin`

- Portal: shell (navegación grafito, barra de sección y almacén), inicio por "qué atender hoy", tablas §8.5, gráficos §20 (paleta en `portal/lib/chart-palette.ts`, bloque `chart-section.tsx`), planes en tabla y formularios de producto y tienda.
- Administración: layout como el portal, sin `aurora-header` ni gradientes; resumen "qué revisar", cola de solicitudes, auditoría con actor/acción/objeto/fecha, estadísticas §20.
- Limpieza: alias de color heredados, `label-mono`, `.thread-v`, `.stitch-line`, animaciones `psi-*` y `--ease-out-thread` eliminados de `index.css`; `.surface-asphalt` pasa a `.ap-surface-dark`; código de animación sin uso retirado de `portal/lib/motion.ts`, `bento.tsx` y `list-skeleton.tsx`.

## Pendiente

1. ~~Borrar `aurora-header.tsx` y `admin.css`~~ (hecho).
2. ~~QA visual~~ de portal y administración en escritorio (1366 px) y móvil (375 px), con datos simulados (v1.6.5). Queda revisarlo con datos reales:
   - Foco y teclado; contraste de bordes de campos sobre papel.
   - Celdas del mapa de calor de ventas (20–24 px) en táctil.
   - Avisos de "pago de prueba" en modo `SIMULATED` (Wompi sandbox es producción: valorar quitarlos).
3. ~~Publicar `v1.6.4`~~ (hecho).

## v1.6.5

- Sucursal activa en el portal (`?branchId=` en dashboard y pedidos); revisor y fecha de revisión de solicitudes (`seller_applications.reviewed_at`) e historial admin por estado.

## Limitaciones de datos

- El resumen de admin no muestra incidencias (requeriría `platformStats`).
- Solicitudes revisadas antes de v1.6.5: sin fecha de revisión ("Fecha no registrada").
- Historial admin: consultas N+1 (solicitante, revisor y documentos por fila); cargar en lote si crece.

## Notas

- La API no informa el estado de compatibilidad (confirmada, parcial o pendiente) ni marca las tiendas verificadas, así que la interfaz no los distingue.
- El check de Vercel en los PR falla porque al preview le faltan `VITE_API_BASE_URL` y `VITE_MARKETPLACE_DATA_SOURCE=http`. Ya pasaba antes del rediseño.
- Los agentes frontend no pueden borrar archivos; el borrado lo hace la sesión principal con autorización.
