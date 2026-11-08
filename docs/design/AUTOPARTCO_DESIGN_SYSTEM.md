# AUTOPARTCO — Manual de identidad visual y diseño de interfaces

**Versión:** 2.0 · **Dirección:** híbrida · **Intensidad:** equilibrada  

**Ámbito:** sitio público, buscador, catálogo, portal de tiendas y administración  

**Stack de referencia:** React + TypeScript + Vite + Tailwind CSS v4 + shadcn/ui sobre Base UI + Lucide

---

## 0. Decisión de diseño

Autopartco combina tres lenguajes que deben convivir sin competir:

1. **Industrial:** precisión, referencias, compatibilidad, inventario y trazabilidad.

2. **Corporativo:** confianza, orden, consistencia y presencia de marca.

3. **Comercial:** búsqueda rápida, comparación clara y acciones de compra o contacto.

La mezcla no consiste en decorar una plantilla de comercio con elementos industriales. La identidad debe surgir de cómo se organiza y presenta la información.

**Idea rectora: un catálogo comercial construido con la precisión de una ficha técnica y la claridad de una marca corporativa.**

La interfaz debe sentirse propia de Autopartco incluso si se retiran el logotipo y el color rojo. Su composición, tipografía, tratamiento de referencias y presentación de productos deben sostener la identidad.

## 1. Principios no negociables

### 1.1 La tarea define la pantalla

Antes de diseñar, responder:

- ¿Quién utiliza esta pantalla?

- ¿Qué necesita encontrar, decidir o completar?

- ¿Qué información necesita para hacerlo con confianza?

- ¿Cuál es la acción principal?

- ¿Qué puede quedar en segundo plano?

No iniciar una pantalla eligiendo tarjetas, colores o animaciones.

### 1.2 La información es la decoración

Las referencias, compatibilidades, precios, ubicaciones, estados y nombres de tiendas son parte del lenguaje visual. No añadir adornos para que una pantalla parezca diseñada.

### 1.3 Densidad controlada

Autopartco no debe ser una interfaz vacía ni una hoja de cálculo. Mostrar suficiente información para comparar repuestos sin convertir cada resultado en una ficha interminable.

### 1.4 Diferenciar no significa exagerar

No crear personalidad con gradientes, sombras grandes, radios extremos o animaciones constantes. La diferenciación debe venir de la composición, la tipografía, el contraste y los patrones propios del producto.

### 1.5 Coherencia sin uniformidad

Las pantallas comparten reglas, pero no tienen que compartir el mismo layout. Un catálogo, un formulario de registro y un panel de inventario resuelven tareas distintas.

---

## 2. Personalidad visual

| Dimensión | Expresión visual | Evitar |
|---|---|---|
| Técnica | Referencias legibles, datos alineados, etiquetas precisas | Simular una interfaz de diagnóstico futurista |
| Comercial | Producto, precio, tienda y disponibilidad visibles | Promociones invasivas y urgencia artificial |
| Corporativa | Estructura firme, contraste y consistencia | Apariencia de plantilla administrativa |
| Cercana | Lenguaje claro y acciones comprensibles | Infantilizar la marca con ilustraciones o emojis |
| Confiable | Procedencia, verificación y estados explícitos | Insignias sin significado o stock no confirmado |

**Tono de interfaz:** directo, concreto y profesional. Usar términos habituales del sector cuando ayuden a identificar una pieza; explicar los términos ambiguos.

## 3. Firma visual de Autopartco

La identidad se construye con cinco recursos reconocibles. No es obligatorio usar los cinco en cada pantalla.

### 3.1 Rojo de acción

El rojo identifica acciones prioritarias, selección activa y algunos detalles de marca. No debe teñir grandes superficies por defecto.

### 3.2 Grafito estructural

El grafito aporta peso a navegación, títulos y encabezados de sección. No convertir toda la aplicación en un tema oscuro.

### 3.3 Tipografía técnica para datos

Referencias, códigos, cantidades y etiquetas de inventario pueden utilizar una tipografía monoespaciada. El texto de lectura permanece en una sans serif.

### 3.4 Índices y etiquetas de sección

En contextos donde aporten orientación, utilizar pequeñas etiquetas como `CATÁLOGO / 02`, `INVENTARIO / 01` o `TIENDA / INFORMACIÓN`. Son metadatos funcionales, no decoración repetida en cada bloque.

### 3.5 Líneas de estructura

Usar divisores finos, alineaciones y una línea de acento para indicar selección o jerarquía. La línea roja puede marcar una navegación activa, una sección principal o una ficha destacada. No añadir barras rojas aleatorias a cada tarjeta.

---

## 4. Sistema de color

Los valores siguientes son una **propuesta de trabajo**, no una afirmación sobre los códigos oficiales del logotipo. Antes de fijarlos en producción, tomar los valores exactos del activo de marca aprobado y comprobar contraste.

### 4.1 Paleta base propuesta

| Token | Valor inicial | Función |
|---|---|---|
| `ap-red` | `#C9272C` | Acción principal y acento de marca |
| `ap-red-hover` | `#A91F25` | Hover y estado activo |
| `ap-red-tint` | `#F8E9E9` | Fondo de selección suave |
| `ap-graphite` | `#202326` | Navegación, títulos y bloques estructurales |
| `ap-ink` | `#292D31` | Texto principal |
| `ap-muted` | `#626A70` | Texto secundario |
| `ap-steel` | `#8B9298` | Metadatos de baja prioridad |
| `ap-paper` | `#F4F3EF` | Fondo cálido de página, solo donde ayude |
| `ap-white` | `#FFFFFF` | Superficies de lectura |
| `ap-line` | `#DDE0E2` | Divisores y bordes |
| `ap-line-strong` | `#B9C0C5` | Límites de controles y tablas |

La combinación principal es grafito + blanco + rojo. El tono papel puede utilizarse en áreas editoriales o de contexto, no como una textura global obligatoria.

### 4.2 Colores semánticos

| Estado | Valor inicial | Uso |
|---|---|---|
| Éxito | `#237A45` | Operación completada |
| Advertencia | `#9A5B0A` | Stock bajo o revisión pendiente |
| Error | `#B4232C` | Error o acción destructiva |
| Información | `#176B87` | Ayuda o estado informativo |

