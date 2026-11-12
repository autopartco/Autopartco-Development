# Plan: reseñas y calificaciones (v2.1.0)

## Objetivo y alcance

- Quien compró puede calificar con estrellas (1–5), puntuar categorías y comentar:
  - el **almacén**, por pedido entregado;
  - cada **producto** que recibió.
- Las calificaciones se ven y llaman la atención:
  - estrellas y conteo en tarjetas, ficha de producto y perfil de almacén;
  - insignias destacadas («Entrega rápida», «Encaja perfecto»);
  - resumen con distribución y lista de comentarios.
- El almacén responde las reseñas desde el portal. El admin oculta las abusivas.
- Se invita a calificar al entregar un pedido (pantalla y correo).
- **Fuera de alcance:** fotos en reseñas, votos de «¿te sirvió?», denuncias de usuarios, preguntas y respuestas.

## Estado actual

- **Backend:** ya existen `store_reviews` (`rating`, `comment`, una por pedido y almacén) y `StoreReviewService`, con elegibilidad por venta `DELIVERED`.
- **Frontend:** solo lista las reseñas de almacén y no tiene formulario. Hay 0 reseñas, aunque existen 1.696 ventas entregadas.
- **Productos:** no tienen reseñas.
- **Índice:** `search_listings.store_rating` ya se calcula con el promedio de las reseñas de almacén.

## Categorías de puntuación (definidas en código)

Cada categoría es opcional y se puntúa de 1 a 5. La calificación general (1–5) es obligatoria.

| Almacén (`StoreAspect`) | Etiqueta | Insignia si promedio ≥ 4,3 |
|---|---|---|
| `SERVICE` | Atención | «Excelente atención» |
| `SPEED` | Rapidez de entrega | «Entrega rápida» |
| `QUALITY` | Calidad de los productos | «Productos de calidad» |
| `PRICE` | Precio justo | «Buenos precios» |
| `ACCURACY` | Pedido completo y correcto | «Pedidos exactos» |

| Producto (`ProductAspect`) | Etiqueta | Insignia si promedio ≥ 4,3 |
|---|---|---|
| `QUALITY` | Calidad | «Muy buena calidad» |
| `FIT` | Encajó en mi vehículo | «Encaja perfecto» |
| `AS_DESCRIBED` | Igual a la descripción | «Tal como se describe» |
| `VALUE` | Vale lo que cuesta | «Gran relación precio-calidad» |
| `INSTALL` | Fácil de instalar | «Fácil de instalar» |

- **Insignias:**
  - aparecen con ≥ 3 calificaciones de esa categoría;
  - se muestran como máximo 3, ordenadas por promedio;
  - el umbral y el mínimo son constantes en código.
- **Etiquetas:** los textos viven en el frontend. El backend envía solo el código.

## Datos (Supabase, sin archivos SQL en el repo)

```sql
-- Almacén: categorías, respuesta y moderación
create table store_review_aspects (review_id uuid references store_reviews(id) on delete cascade,
  aspect varchar(20) not null check (aspect in ('SERVICE','SPEED','QUALITY','PRICE','ACCURACY')),
  rating smallint not null check (rating between 1 and 5), primary key (review_id, aspect));
alter table store_reviews add column reply text check (char_length(reply) <= 1000),
  add column replied_at timestamptz, add column replied_by uuid references accounts(id),
  add column hidden_at timestamptz, add column hidden_reason varchar(200);

-- Producto
create table product_reviews (id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id), store_id uuid not null references store(id),
  person_id uuid not null references persons(id), order_id uuid not null references orders(id),
  rating smallint not null check (rating between 1 and 5),
  comment text check (char_length(comment) <= 1000),
  reply text check (char_length(reply) <= 1000), replied_at timestamptz, replied_by uuid references accounts(id),
  hidden_at timestamptz, hidden_reason varchar(200),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  deleted_at timestamptz, version bigint not null default 0);
create unique index uq_pr_person_product on product_reviews (person_id, product_id) where deleted_at is null;
create index ix_pr_product_created on product_reviews (product_id, created_at desc) where deleted_at is null and hidden_at is null;
create index ix_pr_store on product_reviews (store_id, created_at desc);
create table product_review_aspects (review_id uuid references product_reviews(id) on delete cascade,
  aspect varchar(20) not null check (aspect in ('QUALITY','FIT','AS_DESCRIBED','VALUE','INSTALL')),
  rating smallint not null check (rating between 1 and 5), primary key (review_id, aspect));

-- Índice de búsqueda
alter table search_listings add column product_rating numeric(2,1), add column product_review_count int not null default 0;
-- RLS habilitado en las tablas nuevas, como el resto.
```

