# Auditoría de formularios (T9): «cero escrituras hasta el envío final»

Regla: las lecturas de apoyo (búsquedas, catálogos, comprobaciones de solo lectura) pueden ir al servidor mientras se llena; las escrituras ocurren una sola vez, al pulsar el botón final. Patrón: `docs/frontend/form-draft-pattern.md`.
Este archivo está en `frontend/docs/` por el alcance del agente; moverlo a `docs/frontend/forms-audit.md`.

Veredictos: **cumple** (ya cumplía), **migrado** (se cambió en T9; "candado" = solo se añadió `useSubmitOnce`), **no aplica** (sin escritura).

| Formulario | Archivo (línea) | Escrituras previas al envío | Doble envío | Guardia de cambios | Veredicto |
|---|---|---|---|---|---|
| Login | `auth/pages/login-page.tsx:47` | Ninguna | Cerrado: `useSubmitOnce` (antes solo estado) | No necesaria (2 campos) | migrado (candado) |
| Registro de cliente | `auth/pages/customer-register-page.tsx:31,35` | Ninguna | Cerrado: `useSubmitOnce` | Añadida: `useFormTouched` + `useUnsavedChanges` | migrado |
| Recuperar contraseña (2 pasos) | `auth/pages/password-recovery-page.tsx:40-41` | Ninguna | Cerrado: un `useSubmitOnce` por paso | No necesaria | migrado (candado) |
| Registro de almacén (público y con sesión) | `seller/pages/seller-register-page.tsx:187,199` | Ninguna: los documentos se leen de `FormData` solo al enviar; un único multipart | Cerrado: `useSubmitOnce` | Añadida: `useFormTouched` + `useUnsavedChanges`; validación local del tamaño al elegir archivo | migrado |
| Configurar almacén (asistente de 3 pasos) | `seller/pages/seller-setup-page.tsx:71,78` | Ninguna: logo y banner viven como `File` en estado; un único envío | Cerrado: `useSubmitOnce` | Añadida; se apaga con `saved` antes de navegar al portal | migrado |
| Dirección de entrega (checkout) | `checkout/components/address-form.tsx:46` | Ninguna dentro del formulario (ver limitación 2) | Cerrado: `useSubmitOnce` | No necesaria (formulario inline corto) | migrado (candado) |
| Pagar pedido | `checkout/pages/checkout-page.tsx:103` | Ninguna; un `placeOrder` con `clientRequestId` idempotente | Cerrado: `useSubmitOnce` (antes `busy` por estado) | No aplica (el carrito es local y persiste) | migrado (candado) |
| Sucursal (alta y edición) | `portal/components/branch-sheet.tsx:72,79,170` | Ninguna | Cerrado | Añadida: `useDraftForm`, `useUnsavedChanges` y `useCloseGuard` (X, Esc, fondo, Cancelar) | migrado |
| Invitar colaborador | `portal/components/invite-dialog.tsx:53,56,149` | Ninguna (la lista de permisos es lectura de apoyo) | Cerrado | Añadida (igual que sucursal) | migrado |
| Perfil de la tienda | `portal/pages/store-profile-page.tsx:48,53,54` | Ninguna; un PATCH con solo los campos cambiados | Cerrado | Añadida; el texto «Tienes cambios sin guardar» existía sin guardia real | migrado |
| Confirmaciones (`ConfirmDialog`: desactivar sucursal, revocar invitación, cancelar pedido, cambiar existencias, quitar producto) | `portal/components/confirm-dialog.tsx:33` | Ninguna; una acción por confirmación | Cerrado en el componente compartido | No aplica | migrado (candado) |
| Revisión de solicitud (admin) | `admin/components/review-dialog.tsx:35` | Ninguna | Cerrado: `useSubmitOnce` | No necesaria (una nota) | migrado (candado) |
| Cobro del plan (simulado o Wompi) | `portal/components/simulated-checkout-dialog.tsx:62` | Ninguna; `clientRequestId` por apertura | Cerrado: `useSubmitOnce` | No aplica | migrado (candado) |
| Registro de envío | `portal/components/shipment-panel.tsx:23` | Ninguna | Cerrado: `useSubmitOnce` | No necesaria (2 campos) | migrado (candado) |
| Avance del envío, estado del pedido, reenviar invitación, reenviar correo de configuración | `shipment-panel.tsx:72`, `order-status-actions.tsx:25`, `team-page.tsx:55`, `application-page.tsx:44` | Ninguna; acción única por botón | Botón deshabilitado por estado (React aplica el estado en eventos discretos; sin hallazgo) | No aplica | cumple |
| Producto (alta y edición con imágenes y reglas) | `portal/pages/product-form-page.tsx:177,200,208` | Ninguna; un multipart | Cerrado | Ya tenía `useUnsavedChanges` | cumple (referencia del patrón) |
| Inventario (cantidad por fila) | `portal/pages/inventory-page.tsx:93` | Ninguna; escribe solo tras `ConfirmDialog` | Cubierto por `ConfirmDialog` | No necesaria (borrador de un número) | cumple |
| Filtros y búsqueda de productos, pedidos, sucursales y equipo | `products-page.tsx`, `orders-page.tsx:99`, `branches-page.tsx:124`, `team-page.tsx:135` | Solo estado local o URL | n/a | n/a | no aplica |
| Solicitud de cotización | `portal/components/quote-request-dialog.tsx:66` | Ninguna: hoy solo arma un `mailto:` o texto; no hay llamada al servidor (TODO de backend en el código) | n/a | No necesaria | cumple |
| Lista de solicitudes (admin) | `admin/pages/applications-page.tsx`, `application-sheet.tsx:28` | Solo lecturas y descarga de documento | n/a | n/a | no aplica |
| Marketplace: filtros, buscador, hero | `marketplace/components/filters-panel.tsx`, `hero-search.tsx:26`, `search-bar.tsx:63` | Solo navegación con parámetros de URL (lectura) | n/a | n/a | no aplica |
| Verificar correo | `auth/pages/verify-email-page.tsx:20` | Es la consecuencia del enlace del correo (el token se consume al abrirlo), no un formulario | Una promesa por token | n/a | cumple |

