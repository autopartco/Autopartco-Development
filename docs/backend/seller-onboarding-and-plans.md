# Registro y planes para vendedores

## Alcance solicitado

El flujo se está trabajando en dos fases: backend y luego frontend. El
onboarding, los planes y los nuevos contratos de dashboard están disponibles
en backend; la adaptación completa de la interfaz para consumirlos corresponde
a la fase de frontend.

### Implementación de frontend

El frontend ofrece página de beneficios y planes en `/sell`, selección de plan
y carga de documentos colombianos en el registro de vendedor, seguimiento de
solicitudes en `/seller/application`, configuración final en `/seller/setup`,
panel en `/dashboard/store`, y administración ROOT en `/dashboard/admin`.
Los CTA «Abrir almacén» y «Vender» enlazan a `/sell`.

La vista de retorno de Wompi está disponible en `/seller/payment-result`; el
destino que use la pasarela lo determina `WOMPI_REDIRECT_URL` en el backend.
La vuelta desde Wompi no se considera confirmación de pago: el frontend indica
que espera la notificación validada del webhook.

El acceso de cliente a las funciones de cuenta tampoco está conectado todavía;
es un alcance de frontend separado del flujo de vendedores.

## Requisitos funcionales

### Planes configurables

- Los planes y sus características deben persistirse en la base de datos y ser
  modificables sin publicar cambios en el frontend.
- Debe existir un plan gratuito/de prueba (`FREE` o `TRY`) de un mes.
- Debe poder indicarse desde los datos del plan cuál es el recomendado.
- El registro estándar de vendedor, si no selecciona un plan, debe asignar la
  prueba gratuita.
- El sistema debe representar duración/precio/beneficios del plan y permitir
  cambios administrados en la base de datos.
- La migración `007_seed_seller_plans.sql` configura los planes iniciales:
  `TRY` (visible como `FREE`, 30 días), `BUSINESS` ($149.900 COP/mes) y
  `ENTERPRISE` (desde $499.900 COP/mes). `BUSINESS` queda marcado como
  recomendado; los topes, beneficios, comisiones y costos adicionales se
  muestran en la descripción y características persistidas de cada plan.

### Solicitud y validación de vendedor

- El registro crea una solicitud de vendedor/almacén, no concede
  inmediatamente acceso vendedor.
- La solicitud conserva el plan seleccionado, o el plan gratuito por defecto.
- La solicitud incluye documentos que permitan a un operador `ROOT` revisar la
  identidad y la legalidad de la empresa en Colombia.
- Un operador autorizado aprueba o rechaza la solicitud después de revisar los
  datos y documentos.
- Al aprobarla, se habilita el acceso vendedor y se envía un correo con un
  enlace seguro para terminar de configurar el almacén.
- La configuración posterior recoge como mínimo los activos de marca (logo y
  banner), que no son parte de los datos mínimos de registro.

### Activación y pago

- Tras completar la configuración, el vendedor llega al dashboard.
- Para el plan gratuito, se muestra una recomendación del plan marcado como
  recomendado; se puede cerrar el aviso y continuar con la prueba, o iniciar
  la mejora de plan.
- Si el usuario solicitó un plan pagado, al completar la configuración se le
  ofrecen las acciones de pagarlo o usar la prueba gratuita.
- Si el usuario elige la prueba, el periodo inicial es un mes. Si paga un plan
  elegido, el periodo gratuito se suma a la duración pagada de dicho plan.
- La integración de una pasarela de pago requiere definir proveedor, ciclo de
  facturación y reglas de duración/precio antes de habilitar cobros reales.

### Almacenamiento de archivos

- Referenciar un bucket dedicado de Supabase Storage llamado `bussniess`
  (nombre proporcionado por el usuario) para documentos legales y activos de
  negocio como logos y banners.
- El bucket debe ser privado para que documentos de identidad/legalidad no se
  expongan mediante URL pública. El backend debe controlar la subida y el
  acceso a documentos. Logos y banners aprobados podrán servirse mediante
  endpoints backend controlados; documentos solo serán accesibles al
  solicitante propietario y a `ROOT`.
- El usuario solicita «crear la referencia» desde la configuración backend;
  crear el bucket en el proyecto remoto de Supabase no se asume realizado por
  esta especificación.