No confundir “tienda verificada” con “producto disponible”. Son datos distintos y necesitan etiquetas distintas.

### 4.3 Proporción orientativa

En una pantalla de catálogo típica:

- 70–85 % superficies blancas o neutras.

- 10–20 % grafito y texto.

- 3–8 % rojo de marca.

No son cuotas rígidas. Sirven para detectar pantallas donde el rojo domina sin necesidad.

### 4.4 Reglas

- No usar gradientes como fondo de marca.

- No usar rojo para textos largos ni como fondo de grandes paneles sin una razón funcional.

- No comunicar estados solo mediante color.

- Verificar contraste WCAG 2.2 AA.

- Los colores de estado no sustituyen etiquetas textuales.

---

## 5. Tipografía y tratamiento de datos

### 5.1 Familias recomendadas

**Opción preferida para validar:** IBM Plex Sans para interfaz y IBM Plex Mono para referencias, códigos y datos técnicos.

- IBM Plex Sans: navegación, títulos, párrafos, formularios y botones.

- IBM Plex Mono: referencias de producto, SKU, códigos, cantidades técnicas y marcas de tiempo.

- No utilizar la monoespaciada en párrafos ni en textos extensos.

Si el proyecto ya utiliza Inter y cambiarla implica un coste innecesario, se puede mantener Inter temporalmente. La identidad no debe depender de instalar una fuente. En ese caso, reservar IBM Plex Mono para los datos técnicos y validar el cambio tipográfico con una pantalla real.

No añadir una tercera familia tipográfica.

### 5.2 Escala

| Uso | Tamaño orientativo | Peso |
|---|---:|---:|
| Titular de página pública | 36–44 px | 650–700 |
| Título de página de aplicación | 28–32 px | 650 |
| Título de sección | 20–24 px | 600 |
| Nombre de producto | 16–18 px | 600 |
| Texto de interfaz | 14–16 px | 400–500 |
| Etiqueta | 12–13 px | 500–600 |
| Referencia técnica | 12–14 px | 500, monoespaciada |
| Precio | 18–22 px | 650–700 |

No usar un titular enorme en cada pantalla. En herramientas de trabajo, el título debe dejar espacio a los datos y las acciones.

### 5.3 Reglas tipográficas

- Alinear a la izquierda por defecto.

- Usar mayúsculas solo en códigos, microetiquetas y abreviaturas.

- Evitar tracking exagerado y textos completos en mayúsculas.

- Precios y cantidades usan cifras tabulares cuando la fuente lo permita.

- No reducir metadatos hasta hacerlos ilegibles.

- No utilizar tipografía condensada para simular una estética automotriz.

---

## 6. Geometría, espaciado y superficies

### 6.1 Escala de espacio

Usar una escala de 4 px: `4, 8, 12, 16, 20, 24, 32, 40, 48, 64`.

No introducir valores arbitrarios para corregir cada pantalla. Si un patrón requiere un espaciado nuevo, revisar primero si puede resolverse con la escala existente.

### 6.2 Radios

| Elemento | Radio |
|---|---:|
| Campo y botón | 6 px |
| Etiqueta | 3–4 px |
| Panel de contenido | 8 px |
| Diálogo | 8–10 px |
| Avatar | Circular |

Los radios son deliberadamente contenidos. No aplicar 16–24 px a todos los componentes. No usar botones píldora como estilo general.

### 6.3 Bordes y sombras

- Preferir un borde visible y limpio a una sombra decorativa.

- Usar sombra solo cuando exista una relación de elevación real: menú flotante, popover o diálogo.

- Evitar paneles anidados con borde, sombra y fondo distinto simultáneamente.

- No encerrar cada grupo de datos en una tarjeta.

### 6.4 Contenedores

- Contenido público: máximo aproximado de 1200 px.

- Área de aplicación: aprovechar el ancho disponible sin estirar líneas de lectura.

- Padding de página: 24–32 px escritorio; 16 px móvil.

- En catálogos y tablas, la densidad útil tiene prioridad sobre grandes espacios vacíos.

---

## 7. Composición y layouts propios

### 7.1 Sitio público

La página principal no debe repetir automáticamente el hero centrado de una plantilla SaaS.

Composición preferida:

- Navegación horizontal compacta.

- Titular alineado a la izquierda.

- Buscador como herramienta principal, con campos y pistas útiles.

- A un lado o debajo, contenido de contexto real: categorías, cómo funciona o cobertura.

- Productos o tiendas reales como prueba de utilidad.

- Bloques de contenido con ritmos distintos; no una sucesión de tarjetas idénticas.

### 7.2 Catálogo y resultados

Usar una composición editorial de catálogo:

- Cabecera de búsqueda con consulta visible.

- Conteo y ordenamiento en una línea de control.

- Filtros laterales en escritorio; panel desplegable en móvil.

- Resultados en filas o cuadrícula según la fotografía y la comparación requerida.

- Referencia y compatibilidad como datos de primer nivel.

- Precio, tienda y estado próximos entre sí.

- Separadores para distinguir resultados sin encerrar cada uno en una tarjeta elevada.

No ocultar datos esenciales dentro de tooltips.

### 7.3 Portal de tiendas

- Navegación lateral grafito, compacta y de ancho controlado.

- Área de trabajo clara.

- Cabecera con nombre de la sección, sucursal activa y acción principal.

- Tablas o listas para inventario y pedidos.

- Resúmenes solo si ayudan a decidir qué hacer.

- No colocar cuatro KPIs al inicio por costumbre.

### 7.4 Administración

- Priorizar revisión, trazabilidad, filtros y estados.

- Separar claramente lectura, edición y acciones destructivas.

- Mostrar quién realizó una acción y cuándo cuando el dato esté disponible.

- No convertir cada conjunto de métricas en una tarjeta decorativa.

---

## 8. Componentes y reglas concretas

### 8.1 Botones

- Primario: fondo rojo, texto blanco, radio 6 px.

- Secundario: superficie blanca, borde grafito o gris, texto grafito.