## Reglas

- **Elegibilidad de producto:**
  - el pedido es de la persona;
  - tiene un `order_details.product_id` del producto;
  - su venta está `DELIVERED`.
  - Una reseña por persona y producto (la más reciente compra la habilita).
  - Se edita hasta 30 días después de crearla.
- **Elegibilidad de almacén:** la actual, una reseña por pedido y almacén, con la misma regla de 30 días para editar.
- **Comentario:** opcional, hasta 1.000 caracteres, recortado de espacios. Sin URL: se rechaza con `REVIEW_LINKS_NOT_ALLOWED`.
- **Autor público:** nombre más inicial del apellido («Luis T.») y la insignia «Compra verificada». Nunca correo ni apellido completo (Ley 1581).
- **Respuesta del almacén:** una por reseña, editable. La escribe un colaborador con `STORE_UPDATE`.
- **Reseñas ocultas:** no cuentan en promedios ni insignias. El autor sigue viendo la suya.
- **Límites:**
  - escribir: 10 por hora por cuenta;
  - leer: el límite por IP actual.

## Contratos

**Público**

- `GET /api/v1/marketplace/products/{productId}/reviews/summary` →
  `{ average, count, distribution: {"1":n,…,"5":n}, aspects: [{aspect, average, count}], highlights: [aspect] }`
- `GET /api/v1/marketplace/products/{productId}/reviews?page&size&sort=recent|highest|lowest&stars&withComment` →
  `PageResponse<{ id, rating, comment, aspects:{ASPECT:n}, author, verified:true, createdAt, updatedAt, reply:{text, repliedAt}|null }>`
- `GET /api/v1/marketplace/stores/{storeId}/reviews/summary`: la misma forma con `StoreAspect`.
- `GET /api/v1/marketplace/stores/{storeId}/reviews`: amplía la respuesta actual con `aspects`, `author`, `verified` y `reply`.
- `ProductResponse` añade `rating` (1 decimal o null) y `reviewCount`. `/search`, `/home` y `/ads` los traen desde `search_listings`.
- `/search` añade `sort=product_rating` y el filtro `minProductRating`.

**Comprador (con sesión)**

- `GET /api/v1/marketplace/products/{productId}/reviews/eligibility` → `{ eligibleOrderIds, existingReviewId|null, editableUntil|null }`
- `POST /api/v1/marketplace/products/{productId}/reviews` `{ orderId, rating, comment?, aspects? }` → 201
  - 403 `REVIEW_NOT_ELIGIBLE`
  - 409 `REVIEW_ALREADY_EXISTS`
  - 400 `REVIEW_ASPECT_INVALID`, `REVIEW_LINKS_NOT_ALLOWED`
- `PUT` y `DELETE /api/v1/marketplace/products/{productId}/reviews/{reviewId}`: solo el autor. Editar solo dentro de los 30 días (409 `REVIEW_EDIT_WINDOW_CLOSED`).
- Almacén: `POST /marketplace/stores/{storeId}/reviews` acepta `aspects`. Se añaden `PUT` y `DELETE` con las mismas reglas.
- `GET /api/v1/account/reviews/pending` → `[{ orderId, orderNumber, deliveredAt, store:{id,name,reviewed}, products:[{productId,name,image,reviewed}] }]`, con los pedidos entregados de los últimos 90 días que tienen algo sin calificar.