## Piezas nuevas (T9)

- `frontend/src/lib/use-close-guard.ts` y `components/common/discard-changes-dialog.tsx`: guardia de cierre para `Sheet` y `Dialog` (no son navegación de router). El formulario avisa su estado sucio; X, Esc, fondo y «Cancelar» preguntan «¿Descartar lo que escribiste?»; tras guardar se cierra directo.
- `frontend/src/lib/use-form-touched.ts`: señal «el usuario escribió algo» para formularios no controlados (`FormData`), sin reescribirlos como controlados.
- `ConfirmDialog` usa `useSubmitOnce`: todas las confirmaciones quedan con candado síncrono.

## Limitaciones documentadas (sin cambiar el backend)

1. **Subida de documentos**: el backend ya recibe todo en un solo multipart (`POST /store-applications`, `POST /me/store-applications` y la configuración), así que no hay subida previa. Los archivos se validan en local (tamaño al elegir; total y obligatorios al enviar). Sin hallazgo de contrato.
2. **Dirección dentro del checkout**: «Guardar dirección» crea la dirección en la libreta del comprador antes de «Pagar». Es un formulario propio con su botón, pero la dirección persiste aunque el comprador no termine la compra. Propuesta (T-backend futura): aceptar una dirección en línea en `placeOrder` (`address` en lugar de `addressId`, con `saveAddress`) y guardarla en la libreta en la misma transacción.
3. **Cotización Empresarial**: no se guarda en el servidor; cuando exista `POST /stores/{id}/quote-requests`, migrar a `useDraftForm` + `useSubmitOnce` (hoy no hay escritura que proteger).
4. **Configurar almacén**: si la escritura tiene éxito pero falla el reemplazo de sesión posterior (`finish`), se muestra el error con la configuración ya guardada y reintentar vuelve a enviar. Mejorable si el backend hace idempotente la configuración.
5. Errores de servidor por campo: en formularios no controlados (auth, vendedor) se limpian al reenviar, no al editar cada campo.

## Verificación

- `./node_modules/.bin/tsc -b --noEmit`: sin errores. `./node_modules/.bin/oxlint`: solo 3 avisos previos (`only-export-components` en `button.tsx`, `badge.tsx`, `payment-method-picker.tsx`). `npm run build`: correcto.
- Navegador (Chrome headless por CDP, `VITE_MARKETPLACE_DATA_SOURCE=mock`, puerto 5174): se envolvieron los métodos de escritura de los servicios mock con un contador. Resultados:
  - Login: 2 `requestSubmit()` síncronos, 1 escritura.
  - Perfil de tienda: tras editar, 0 escrituras; un enlace a otra sección muestra «Salir sin guardar»; «Seguir editando» conserva; doble envío, 1 `updateProfile`; tras guardar se navega sin diálogo.
  - Sucursal (panel): tras llenar, 0 escrituras; Cancelar y Esc preguntan; «Descartar» cierra sin escribir; sin cambios cierra sin preguntar; doble envío, 1 `create` (el mock respondió el límite de plan y el panel quedó abierto con lo escrito, como antes).
  - Invitar: 0 escrituras al llenar; Cancelar pregunta; doble envío, 1 `invite` y el diálogo se cierra.
  - Registro de almacén: elegir 2 PDF, 0 escrituras; un archivo de 11 MB marca error local sin red; guardia al salir; doble envío, 1 `submit`.
  - Configurar almacén: elegir logo, 0 escrituras; guardia; doble envío, 1 `completeSetupAuthenticated` y navega a `/portal` sin diálogo.
  - Registro de cliente: 0 escrituras al llenar; guardia; doble envío, 1 `register`; tras el éxito no hay guardia.
  - Recuperar contraseña: doble envío, 1 `requestPasswordRecovery` y 1 `resetPassword`.
  - `ConfirmDialog` (desactivar sucursal): doble clic síncrono, 1 `deactivate`.
- No comprobado en navegador (solo lectura de código y tipos): dirección del checkout, pagar pedido, cobro del plan, registro de envío, revisión de solicitud (admin). Es el mismo cambio mecánico (`useSubmitOnce`), sin diálogo nuevo.
- El mock guarda la sesión en memoria: las pruebas navegan por la SPA para no perderla.
