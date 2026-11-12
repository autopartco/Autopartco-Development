# Changelog

## v2.4.1 — 2026-11-12

### Correcciones
- «Mis pedidos»: el número y la fecha del pedido vuelven a la primera columna en escritorio.
- El detalle del pedido muestra la miniatura de cada producto con enlace a su ficha.
- Todos los correos usan el diseño de Autopartco (grafito, rojo de acción, datos en monoespaciada); los que llegaban solo en texto ahora tienen versión HTML.

## v2.4.0 — 2026-11-12

### Mi cuenta
- Nueva sección «Mi cuenta», igual para clientes, almacenes y administradores: datos personales, seguridad, direcciones, preferencias y privacidad.
- Cambiar contraseña (cierra las demás sesiones) y correo (con código al correo nuevo); verificación en dos pasos, sesiones abiertas e ingreso con Google.
- Descargar mis datos y solicitar la eliminación de la cuenta.
- El menú de cuenta comparte «Mi cuenta», «Mis pedidos», «Por calificar» y «Notificaciones» en todos los roles.

### Calificaciones
- Al entregarse un pedido llega una notificación para calificarlo; recordatorios por correo a los 3 y 10 días mientras falte calificar (se pueden desactivar).

### Correcciones
- El logo y el banner del almacén se ven en el portal.
- La barra de desplazamiento del menú lateral del portal y del admin usa el tono oscuro.
- Se quitó el diagrama «Relaciones» del panel de solicitudes.

## v2.3.0 — 2026-11-11

### Pedidos
- Comprobante de compra imprimible desde el detalle del pedido.
- Los almacenes adjuntan la factura electrónica (PDF) a cada venta; el comprador la descarga desde «Mis pedidos».

### Sitio
- Pie de página actualizado: sección «Comprar» con carrito, pedidos, calificaciones y notificaciones.

## v2.2.1 — 2026-11-11

### Notificaciones
- Menú de la campana compacto: 6 recientes, contador real, «Marcar todo como leído» y «Limpiar leídas».
- Las notificaciones con destino lo indican; las demás no parecen enlaces.
- Nueva página «Notificaciones» con bandeja, historial, archivado individual y paginación.

## v2.2.0 — 2026-11-11

### Notificaciones
- Bandeja paginada con contador de no leídas, «marcar todo como leído» y limpiar (archivar) con histórico.
- Las notificaciones de pedido y envío llevan al detalle del pedido.
- Cada notificación tiene un tipo (pedido, pregunta, respuesta…).
- El histórico se depura a los 180 días.

## v2.1.3 — 2026-11-11

### Sedes en el mapa
- Al crear o editar una sucursal se marca su ubicación con un pin ajustable; es obligatoria.
- Editar una sucursal ya no borra sus coordenadas.
- Las sedes con ubicación aproximada lo indican en el perfil del almacén.

## v2.1.2 — 2026-11-11

### Correcciones
- El logo del almacén se ve completo sobre el banner del perfil.
- En el teléfono, la ubicación va junto al buscador y ya no se solapa con el imagotipo.
- En escritorio el buscador mantiene un ancho mínimo; si falta espacio se recorta el nombre de la ciudad.

## v2.1.1 — 2026-11-11

### Correcciones
- El registro de cuentas nuevas con Google ya no falla con error del servidor.

## v2.1.0 — 2026-11-11

### Reseñas y calificaciones
- Reseñas de producto y de almacén con calificación por categorías e insignias.
- Calificar desde el pedido; invitación por correo y lista de reseñas pendientes.
- Respuesta del vendedor, edición de la reseña y moderación desde el admin.
- Calificaciones visibles en tarjetas, ficha de producto, perfil de tienda y búsqueda.

### Preguntas de producto
- Preguntas y respuestas en la ficha, con gestión en el portal y el admin.
- Avisos con enlace al responder; un aviso por respuesta.
- Filtro de datos de contacto que no rechaza referencias OEM ni años.

### Perfil de tienda
- Perfil nuevo con calificaciones, reseñas y preguntas.

### Despliegue
- El backend se despliega en el VPS de Hostinger (`api.autopartco.co`) al promover a `main`.

### Correcciones
- Las reseñas borradas no evaden la moderación.
- Cupo de reseñas tras validar y enlaces internos seguros.

## v2.0.0 — 2026-11-11

### Búsqueda
- Motor nuevo sobre Postgres (texto completo y trigramas): texto libre que interpreta marca, modelo, año, ciudad y referencia; tolera errores de escritura.
- Más de 20 filtros, facetas con conteos, búsqueda por radio y orden por cercanía.
- Sugerencias mientras se escribe.
- Índice `search_listings` actualizado tras cada cambio, cada noche y a demanda del admin.

### Publicidad
- Campañas prepagadas por día en cuatro ubicaciones (búsqueda, inicio, categoría y productos relacionados), pagadas con Wompi.
- Estadísticas por campaña: impresiones, alcance, clics, CTR, WhatsApp, carrito, pedidos, ingresos, costo por clic y ROAS.
- Moderación y tarifas desde el admin.
- Etiqueta «Patrocinado» en todos los anuncios.

### Inicio
- Página `/` nueva: buscador con selector de vehículo, mapa de cobertura, categorías, «Cerca de ti», destacados, cómo funciona, cifras reales y sección para vendedores.

### Perfil de almacén
- Servicios además de repuestos.
- Sucursales en el mapa con «Cómo llegar».
- WhatsApp por sucursal con mensaje y enlace del producto; los clics cuentan como métrica.
- Configuración de cuenta y de almacén, con 2FA.
- Verificación de correo por enlace o código y reenvío del código.

### Rendimiento y privacidad
- Caché con Redis opcional (`REDIS_URL`) y respaldo en memoria.
- ETag y `Cache-Control` en los GET públicos.
- Límite de intentos en `/auth`.
- Cookies funcionales `ap_aid` y `ap_area`.
- Página `/privacidad` (Ley 1581).

### Seguridad
- Correcciones de la revisión de QA: exigencia de 2FA del almacén, deduplicación de eventos de anuncios, límites en 2FA, claves de Redis con HMAC y redirección de pago restringida a Wompi.