**Portal (`STORE_UPDATE` para responder, `STORE_READ` para leer)**

- `GET /api/v1/stores/{storeId}/reviews?type=store|product&replied=true|false&stars&page` → reseñas del almacén y de sus productos, con `productName`.
- `PUT /api/v1/stores/{storeId}/reviews/{type}/{reviewId}/reply { text }`
- `DELETE /api/v1/stores/{storeId}/reviews/{type}/{reviewId}/reply`
- `GET /api/v1/stores/{storeId}/reviews/summary`: el resumen del almacén más el top y el bottom de productos por calificación.

**Admin (ROOT, con auditoría)**

- `POST /api/v1/admin/reviews/{type}/{reviewId}/hide { reason }`
- `POST /api/v1/admin/reviews/{type}/{reviewId}/unhide`
- `GET /api/v1/admin/reviews?hidden&type`

**Eventos**

- Al crear, editar, borrar u ocultar una reseña: `ListingsChanged` del producto o del almacén (reindexa `product_rating` y `store_rating`) y se invalida la caché `store:profile`.
- `SaleDelivered`, existente o nuevo: correo «¿Cómo te fue con tu pedido?» con enlace a `/cuenta/calificar?pedido=…`. Se envía una sola vez por pedido, después del commit.

## Interfaz (sigue `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md`)

- **Tarjetas** de búsqueda, inicio, anuncios y relacionados: ★ 4,7 (128) junto al precio. Sin reseñas, no se muestra nada (nunca «0 estrellas»).
- **Ficha de producto:**
  - Bajo el título: estrellas, promedio, «128 opiniones» (ancla a la sección) y hasta 3 insignias.
  - La sección «Opiniones de compradores» tiene:
    - el promedio grande;
    - barras de distribución por estrellas, que funcionan como filtro;
    - barras por categoría;
    - el orden;
    - la lista con «Compra verificada» y la respuesta del almacén destacada.
  - Si el usuario puede calificar, aparece el CTA «Califica este producto».
- **Perfil de almacén:** lo mismo en la cabecera (estrellas e insignias junto al nombre) y la sección «Lo que dicen sus clientes».
- **Formulario** en un diálogo, que en móvil es una hoja inferior:
  - estrellas grandes accesibles (radio group con teclado);
  - categorías opcionales en filas de estrellas pequeñas;
  - comentario con contador.
  - Textos orientados al cliente, sin jerga.
- **Comprador:**
  - en el detalle del pedido entregado, «Calificar almacén» y «Calificar» por producto;
  - página `/cuenta/calificar` con lo pendiente;
  - aviso en «Mis pedidos».
- **Portal:**
  - página «Reseñas» con filtros (sin responder, estrellas, tipo) y responder en línea;
  - en el panel, una tarjeta con el promedio y las reseñas sin responder.
- **Admin:** lista con ocultar o mostrar y motivo.

## Decisiones

- **Categorías con estrellas (1–5) y no etiquetas sí/no:** dan promedios comparables y alimentan las insignias. Se descartaron las etiquetas libres, porque no se pueden agregar.
- **Categorías en código con `check` en la BD**, y no una tabla de catálogo: son pocas y estables, y cambiarlas requiere también textos de frontend.
- **Una reseña por persona y producto:** evita inflar con compras repetidas. Se descartó una por línea de pedido.
- **Promedios sin caché propia:** el resumen se calcula con consulta indexada y pasa por la caché `store:profile` o por una nueva `product:reviews` de 5 min. En las listas se usa la columna desnormalizada de `search_listings`.
- **Solo compra verificada:** sin reseñas anónimas. Es la mejor defensa contra reseñas falsas sin moderación manual.

## Tareas