- Terciario: sin contenedor; para acciones contextuales.

- Destructivo: rojo semántico y confirmación cuando corresponda.

Una acción principal por región funcional. El texto debe describir el resultado: “Agregar repuesto”, “Guardar cambios”, “Consultar tienda”.

No usar gradientes, brillos, sombras ni forma píldora.

### 8.2 Campos

- Etiqueta persistente encima del campo.

- Borde visible en reposo; foco con contorno accesible.

- Placeholder como ejemplo, nunca como única etiqueta.

- Mensaje de error junto al campo.

- Ayuda concreta y breve.

- No convertir todos los campos en grandes cajas redondeadas.

### 8.3 Tarjetas

Una tarjeta se justifica cuando el contenido es una unidad autónoma que necesita separación espacial o interacción propia.

Antes de crearla, preguntar:

- ¿Este contenido necesita un límite visual?

- ¿Una fila, un divisor o una sección abierta funcionaría mejor?

- ¿Se está creando una tarjeta solo porque el generador la propone?

No usar tarjetas anidadas para agrupar campos o párrafos.

### 8.4 Insignias y estados

- Etiquetas compactas, con texto claro.

- “Tienda verificada” y “Disponible” nunca son intercambiables.

- Mostrar fecha de actualización si la frescura del dato importa.

- No usar una insignia para cada atributo.

### 8.5 Tablas

- Encabezados claros y alineados.

- Números y precios alineados a la derecha.

- Acciones por fila agrupadas y consistentes.

- Fila seleccionada con fondo tenue y/o línea de acento.

- No reducir la tabla a una cuadrícula ilegible en móvil; transformar filas o habilitar desplazamiento horizontal controlado.

### 8.6 Iconografía

Lucide es la biblioteca técnica disponible, no una obligación decorativa.

- Usar icono cuando acelere reconocimiento o reduzca texto.

- Mantener tamaños de 16–20 px para controles.

- Acompañar con etiqueta los iconos ambiguos.

- No colocar un icono en cada título, campo y tarjeta.

- No mezclar iconos outline, filled y emojis.

---

## 9. Fotografía y representación de productos

Las imágenes tienen una función comercial: ayudar a reconocer el repuesto.

- Priorizar fotografía real del producto proporcionada por la tienda o una fuente autorizada.

- Mantener fondo y escala visual consistentes dentro de una misma lista.

- Utilizar `object-fit: contain` para piezas cuando sea importante conservar su forma.

- No recortar conectores, terminales, perforaciones ni rasgos identificadores.

- No generar imágenes que aparenten representar una referencia real si no corresponden a ella.

- Cuando falte una imagen, mostrar un placeholder sobrio con la leyenda “Imagen no disponible”.

- No repetir la misma fotografía genérica para rellenar el catálogo.

---

## 10. Movimiento e interacción

El movimiento confirma una acción o explica un cambio de estado.

Permitido:

- Transiciones de color y opacidad de 120–180 ms.

- Apertura de menús y paneles de 150–220 ms.

- Skeletons solo mientras se cargan datos.

- Feedback inmediato en botones y controles.

- Respeto a `prefers-reduced-motion`.

Evitar:

- Animar todos los elementos al entrar en pantalla.

- Parallax, partículas, blobs y elementos flotantes decorativos.

- Pulsaciones continuas en botones.

- Retrasar una acción para completar una animación.

- Animación como sustituto de una jerarquía visual clara.

---

## 11. Responsive y accesibilidad

- Reorganizar el contenido según la tarea; no limitarse a encogerlo.

- Mantener controles táctiles de aproximadamente 44 × 44 px.

- Navegación por teclado y foco visible.

- Etiquetas semánticas y nombres accesibles.

- Contraste WCAG 2.2 AA.

- Estados no comunicados únicamente por color.

- Zoom al 200 % y textos extensos sin pérdida de contenido.

- Filtros en móvil dentro de un panel accesible, con resumen de filtros activos.

- No ocultar precio, nombre de pieza o tienda para ahorrar espacio.

---

## 12. Estados de interfaz obligatorios

Toda pantalla dependiente de datos debe contemplar los estados que correspondan:

1. Cargando.

2. Resultado normal.

3. Sin resultados.

4. Error recuperable.

5. Error de permisos o sesión.

6. Acción completada.

7. Acción en proceso.

8. Estado deshabilitado.

Un estado vacío debe explicar qué falta y cuál es el siguiente paso posible. Un error debe indicar cómo recuperarse cuando exista una solución.

No usar métricas, reseñas, disponibilidad, precios o nombres ficticios en una interfaz conectada a datos reales. En prototipos, marcar claramente el contenido como demostración.

---

## 13. Antipatrones de IA: bloqueados por defecto

No incorporar sin justificación de producto:

- Gradientes morado-azul-rosa.

- Glassmorphism, blur o transparencias decorativas.

- Blobs, órbitas, partículas y círculos abstractos.

- Cuatro tarjetas KPI por defecto.

- Hero centrado con titular genérico, dos CTA y mockup flotante.

- Tarjetas para cada bloque de contenido.

- Tarjetas anidadas.

- Sombras grandes en todos los contenedores.

- Radios extremos o botones píldora generalizados.

- Iconos decorativos en cada título.

- Emojis como iconografía de producto.

- Ilustraciones abstractas para rellenar espacios vacíos.

- Animaciones de entrada indiscriminadas.

- Tipografía condensada solo para parecer “automotriz”.

- Texto de relleno, estadísticas ficticias o stock inventado.

- Secciones repetidas con la misma composición aunque tengan tareas diferentes.

**Excepción:** un patrón de esta lista puede utilizarse si resuelve una necesidad demostrable, se aplica de forma contenida y no rompe la identidad.

---

## 14. Tokens CSS iniciales

Los valores deben centralizarse y ajustarse a los colores oficiales de marca cuando estén confirmados.