## Fases

1. **Backend:** modelo/migración de planes configurables, solicitudes y
   estados de revisión, carga y consulta protegida de documentos, aprobaciones
   `ROOT`, flujo de completar configuración, concesión y cómputo de planes,
   correo de activación/configuración y referencia al bucket.
2. **Frontend:** página pública de beneficios y planes, registro con selección
   de plan y documentación, estados de solicitud, flujo de configuración,
   dashboard, recomendación de plan y navegación de pagos, consumiendo los
   contratos del backend.

## Decisiones acordadas

- Documentos por tipo:
  - Persona natural: cédula y RUT.
  - Persona jurídica: certificado de existencia y representación legal, RUT
    y cédula del representante legal.
- Pasarela: Wompi.
- Facturación: pago único por meses prepagados; el mes inicial de prueba se
  suma al periodo pagado.
- Un bucket privado `bussniess` aloja documentos y activos de marca; el backend
  controla el acceso y sirve solo los activos públicos autorizados.
- Planes y características: API backend protegida para `ROOT`; la UI
  administrativa queda para la fase frontend.
- Los meses pagados se computan como meses calendario.
- Cada pago puede cubrir de 1 a 12 meses; se pueden realizar pagos adicionales
  para extender el periodo.

## Contratos disponibles en backend

- `GET /api/v1/plans`: planes habilitados y sus características.
- `GET /api/v1/admin/plans` y las operaciones `POST`, `PUT` y `DELETE`
  bajo `/admin`: consulta y administración exclusiva de `ROOT`. El borrado
  deshabilita el plan; `TRY` no se puede deshabilitar ni renombrar.
- `POST /api/v1/store-applications`: alta de cuenta nueva como solicitud.
  Recibe una parte multipart JSON llamada `request` con `account`, `store`,
  `legalType` (`NATURAL_PERSON` o `LEGAL_ENTITY`) y `planId` opcional. Las
  partes de archivos son `identityDocument`, `rut`,
  `legalRepresentativeId` y `certificateOfExistence`.
- `POST /api/v1/me/store-applications`: mismo registro legal para un usuario
  `CUSTOMER` autenticado, sin crear una segunda cuenta.
- `GET /api/v1/me/store-applications`: estado de las solicitudes propias,
  junto con plan vigente y plan originalmente seleccionado.
- `GET /api/v1/admin/store-applications/pending`,
  `PATCH /api/v1/admin/store-applications/{applicationId}/review` y
  `GET /api/v1/me/store-applications/{applicationId}/documents/{documentId}` (solicitante) y
  `GET /api/v1/admin/store-applications/{applicationId}/documents/{documentId}` (root):
  revisión y descarga privada de documentos; solo `ROOT` puede revisar y
  descargar documentos de cualquier solicitante. El titular solo puede
  descargar los de su propia solicitud.
- La aprobación cambia el estado a `CONFIGURATION_PENDING` y envía un enlace
  de configuración de un solo uso que vence en 48 horas. `POST
  /api/v1/store-applications/setup` acepta ese token y los activos opcionales
  `logo` y `banner`; requiere correo verificado, activa el almacén con un mes
  de prueba y devuelve los tokens de sesión para continuar al dashboard.
- `GET /api/v1/marketplace/stores/{storeId}/brand/{logo|banner}` sirve
  activos solo para almacenes activos. Los documentos no tienen URL pública.
- `POST /api/v1/stores/{storeId}/subscription/checkout` inicia
  un pago Wompi de 1 a 12 meses. `POST
  /api/v1/webhooks/wompi` valida la firma declarada en el evento
  antes de actualizar la vigencia; los pagos aprobados se suman al mayor entre
  la fecha actual, la prueba y el periodo pagado vigente.

### Dashboard, ventas y pedidos

- `GET /api/v1/stores/{storeId}/dashboard` devuelve la tienda, el plan efectivo
  y el seleccionado, vigencias, topes, uso, capacidades y resumen del mes.
  `setupRequired` indica que falta completar la configuración inicial; el
  frontend debe abrir ese flujo antes de mostrar las funciones del dashboard.
- `GET /api/v1/stores/{storeId}/orders` lista las ventas del almacén,
  con filtro opcional `status` y paginación `page`/`size` (máximo 100).