### T1: Esquema de reseñas ✅ (2026-11-11, migración `reviews_aspects_product_reviews`; más índices `ix_pr_order` y `ix_*_replied_by`, y checks de respuesta con texto y fecha a la vez)
- **Modelo:** Haiku · **Agente:** database
- **Contexto:** el SQL de «Datos» se aplica en Supabase `autopartco-dev` y no se escribe en el repo. **Requiere autorización.**
- **Aceptación:** tablas y columnas creadas, con RLS habilitado y sin alertas nuevas en los advisors.

### T2: Reseñas en el backend ✅ (2026-11-11; el almacén conserva sus errores actuales 409 `REVIEW_NOT_ALLOWED` y 404 `ORDER_NOT_FOUND`; el equipo del almacén no puede calificar; resumen sin caché propia)
- **Modelo:** Opus · **Agente:** backend
- **Archivos:**
  - `marketplace/reviews/*`: `ProductReviewService`, `ProductReviewController`, `ReviewSummary`, `StoreAspect`, `ProductAspect` y la ampliación de `StoreReviewService`;
  - `core/identity` o `marketplace/account` para `pending`;
  - `SearchIndexer` (`product_rating`, `product_review_count`);
  - `ProductResponse`, `SearchSql` (orden y filtro);
  - `WebSecurityConfig` (los GET públicos nuevos);
  - pruebas.
- **Contexto:** contratos de comprador y públicos. Elegibilidad por `order_details` y `sales` `DELIVERED`. Autor público como nombre e inicial. Publica `ListingsChanged`.
- **Aceptación:**
  - `-Dtest=ProductReviewServiceTest,StoreReviewServiceTest,ReviewSummaryTest,PendingReviewsTest,SearchServiceTest,WebSecurityConfigTest,ModularityTests` en verde.
  - Sin compra entregada → 403.
  - Una segunda reseña → 409.
  - Editar después de 30 días → 409.
  - Una reseña oculta no cuenta en el promedio.
  - Las insignias salen solo con ≥ 3 calificaciones y promedio ≥ 4,3.
  - El autor nunca incluye apellido completo ni correo.

### T3: Respuestas, moderación y correo ✅ (2026-11-11; puertos en `core.reviews`, evento `SaleDelivered`, idempotencia del correo en `audit_logs`)
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:**
  - `storesuite/reviews/*` (portal);
  - `operations/reviews/*` (admin con auditoría);
  - listener de `SaleDelivered` y plantilla en `EmailTemplate`;
  - pruebas.
- **Contexto:** contratos de portal, admin y eventos. Depende de T2.
- **Aceptación:**
  - `-Dtest=StoreReviewsPortalTest,AdminReviewsTest,ReviewInvitationTest,ModularityTests,WebSecurityConfigTest` en verde.
  - Responder exige `STORE_UPDATE`, y una reseña de otro almacén responde 404.
  - El correo se envía una sola vez por pedido.

### T4: Calificaciones visibles en el frontend ✅ (2026-11-11, sin prueba en navegador)
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:**
  - componentes `features/marketplace/components/reviews/*`: `StarRating`, `RatingSummary`, `AspectBars`, `ReviewHighlights`, `ReviewList`;
  - las tarjetas de producto;
  - `product-page.tsx` y `store-page.tsx`;
  - `api/*`.
- **Contexto:** contratos públicos e «Interfaz». Depende de T2.
- **Aceptación:**
  - build y lint en verde;
  - las tarjetas muestran ★ y conteo solo si hay reseñas;
  - la ficha y el perfil muestran resumen, insignias y lista filtrable;
  - estrellas con `aria-label` («4,7 de 5»);
  - sin scroll horizontal a 360 px.

### T5: Calificar desde el pedido ✅ (2026-11-11; editar la reseña de almacén y precargar el formulario quedan para T10)
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:**
  - `ReviewDialog` (estrellas accesibles, categorías, comentario);
  - `order-detail-page.tsx` y `orders-page.tsx`;
  - la nueva `/cuenta/calificar`.
- **Contexto:** contratos de comprador. Depende de T2 y de los componentes de T4.
- **Aceptación:**
  - build y lint en verde;
  - el formulario se maneja con teclado;
  - los errores `REVIEW_*` se muestran con texto claro;
  - después de calificar desaparece de los pendientes.