```css

:root {

  color-scheme: light;

  --ap-red: #C9272C;

  --ap-red-hover: #A91F25;

  --ap-red-tint: #F8E9E9;

  --ap-graphite: #202326;

  --ap-ink: #292D31;

  --ap-muted: #626A70;

  --ap-steel: #8B9298;

  --ap-paper: #F4F3EF;

  --ap-white: #FFFFFF;

  --ap-line: #DDE0E2;

  --ap-line-strong: #B9C0C5;

  --ap-success: #237A45;

  --ap-warning: #9A5B0A;

  --ap-danger: #B4232C;

  --ap-info: #176B87;

  --ap-radius-control: 6px;

  --ap-radius-panel: 8px;

  --ap-radius-dialog: 10px;

  --ap-shadow-overlay: 0 12px 32px rgb(32 35 38 / 0.16);

  --ap-content-width: 1200px;

}

```

No crear tokens duplicados en componentes individuales. Si Tailwind CSS v4 ya centraliza los tokens en una capa `@theme`, mapear allí estos valores en lugar de mantener dos fuentes de verdad.

---

## 15. Ejemplo de aplicación: resultados de búsqueda

### Objetivo

Una persona busca un filtro de aceite. Necesita comparar referencia, compatibilidad, precio, tienda y disponibilidad sin abrir cada resultado.

### Wireframe de escritorio

```text

┌─────────────────────────────────────────────────────────────────────┐

│ AUTOPARTCO       Repuestos   Tiendas   Cómo funciona    Ingresar     │

├─────────────────────────────────────────────────────────────────────┤

│ CATÁLOGO / 02                                                       │

│ Encuentra el repuesto que necesitas                                 │

│ [ filtro de aceite                                  ] [ Buscar ]    │

│                                                                     │

│ Repuestos  /  Resultados para “filtro de aceite”                    │

├───────────────────┬─────────────────────────────────────────────────┤

│ FILTRAR RESULTADOS│  24 resultados                  Orden: Relevancia│

│                   │                                                 │

│ Categoría         │  ┌───────┐  Filtro de aceite                     │

│ □ Filtros         │  │       │  REF. FLT-123                         │

│ □ Motor           │  │ PIEZA │  Compatibilidad: consultar vehículos │

│ □ Frenos          │  │       │  Tienda verificada · Cartagena        │

│                   │  └───────┘  $ 45.000       [Ver producto]       │

│ Marca             │  ─────────────────────────────────────────────  │

│ □ Marca A         │  ┌───────┐  Filtro de aceite                     │

│ □ Marca B         │  │       │  REF. OIL-456                         │

│                   │  │ PIEZA │  Compatibilidad confirmada            │

│ Precio            │  │       │  Repuestos del Caribe · Bolívar       │

│ [Mín.]    [Máx.]  │  └───────┘  $ 52.000       [Ver producto]       │

│                   │                                                 │

│ Ubicación         │                                                 │

│ □ Cartagena       │                                                 │

└───────────────────┴─────────────────────────────────────────────────┘

```

### Qué hace reconocible esta pantalla

- La etiqueta `CATÁLOGO / 02` aporta una convención editorial y técnica.

- La búsqueda es una herramienta, no un elemento ornamental.

- Las referencias usan un tratamiento tipográfico distinto al texto normal.

- Los resultados son filas de catálogo, no tarjetas flotantes.

- El precio y la acción se alinean en una columna estable.

- El rojo se reserva para la acción principal y el foco.

- La tienda y la compatibilidad forman parte de la decisión comercial.

### Versión móvil

En móvil, el filtro se abre desde un botón “Filtrar” que informa cuántos filtros están activos. Cada resultado se convierte en una fila apilada: imagen, nombre, referencia, tienda, precio y acción. No se comprimen las columnas de escritorio.

---

## 16. Ejemplo React + TypeScript + Tailwind CSS v4

Ejemplo de una fila de catálogo. Es ilustrativo: conectar los datos a la API real y adaptar los enlaces y componentes a las rutas y convenciones del repositorio.

```tsx

type PartResultProps = {

  name: string;

  reference: string;

  price: string;

  store: string;

  location: string;

  compatibility: string;

  imageUrl?: string;

  verified: boolean;

  href: string;

};

export function PartResult({

  name,

  reference,

  price,

  store,

  location,

  compatibility,

  imageUrl,

  verified,

  href,

}: PartResultProps) {

  return (

    <article className="grid grid-cols-[76px_minmax(0,1fr)] gap-x-4 border-b border-[var(--ap-line)] py-5 sm:grid-cols-[104px_minmax(0,1fr)_180px] sm:items-center">

      <div className="row-span-2 flex aspect-square items-center justify-center overflow-hidden rounded-[var(--ap-radius-control)] bg-[var(--ap-paper)] sm:row-span-1">

        {imageUrl ? (

          <img

            src={imageUrl}

            alt={name}

            className="h-full w-full object-contain p-2"

          />

        ) : (

          <span className="px-2 text-center text-xs text-[var(--ap-muted)]">

            Imagen no disponible

          </span>

        )}

      </div>

      <div className="min-w-0">

        <p className="font-mono text-xs tracking-wide text-[var(--ap-muted)]">

          REF. {reference}

        </p>

        <h2 className="mt-1 text-base font-semibold leading-snug text-[var(--ap-ink)]">

          {name}

        </h2>

        <p className="mt-1 text-sm text-[var(--ap-muted)]">

          {compatibility}

        </p>

        <p className="mt-2 text-sm text-[var(--ap-ink)]">

          <span className="font-medium">{store}</span>

          {verified && (

            <span className="ml-2 text-xs font-semibold text-[var(--ap-success)]">

              Verificada

            </span>

          )}

          <span className="text-[var(--ap-muted)]"> · {location}</span>

        </p>

      </div>

      <div className="col-span-2 mt-3 flex items-center justify-between gap-3 sm:col-span-1 sm:mt-0 sm:flex-col sm:items-end">

        <p className="text-lg font-bold tabular-nums text-[var(--ap-ink)]">

          {price}

        </p>

        <a

          href={href}

          className="inline-flex min-h-11 items-center justify-center rounded-[var(--ap-radius-control)] bg-[var(--ap-red)] px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-[var(--ap-red-hover)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ap-red)]"

        >

          Ver producto

        </a>

      </div>

    </article>

  );

}

```

### Reglas para integrar el ejemplo

- Reutilizar el componente de botón existente si el repositorio ya tiene uno.

