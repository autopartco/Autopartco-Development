# Imágenes de productos

Las imágenes se guardan en Supabase Storage; PostgreSQL almacena únicamente la ruta, el tipo MIME y los metadatos de galería. El bucket usado por la configuración actual es `products-images`. Debe ser público para que las URL de lectura incluidas en las respuestas puedan mostrarse sin exponer credenciales.

## Configuración

Define en el `.env` de la raíz del repo:

```dotenv
SUPABASE_URL=https://<proyecto>.supabase.co
SUPABASE_SERVICE_KEY=<clave-secreta-del-servidor>
SUPABASE_BUCKET=products-images
```

La clave de servicio solo se usa desde Spring Boot; nunca debe enviarse al navegador. La configuración mantiene compatibilidad con `SUPABASE_BUCKET_PRODUCTS_IMGS` si ya existe en el entorno. La clave de servicio no debe almacenarse en `.env.example`.

Los archivos se almacenan con UUID, agrupados por producto, y conservan su formato validado:

```text
products-images/
└── products/{productId}/{uuid}.jpg|png|webp
```

Se aceptan JPEG, PNG y WebP válidos, hasta 5 MB por imagen y 10 imágenes por producto. El backend verifica el tipo declarado contra la firma del archivo, limita dimensiones, y no instala un codificador adicional: mantiene el formato original en vez de renombrar o convertir datos incorrectamente.

## Endpoints

Todos los endpoints usan el prefijo `/api/v1`.

| Método | Ruta | Acceso / función |
| --- | --- | --- |
| `GET` | `/marketplace/products` | Consulta pública; incluye `images` en cada producto. |
| `GET` | `/marketplace/products/{productId}` | Consulta pública del producto publicado; incluye `images`. |
| `POST` | `/stores/{storeId}/products/{productId}/images` | Requiere autenticación y permisos de gestión de tienda. `multipart/form-data`: `file`, `files` repetido o `files[]` repetido. |
| `PUT` | `/stores/{storeId}/products/{productId}/images/{imageId}` | Reemplaza una imagen; `multipart/form-data`, campo `file`. |
| `PATCH` | `/stores/{storeId}/products/{productId}/images/{imageId}/main` | Marca una imagen como principal y desmarca las demás. |
| `PATCH` | `/stores/{storeId}/products/{productId}/images/order` | Recibe `{"imageIds":["<uuid>", "..."]}` en el orden deseado. |
| `DELETE` | `/stores/{storeId}/products/{productId}/images/{imageId}` | Elimina la imagen; si era la principal, promueve otra automáticamente. |
| `DELETE` | `/stores/{storeId}/products/{productId}` | Eliminación lógica del producto, imágenes e inventario; limpia los archivos de Storage. |

Las respuestas de producto incluyen una lista `images` ordenada por `displayOrder`, con `id`, `url`, `thumbUrl`, `cardUrl`, `isMain`, `displayOrder` y `createdAt`. La imagen principal se mantiene única mediante una restricción de PostgreSQL.

La creación y edición de productos suben las imágenes junto con los datos en un único `multipart` (ver `PLAN.md`): parte `data` (JSON) y partes `images` / `newImages`. Los endpoints por imagen de la tabla anterior siguen disponibles.

## Miniaturas (`thumbUrl` 400 px, `cardUrl` 800 px)

`url` es siempre el original. `thumbUrl` y `cardUrl` las genera `ImageUrls` según `IMAGE_PROVIDER`; sin proveedor valen lo mismo que `url`. Si el proveedor está mal configurado o es desconocido, se degrada a la URL pública de Supabase.

| `IMAGE_PROVIDER` | Costo / alojamiento | URL | Notas |
| --- | --- | --- | --- |
| `none` (defecto) | — | URL pública de Supabase | Sin transformación. |
| `wsrv` | Gratis, sin cuenta ni alojamiento | `https://wsrv.nl/?url={urlPública}&w=&h=&fit=cover&output=webp&q=80` | Servicio comunitario sin SLA. Recomendado para desarrollo y arranque. |
| `cloudinary` | Plan gratis, cuenta (`CLOUDINARY_CLOUD_NAME`) | `https://res.cloudinary.com/{cloud}/image/fetch/w_,h_,c_fill,f_auto,q_auto/{urlPública}` | Recomendado para producción. Puede exigir permitir el dominio de Supabase en la configuración de «fetch». |
| `openinary` | Gratis pero autoalojado (`OPENINARY_URL`) | `{base}/t/w_,h_,c_fill,f_webp/{ruta}` | Opcional; ver `docker-compose.openinary.yml`. |

Con `wsrv`, `cloudinary` y `openinary` el bucket debe ser público. Las transformaciones nativas de Supabase son de pago (plan Pro) y no se usan. Además, el frontend comprime a WebP en el navegador antes de subir.

## Inicialización y prueba

Ejecuta `backend/database/init.sql` para crear `product_images` y su índice de imagen principal. En Supabase, crea o reutiliza el bucket público `products-images`.

Para subir varias imágenes con `curl` usando un JWT y UUID existentes:

```sh
curl -X POST "http://localhost:8081/api/v1/stores/<storeId>/products/<productId>/images" \
  -H "Authorization: Bearer <access-token>" \
  -F "files=@pastillas.jpg;type=image/jpeg" \
  -F "files=@disco.webp;type=image/webp"
```

Los errores usan el sobre API existente: `400` para formato o límites inválidos, `403` para el rol no autorizado, `404` cuando el producto o imagen no existe, `413` cuando se excede el tamaño permitido y `500` cuando falla Storage.

El frontend actual no contiene todavía formularios de gestión ni una vista de detalle de productos; por ello, esta entrega expone las imágenes desde la API sin añadir una interfaz paralela de productos.
