# Datos de demostración de Autopartco (B4)

Semilla idempotente para desarrollo y demos. Combina **datos abiertos reales de Colombia** (vehículos y geografía) con datos sintéticos (repuestos, almacenes, personas, pedidos, pagos y navegación). Puebla el **esquema migrado**: `database/init.sql`, luego todas las `V*` de `database/migrations/` y después las `R__`.

> **Nunca en producción.** Las tablas de solo inserción (`audit_logs`, `inventory_movements`, historiales…) no permiten borrar la demo después. Para "resetear", recrea la base desechable.

## Archivos

| Archivo | Qué es |
|---|---|
| `generate_demo_seed.py` | Fuente del generador. Cada `# %%` es una celda del notebook |
| `generate_demo_seed.ipynb` | Notebook de Google Colab construido a partir del `.py` (mismas celdas, mismo resultado) |
| `demo_seed.sql` | Salida del generador. **No se edita a mano** |
| `demo_seed.summary.json` | Conteos de la última generación (marcas, líneas, filas por tabla) |
| `verify_demo_seed.py` | Verificación estática del `.sql` contra el esquema migrado (no se conecta a ninguna base) |

## 1. Generar `demo_seed.sql`

### En Google Colab

1. Abre `generate_demo_seed.ipynb` en Colab (*Archivo → Subir notebook*).
2. Si quieres, cambia los parámetros de la segunda celda (volumen, número de líneas, `DATA_ORIGIN`).
3. *Entorno de ejecución → Ejecutar todo*. Al final se descarga `demo_seed.sql`. Cópialo a `database/seeds/demo/`.

Solo usa la biblioteca estándar de Python: no hay que instalar nada.

### En local

Desde la raíz del repositorio. La caché de descargas debe quedar fuera del repo:

```bash
export APC_CACHE_DIR="$HOME/.cache/autopartco-demo-seed"
export APC_OUT_PATH="database/seeds/demo/demo_seed.sql"
PYTHONIOENCODING=utf-8 python database/seeds/demo/generate_demo_seed.py
```

- La primera ejecución descarga los datos agregados de datos.gov.co (API pública, sin clave) y los guarda en `APC_CACHE_DIR`.
- Las siguientes ejecuciones reutilizan la caché, así que el archivo sale idéntico byte a byte. Con `APC_OFFLINE=1` falla en vez de descargar.
- La huella de la caché queda en la cabecera del `.sql` ("Huella de los datos de origen").

### Parámetros (variables de entorno `APC_*`)

| Variable | Por defecto | Qué controla |
|---|---|---|
| `APC_N_CAR_LINES` / `APC_N_MOTO_LINES` | 40 / 6 | Líneas de automóvil, camioneta o campero, y de moto, más comunes |
| `APC_MAX_VERSIONS` | 8 | Versiones (año y cilindraje) por línea |
| `APC_N_BUYERS` | 80 | Compradores ficticios |
| `APC_N_ORDERS` | 300 | Pedidos históricos |
| `APC_HISTORY_DAYS` | 180 | Ventana del historial |
| `APC_SNAPSHOT_DAYS` | 14 | Días de `inventory_daily_snapshots` (es la tabla que más pesa) |
| `APC_N_ANON_SESSIONS` | 250 | Sesiones de visitantes sin cuenta (`tracking.*`) |
| `APC_N_PO_PER_STORE` | 6 | Órdenes de compra por almacén |
| `APC_DATA_ORIGIN` | `DEMO` | Valor de `store.data_origin` y `orders.data_origin` (ver §4) |
| `APC_INCLUDE_BOYACA` | `0` | `1` añade el parque automotor de Boyacá |
| `APC_RANDOM_SEED` | 20261014 | Semilla del generador |

## 2. Verificar (sin base de datos)

```bash
PYTHONIOENCODING=utf-8 python database/seeds/demo/verify_demo_seed.py database/seeds/demo/demo_seed.sql
```

El script construye el esquema final leyendo `init.sql` y las `V*` en orden (sin `pending-approval/`). Después comprueba lo siguiente:

- **Estructura:** tablas y columnas de cada `INSERT`, sin columnas generadas ni `IDENTITY`. Que cada `NOT NULL` sin `DEFAULT` esté presente. Que cada columna use el tipo temporal correcto (`TIMESTAMP` heredado o `TIMESTAMPTZ` nuevo).
- **Integridad:** cada FK apunta a una fila del propio script o a una semilla de `init.sql`, `V*` o `R__`.
- **Reglas:** los CHECK y las unicidades.
- **Invariantes de negocio:**
  - totales de ventas, pedidos, pagos y recibos;
  - un solo pago `APPROVED` por pedido;
  - transiciones válidas según `SaleStatusService` y `ShipmentService`;
  - coherencia de almacén de los triggers de `V202610130925` y `V202610141330`;
  - que el ledger cuadre con el stock.