- No crear un sistema paralelo de componentes.

- Mostrar `verified` únicamente si la API confirma la verificación.

- Mostrar precio solo si existe un precio válido y vigente.

- La compatibilidad debe distinguir entre confirmada, parcial y pendiente.

- No mostrar “Disponible” si la API no confirma inventario.

- Añadir estados de carga, error y ausencia de imagen en el componente que consulta los datos.

---

## 17. Criterios de aceptación visual

Antes de aprobar una pantalla:

- [ ] La composición responde a una tarea concreta.

- [ ] La jerarquía se entiende sin depender del color.

- [ ] La pantalla se reconoce como Autopartco por su estructura y tratamiento de datos.

- [ ] El rojo tiene un propósito y no domina la interfaz.

- [ ] Las referencias y datos técnicos son fáciles de localizar.

- [ ] Se eligieron filas, listas, tablas o tarjetas según el contenido.

- [ ] No se agregaron gradientes, sombras o iconos por costumbre.

- [ ] Los estados de carga, vacío, error y éxito están resueltos.

- [ ] El diseño móvil reorganiza la información correctamente.

- [ ] Los controles funcionan con teclado y muestran foco.

- [ ] No existen datos comerciales inventados.

- [ ] Se reutilizaron los componentes y tokens existentes.

Si una pantalla cumple la lista pero sigue pareciendo una plantilla intercambiable, no está terminada: revisar composición, ritmo, tipografía y densidad.

---

## 18. Protocolo obligatorio para agentes de IA

Antes de modificar una interfaz:

1. Leer este documento.

2. Inspeccionar la pantalla existente, los componentes y los tokens.

3. Identificar el usuario, la tarea principal y los datos disponibles.

4. Proponer una composición adecuada a esa tarea.

5. Reutilizar componentes existentes; no duplicar variantes.

6. Implementar los estados reales y la versión móvil.

7. Verificar accesibilidad, navegación y comportamiento.

8. Revisar visualmente la pantalla en al menos un viewport de escritorio y uno móvil.

9. No modificar pantallas fuera del alcance solicitado.

10. Informar archivos modificados, decisiones visuales y limitaciones.

### Prompt para agentes

> Implementa la pantalla solicitada siguiendo `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md`. Primero inspecciona el repositorio, los componentes existentes y los tokens actuales. No empieces desde una plantilla genérica. Diseña según la tarea real y los datos del dominio de autopartes. Aplica la composición editorial de catálogo, el tratamiento técnico de referencias y la paleta de Autopartco solo donde corresponda. Evita gradientes decorativos, tarjetas innecesarias, sombras generalizadas, radios exagerados, iconos decorativos y dashboards con KPIs automáticos. Reutiliza la arquitectura actual de React, TypeScript, Tailwind CSS v4 y Base UI/shadcn. No inventes datos. Implementa responsive, accesibilidad y estados de interfaz. Al finalizar, revisa escritorio y móvil y explica las decisiones tomadas.

---

## 19. Gobernanza y evolución

Esta guía establece una dirección visual, pero no reemplaza la validación con la marca real ni con usuarios.

Antes de declarar el sistema definitivo:

1. Comparar el rojo y el grafito con el logotipo oficial.

2. Probar esta dirección en tres pantallas: página principal, catálogo y portal de tiendas.

3. Revisar la legibilidad de referencias, precios y estados en móvil.

4. Validar con usuarios si encuentran y comparan repuestos con facilidad.

5. Centralizar los tokens en una única fuente.

6. Documentar nuevas variantes solo cuando exista una necesidad repetida.

7. Actualizar este archivo cuando una decisión cambie; no crear guías paralelas.

**Resultado buscado:** una plataforma comercial especializada que combina claridad de catálogo, precisión técnica y confianza corporativa. Autopartco debe reconocerse por cómo organiza la información y facilita encontrar el repuesto correcto, no por acumular efectos visuales.

## 20. Sistema de visualización de datos (Data Visualization)

### 20.1. Propósito

Las visualizaciones de Autopartco deben transformar datos operativos y comerciales en información útil para tomar decisiones.

No se busca llenar las interfaces de gráficos ni reproducir los dashboards genéricos habituales de las plantillas SaaS. Cada visualización debe responder a una pregunta concreta, aportar información que no sea evidente mediante una lectura tabular y permitir identificar patrones, anomalías, oportunidades o problemas.

La identidad visual debe mantener el lenguaje industrial, corporativo y comercial de Autopartco.

**Criterio principal:**

Una visualización debe parecer una herramienta de análisis empresarial desarrollada para el sector automotriz, no un gráfico decorativo generado automáticamente.

### 20.2. Principios de diseño

1. **Datos antes que decoración:** ningún gráfico existe únicamente para llenar espacio.

2. **Visualización adecuada:** seleccionar la representación según la naturaleza de los datos y la pregunta que se quiere responder.

3. **Densidad informativa controlada:** aprovechar el espacio sin saturar la lectura.

4. **Contexto obligatorio:** indicar período, unidad, fuente y alcance de los datos cuando corresponda.

5. **Jerarquía analítica:** destacar hallazgos relevantes, no utilizar colores llamativos indiscriminadamente.

6. **Interactividad funcional:** permitir explorar, filtrar, comparar y consultar datos cuando aporte valor.

7. **Integridad estadística:** no manipular escalas, proporciones ni rangos para exagerar resultados.

8. **Consistencia visual:** las visualizaciones deben compartir una gramática gráfica, pero no necesariamente el mismo formato.

9. **Especialización:** utilizar visualizaciones avanzadas cuando la complejidad de los datos lo justifique.

10. **Trazabilidad:** facilitar la consulta de los datos que respaldan una conclusión.

### 20.3. Identidad visual de las visualizaciones

Las gráficas deben utilizar una estética técnica y editorial, con inspiración en herramientas profesionales de inteligencia de negocios, sistemas logísticos y plataformas de análisis industrial.

#### Paleta cromática

Utilizar los tokens globales definidos en este documento.

