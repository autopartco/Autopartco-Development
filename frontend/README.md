# Autopartco · Frontend (marketplace público)

React 19 + TypeScript + Vite 8, Tailwind CSS v4 (configuración en `src/index.css`), shadcn/ui sobre Base UI, Lucide, React Router y Motion.

## Scripts

```bash
npm run dev      # servidor de desarrollo
npm run build    # typecheck (tsc -b) + build de producción
npm run lint     # oxlint
```

## Rutas

| Ruta | Pantalla |
| --- | --- |
| `/` | Inicio: buscador con vehículo, categorías, destacados y tiendas |
| `/buscar?q=` | Resultados con filtros, orden y paginación en la URL |
| `/categorias` | Todas las categorías |
| `/categoria/:slug` | Resultados de una categoría |
| `/producto/:id` | Detalle del repuesto |
| `/tiendas` | Tiendas publicadas |
| `*` | 404 |

Parámetros de búsqueda: `q`, `categoria`, `marca` (ids separados por comas), `tienda`, `vehiculo`, `modelo`, `anio`, `precio_min`, `precio_max`, `disponible=1`, `verificada=1`, `orden` (`price_asc`, `price_desc`) y `pagina`.

## Origen de datos

Copia `.env.example` a `.env.local` y ajusta:

- `VITE_MARKETPLACE_DATA_SOURCE`: `mock` (por defecto) o `http`.
- `VITE_API_BASE_URL`: URL base con el prefijo de versión, p. ej. `http://localhost:8080/api/v1`.

La interfaz `MarketplaceService` (`src/features/marketplace/api/types.ts`) tiene dos implementaciones que se cargan bajo demanda:

- **mock** (`api/mock/`): unos 44 repuestos con precios en COP, marcas y vehículos del mercado colombiano y tiendas ficticias. Búsqueda, filtros, orden y paginación en el cliente, con latencia simulada. Añade `?simulateError=1` a cualquier URL para probar los estados de error.
- **http** (`api/http/`): solo usa los endpoints públicos existentes (`/marketplace/products`, `/marketplace/products/{id}`, `/marketplace/product-options[/categories]` y `/marketplace/stores`). Lo que el backend aún no ofrece está marcado con `TODO(api)`; la UI oculta los filtros, el orden y la paginación que el servicio no declara en `capabilities`.

## Logo e imágenes

- Logo: coloca el archivo real en `public/logo.svg`. Se muestra con `alt="Autopartco"`; si no carga, aparece el texto "Autopartco".
- Imágenes genéricas de Unsplash centralizadas en `src/lib/images.ts`.