Imprime una tabla con las filas generadas para cada tabla de `public`, `tracking`, `analytics`, `ml` y `privacy`, y el motivo de las que quedan vacías. Si encuentra un error, sale con código 1.

## 3. Aplicar (lo ejecuta el usuario)

Orden completo sobre una base **desechable**:

1. `database/init.sql`
2. Todas las `V*` en orden, y después `R__seed_geo_divipola.sql` y `R__seed_holidays_co.sql`. El procedimiento está en `database/migrations/README.md` §3.1, y no incluye nada de `pending-approval/`.
3. `demo_seed.sql`
4. Refrescos de analytics. Ya van al final de `demo_seed.sql`, fuera de la transacción. También se pueden repetir a mano con `database/migrations/README.md` §7.3.

```bash
export PGCLIENTENCODING=UTF8
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f database/seeds/demo/demo_seed.sql
# Opcional: enlazar una cuenta ya registrada en la app como dueña de los almacenes demo
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v owner_email=tu-correo@dominio.com -f database/seeds/demo/demo_seed.sql
```

- **Requiere `psql`.** Usa `\if` y `\gset`, así que el editor SQL de Supabase no sirve.
- **Una sola transacción.** Usa `BEGIN`/`COMMIT` y fija `SET LOCAL autopartco.capture_managed_by_app = 'on'`, de modo que los triggers puente de `V202610141025` no dupliquen el ledger, el historial de precios ni el de estados. El seed escribe esos historiales con sus fechas reales.
- **Idempotente.** Al terminar inserta un marcador en `audit_logs` (`action = 'DEMO_SEED_APPLIED'`). Si lo encuentra, no vuelve a insertar datos. Además, cada `INSERT` lleva `ON CONFLICT DO NOTHING` con claves deterministas (UUID v5). El bloque `owner_email` sí se ejecuta siempre, así que se puede repetir con otro correo.
- **Fechas relativas.** Todo el historial se ancla a la medianoche de Bogotá del día en que se aplica (`pg_temp.demo_tstz`). Las columnas `TIMESTAMP` heredadas guardan la hora UTC, que es lo que asume `public.legacy_ts()`.
- **Particiones.** Al inicio llama a `public.ensure_monthly_partitions` para `tracking.*` y `inventory_daily_snapshots`. Al final comprueba que las particiones `default` estén vacías.
- **Comprobaciones finales.** Dentro de la transacción revisa los conteos esperados, que el ledger cuadre (consulta de `database/migrations/README.md` §7.1) y que las `default` estén vacías. Si algo falla, revierte todo.

## 4. Cuentas, datos personales y `data_origin`

- **Las cuentas del seed no permiten iniciar sesión.** `accounts.password` contiene un texto que no es un hash BCrypt válido, y `BCryptPasswordEncoder.matches` siempre devuelve `false`. Los correos usan el dominio reservado `@demo.autopartco.test` (RFC 2606): ningún correo de recuperación puede llegar a nadie.
- **Para entrar a la demo:**
  1. Registra tu cuenta en la app (con verificación de correo).
  2. Vuelve a aplicar el seed con `-v owner_email=<tu correo>`. Tu cuenta queda como `OWNER` (colaborador activo con todas las sucursales) de los cuatro almacenes activos. Si era `CUSTOMER`, pasa a `STORE_MANAGER`, como hace `CompleteStoreSetup`.
  3. Cierra sesión y vuelve a entrar.
- **ROOT (requiere aprobación).** El final de `demo_seed.sql` incluye, comentado, el `UPDATE` que convierte en ROOT una cuenta ya registrada. Hay que descomentarlo a mano y volver a iniciar sesión.
- **Personas ficticias.** Nombres combinados al azar y documentos `DEMO-000001…`. Los teléfonos empiezan por `000`, que no es un número colombiano válido. Las IP son de los rangos de documentación (RFC 5737).
- **Tablas sensibles.** `account_tokens` y `account_sessions` solo tienen filas vencidas, usadas o revocadas. Sus "hashes" son valores aleatorios sin preimagen. `login_attempts` tiene más de 10 días de antigüedad.
- **Consentimientos.** Cada persona tiene `TERMS_OF_SERVICE` y `PERSONAL_DATA_PROCESSING`, más algunos opcionales. Solo se enlaza `subject_key` en `tracking.*` si la persona otorgó `ANALYTICS_TRACKING`.
- **`data_origin = 'DEMO'`** en `store` y `orders`. Las funciones `analytics.refresh_*` solo agregan filas `REAL`, así que con el valor por defecto los hechos de analytics quedan vacíos a propósito. Para ver los hechos poblados en una base desechable, genera con `APC_DATA_ORIGIN=REAL`, sabiendo que esos datos contarán como reales.

## 5. Qué contiene