| Uso | Color |
|---|---|
| Serie principal | Rojo Autopartco |
| Serie secundaria | Grafito |
| Series adicionales | Gris acero y tonos neutros |
| Datos históricos | Gris medio |
| Referencias y objetivos | Gris o línea discontinua |
| Resultado positivo | Verde semántico |
| Advertencia | Ámbar semántico |
| Resultado negativo | Rojo semántico |
| Información complementaria | Azul informativo |

Reglas:

- El rojo Autopartco debe tener una función semántica y no aparecer en todos los elementos.

- No utilizar una paleta arcoíris para representar categorías.

- No asignar colores aleatorios a las series en cada renderizado.

- Mantener colores consistentes para una misma categoría o estado.

- Utilizar variaciones de luminosidad, saturación y patrones para diferenciar series cuando sea necesario.

- Garantizar contraste suficiente entre elementos y fondo.

- No depender exclusivamente del color para comunicar diferencias.

### 20.4. Tipos de visualización permitidos

No existe una colección obligatoria de gráficos que deba aparecer en todas las interfaces. Se seleccionarán según el problema analítico.

#### A. Visualizaciones temporales

Para analizar cambios, tendencias y comportamientos a través del tiempo.

Tipos:

- Gráficos de líneas.

- Gráficos de áreas acumuladas.

- Series temporales comparativas.

- Gráficos de variación porcentual.

- Sparklines integradas en tablas.

- Gráficos de calendario.

- Gráficos de eventos y actividad.

Aplicaciones:

- Evolución de ventas.

- Comportamiento de pedidos.

- Crecimiento de tiendas registradas.

- Consultas de productos.

- Actividad de usuarios.

- Variaciones de inventario.

- Comparación entre períodos comerciales.

No utilizar gráficos de líneas cuando los datos no tengan un orden temporal real.

#### B. Visualizaciones comparativas

Para identificar diferencias entre grupos, categorías, períodos o establecimientos.

Tipos:

- Barras horizontales y verticales.

- Barras agrupadas.

- Barras apiladas.

- Dumbbell charts.

- Slope charts.

- Bullet charts.

- Dot plots.

- Lollipop charts.

Aplicaciones:

- Ventas por tienda.

- Productos más consultados.

- Categorías con mayor movimiento.

- Comparación de inventarios.

- Rendimiento por sucursal.

- Cumplimiento de objetivos.

- Comparación de precios.

Evitar utilizar gráficos circulares para comparar muchas categorías. Para esos casos, priorizar barras ordenadas.

#### C. Mapas de calor (Heatmaps)

Los mapas de calor son una visualización especialmente relevante para Autopartco porque permiten detectar concentraciones, patrones y comportamientos que no se identifican fácilmente mediante tablas.

Tipos:

**1. Heatmap geográfico**

Representa la concentración de actividad en una zona geográfica.

Aplicaciones:

- Concentración de tiendas de autopartes en Colombia.

- Distribución de establecimientos por ciudad.

- Demanda de repuestos por región.

- Zonas con mayor cantidad de búsquedas.

- Regiones con poca cobertura comercial.

- Distribución de pedidos por ubicación.

- Identificación de oportunidades de expansión.

Puede utilizar mapas de Colombia, departamentos, municipios o mapas de densidad geográfica.

**2. Heatmap de calendario**

Representa la intensidad de actividad por día.

Aplicaciones:

- Consultas de productos.

- Pedidos recibidos.

- Ventas diarias.

- Actividad de las tiendas.

- Interacciones de los clientes.

**3. Heatmap matricial**

Representa la intensidad de una variable mediante una matriz de filas y columnas.

Aplicaciones:

- Demanda de categorías por departamento.

- Búsquedas por día de la semana y hora.

- Compatibilidad entre categorías de vehículos y repuestos.

- Rendimiento de tiendas por período.

- Distribución de consultas por marca y categoría.

**4. Heatmap de correlación**

Permite identificar relaciones estadísticas entre variables.

Aplicaciones:

- Relación entre visitas y ventas.

- Relación entre disponibilidad e intención de compra.

- Relación entre precio y demanda.

- Relación entre tiempos de respuesta y conversión.

No presentar correlación como causalidad.

#### D. Visualizaciones geográficas

Los mapas deben ser tratados como herramientas analíticas y no únicamente como elementos de ubicación.

Tipos:

- Mapas coropléticos por departamento o municipio.

- Mapas de puntos.

- Mapas de densidad.

- Mapas de agrupación de marcadores.

- Mapas de burbujas proporcionales.

- Mapas de flujos entre ubicaciones.

- Mapas de cobertura territorial.

- Mapas de rutas comerciales.

Aplicaciones:

- Cobertura nacional de tiendas.

- Distribución de establecimientos verificados.

- Demanda geográfica de repuestos.

- Zonas sin cobertura.

- Flujo de pedidos entre ciudades.

- Concentración de clientes.

- Identificación de mercados potenciales.

Reglas:

- Utilizar límites geográficos reales.

- Evitar mapas decorativos sin información verificable.

- Incorporar leyendas, escalas y filtros.

- Mostrar información contextual al seleccionar una región.

- Permitir cambiar el nivel geográfico cuando la fuente de datos lo permita.

- No representar coordenadas aproximadas como ubicaciones exactas.

- No confundir cantidad de establecimientos con volumen de demanda.

#### E. Visualizaciones de distribución

Para estudiar cómo se distribuyen los valores y detectar concentraciones o valores atípicos.

Tipos:

- Histogramas.

- Box plots.

- Violin plots.

- Density plots.

- ECDF (función de distribución acumulada).

- Ridgeline plots.

Aplicaciones:

- Distribución de precios de repuestos.

- Variación de tiempos de entrega.

- Distribución de inventario.

- Diferencias de precios entre establecimientos.

- Distribución de tiempos de respuesta.

- Análisis de demanda por categoría.

Utilizar estas representaciones cuando exista suficiente cantidad y calidad de datos.

#### F. Visualizaciones de relaciones

Para explorar relaciones entre diferentes variables.

Tipos:

- Scatter plots.

- Bubble charts.

- Matrices de correlación.

- Pair plots.

- Network graphs.

- Sankey diagrams.

- Alluvial diagrams.

Aplicaciones:

