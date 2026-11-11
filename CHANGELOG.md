# Changelog

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