- `GET /api/v1/stores/{storeId}/orders/{saleId}` devuelve el detalle
  de venta, artículos, estado/método del pago, envío y dirección de entrega.
- `PATCH /api/v1/stores/{storeId}/orders/{saleId}/status` admite las
  transiciones `PENDING -> CONFIRMED|CANCELLED` y
  `CONFIRMED -> PREPARING|CANCELLED`; no modifica ni reembolsa pagos.
- `POST /api/v1/stores/{storeId}/orders/{saleId}/shipment` registra
  transportadora y guía cuando la venta está `PREPARING`. `PATCH` sobre esa
  misma ruta avanza el envío por `DISPATCHED`, `IN_TRANSIT`,
  `OUT_FOR_DELIVERY` y `DELIVERED`; el detalle devuelve el historial de eventos
  e informa al cliente cuando se registra o avanza un envío.
- `GET /api/v1/me/notifications` lista las 50 notificaciones
  más recientes de la cuenta autenticada;
  `PATCH /api/v1/me/notifications/{notificationId}/read` marca
  como leída únicamente una notificación de esa misma cuenta.
- `GET /api/v1/stores/{storeId}/staff` lista integrantes e
  invitaciones del almacén. Requiere `COLLABORATOR_READ`.
- `GET /api/v1/stores/{storeId}/staff/permission-options` enumera
  permisos asignables y requiere `COLLABORATOR_MANAGE`.
- `POST /api/v1/stores/{storeId}/staff/invitations` recibe
  `email`, `roleName` y `permissionCodes`, crea el rol del almacén y envía una
  invitación con token de un solo uso que vence en siete días.
- `POST /api/v1/stores/{storeId}/staff/invitations/{invitationId}/resend`
  renueva el token y el vencimiento, incluido para invitaciones expiradas.
  `PATCH /api/v1/stores/{storeId}/staff/invitations/{invitationId}/revoke`
  invalida una invitación no aceptada. Ambas operaciones requieren
  `COLLABORATOR_MANAGE`; la capacidad `CUSTOM_ROLES` y los cupos `USERS` del
  plan aplican al crear y reenviar invitaciones.
- `GET /api/v1/staff-invitations/preview?token=...` expone solo los
  datos necesarios para mostrar la invitación y decidir si se debe crear una
  cuenta. `POST /api/v1/staff-invitations/accept` acepta el token;
  si no existe una cuenta para el correo, recibe además `account` con nombre,
  apellido, identificación, fecha de nacimiento, teléfono y contraseña. Estos
  dos endpoints no exigen sesión: el token prueba el control del correo. La
  aceptación activa una cuenta nueva o vincula la existente al rol asignado.
- La capacidad de usuarios cuenta colaboradores activos y reserva cupos para
  invitaciones vigentes. El propietario ya cuenta como usuario del almacén.

Todas las rutas de almacén verifican el permiso del colaborador y filtran las
ventas por `branches.store_id`, por lo que una orden con varios vendedores no
expone artículos o totales de otro almacén. Después del vencimiento de FREE o
de un periodo pagado se conservan las consultas y la gestión de ventas ya
existentes; las operaciones nuevas y la publicación quedan sujetas al estado
de suscripción y a los límites del plan. `null` en un tope significa ilimitado.

La migración `008_seller_dashboard_entitlements.sql` incorpora topes,
capacidades y permisos de ventas/envíos. La migración es reejecutable y debe
aplicarse en cada base de datos ya creada; una inicialización limpia también
recibe los permisos desde `database/init.sql`.

Las solicitudes multipart admiten documentos PDF/PNG/JPEG de hasta 10 MiB y
logos/banners PNG/JPEG/WebP de hasta 5 MiB. El contenido se valida por
firma binaria y no por la extensión o el MIME declarado por el cliente. El
backend requiere el bucket Supabase privado `bussniess` (variable
`SUPABASE_BUCKET_BUSINESS`) y las variables de Wompi definidas en
`backend/.env.example`; no crea el bucket remoto.

## Configuración de integración requerida

- Crear/configurar el bucket privado `bussniess` en el proyecto Supabase.
- Configurar las llaves Wompi para el ambiente que se vaya a usar. No se deben
  guardar llaves reales en el repositorio ni en respuestas del API.