- **Vehículos (reales):** las líneas más comunes del parque automotor de Risaralda, con marca y línea normalizadas y sin "SIN LINEA" ni "SIN INFORMACIÓN". Se convierten en `make`, `model`, `versions` (año y cilindraje, con `year_from`, `year_to` y `displacement_cc`) y `compatibilities`.
- **Geografía (real):** municipios de la DIVIPOLA con coordenadas del centroide. Los municipios usados se insertan en `geo_municipalities` con `ON CONFLICT DO NOTHING`, porque `R__seed_geo_divipola.sql` solo carga departamentos y la carga completa de municipios (`pending-approval/load_divipola_municipalities.sql`) no se aplica.
- **Almacenes (ficticios):** cuatro activos, en Bogotá/Soacha, Medellín/Envigado, Cali y Pereira/Dosquebradas, con siete sucursales. Hay tres solicitudes más que no operan (`PENDING_REVIEW`, `CONFIGURATION_PENDING` y `REJECTED`). Cada almacén activo tiene:
  - solicitud `ACTIVE` con suscripción pagada hasta dentro de 200 a 330 días, en un plan `DEMO_*` con `enabled = false`, que no aparece en el catálogo público;
  - roles `OWNER`, `VENDEDOR` y `BODEGA`;
  - colaboradores e invitaciones;
  - proveedores y órdenes de compra con recepciones.
- **Repuestos (sintéticos):**
  - unos 36 tipos en 8 categorías, con fabricantes reales;
  - referencias `APC-XXX-0000` que nunca son números OEM;
  - precios en COP con `Decimal`, con variación entre almacenes y sucursales;
  - compatibilidad por reglas (tipo de repuesto, segmento del vehículo y cilindraje), con `evidence_status = SELLER_DECLARED`.
- **Actividad comercial:**
  - pedidos en todos los estados, con transiciones válidas;
  - pagos `SIMULATED` y evaluación de riesgo;
  - recibos, envíos y eventos de envío;
  - devoluciones con reingreso;
  - reseñas, solo de ventas entregadas;
  - reclamos, notificaciones y carritos.
- **Inventario e historiales:**
  - ledger completo: saldo inicial, ventas, cancelaciones, compras y devoluciones;
  - historial de precios y políticas de reorden;
  - snapshots diarios.
- **Navegación:** `tracking.sessions`, `search_events`, `product_events` y `events`, sin PII.
- **Tablas que quedan vacías a propósito** (`verify_demo_seed.py` imprime el motivo de cada una):
  - catálogos que ya siembran `init.sql`, las `V*` o las `R__`;
  - `product_images`: no hay archivos en el almacenamiento;
  - `ml.*`: no hay modelos entrenados;
  - `analytics.fact_*`: se llenan con los refrescos.

La cabecera de `demo_seed.sql` lista productos con stock 1 o 2, para probar el 409 del checkout, y productos sin stock.

## NOTICE: atribución de las fuentes

Este seed contiene datos derivados de conjuntos publicados en el Portal de Datos Abiertos de Colombia ([datos.gov.co](https://www.datos.gov.co)) bajo la licencia **Creative Commons Atribución-CompartirIgual 4.0 Internacional (CC BY-SA 4.0)**:

| Conjunto | Identificador | Uso en el seed |
|---|---|---|
| Parque automotor de Risaralda | [`x9pp-pcn5`](https://www.datos.gov.co/d/x9pp-pcn5) | Conteos agregados por clase, marca, línea, año modelo y cilindraje. La placa nunca se consulta |
| Parque automotor activo de Boyacá (opcional) | [`em3d-hmim`](https://www.datos.gov.co/d/em3d-hmim) | Igual que el anterior, solo si `APC_INCLUDE_BOYACA=1` |
| División Político-Administrativa de Colombia (DIVIPOLA), DANE | [`gdxc-w37w`](https://www.datos.gov.co/d/gdxc-w37w) | Códigos, nombres y coordenadas de municipios |

La entidad publicadora y la fecha de actualización de cada conjunto están en su ficha de datos.gov.co. La fecha de los datos usados queda también en la cabecera de `demo_seed.sql`.

- **Cambios realizados:** agregación en el servidor, normalización de marcas y líneas, selección de las líneas más comunes, redondeo del cilindraje y conversión de las coordenadas de coma a punto decimal.
- **CompartirIgual:** las tablas `make`, `model`, `versions`, `compatibilities` y `geo_municipalities` de `demo_seed.sql`, y los nombres de municipios en `locations`, son una obra derivada. Si se redistribuyen, debe hacerse bajo CC BY-SA 4.0 y con esta atribución.
- **Sin datos personales:** el seed no contiene datos personales reales. Lo demás es sintético y se publica con el repositorio.
- **Marcas registradas:** los nombres de fabricantes de repuestos (Bosch, NGK, Monroe, Mann-Filter, Gates…) y de vehículos son marcas de sus titulares y se usan solo de forma descriptiva. Ningún producto del seed corresponde a una referencia real de esos fabricantes.