### T6: Reseñas en el portal y el admin ✅ (2026-11-11)
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `features/portal/pages/reviews-page.tsx`, la tarjeta del panel, la navegación y `features/admin/pages/reviews-page.tsx`.
- **Contexto:** contratos de portal y admin. Depende de T3.
- **Aceptación:**
  - build y lint en verde;
  - se puede responder y editar la respuesta;
  - el filtro «sin responder» funciona;
  - el admin oculta con motivo.

### T7: QA del diff ✅ (2026-11-11; 0 altos; M1 reseñas borradas y ocultas, M2 contacto en preguntas, B1–B3 y error 500 al volver a crear la reseña de almacén, corregidos)
- **Modelo:** Opus · **Agente:** qa-seguridad
- **Contexto:** solo el diff de T2–T6. Revisar:
  - IDOR en reseñas, respuestas y pendientes;
  - reseñas sin compra;
  - XSS en comentarios y respuestas;
  - datos personales del autor;
  - límites;
  - caché que sirva reseñas ocultas.
- **Aceptación:** informe clasificado; pruebas con `-Dtest` explícito.

### T8: Publicar v2.1.0
- **Modelo:** Haiku · **Agente:** orquestador
- **Contexto:** `CHANGELOG.md`, PR a `develop` y, **con autorización**, la etiqueta `v2.1.0`.
- **Aceptación:**
  - el workflow de promoción en verde;
  - en producción, calificar un pedido entregado de prueba y ver las estrellas en la ficha.

Orden: T1 → T2 → (T3 ∥ T4) → T5 → T6 → (T9 → T10 → T11) → T7 → T8. T3 y T4 tocan capas distintas.

## Preguntas y respuestas (añadido)

- **Quién pregunta y quién responde:** cualquier comprador con sesión pregunta en la ficha de un producto, sin necesidad de haberlo comprado. Responde el almacén: el dueño o un colaborador con `STORE_UPDATE`, o uno asignado a una sucursal que tenga el producto.
- **Quién se entera:**
  - Al llegar una pregunta se notifica dentro de la app al dueño y a los colaboradores activos de las sucursales con inventario del producto.
  - Además se envía un correo a los destinatarios de `notifications.recipients` del almacén si el ajuste nuevo `notifications.questions` (por defecto activo) lo permite.
  - Cuando el almacén responde, el que preguntó recibe una notificación y un correo.
- **Qué se ve en la ficha:** «Preguntas sobre este producto», con las respondidas primero, el buscador de preguntas y «Pregúntale al almacén». Las pendientes las ven solo su autor y el almacén.
- **Reglas:**
  - Pregunta de 10 a 300 caracteres y respuesta de hasta 1000.
  - No se aceptan enlaces, teléfonos ni correos (`QUESTION_CONTACT_NOT_ALLOWED`), para que la conversación siga en la plataforma.
  - Límite de 5 preguntas por hora por cuenta.
  - El autor público se muestra como en las reseñas: nombre e inicial del apellido.
  - El admin puede ocultar preguntas, con el mismo flujo que las reseñas.

**Datos (Supabase):**
```sql
create table product_questions (id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id), store_id uuid not null references store(id),
  person_id uuid not null references persons(id),
  question text not null check (char_length(question) between 10 and 300),
  answer text check (char_length(answer) <= 1000), answered_at timestamptz, answered_by uuid references accounts(id),
  hidden_at timestamptz, hidden_reason varchar(200),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  deleted_at timestamptz, version bigint not null default 0,
  check ((answer is null) = (answered_at is null)));
create index ix_pq_product on product_questions (product_id, answered_at desc nulls last, created_at desc) where deleted_at is null and hidden_at is null;
create index ix_pq_store_pending on product_questions (store_id, created_at) where answer is null and deleted_at is null;
-- notifications: enlace al que lleva la notificación
alter table notifications add column link varchar(300);
```