- Relación entre precio y demanda.

- Relación entre disponibilidad y conversiones.

- Flujo entre búsquedas, consultas y pedidos.

- Relación entre tiendas, productos y categorías.

- Comportamiento de los clientes durante un proceso de compra.

Los diagramas de red deben reservarse para relaciones complejas que no puedan explicarse de forma más sencilla.

#### G. Visualizaciones de composición y flujo

Para entender cómo se distribuyen los elementos dentro de un conjunto o cómo se desarrolla un proceso.

Tipos:

- Treemaps.

- Sunburst charts.

- Sankey diagrams.

- Funnel charts.

- Waterfall charts.

- Marimekko charts.

Aplicaciones:

- Participación de categorías en el catálogo.

- Distribución de productos por marca.

- Flujo de conversión de usuarios.

- Descomposición de ingresos.

- Seguimiento de pedidos por etapas.

- Distribución del inventario por categoría y sucursal.

No utilizar diagramas Sankey o Treemap únicamente por su apariencia. Deben resolver una necesidad analítica real.

#### H. Visualizaciones operativas

Representan el estado y comportamiento de procesos empresariales.

Tipos:

- Bullet charts.

- Gauges cuando exista un objetivo o umbral real.

- Indicadores de capacidad.

- Barras de progreso.

- Diagramas de estado.

- Líneas de tiempo.

- Tableros de actividad.

- Matrices de cumplimiento.

Aplicaciones:

- Estado de pedidos.

- Nivel de inventario.

- Cumplimiento de objetivos comerciales.

- Estado de verificación de tiendas.

- Capacidad de almacenamiento.

- Tiempo promedio de procesamiento.

- Estado de sincronización con sistemas externos.

Evitar medidores circulares para métricas que se entienden mejor con números o barras.

### 20.5. Aplicación por módulo de Autopartco

Las visualizaciones deben responder a las necesidades específicas de cada módulo.

#### Portal de tiendas

Visualizaciones posibles:

- Evolución de ventas.

- Pedidos por estado.

- Productos más vendidos.

- Productos con mayor número de consultas.

- Rotación de inventario.

- Productos con baja disponibilidad.

- Comparación de ventas por sucursal.

- Calendario de actividad comercial.

- Relación entre visitas y conversiones.

#### Administración de Autopartco

Visualizaciones posibles:

- Distribución geográfica de tiendas.

- Heatmap de cobertura comercial.

- Crecimiento de establecimientos registrados.

- Tiendas pendientes de verificación.

- Distribución de usuarios por región.

- Actividad de la plataforma.

- Categorías más consultadas.

- Evolución de pedidos.

- Análisis de incidencias y reportes.

- Distribución de establecimientos por estado.

#### Analítica comercial

Visualizaciones posibles:

- Demanda de repuestos por región.

- Categorías con mayor crecimiento.

- Comparación de precios.

- Productos más buscados.

- Distribución de la oferta por ciudad.

- Zonas con baja disponibilidad.

- Embudos de conversión.

- Relación entre disponibilidad y demanda.

#### Catálogo público

Las visualizaciones deben utilizarse únicamente cuando ayuden al usuario a encontrar o comparar productos.

Ejemplos:

- Distribución de precios de una categoría.

- Comparación de rangos de precios.

- Disponibilidad por ubicación.

- Mapa de tiendas cercanas.

- Comparación de características técnicas.

No convertir el catálogo público en un dashboard administrativo.

### 20.6. Composición de los paneles analíticos

Los paneles deben organizarse según las preguntas que el usuario necesita responder.

No utilizar automáticamente la estructura:

- Cuatro tarjetas KPI.

- Dos gráficos de líneas.

- Dos gráficos circulares.

- Una tabla al final.

Esta composición es un patrón genérico y no debe ser la plantilla predeterminada.

En su lugar:

1. Identificar la pregunta principal del panel.

2. Seleccionar las métricas que permiten responderla.

3. Elegir la visualización que mejor representa cada conjunto de datos.

4. Organizar los elementos según su importancia.

5. Incorporar tablas cuando sea necesario consultar valores exactos.

6. Evitar visualizaciones redundantes.

Los paneles pueden utilizar composiciones asimétricas, secciones editoriales, vistas geográficas amplias, matrices compactas y tablas integradas.

La composición debe responder a la complejidad de la información, no a una distribución de tarjetas predeterminada.

### 20.7. Componentes de una visualización

Cuando sean necesarios, los gráficos deben incluir:

- Título descriptivo.

- Descripción breve de la métrica.

- Período analizado.

- Unidad de medida.

- Leyenda.

- Ejes y escalas.

- Etiquetas.

- Valores exactos mediante tooltip.

- Comparación con períodos anteriores.

- Referencias u objetivos.

- Fuente de los datos.

- Fecha de última actualización.

- Controles de filtrado.

- Acciones para consultar o exportar los datos.

No todos los gráficos necesitan todos estos elementos. Incluir únicamente los que faciliten su interpretación.

### 20.8. Interactividad

La interactividad debe tener una finalidad analítica.

Comportamientos permitidos:

- Selección de períodos.

- Comparación entre intervalos.

- Filtrado por categoría, tienda, sucursal o ubicación.

- Zoom y desplazamiento cuando la visualización lo requiera.

- Selección de regiones geográficas.

- Drill-down entre niveles de información.

- Consulta de detalles al seleccionar un elemento.

- Tooltips con datos adicionales.

- Alternancia entre vistas cuando aporte valor.

- Exportación de información cuando corresponda.

Ejemplo de drill-down geográfico:

Colombia → Departamento → Municipio → Tiendas.

La navegación debe conservar los filtros activos y permitir regresar al nivel anterior.

### 20.9. Estados de visualización

Todas las visualizaciones deben contemplar:

**Loading**

- Utilizar skeletons proporcionales al tipo de gráfico.

- Evitar mostrar datos ficticios mientras se cargan datos reales.

**Empty state**

- Explicar por qué no existen datos.

- Indicar qué filtros pueden estar limitando los resultados.

- Evitar gráficos vacíos que parezcan errores.

**Error**

- Mostrar un mensaje claro.