**Contratos:**
- `GET /marketplace/products/{id}/questions?page&size&q` (público): `{id, question, author, createdAt, answer:{text, answeredAt}|null, mine}`. Las no respondidas solo aparecen al autor.
- `POST /marketplace/products/{id}/questions {question}` (sesión) → 201. Errores 400 `QUESTION_INVALID` y `QUESTION_CONTACT_NOT_ALLOWED`, 429 `QUESTION_RATE_LIMITED`.
- `DELETE /marketplace/products/{id}/questions/{qid}`: solo el autor y solo si no tiene respuesta.
- Portal:
  - `GET /stores/{storeId}/questions?answered&branchId&page` (`STORE_READ`, o colaborador de la sucursal);
  - `PUT …/questions/{qid}/answer {text}` y `DELETE …/answer` (`STORE_UPDATE` o colaborador de una sucursal con el producto);
  - `GET /stores/{storeId}/questions/summary` → `{pending}`.
- Admin: `GET /admin/questions?hidden`, `POST …/{qid}/hide {reason}` y `POST …/unhide`.
- `NotificationResponse` añade `link`, para ir directo a la pregunta.
- Eventos `ProductQuestionAsked` y `ProductQuestionAnswered`, procesados después del commit y en segundo plano.

**Tareas:**
- **T9 · database · Haiku ✅ (2026-11-11, migración `product_questions_and_notification_link`; índices extra por almacén, persona y `answered_by`; `notifications.link` solo rutas internas `/…`):** el SQL de arriba en Supabase. **Requiere autorización.** Aceptación: tablas creadas y advisors sin alertas nuevas.
- **T10 · backend · Sonnet ✅ (2026-11-11; teléfono = celular 3xx o fijo 60x de 10 dígitos, no referencias OEM; `/reviews/mine` y `reviewId` en pendientes):**
  - Archivos: `marketplace/questions/*`, `storesuite/questions/*`, `operations/questions/*`, puertos en `core.reviews` o en un `core.questions` nuevo, `NotificationService` (destinatarios y `link`), plantillas de correo, el ajuste `notifications.questions` en `SettingsSchemas`, `WebSecurityConfig`.
  - Aceptación: `-Dtest=ProductQuestionServiceTest,QuestionNotificationTest,StoreQuestionsPortalTest,AdminQuestionsTest,ModularityTests,WebSecurityConfigTest` en verde.
    - Una pregunta con teléfono responde 400.
    - Notifica al dueño y a los colaboradores de la sucursal.
    - Una respuesta de otro almacén responde 404.
    - El autor ve su pregunta pendiente; otros no.
- **T11 · frontend · Sonnet ✅ (2026-11-11; campana de notificaciones nueva; huecos de T5 cerrados):**
  - Archivos: la sección «Preguntas» en la ficha de producto; `/portal/:storeId/preguntas` con un contador en la navegación y una tarjeta en el panel; las notificaciones con enlace; el admin de preguntas.
  - Aceptación: build y lint en verde, el flujo completo se maneja con teclado y sin scroll horizontal a 360 px.

Orden: T9 → T10 → T11, antes de T7 (QA), que cubre también el diff de preguntas.

## Riesgos y puntos a verificar

- **Plataforma vacía:** sin reseñas, las secciones deben ocultarse o invitar a calificar, nunca mostrar «0,0». Conviene el correo de T3 para arrancar.
- **Datos personales en la invitación:** el correo de invitación usa datos del comprador; enviarlo al pedido entregado es parte del servicio, y no publicidad. Revisar el texto con lo que ya se informa en `/privacidad`.
- **Reseña sin estado de venta:** una venta marcada `DELIVERED` y luego devuelta (`return_requests`) sigue habilitando la reseña. Se acepta, porque la reseña negativa es legítima.
- **Retraso del índice:** `search_listings` se actualiza unos 2 s después de una reseña. El resumen de la ficha se lee en vivo (o con caché de 5 min) y puede diferir un instante de la tarjeta.