- Permitir reintentar la consulta cuando sea posible.

- No presentar valores anteriores como si fueran actuales.

**Partial data**

- Identificar los períodos o categorías con información incompleta.

- Evitar conclusiones que no estén respaldadas por todos los datos.

**Stale data**

- Indicar la fecha de actualización.

- Diferenciar información histórica de información en tiempo real.

### 20.10. Reglas de integridad y precisión

- No truncar ejes para exagerar diferencias sin indicarlo.

- No utilizar escalas incompatibles entre series sin advertencia.

- No mezclar porcentajes con cantidades absolutas sin diferenciarlos.

- No comparar períodos de distinta duración como si fueran equivalentes.

- No ocultar categorías que cambien significativamente la interpretación.

- No presentar datos simulados como datos reales.

- No utilizar proyecciones sin identificar que son estimaciones.

- No confundir correlación con causalidad.

- No utilizar mapas de densidad sin explicar qué representa la intensidad.

- Mostrar el tamaño de la muestra cuando sea relevante.

- Considerar datos faltantes, duplicados y valores atípicos.

### 20.11. Accesibilidad y diseño responsive

- Todas las visualizaciones deben tener alternativas accesibles.

- Utilizar etiquetas y leyendas legibles.

- No depender exclusivamente del color.

- Permitir navegación por teclado en controles interactivos.

- Incorporar descripciones textuales cuando sean necesarias.

- Ofrecer tablas alternativas para visualizaciones complejas.

- Adaptar las dimensiones al dispositivo.

- Evitar reducir gráficos complejos hasta hacer ilegibles sus etiquetas.

- En móviles, permitir desplazamiento horizontal cuando la naturaleza de los datos lo requiera.

- Mantener controles táctiles con dimensiones adecuadas.

### 20.12. Tecnologías recomendadas

La implementación debe respetar el stack actual de Autopartco.

**Recharts**

- Gráficos de líneas.

- Barras.

- Áreas.

- Composición.

- Visualizaciones administrativas convencionales.

**Apache ECharts**

- Heatmaps.

- Gráficos de distribución.

- Visualizaciones avanzadas.

- Sankey.

- Scatter plots.

- Treemaps.

- Calendarios de actividad.

- Visualizaciones de gran volumen de datos.

**Apache ECharts con extensiones geográficas**

- Mapas coropléticos.

- Mapas de densidad.

- Visualización territorial.

- Mapas interactivos con capas de datos.

**MapLibre GL JS**

- Mapas geográficos interactivos.

- Capas de puntos.

- Agrupación de marcadores.

- Visualización de densidad.

- Información territorial.

**D3.js**

- Visualizaciones personalizadas.

- Gráficos especializados.

- Representaciones que requieran control preciso de geometría y comportamiento.

No incorporar todas estas tecnologías por defecto. Cada dependencia debe justificarse por una necesidad concreta.

Preferencia inicial:

- Recharts para gráficos administrativos habituales.

- Apache ECharts para analítica avanzada.

- MapLibre GL JS para cartografía interactiva.

- D3.js únicamente cuando las soluciones anteriores no permitan implementar adecuadamente la visualización requerida.

### 20.13. Reglas de implementación

- Crear componentes reutilizables por tipo de visualización.

- Separar la obtención y transformación de datos de la representación gráfica.

- Utilizar TypeScript para definir estructuras de datos.

- Evitar cálculos estadísticos complejos directamente dentro de los componentes visuales.

- Mantener las configuraciones de colores centralizadas.

- No duplicar lógica de filtros entre gráficos.

- Implementar estados de carga y error.

- Evitar renderizados innecesarios en conjuntos de datos grandes.

- Utilizar agregaciones y paginación cuando corresponda.

- Aplicar técnicas de optimización para mapas y grandes volúmenes de puntos.

- Mantener consistencia entre los filtros de un mismo panel.

- No introducir dependencias para resolver problemas que pueden solucionarse con componentes existentes.

### 20.14. Antipatrones prohibidos

No utilizar por defecto:

- Dashboards con cuatro KPI idénticos en la parte superior.

- Gráficos de dona para cualquier distribución.

- Gráficos circulares con numerosas categorías.

- Gradientes multicolor en todas las series.

- Gráficos con colores aleatorios.

- Sombras y contenedores decorativos alrededor de cada gráfico.

- Medidores circulares sin objetivos reales.

- Gráficos sin unidades ni contexto.

- Mapas decorativos que no representen datos reales.

- Heatmaps utilizados únicamente por su apariencia.

- Animaciones constantes.

- Efectos tridimensionales innecesarios.

- Leyendas redundantes.

- Indicadores que repitan información ya visible.

- Datos ficticios en interfaces de producción.

- Visualizaciones complejas que no aporten información adicional.

### 20.15. Criterio de aceptación

Una visualización se considera aprobada cuando:

1. Responde a una pregunta empresarial concreta.

2. Utiliza una representación adecuada a los datos.

3. Mantiene la identidad visual de Autopartco.

4. Es comprensible sin explicaciones externas.

5. Presenta datos y unidades correctamente.

6. No induce a interpretaciones engañosas.

7. Funciona con datos reales.

8. Contempla estados de carga, error y ausencia de información.

9. Es responsive y accesible.

10. Aporta más valor que una representación tabular sencilla.

### 20.16. Instrucción obligatoria para agentes de IA

Antes de implementar cualquier panel analítico o visualización:

1. Identificar qué decisión debe facilitar.

2. Determinar qué datos existen realmente.

3. Evaluar la naturaleza y volumen de los datos.

4. Seleccionar el tipo de visualización más apropiado.

5. Justificar el uso de visualizaciones avanzadas.

6. Consultar los componentes y tokens del sistema de diseño.

7. Evitar estructuras genéricas de dashboards.

8. Implementar estados y comportamiento interactivo.

9. Verificar precisión, accesibilidad y responsive.

10. Comprobar que la visualización no sea simplemente decorativa.

**Regla final:** Autopartco no utiliza gráficos para parecer una plataforma tecnológica avanzada. Utiliza visualizaciones para convertir información del sector automotriz en conocimiento útil para tiendas, clientes y administradores.

