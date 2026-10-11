# AUTOPARTCO — Definición integral del producto y plan de desarrollo

**Versión:** 1.0 — base de definición del producto  
**Fecha:** 11 de octubre de 2026  
**Estado:** propuesta de alcance y hoja de ruta; debe contrastarse con una auditoría ejecutable del repositorio antes de convertir cada elemento en tarea de implementación.  
**Propósito:** servir como documento rector para producto, diseño UX/UI, arquitectura, desarrollo, agentes de IA, pruebas y decisiones comerciales.

---

## 1. Definición ejecutiva

**Autopartco es una plataforma comercial de autopartes para Colombia que conecta a conductores y mecánicos con almacenes verificados, y ayuda a esos almacenes a gestionar su catálogo, inventario por sucursal, pedidos, colaboradores, integraciones empresariales y ventas desde un solo lugar.**

Autopartco combina cuatro áreas de producto:

1. **Marketplace:** permite buscar repuestos, comprobar compatibilidad y disponibilidad, comparar opciones y realizar pedidos.
2. **SaaS para almacenes:** ofrece herramientas para que cada negocio gestione productos, sucursales, existencias, empleados, pedidos, promociones, reportes y su presencia digital.
3. **Integración empresarial:** conecta Autopartco con sistemas que el almacén ya usa, empezando por **Alegra y Siigo**, con importación inicial, sincronización, trazabilidad y alternativa CSV.
4. **Inteligencia automotriz y comercial:** incorpora búsqueda asistida por IA, ayuda orientativa para diagnóstico, evaluación de compatibilidad y analítica de inventario y demanda cuando existan datos suficientes.

La plataforma también necesita una quinta área interna: **operaciones de Autopartco**, con administración raíz, revisión de almacenes, moderación, planes, soporte, auditoría y supervisión de integraciones.

### Principio rector

La IA no debe añadir características por el simple hecho de poder generarlas. Cada función debe responder a un problema de usuarios, tener un criterio de aceptación, respetar los límites de los datos disponibles y ser segura, comprobable y sostenible en coste.

### Qué no es Autopartco

- No es solo un catálogo público con enlaces a WhatsApp.
- No es un sistema contable que reemplace a Alegra o Siigo.
- No debe convertirse desde el comienzo en una colección de microservicios difíciles de desplegar.
- No debe presentar una recomendación generada por IA como diagnóstico mecánico definitivo ni como compatibilidad confirmada.
- No debe afirmar que un pago fue recibido por el solo hecho de volver desde la pasarela a la web.
- No debe mostrar predicciones de negocio como fiables si no hay historial suficiente para evaluarlas.

---

## 2. Problema, mercado y propuesta de valor

### 2.1 Problema que resuelve

La información sobre autopartes y sus existencias está fragmentada entre almacenes, catálogos, llamadas, mensajes, archivos y software administrativo. El comprador puede perder tiempo buscando una pieza, desplazarse a otro lugar sin saber si hay existencias o comprar una referencia incompatible. El almacén puede mantener información duplicada, actualizar el catálogo manualmente, perder ventas por falta de visibilidad y no disponer de una visión clara del rendimiento del inventario.

### 2.2 Clientes y usuarios principales

- **Conductores:** necesitan localizar piezas apropiadas para su vehículo de manera clara y confiable.
- **Mecánicos y talleres:** necesitan buscar piezas para varios vehículos, comparar referencias, precios y disponibilidad, preparar cotizaciones y hacer seguimiento de pedidos.
- **Almacenes independientes y cadenas:** necesitan vender en línea, administrar catálogo y existencias, atender pedidos y medir ventas.
- **Propietarios y administradores de almacén:** necesitan controlar su negocio y sus sucursales sin perder la trazabilidad.
- **Empleados de tienda:** necesitan realizar solo las acciones que su rol permite.
- **Equipo de Autopartco:** necesita verificar negocios, moderar información, administrar planes y resolver casos de soporte.

### 2.3 Propuesta de valor

**Para compradores:** “Encuentra el repuesto correcto, comprueba qué tan confiable es la coincidencia y conoce qué tienda lo ofrece.”  
**Para almacenes:** “Publica una vez, mantiene el inventario conectado y convierte el catálogo en otro canal de ventas.”  
**Para Autopartco:** “Una plataforma de marketplace y software empresarial que puede monetizar suscripciones y, cuando el modelo esté definido, transacciones y servicios adicionales.”

### 2.4 Principios de producto

1. La confianza importa más que la cantidad de resultados.
2. La compatibilidad es una afirmación que requiere evidencia, no solo una coincidencia textual.
3. Los datos de una sucursal son distintos de los datos generales del almacén.
4. Cada integración debe tener un sistema fuente claro para cada tipo de dato.
5. La interfaz debe guiar a la persona y explicar qué ocurrirá antes de ejecutar acciones de alto impacto.
6. El comprador no debe necesitar conocimientos técnicos para buscar o comprar.
7. El vendedor debe poder empezar con un CSV si aún no puede conectar su ERP.
8. El soporte y la auditoría forman parte del producto; no son tareas improvisadas por fuera.
9. La seguridad se controla en el backend, no solo ocultando botones o rutas en React.
10. Toda función crítica debe poder verificarse mediante pruebas y evidencia.

---

## 3. Alcance general del producto

Autopartco se divide en cuatro superficies conectadas y una capa de servicios compartidos.

| Superficie | Usuarios | Responsabilidad |
|---|---|---|
| Marketplace público | Visitantes, conductores, mecánicos | Buscar, evaluar, comparar, comprar y consultar pedidos. |
| Portal comercial | Propietarios, administradores y empleados del almacén | Gestionar sucursales, catálogo, inventario, pedidos, integraciones, equipo y analítica. |
| Consola de operaciones | Root admins de Autopartco | Controlar la plataforma, verificaciones, moderación, planes, configuración y auditoría. |
| Centro de soporte | Agentes de soporte autorizados | Atender incidentes y consultas con permisos limitados y trazabilidad. |
| Servicios compartidos | Todos los módulos | Identidad, permisos, notificaciones, pagos, integraciones, eventos, registros y analítica. |

El objetivo inicial es construir un **monolito modular** en Spring Boot y una aplicación React con áreas de interfaz bien separadas. La extracción de servicios independientes solo se considerará cuando haya evidencia de necesidad operativa, escalabilidad o aislamiento.

---

## 4. Tipos de cuenta, organizaciones y permisos

Deben distinguirse tres conceptos que no son intercambiables:

- **Tipo de cuenta:** qué clase de usuario es la persona en la plataforma (por ejemplo, comprador, cuenta vinculada a una tienda o personal interno).
- **Rol:** qué acciones puede ejecutar una persona dentro de una organización, tienda o sucursal.
- **Permiso sobre un recurso:** qué puede hacer específicamente con un pedido, producto, inventario, ticket o configuración.

Un menú oculto no constituye una autorización. Cada operación del backend debe verificar autenticación, pertenencia a la organización y permiso sobre el recurso.

### 4.1 Roles propuestos

1. **Visitante:** consulta el catálogo público y las páginas públicas de tiendas.
2. **Cliente:** gestiona perfil, vehículos guardados, carrito, pedidos, reseñas, reportes y preferencias.
3. **Solicitante de tienda:** envía datos y documentos para solicitar la activación de un almacén.
4. **Propietario de organización/tienda:** controla la cuenta comercial, sucursales, colaboradores, integraciones, plan y configuración.
5. **Administrador de tienda:** opera la tienda con permisos delegados por el propietario.
6. **Encargado de sucursal:** administra catálogo y operaciones limitadas a la sucursal o sucursales autorizadas.
7. **Empleado:** ejecuta funciones específicas, por ejemplo, actualizar existencias o procesar pedidos.
8. **Agente de soporte de Autopartco:** atiende tickets y consulta solamente la información necesaria para resolverlos.
9. **Moderador/operador de Autopartco:** revisa solicitudes, reportes, contenido e información comercial según permisos asignados.
10. **Root admin de Autopartco:** accede a operaciones privilegiadas. Sus acciones deben estar auditadas y no debe usarse como cuenta cotidiana.

Los roles definitivos se deben convertir en una matriz de permisos explícita. No conviene codificar la lógica de autorización únicamente con nombres de rol si cada empresa necesita combinar permisos por sucursal.

### 4.2 Organización y sucursales

- Una organización puede administrar una o varias tiendas y/o sucursales según el modelo comercial final.
- Cada sucursal tiene dirección, zona, datos de contacto, horarios y estado propios.
- Un colaborador puede tener acceso a una o varias sucursales con permisos distintos.
- Los productos del catálogo comercial pueden compartirse, pero el precio, disponibilidad e inventario deben resolverse por sucursal cuando corresponda.
- Se deben conservar invitaciones, altas y bajas de colaboradores, quién cambió permisos y cuándo.

### 4.3 Seguridad de cuentas

La línea base debe incluir registro e inicio de sesión, verificación de correo si está implementada, restablecimiento de contraseña, gestión de sesiones/tokens, cierre de sesión y revocación cuando corresponda. La implementación existente debe revisarse antes de reescribirla.

---

## 5. Marketplace para compradores

### 5.1 Inicio y descubrimiento

La página principal debe llevar rápidamente a la acción principal: buscar un repuesto. Puede incluir categorías, marcas populares, tiendas verificadas, ventajas de la plataforma y orientación para quien no sabe el nombre exacto de la pieza. No debe convertirse en una página llena de banners y tarjetas sin jerarquía.

### 5.2 Búsqueda

La búsqueda debe admitir combinaciones de:

- Texto libre, nombre común y referencia/SKU.
- Marca, categoría y características del producto.
- Vehículo: marca, modelo, año y, cuando los datos lo permitan, generación/motor/versión.
- Rango de precios.
- Ubicación, distancia o zona de entrega, cuando existan datos confiables.
- Tienda, sucursal, reputación y disponibilidad.
- Tipo de producto: original, equivalente o alternativo, solo si se ha definido y verificado esa clasificación.

La página de resultados debe mostrar filtros claros, conteos cuando sea posible, ordenamiento consistente y una forma sencilla de retirar filtros. La consulta debe poder recuperarse al volver desde el detalle del producto.

### 5.3 Resultados vacíos y errores

Si no hay resultados, Autopartco debe explicar que no encontró coincidencias con esos criterios, permitir quitar filtros y mostrar alternativas justificadas. No debe simular resultados ni sugerir compatibilidad sin base. Durante las cargas se mostrarán skeletons apropiados. Los errores deben ofrecer una siguiente acción concreta.

### 5.4 Página de producto

Debe mostrar, cuando haya datos:

- Nombre, fotos, referencia y descripción.
- Precio, moneda, condiciones de venta y disponibilidad.
- Tienda y sucursal responsables del inventario.
- Marca/fabricante y especificaciones relevantes.
- Compatibilidad por vehículo y estado de evidencia.
- Existencias o disponibilidad sin revelar datos internos sensibles.
- Condiciones de entrega, recogida, garantía y devolución que estén realmente configuradas.
- Reputación y datos de la tienda.
- Botón de compra o contacto según el flujo comercial habilitado.

La UI debe distinguir **“compatibilidad confirmada por una fuente”**, **“declarada por el vendedor”**, **“coincidencia sugerida”** y **“sin datos suficientes”**. No se deben representar como equivalentes.

### 5.5 Tienda y sucursal

La página pública de una tienda debe incluir nombre, estado de verificación, descripción, ubicación, contacto, horarios, catálogo, condiciones comerciales y sucursales públicas. La insignia de verificación debe obedecer a un proceso real y auditable.

### 5.6 Carrito, pedidos y seguimiento

El carrito debe conservar productos, cantidades, sucursal/proveedor y precio capturado. En la creación del pedido se vuelve a validar disponibilidad y precio para reducir discrepancias. El detalle del pedido debe mostrar artículos, cantidades, precio histórico, estado y eventos del seguimiento.

Los estados definitivos deben modelarse explícitamente, por ejemplo: borrador/pendiente de pago, pago pendiente, confirmado, en preparación, despachado/listo para recogida, entregado, cancelado y devolución solicitada. La máquina de estados exacta debe definirse junto con la operación logística y no debe permitir transiciones arbitrarias desde el frontend.

En la primera versión, la lógica de compra debe ser sencilla. Funciones de envío avanzado, múltiples vendedores por carrito, liquidaciones a vendedores o devoluciones automatizadas solo entran en alcance cuando se decida cómo se resolverán comercialmente.

### 5.7 Cuentas, vehículos y fidelización

Funciones base: perfil, direcciones/contactos cuando sean necesarios, historial de pedidos, preferencias de notificación, reseñas y reportes de tiendas/productos. Como evolución:

- Garaje digital con vehículos guardados.
- Historial de mantenimiento y repuestos instalados.
- Recordatorios basados en intervalos documentados o definidos por el usuario.
- Listas compartidas o solicitudes de cotización para talleres.

Los recordatorios no deben inventar intervalos de mantenimiento. El usuario debe poder editar, pausar o eliminar los datos correspondientes.

### 5.8 Confianza, reseñas y reportes

- Solo permitir reseñas bajo reglas explícitas, idealmente vinculadas a una compra verificada cuando aplique.
- Evitar spam, duplicados y manipulación de calificaciones.
- Los reportes deben tener categoría, estado, responsable y resolución.
- La tienda reportada debe poder ser revisada de manera justa y el resultado debe quedar registrado.

---

## 6. Portal comercial para almacenes

### 6.1 Registro y verificación

El flujo debe explicar qué información se solicita, por qué se necesita y cuánto falta para habilitar la tienda. Estado propuesto: borrador, enviado, en revisión, requiere correcciones, aprobado, rechazado o suspendido. Debe conservarse el historial de cambios y la persona responsable de la decisión.

Al aprobar una solicitud, el propietario debe poder completar datos, crear sucursales e invitar colaboradores sin tener que entregar credenciales por canales inseguros.

### 6.2 Panel inicial

La pantalla inicial de negocio debe responder preguntas operativas claras:

- ¿Qué pedidos requieren acción hoy?
- ¿Qué productos tienen existencias bajas o datos incompletos?
- ¿Qué sincronizaciones fallaron?
- ¿Cómo se comportaron las ventas en el periodo seleccionado?
- ¿Qué configuración falta para terminar la puesta en marcha?

No se deben llenar los paneles con métricas decorativas. Cada indicador debe tener definición, periodo, fuente y acción relacionada.

### 6.3 Catálogo y publicación

- Crear y editar productos manualmente.
- Cargar fotos y gestionar imágenes.
- Mantener SKU/referencia, marca, categoría, descripción, especificaciones, unidad, precios y códigos externos.
- Validar campos obligatorios y detectar posibles duplicados.
- Importar/exportar CSV con plantilla versionada, previsualización, fila de errores y opción de reintento.
- Controlar publicación, borrador, agotado, pausado y archivado según reglas definidas.
- Separar el producto comercial de la oferta del almacén cuando varias tiendas puedan vender el mismo artículo.

La entrada de un producto desde ERP no debe borrar la información enriquecida propia de Autopartco, como compatibilidad, imágenes seleccionadas o etiquetas verificadas, salvo que el usuario haya configurado expresamente esa fuente como autoridad.

### 6.4 Inventario por sucursal

El inventario pertenece a una sucursal concreta. Como mínimo debe poder verse existencia actual, existencia reservada, disponibilidad vendible y fecha de última sincronización. Las cantidades deben cambiar mediante operaciones trazables, no a través de ediciones silenciosas.

Se necesita un **libro de movimientos de inventario** para registrar entradas, ventas, reservas, liberaciones, ajustes, devoluciones, transferencias y cambios recibidos del ERP. Cada movimiento registra origen, actor/sistema, cantidad, fecha, referencia y motivo. El saldo actual puede mantenerse para consultas rápidas, pero debe poder reconciliarse con el historial.

La transferencia entre sucursales, órdenes de compra, recepción de proveedores y conteos físicos son evoluciones importantes. Se priorizarán según el flujo real de los primeros almacenes piloto.

### 6.5 Pedidos, ventas y promociones

El almacén necesita revisar pedidos, ver artículos y cantidades, consultar pagos, avanzar estados permitidos, dejar notas operativas y contactar al cliente por canales habilitados. Las cancelaciones y ajustes deben tener reglas claras y auditables.

Las promociones requieren inicio, fin, alcance, condiciones, límites y prioridad. No deben alterar el precio maestro ni el precio histórico de pedidos anteriores.

### 6.6 Colaboradores

El propietario puede invitar, suspender o quitar acceso a colaboradores, asignar permisos y restringirlos por sucursal. El panel debe explicar por qué una acción no está disponible para un empleado y ofrecer una forma de solicitar permisos sin filtrar datos que no le corresponden.

### 6.7 Analítica comercial

Primera capa: ventas por periodo, número de pedidos, unidades vendidas, valor bruto, descuentos, cancelaciones, categorías y sucursales, productos más vendidos y productos sin movimiento. Debe distinguirse ingreso bruto, descuentos, devoluciones, comisiones, tarifas de pasarela y margen; no se debe llamar “ganancia” a un indicador si no están incluidos los costos pertinentes.

Segunda capa: alertas, análisis de rotación, quiebres de stock y recomendaciones de reposición. La predicción avanzada se habilitará después de reunir y evaluar suficiente historial.

---

## 7. Integraciones con Alegra y Siigo

**Requisito de producto de primera clase.** La integración debe ser parte de la propuesta comercial de Autopartco, no una tarea manual del equipo interno.

Documentación oficial de referencia:

- [Alegra Developer](https://developer.alegra.com/)
- [Alegra — productos y servicios](https://developer.alegra.com/docs/productos-y-servicios)
- [Siigo API](https://developers.siigo.com/docs/siigoapi/)
- [Wompi — sandbox de pruebas](https://docs.wompi.co/docs/colombia/pruebas-sandbox-pagos-a-terceros/)

La disponibilidad exacta de campos, endpoints, webhooks, permisos, límites y condiciones puede variar por recurso y tipo de cuenta. Se debe validar contra la documentación vigente y una cuenta de pruebas antes de prometer cada operación.

### 7.1 Objetivo de integración

Permitir que una tienda conecte su cuenta autorizada de Alegra o Siigo, importe su catálogo e inventario y sincronice los cambios seleccionados. El almacén mantiene su software administrativo y usa Autopartco como canal comercial sin registrar repetidamente los mismos datos.

### 7.2 Funciones mínimas

1. Conectar/desconectar una cuenta del ERP con instrucciones claras.
2. Guardar credenciales/tokens de forma protegida en backend; nunca exponer secretos al navegador.
3. Verificar conexión y permisos.
4. Mapear empresa/bodega/almacén externo con organización/sucursal Autopartco.
5. Importar productos mediante vista previa y validación.
6. Relacionar los IDs externos para que sincronizar no cree duplicados.
7. Definir por entidad y campo qué sistema es la fuente autorizada.
8. Procesar cambios incrementalmente cuando sea viable.
9. Recibir webhooks cuando la API los proporcione y ejecutar reconciliaciones periódicas para detectar eventos perdidos.
10. Mostrar historial: fecha, dirección, duración, registros nuevos/actualizados/ignorados, advertencias y errores.
11. Reintentar operaciones fallidas de forma segura.
12. Detectar desconexión, token vencido, permisos insuficientes, límites de API y conflictos.
13. Desconectar la integración y revocar/retirar credenciales conforme a las capacidades del proveedor.
14. Mantener CSV como alternativa de importación y exportación.

### 7.3 Datos a integrar por prioridad

| Prioridad | Datos | Recomendación inicial |
|---|---|---|
| P0 | Productos, SKU/referencia, nombre y descripción disponible | Importar ERP → Autopartco, con mapeo y validación. |
| P0 | Bodegas/sucursales e identificadores externos | Asociar cada bodega con una sucursal correcta; bloquear sincronización de stock si el mapeo es ambiguo. |
| P0 | Existencias | ERP → Autopartco como dirección inicial propuesta; reconfirmar tras analizar el flujo de ventas del almacén. |
| P0 | Precio/listas de precio | ERP → Autopartco con reglas configurables y vista previa. |
| P1 | Clientes y datos comerciales necesarios para pedidos | Compartir solo los datos necesarios y autorizados, respetando los permisos del conector. |
| P1 | Pedido confirmado para generar documento comercial en ERP | Autopartco → ERP solo cuando el flujo haya sido validado y sea soportado por cuenta/API. |
| P2 | Compras, proveedores, recepción y conciliación | Evaluar tras el piloto, evitando duplicar funciones contables del ERP. |

Los productos, taxonomía automotriz, compatibilidad y descripciones enriquecidas de Autopartco necesitan reglas propias. Un ERP genérico puede no contener información suficiente sobre generación, motor o compatibilidad por vehículo.

### 7.4 Reglas de sincronización

- **No asumir bidireccionalidad total.** Definir autoridad por campo y entidad.
- **Idempotencia:** repetir el mismo evento o petición no debe crear duplicados ni aplicar dos veces una venta/movimiento.
- **Conflictos:** el sistema debe explicar qué cambió y qué política decidió aplicarse.
- **Concurrencia:** si hay una venta en Autopartco y un ajuste en ERP casi al mismo tiempo, debe existir una estrategia de reconciliación.
- **Stock reservado:** distinguir existencia física, reservada y disponible para venta.
- **Errores parciales:** una fila inválida no debería ocultar los resultados del resto del lote.
- **Resiliencia:** backoff con límites, reintentos controlados, circuit breaker si resulta necesario y cola persistente de trabajos pendientes.
- **Observabilidad:** métricas de tasa de éxito, retraso de sincronización, errores por proveedor y registros sin mapear.
- **Privacidad:** restringir quién puede ver datos de clientes y tokens; redactar secretos de logs.
- **Límites del proveedor:** respetar rate limits y no intentar evadir límites de API.

### 7.5 Diseño técnico

Crear un módulo `integration` con un contrato de proveedor, adaptadores `Alegra` y `Siigo`, mapeadores de datos, jobs/worker de sincronización, control de checkpoints, logs, historial y manejo de webhooks. Los módulos de inventario y catálogo no deben contener llamadas específicas a Alegra/Siigo repartidas por sus servicios.

Una tabla de mapeo debe permitir asociar, como mínimo, organización, conexión, tipo de entidad, ID externo, ID interno y fecha de última reconciliación. Los trabajos deben ser idempotentes y dejar un estado visible para el usuario.

### 7.6 Criterios de aceptación

- Un propietario puede conectar una cuenta de pruebas de cada ERP compatible.
- Puede previsualizar importación antes de aplicar cambios.
- Los productos importados conservan la referencia externa y no se duplican al sincronizar de nuevo.
- Los productos con errores quedan identificados, con motivo y opción de reintento.
- Las bodegas quedan vinculadas a sucursales sin ambigüedad.
- Se puede ver cuándo se realizó la última sincronización y cuál fue su resultado.
- Un fallo temporal no deja operaciones en un estado indeterminado sin registro.
- Los secretos no aparecen en el frontend, respuestas API ni logs.
- Hay pruebas de token expirado, timeout, límite de API, evento duplicado, mapeo faltante y actualización concurrente de stock.

---

## 8. Suscripciones, planes y monetización

Autopartco es un negocio comercial. Los planes y límites deben poder modificarse sin desplegar una nueva versión de código.

### 8.1 Modelo comercial de referencia

- **Prueba gratuita de un mes:** facilitar el onboarding y permitir validar el valor del producto. Definir si se exige método de pago, cuándo empiezan los cargos y qué ocurre al vencer el periodo.
- **Plan para negocio pequeño:** catálogo, operación básica, límites modestos y herramientas esenciales.
- **Plan para negocio mediano:** más productos, usuarios/sucursales, automatización e informes.
- **Plan para negocio grande:** más sucursales/usuarios, límites de integración más altos, controles administrativos y soporte comercial definido.
- **Funciones complementarias opcionales:** publicidad/promoción de productos o tiendas, analítica avanzada e integraciones con límites mayores, si la economía del producto lo justifica.

Los precios no quedan fijados en este documento porque requieren validar costos de soporte, almacenamiento, uso de IA, límites de sincronización, comisión comercial y sensibilidad de los almacenes.

### 8.2 Separar cargos

Si Autopartco cobra suscripción, comisión del marketplace y costos de pasarela, deben mostrarse como conceptos distintos. Una comisión de Autopartco remunera el servicio comercial; la tarifa del proveedor de pagos es un costo de procesamiento según el acuerdo aplicable. No deben agruparse de forma que el comerciante no entienda lo que paga.

### 8.3 Entidades necesarias

Planes versionados, funcionalidades/limites, suscripciones, periodos, estado de prueba, facturas/recibos o referencias de cobro, intentos de pago, eventos de renovación, cambios de plan, cancelaciones e historial de cambios. Debe guardarse qué versión del plan aplicaba a cada periodo.

La facturación tributaria y los documentos oficiales deben asignarse al sistema responsable y revisarse contra las obligaciones aplicables en Colombia; Autopartco no debe prometer reemplazar el software contable del negocio.

---

## 9. Pagos de demostración y Wompi sandbox

La meta actual es **demostrar el flujo completo sin procesar dinero real**. Esto exige una separación inequívoca entre el proveedor real en sandbox y un simulador de pagos local.

### 9.1 Capas

- `PaymentService`: crea y administra intentos, valida importe/moneda, registra estado y vincula pago/pedido.
- `PaymentProvider`: contrato interno independiente del proveedor.
- `WompiSandboxProvider`: utiliza credenciales y ambiente de pruebas válidos.
- `SimulatedPaymentProvider`: permite forzar aprobado, rechazado, pendiente, expirado o cancelado sin tocar la red de pagos real.

### 9.2 Flujo esperado

1. El backend calcula y congela el importe del pedido desde datos validados.
2. Crea una referencia única e intento de pago.
3. Inicia el checkout sandbox o simulador.
4. El navegador muestra resultado, pero el backend no confía únicamente en la redirección.
5. El resultado se confirma a través del mecanismo servidor-servidor documentado, con validación del evento/firma que corresponda.
6. El webhook se procesa de forma idempotente; se registra el evento incluso si ya se había procesado.
7. Se actualiza el pedido mediante transacción y reglas de estado.
8. El usuario ve el resultado y una acción para reintentar o consultar soporte.

### 9.3 Controles obligatorios

- Nunca exponer claves privadas o secretos de integridad/eventos en frontend.
- Nunca tratar un redirect como prueba suficiente del pago.
- Validar importe, moneda, referencia, estado e identidad de la transacción.
- Evitar procesar dos veces webhooks duplicados.
- Registrar fallos, rechazos, cancelaciones y tiempos de espera.
- Mantener la simulación visualmente etiquetada como prueba.
- Probar desde el entorno sandbox del proveedor y no usar credenciales de producción para demos.

### 9.4 Fuera de alcance inicial

Procesar pagos reales, hacer dispersión/liquidación automática a múltiples vendedores, conciliación bancaria o automatizar reembolsos requiere una decisión comercial, de proveedor, de cumplimiento y operativa explícita. No debe inferirse que la configuración Wompi existente significa que el flujo ya está completo.

---

## 10. Funciones de inteligencia artificial

La IA debe integrarse como capacidad transversal con límites de coste, seguridad, evaluación y trazabilidad. No debe usarse donde una consulta SQL, una validación determinista o una regla de negocio sea más fiable.

### 10.1 Búsqueda semántica de repuestos

El usuario describe la necesidad en lenguaje natural (“necesito pastillas delanteras para…”) y Autopartco normaliza el texto, solicita datos faltantes y combina búsqueda textual/semántica con filtros estructurados y catálogo. La IA ayuda a interpretar; no inventa existencias, precios, referencias ni compatibilidades.

Los resultados deben distinguir coincidencias exactas, referencias equivalentes verificadas y alternativas aproximadas. Cada recomendación se vincula con productos reales recuperados desde la base de datos.

### 10.2 Copiloto de diagnóstico orientativo

Flujo propuesto:

1. El usuario selecciona vehículo o indica que no conoce todos los datos.
2. Describe síntomas, condiciones en las que ocurren y reparaciones recientes.
3. El sistema pide aclaraciones cuando falten datos esenciales.
4. Backend recupera información útil y el modelo genera hipótesis estructuradas, no una respuesta libre sin control.
5. Reglas de seguridad detectan síntomas de riesgo y priorizan inspección profesional o dejar de usar el vehículo cuando corresponda.
6. El sistema muestra incertidumbre, razones a favor/en contra, pruebas no invasivas permitidas y próximos pasos prudentes.
7. Los repuestos solo se recomiendan si hay evidencia suficiente de compatibilidad; se marca su nivel de certeza.
8. Se registra feedback para medir utilidad sin convertir automáticamente comentarios en verdad mecánica.

No se debe afirmar que un componente está dañado por una única palabra del síntoma. No se debe presentar una hipótesis como diagnóstico definitivo ni recomendar maniobras peligrosas. Entrada por imagen, audio u OBD-II son evoluciones posteriores; el sistema debe indicar explícitamente qué información realmente analizó.

### 10.3 Compatibilidad y equivalencias

Cada registro de compatibilidad debe tener fuente/procedencia, identificadores de vehículo, pieza, rango de años/versión, fecha, quien lo confirmó y estado de revisión. La IA puede ayudar a clasificar o proponer coincidencias, pero no elevarlas automáticamente al estado “confirmado”.

Fuentes posibles a evaluar: datos del fabricante con derechos de uso, catálogos autorizados, referencias OEM, equivalencias de proveedores y datos enviados por vendedores. La licencia y calidad de los datos deben comprobarse antes de importarlos.

### 10.4 Inteligencia de inventario

Funciones de progresión:

1. Alertas deterministas: stock bajo, datos incompletos, sincronización retrasada.
2. Analítica histórica: rotación, ventas por sucursal, productos inmóviles, patrones por periodo.
3. Recomendación explicable de reposición: ventas recientes, stock disponible, reservas, tiempo de reposición conocido y pedidos pendientes.
4. Predicción de demanda por producto/sucursal con línea base y evaluación retrospectiva.
5. Detección de anomalías de inventario/precios, previa medición de falsos positivos.

No debe llamarse “IA predictiva” a un número arbitrario. Se deben registrar horizonte, fecha de cálculo, datos usados, línea base, error de evaluación y límites.

### 10.5 Herramientas internas para vendedores

- Sugerir categorías y etiquetas para nuevos productos.
- Detectar posible duplicado a partir de referencia, marca y descripción.
- Mejorar redacción sin inventar especificaciones.
- Mapear columnas de CSV/ERP a campos de Autopartco con vista previa y confirmación humana.
- Detectar anomalías en los datos antes de publicar.

La IA nunca debe cambiar silenciosamente precio o stock ni publicar datos técnicamente inciertos sin autorización del usuario.

### 10.6 Arquitectura de IA

Crear un módulo interno de `intelligence` que gestione:

- Abstracción del proveedor/modelo para no quedar atados a uno solo.
- Prompts versionados y salidas estructuradas validadas.
- Límites de tokens, tiempo, frecuencia y coste por plan/usuario.
- Registro de errores y feedback con minimización de datos personales.
- Política de fallback cuando el modelo no responde.
- Evaluaciones de regresión con casos etiquetados.
- Reglas de seguridad deterministas alrededor de las respuestas generadas.

Los modelos de lenguaje no deben tener acceso directo sin control a secretos, datos de otras tiendas ni operaciones destructivas.

---

## 11. Analítica y predicción: qué medir antes de entrenar

La analítica descriptiva es prioritaria; el aprendizaje automático se incorpora cuando haya calidad e historial suficientes.

### 11.1 Eventos y métricas de producto

- Búsqueda: consulta, filtros, cero resultados, clic en producto, contacto con tienda.
- Comercio: alta de carrito, pedido creado, cancelación, estado del pedido y conversión.
- Vendedor: producto creado/importado, error de catálogo, sincronización, cambios de inventario.
- Suscripción: inicio de prueba, activación, cambio de plan y cancelación.
- IA: función usada, versión del modelo, latencia, coste estimado, feedback y error de evaluación.

No registrar texto sensible, documentos o datos personales completos por defecto. Definir retención y accesos.

### 11.2 Indicadores

- Conversión de búsqueda a vista y a pedido.
- Porcentaje de búsquedas sin resultados.
- Catálogo con campos mínimos completos.
- Éxito y retraso de sincronización por ERP.
- Discrepancias de inventario detectadas.
- Tasa de cancelación y cumplimiento de pedidos.
- Activación, retención y cancelación de vendedores.
- Coste y latencia por función de IA.
- Satisfacción/feedback y utilidad de las recomendaciones.

### 11.3 Evaluación de modelos

Para demanda, empezar con una línea base simple y comparar modelos usando datos históricos separados temporalmente. No mezclar datos futuros con el entrenamiento. Medir error por categoría/producto, y documentar dónde no funciona bien. Si la muestra es insuficiente, mostrar tendencias descriptivas y recomendaciones basadas en reglas, no pronósticos con falsa precisión.

Google Colab puede servir para exploración y prototipos de modelos; los resultados deben convertirse en scripts reproducibles, datos versionados y evaluaciones antes de llevarse al backend.

---

## 12. Modelo de datos objetivo

Este es el mapa conceptual de destino, no una orden de recrear todas estas tablas. Primero se debe cotejar con `database/init.sql`, entidades JPA y tablas ya existentes. Los nombres pueden ajustarse al esquema actual si la semántica es correcta.

### 12.1 Identidad y estructura comercial

- `accounts/users`: credenciales y perfil de usuario.
- `account_types`: tipo general de cuenta, según el modelo existente.
- `organizations` / `stores`: entidad comercial.
- `branches`: sucursales con ubicación y datos operativos.
- `memberships`: usuarios vinculados a organización y/o sucursal.
- `roles`, `permissions`, `role_permissions`, `membership_roles` o equivalente.
- `invitations`, sesiones, recuperaciones y verificaciones.
- `audit_events`: acciones relevantes, actor, objetivo, fecha y contexto seguro.

### 12.2 Catálogo y universo automotriz

- `products`: datos base de producto cuando pueda compartirse catálogo.
- `store_listings` u objeto equivalente: oferta comercial del vendedor.
- `categories`, `brands`, unidades y especificaciones normalizadas.
- `product_identifiers`: SKU, referencia del fabricante, OEM y códigos externos.
- `vehicles`, generaciones, motores/versiones, modelos/años.
- `product_fitments`: compatibilidad declarada/confirmada/sugerida, fuente, periodo y evidencia.
- `product_images` y metadatos de los archivos.

Es necesario separar producto base, publicación del vendedor, precio y stock. Si el modelo actual tiene otra estructura válida, no se deben crear duplicados por seguir los nombres de este documento.

### 12.3 Inventario y compras

- `branch_inventory`: estado/saldo actual por producto y sucursal.
- `inventory_movements`: ledger de movimientos.
- `stock_reservations` o estructura equivalente para pedidos activos.
- `inventory_snapshots`: cortes históricos para análisis.
- Futuro: proveedores, órdenes de compra, recepciones, transferencias y conteos físicos.

### 12.4 Comercio y pagos

- `carts` y líneas, si el carrito persiste en servidor.
- `orders`, `order_items` con instantánea de descripción, precio, cantidad y condiciones.
- `order_events` o historial de estados.
- `payment_attempts`, transacciones externas, eventos de webhook e historial.
- Futuro: devoluciones, garantías, reembolsos y liquidación de vendedores.

Los importes deben almacenarse con un tipo decimal definido o en unidades menores enteras, nunca `float` para dinero. Se debe fijar moneda, reglas de redondeo, impuestos y tratamiento de descuentos.

### 12.5 Integraciones

- `integration_connections`: proveedor, organización, estado, credenciales cifradas/referencia a secreto y configuración no sensible.
- `external_entity_mappings`: tipo de entidad, ID externo, ID interno y conexión.
- `sync_jobs` / `sync_runs`: estado, contadores, inicio/fin, error seguro, checkpoints.
- `webhook_events`: proveedor, ID/evento externo, resultado y clave de idempotencia.
- `sync_conflicts`: datos en conflicto, política aplicada y decisión humana si fue necesaria.

### 12.6 Suscripciones y soporte

- Planes y versiones, límites/funcionalidades, suscripciones y periodos.
- Intentos/eventos de cobro de la suscripción, cancelación y cambios de plan.
- `support_tickets`, mensajes, adjuntos, categoría, prioridad, responsable, estado, SLA si se adopta y referencias a pedido/tienda/cuenta.

### 12.7 IA y analítica

- Sesiones de diagnóstico, síntomas, preguntas, hipótesis estructuradas, fuentes, versión del prompt/modelo y feedback.
- Salidas de recomendación con explicación y vínculos a productos reales.
- Eventos analíticos y agregados por día/producto/sucursal/categoría.
- Registro de evaluaciones de IA, resultados y versiones de datasets/modelos.

### 12.8 Reglas de modelado

- Definir claves foráneas, restricciones `UNIQUE` y `CHECK` donde correspondan.
- Crear índices a partir de consultas reales y planes de ejecución, no por intuición.
- Usar borrado lógico solo cuando aporte trazabilidad; definir retención y cómo evitar registros huérfanos.
- Mantener fechas de creación/actualización y actor cuando aplique.
- Evitar duplicar datos derivables sin razón; cuando se desnormalice por rendimiento, definir reconciliación.
- Separar datos por organización y probar explícitamente que no existan fugas entre tenants.
- Usar migraciones versionadas y reproducibles. No depender de mutaciones automáticas de esquema en producción.

---

## 13. Arquitectura técnica

### 13.1 Frontend existente como base

- React + TypeScript + Vite.
- Tailwind CSS v4.
- shadcn/ui con Base UI y Lucide React.
- React Router.
- Inter como tipografía actual.
- Logo real desde `public/logo.svg`.

**Restricciones:** no migrar Tailwind v4 a v3; no añadir `tailwind.config.js` por costumbre; no añadir dependencias si las herramientas existentes pueden resolver la necesidad; no reemplazar el logo real por un texto o imagen de muestra.

### 13.2 Backend existente como base

- Java 21.
- Spring Boot 4.1.1 declarado como objetivo en el proyecto.
- Maven.
- PostgreSQL 16+.
- Spring Security/JWT según la implementación actual.
- Variables de entorno para configuración sensible.
- Convención de paquetes `com.autopartco`.

Antes de cambiar versiones se deben comprobar la compatibilidad real del proyecto y la matriz de dependencias de Spring Boot. El documento no da por hecho que el build actual pase.

### 13.3 Módulos lógicos propuestos

- `auth` — identidad, login, sesiones y tokens.
- `organization` / `store` / `branch` — negocio, tiendas, sucursales y colaboradores.
- `catalog` / `vehicle` / `compatibility` — productos, taxonomía, vehículos y compatibilidad.
- `inventory` — existencias, reservas, movimientos y transferencias.
- `order` — carrito, pedidos y máquina de estados.
- `payment` — abstracción de proveedores, eventos y reconciliación.
- `subscription` — planes, periodos y límites.
- `integration` — conectores Alegra/Siigo, CSV, colas y mapeos externos.
- `intelligence` — IA, búsqueda semántica, diagnóstico y evaluación.
- `analytics` — métricas, eventos, agregaciones y predicción.
- `support` — tickets y gestión de casos.
- `administration` — operaciones internas y moderación.
- `notification` — correo y canales acordados; WhatsApp dependerá del proveedor, permisos y costes.
- `audit`, `common`, `config` — responsabilidades compartidas con límites claros.

La lista no debe utilizarse para renombrar todo de forma masiva. Primero se mapean los módulos actuales y luego se refactoriza de manera incremental.

### 13.4 APIs y contratos

- REST API con DTO de entrada/salida, validación y reglas de negocio en backend.
- Convención de errores consistente, códigos HTTP correctos y mensajes seguros.
- Documentación OpenAPI si encaja con la estructura real del proyecto.
- Versionado/compatibilidad documentados cuando se cambien contratos.
- Lógica de precio, permisos, pagos e inventario calculada en el servidor.
- Jobs y webhooks asíncronos cuando se requiera tolerancia a fallos.
- No introducir Kafka, Redis u otra infraestructura de alto coste/operación sin una necesidad demostrable; primero usar PostgreSQL y mecanismos sencillos adecuados al volumen.

### 13.5 Configuración y despliegue

- `.env.example` con nombres de variables, nunca secretos reales.
- Secretos inyectados por entorno o gestor de secretos.
- Configuración separada para local, pruebas, staging y producción.
- CORS limitado a orígenes concretos.
- Migraciones versionadas.
- Health/readiness checks y logs estructurados.
- Copias de seguridad y prueba documentada de restauración.
- CI con build, tests, análisis estático y comprobación de dependencias.

---

## 14. Experiencia de usuario y diseño visual

**La UX es un requisito principal, no una capa estética al final.** La calidad visual debe hacer que el sistema sea más fácil de comprender, no solo más vistoso.

### 14.1 Principios de diseño

1. Priorizar la tarea principal de cada pantalla.
2. Usar lenguaje sencillo, claro y natural en español de Colombia.
3. Mantener la jerarquía visual y patrones consistentes entre módulos.
4. Hacer visible el estado del sistema y el resultado de cada acción.
5. Diseñar primero para móvil en el marketplace y para uso eficiente en escritorio en paneles de negocio.
6. Mostrar información progresivamente; no pedir al usuario demasiados datos al inicio.
7. Prevenir errores mediante opciones válidas, vista previa y confirmación proporcionada.
8. No esconder la acción principal entre botones secundarios ni utilizar iconos ambiguos sin etiqueta cuando se necesite claridad.
9. La animación debe informar o ayudar; nunca bloquear ni distraer.
10. Diseñar estados de error, carga, vacío, sin conexión, permisos insuficientes y éxito como parte del flujo.

### 14.2 Identidad visual

Mantener la identidad de Autopartco: rojo, negro y blanco, el logo real, y la tipografía Inter existente salvo una decisión explícita basada en investigación. Establecer tokens de color, tipografía, espaciado, bordes, elevación, foco y movimiento. Evitar gradientes, glassmorphism, exceso de tarjetas y animaciones decorativas por defecto.

### 14.3 Biblioteca y componentes

Antes de crear un componente desde cero, revisar shadcn/ui, Base UI y Lucide disponibles. Crear patrones reutilizables para formularios, tablas, filtros, pasos, diálogos, alertas, estados, badges de estado, paginación y skeletons. No duplicar componentes que cumplen la misma función con pequeñas diferencias accidentales.

### 14.4 Accesibilidad y responsive

Objetivo de diseño: alinearse con WCAG 2.2 AA en los flujos principales. Comprobar contraste, foco visible, orden de teclado, etiquetas asociadas, mensajes para lectores de pantalla, zoom y escalado de texto, reducción de movimiento y ausencia de desbordamiento horizontal. Como criterio UX, procurar objetivos táctiles de aproximadamente 44×44 px en móviles cuando el contexto lo permita.

### 14.5 Formas y datos

- Validación cerca del campo y errores que indiquen cómo solucionarlos.
- Etiquetas explícitas y ejemplos apropiados para Colombia.
- Fechas, cantidades y dinero en formatos locales coherentes.
- Autoguardado solo cuando sea seguro y el estado se haga visible.
- En importaciones, mostrar número de filas, filas válidas, advertencias, errores y muestra de cambios.
- En acciones destructivas, explicar el efecto y ofrecer cancelar; requerir confirmación según riesgo.

### 14.6 Investigación y pruebas de UX

Antes de completar un flujo importante:

1. Definir persona, contexto y objetivo de la tarea.
2. Dibujar el flujo y detectar decisiones innecesarias.
3. Revisar jerarquía y microcopy.
4. Probar con usuarios representativos cuando sea posible.
5. Automatizar pruebas de interacción y responsive.
6. Revisar errores de consola y red, no solo capturas bonitas.
7. Corregir los problemas de mayor impacto antes de seguir añadiendo funciones.

---

## 15. Skills y herramientas para agentes de IA

El objetivo no es crear muchas skills propias. Es **investigar, seleccionar, verificar y usar skills existentes** según la tarea. Las skills deben cargarse bajo demanda para reducir contexto y duplicación.

### 15.1 Diseño: conjunto recomendado

#### A. UI/UX Pro Max — skill principal de inteligencia de diseño

Repositorio: [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)  
Aporta referencias de estilos, paletas, tipografía, patrones UX, tipos de visualización y stacks. El repositorio documenta compatibilidad con OpenCode, GitHub Copilot, Gemini y otros agentes. Debe actuar como base para proponer y revisar sistemas visuales, sin permitir que reemplace la identidad existente de Autopartco.

Instalación por CLI documentada por el proyecto, desde la raíz del repo y solo después de revisar los cambios que generará:

```bash
npx ui-ux-pro-max-cli init --ai opencode
```

Para otro agente, sustituir `opencode` por el identificador soportado correspondiente. Revisar primero `--dry-run` cuando la versión instalada lo ofrezca. No usar `--force` hasta saber qué archivos serían sobrescritos.

#### B. Frontend Design de Anthropic — dirección visual distintiva

Repositorio: [anthropics/claude-code — frontend-design](https://github.com/anthropics/claude-code/tree/main/plugins/frontend-design)  
Sirve para evitar interfaces genéricas y decidir propósito, tono, tipografía, jerarquía y rasgo visual distintivo. Complementa UI/UX Pro Max; no reemplaza investigación de usuarios ni pruebas de accesibilidad. Su instalación directa depende de usar Claude Code/plugin; no se debe asumir que la app web de Claude Pro carga automáticamente skills locales.

#### C. Web Interface Guidelines de Vercel — auditoría de UI

Repositorio: [vercel-labs/web-interface-guidelines](https://github.com/vercel-labs/web-interface-guidelines)  
Útil como checklist para revisar etiquetas, estados, interacción, accesibilidad y calidad de interfaces existentes. La skill se usa como revisor del código implementado, no como generador de una estética completamente nueva.

Comando de instalación publicado por Vercel Skills para el catálogo:

```bash
npx skills add https://github.com/vercel-labs/agent-skills --skill web-design-guidelines
```

#### D. Design MD Guidelines — accesibilidad y consistencia

Skill de `github/awesome-copilot`: [gem-design-md-guidelines](https://github.com/github/awesome-copilot/tree/main/skills/gem-design-md-guidelines). Incluye criterios de contraste, foco, teclado, targets táctiles, movimiento y estados de interfaz. Se puede seleccionar si el flujo de diseño requiere una revisión de accesibilidad más explícita.

**Uso conjunto recomendado:** UI/UX Pro Max para elegir patrones/tokens; Frontend Design cuando se necesite una dirección visual característica; Web Interface Guidelines y/o Design MD Guidelines para revisar el resultado. No activar todos automáticamente para cada cambio menor.

### 15.2 Código, planificación y depuración

#### Superpowers
Repositorio: [obra/superpowers](https://github.com/obra/superpowers).  
Skills útiles: `brainstorming`, `writing-plans`, `test-driven-development`, `systematic-debugging`, `verification-before-completion`, `requesting-code-review`, `dispatching-parallel-agents` y `using-git-worktrees`. Usar el flujo TDD donde aporte valor y no convertir una metodología en un ritual rígido. La regla más importante es investigar la causa raíz y verificar con evidencia antes de declarar terminado.

#### Conocimiento del repositorio

`acquire-codebase-knowledge` del catálogo [GitHub Awesome Copilot](https://github.com/github/awesome-copilot). Complementar con Graphify ya generado en el proyecto. Antes de tocar código, consultar el grafo y leer los archivos relevantes; no confiar en un resumen antiguo sin comprobar la rama/versión real.

### 15.3 Backend, datos y calidad

- [`java-springboot`](https://github.com/github/awesome-copilot/tree/main/skills/java-springboot): guía para desarrollar de acuerdo con Spring Boot.
- [`spring-boot-testing`](https://github.com/github/awesome-copilot/tree/main/skills/spring-boot-testing): estrategias de pruebas para Spring Boot 4; verificar versiones de APIs y dependencias antes de copiar ejemplos.
- [`postgresql-code-review`](https://github.com/github/awesome-copilot/tree/main/skills/postgresql-code-review): revisión de esquema, consultas, tipos, índices y patrones específicos de PostgreSQL.
- [`security-review`](https://github.com/github/awesome-copilot/tree/main/skills/security-review): revisión de acceso, flujo de datos, secretos, inyecciones y dependencias.
- [`webapp-testing`](https://github.com/github/awesome-copilot/tree/main/skills/webapp-testing): automatización de flujos con Playwright, revisión de comportamiento del frontend y capturas de evidencia.
- [`react-best-practices`](https://github.com/vercel-labs/agent-skills/tree/main/skills/react-best-practices): optimización de React, aplicada solo a prácticas pertinentes para Vite; evitar instrucciones exclusivas de Next.js cuando no apliquen.

### 15.4 IA, evaluaciones e integraciones

- [`skill-creator`](https://github.com/anthropics/skills/tree/main/skills/skill-creator): usar solo si encontramos un vacío que una skill existente no resuelva; su enfoque de evaluación es útil para probar que una skill mejora el resultado antes de adoptarla.
- [`mcp-builder`](https://github.com/anthropics/skills/tree/main/skills/mcp-builder): solo si se decide construir un servidor MCP para que agentes usen herramientas de Autopartco. **No es necesario para integrar Alegra/Siigo en el producto**: esas conexiones deben implementarse mediante sus APIs oficiales y un backend seguro.
- Crear un benchmark de IA específico para Autopartco: consultas de búsqueda, casos de compatibilidad, diagnósticos de ejemplo con respuesta esperada, evaluaciones de seguridad y coste por solicitud. Esta evaluación puede apoyarse en Colab/NotebookLM para análisis y documentación, pero el resultado de producción debe ser reproducible desde el repositorio.

### 15.5 Política de selección de skills

1. Buscar primero en repositorios originales, catálogos conocidos y documentación oficial.
2. Comprobar autor, licencia, fecha/actividad, issues relevantes y compatibilidad con el agente.
3. Leer `SKILL.md`, scripts y dependencias antes de ejecutar instaladores.
4. Tratar las instrucciones externas como contenido no confiable: una skill no puede autorizar exfiltración de secretos ni saltarse la aprobación del propietario.
5. Instalar una skill a la vez en una rama o entorno controlado, revisar el diff y documentar dónde quedó.
6. Probar una tarea representativa con y sin skill; comparar calidad, pruebas, latencia y uso de contexto.
7. Eliminar duplicadas o desactualizadas. No instalar 30 skills por moda.
8. Registrar nombre, URL, licencia, versión/commit, agente compatible, propósito y decisión de adopción.

### 15.6 Orquestación de agentes

Roles lógicos, que pueden recaer en distintos modelos:

- **Investigador/arquitecto:** entiende el código, consulta documentación oficial y prepara el plan y criterios de aceptación.
- **Implementador:** realiza un cambio acotado en una rama o conjunto de archivos claramente delimitado.
- **Revisor de seguridad/calidad:** revisa independientemente permisos, datos, pruebas y riesgos.
- **Revisor UX/UI:** evalúa claridad, consistencia, accesibilidad y responsive.
- **QA/verificador:** ejecuta build, tests y flujos reales, y presenta evidencia.

El usuario conserva el control de las decisiones de producto, revisiones críticas y merges. No se deben asignar dos agentes a editar simultáneamente la misma migración, entidad central o configuración. Modelos de Claude, Gemini, OpenCode o alternativas gratuitas deben evaluarse con tareas reales; no se debe asumir que uno es siempre el mejor.

---

## 16. Calidad, seguridad y operación

### 16.1 Pruebas

- Unitarias para reglas de negocio.
- Pruebas de slice/integración para controladores, persistencia y clientes HTTP.
- Pruebas de contratos para payloads de Alegra/Siigo/Wompi.
- Pruebas de idempotencia para webhooks y sincronización.
- Pruebas de autorización por organización, sucursal y recurso.
- Pruebas E2E con Playwright para flujos completos.
- Pruebas de responsive y accesibilidad en pantallas principales.
- Datos de demostración sintéticos, identificados como tales y nunca confundidos con datos reales.

### 16.2 Seguridad

Tomar como referencia OWASP API Security Top 10 ([fuente oficial](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)), con especial atención a autorización a nivel de objeto, autenticación, acceso a propiedades, límites de consumo y configuración. Revisar además dependencias, secretos y seguridad específica de pagos e IA.

Controles mínimos:

- Autorización server-side en cada operación que acceda a una organización, sucursal, pedido o ticket.
- Principio de mínimo privilegio, separación de root admin y soporte.
- Validación de payloads y límites de tamaño/tasa.
- Tokens/secretos cifrados o guardados en un gestor de secretos y redactados de logs.
- Rotación/revocación de credenciales cuando se pueda.
- No confiar en cantidades, precios, permisos o resultados de pago enviados por el navegador.
- Auditoría de acciones administrativas e integración.
- Política de retención, exportación y eliminación de datos, con revisión de obligaciones aplicables en Colombia.
- Protección de archivos, tipos MIME, tamaños, permisos y URL de acceso.
- Protección contra abuso de funciones de IA y gasto descontrolado.

### 16.3 Observabilidad

La aplicación debe registrar logs estructurados, métricas y trazas donde aporte valor, siguiendo los recursos de observabilidad de Spring Boot/Micrometer ([documentación oficial](https://docs.spring.io/spring-boot/reference/actuator/observability.html)). No registrar contraseñas, tokens, claves ni contenido sensible innecesario.

Alertas iniciales: errores 5xx, fallos repetidos de sincronización, webhooks no procesados, pagos en estados inconsistentes, colas atrasadas, errores de base de datos y gasto/latencia de IA fuera de límites.

### 16.4 Respaldo y recuperación

Definir frecuencia de copias, cifrado, retención, restauración de prueba, recuperación de credenciales e incidentes. Una copia de seguridad no cuenta como estrategia hasta que se haya probado restaurarla.

### 16.5 Rendimiento

Medir antes de optimizar. Prioridades: consultas de catálogo y filtros, paginación, imágenes, importaciones por lotes, llamadas externas, consultas N+1 y renderizaciones innecesarias. Redis, colas externas o microservicios se deciden cuando el perfil real lo justifique.

---

## 17. Hoja de ruta y entregables

El desarrollo se divide por resultados verificables, no por cantidad de pantallas ni por cantidad de prompts a los agentes.

### Fase 0 — Descubrimiento y línea base

**Entregables:** inventario de módulos/rutas/endpoints/tablas; diagrama de arquitectura actual; lista de dependencias y riesgos; build repetible; tests actuales ejecutados; inventario de funciones funcionales/parciales/ausentes; registro de skills seleccionadas; backlog priorizado.

**Criterio de salida:** se puede explicar qué existe, qué compila, qué prueba pasa y qué no se ha verificado. No se hacen migraciones destructivas por anticipado.

### Fase 1 — Fundación y UX del producto

**Entregables:** mapa de navegación de las cuatro superficies; matriz de roles y permisos; design system base; componentes/estados de UI; contratos API principales; estructura multiempresa/sucursal revisada; estrategia de migraciones y errores.

**Criterio de salida:** los flujos críticos están diseñados y la autorización se puede probar en backend.

### Fase 2 — Portal comercial, catálogo e inventario

**Entregables:** onboarding/verificación, sucursales, colaboradores, gestión de productos, CSV, inventario por sucursal, movimientos, catálogo público mínimo y auditoría básica.

**Criterio de salida:** un almacén piloto puede publicar catálogo y manejar cantidades por sucursal sin editar tablas manualmente.

### Fase 3 — Integraciones Alegra y Siigo

**Entregables:** arquitectura de conectores, conexión segura, mapeo de sucursales, preview/importación, sincronización inicial, historial, reintentos, webhooks si están disponibles y reconciliación, pruebas con cuentas de sandbox/prueba, fallback CSV.

**Criterio de salida:** las integraciones sobreviven a errores previsibles y permiten a un usuario entender qué se sincronizó, qué no y cómo resolverlo.

### Fase 4 — Marketplace y ciclo de pedido

**Entregables:** búsqueda/filtros/detalle, tienda pública, carrito, validación de disponibilidad, creación de pedido, estados, seguimiento, reseñas/reportes y notificaciones iniciales.

**Criterio de salida:** una compra de prueba recorre desde la búsqueda hasta el cierre del pedido, con permisos y precio histórico correctamente validados.

### Fase 5 — Pagos de demostración y monetización

**Entregables:** proveedor de pago abstracto, Wompi sandbox o simulador, webhooks validados, planes versionados, periodo de prueba de un mes, límites de planes y panel de suscripción.

**Criterio de salida:** se pueden demostrar estados de pago aprobados/rechazados/pendientes sin procesar dinero real, y la aplicación no confía en la redirección del navegador.

### Fase 6 — Operaciones y soporte

**Entregables:** consola root, revisión de tiendas, moderación, gestión de reportes, soporte con roles limitados, registro de acciones y métricas operativas.

**Criterio de salida:** las decisiones internas son trazables y el personal de soporte no tiene permisos superiores a los necesarios.

### Fase 7 — Inteligencia aplicada

**Entregables:** búsqueda semántica basada en catálogo real, compatibilidad con procedencia, primer copiloto de diagnóstico limitado, evaluación de respuestas, feedback y límites de coste.

**Criterio de salida:** las salidas están estructuradas, se muestran niveles de incertidumbre, las recomendaciones apuntan a datos reales y existe un conjunto de pruebas de regresión.

### Fase 8 — Analítica predictiva y preparación de producción

**Entregables:** eventos de negocio, paneles fiables, snapshots de inventario, modelo base de demanda evaluado si hay datos, alertas, observabilidad, backups/restauración, seguridad, carga, documentación y despliegue.

**Criterio de salida:** el producto tiene evidencia de estabilidad, procedimientos de soporte y límites documentados. Una función se declara lista solo tras ejecutar las comprobaciones definidas.

---

## 18. Definición de MVP comercial

El MVP no debe incluir todas las ideas avanzadas. Debe incluir un ciclo de valor que un almacén pueda probar y por el que eventualmente pagaría.

### Obligatorio para el primer MVP funcional

- Marketplace público con búsqueda, filtros, detalle y página de tienda.
- Registro/autenticación y controles de acceso coherentes.
- Solicitud/verificación de almacén y configuración de sucursales.
- Gestión de productos y existencias por sucursal.
- Importación CSV con preview, errores por fila y reintento.
- Integración inicial de Alegra y Siigo bajo alcance de campos documentado, no como promesa genérica.
- Carrito/pedido y estados básicos.
- Pago en demo mediante simulador y/o sandbox validado.
- Plan de prueba gratuita por un mes y planes configurables.
- Roles de colaboradores y permisos de administración.
- Root admin, verificación, moderación y soporte esencial.
- Registros de auditoría e historial de sincronización.
- Pruebas de seguridad de tenant, pruebas E2E de flujos críticos y documentación para desplegar la demo.
- UI responsiva, accesible y consistente con identidad Autopartco.

### Después del MVP

- Voz/imágenes/OBD-II en diagnóstico.
- Predicción avanzada para cada producto cuando no haya suficientes datos.
- Compras automáticas a proveedores y gestión completa de abastecimiento.
- Multi-vendedor avanzado en un solo carrito, si complica la liquidación.
- Payouts/dispersión automática a vendedores.
- Facturación tributaria completa dentro de Autopartco; la primera opción será integrarse con el ERP cuando esté soportado.
- Aplicación móvil nativa; inicialmente se prioriza una web responsiva. No asumir que debe ser PWA.
- Microservicios, event streaming o infraestructura compleja sin justificación.

---

## 19. Criterios globales de aceptación

Una funcionalidad se considera lista solo cuando:

1. Cumple criterios funcionales definidos previamente.
2. Respeta permisos en backend y tiene pruebas negativas de acceso.
3. Tiene estados de carga, vacío, error, éxito y permisos insuficientes pertinentes.
4. Se adapta a los tamaños de pantalla soportados.
5. No añade dependencias sin una razón y revisión.
6. Tiene pruebas acordes con el riesgo: unitarias, integración, contrato o E2E.
7. Los errores son entendibles, no revelan secretos y ofrecen una acción posible.
8. Tiene logs/telemetría pertinentes sin datos sensibles innecesarios.
9. La documentación y la configuración de ejemplo están actualizadas.
10. Los comandos de validación se ejecutaron en esta versión y el resultado está registrado.
11. Se han revisado migraciones, efectos sobre datos existentes y procedimiento de rollback cuando aplique.
12. Los cambios de UX han pasado revisión visual, accesibilidad y navegación.

No debe afirmarse “compila”, “pasa pruebas” o “está integrado” hasta ejecutar los comandos o las pruebas pertinentes y revisar su salida.

---

## 20. Métricas de éxito del producto

### Para compradores

- Menor porcentaje de búsquedas sin resultado.
- Más búsquedas que terminan en una ficha relevante o un contacto/pedido.
- Menos cancelaciones por incompatibilidad o falta de stock.
- Buena comprensión del estado del pedido y de las políticas de venta.

### Para almacenes

- Tiempo para importar/publicar un catálogo inicial.
- Porcentaje de productos con datos completos.
- Porcentaje de sincronizaciones exitosas y retraso medio.
- Diferencias de existencias detectadas y resueltas.
- Tiempo de procesamiento de pedido.
- Activación, retención y renovación de vendedores.

### Para Autopartco

- Conversión de prueba gratuita a plan de pago.
- Retención y cancelación por plan.
- Margen neto por plan tras costes de infraestructura, soporte, pasarela e IA.
- Coste por búsqueda asistida y diagnóstico.
- Tasa de errores/incidentes por módulo.
- Tiempo medio de resolución de tickets.

Los objetivos numéricos se definen con los primeros pilotos y un periodo de referencia; no inventar benchmarks sin evidencia.

---

## 21. Gobernanza del desarrollo con agentes

1. Mantener este documento como contrato de producto y cambiarlo deliberadamente cuando cambie el alcance.
2. Antes de una tarea, el agente lee las instrucciones del proyecto y usa Graphify para localizar archivos relevantes si el grafo corresponde a la rama actual.
3. La tarea contiene objetivo, contexto, límites, archivos/módulos probables, criterios de aceptación y comandos de validación.
4. Investigar documentación oficial antes de utilizar APIs externas o dependencias con versiones cambiantes.
5. Implementar en una rama acotada.
6. El revisor debe ser independiente y cuestionar permisos, migraciones, errores, estados UX y casos límite.
7. QA ejecuta build/tests/E2E y comparte evidencia reproducible.
8. El usuario decide el merge, cambios de alcance y decisiones comerciales.
9. Cualquier agente debe reportar archivos modificados, decisiones, supuestos, pruebas ejecutadas, resultados y pendientes.
10. No paralelizar cambios concurrentes en el mismo núcleo de datos o migraciones.

### Plantilla de tarea para agentes

```text
Objetivo:
Problema de usuario/negocio:
Contexto del código existente (rutas/archivos leídos):
Qué está dentro del alcance:
Qué queda fuera:
Reglas de arquitectura y seguridad:
Criterios de aceptación verificables:
Pruebas que deben ejecutarse:
Restricciones de dependencias y versiones:
Entregables esperados:
Riesgos y decisiones que requieren aprobación humana:
```

---

## 22. Estado conocido del repositorio y límites de la auditoría

En las revisiones previas de archivos y documentación se observaron una base de backend Java/Spring Boot/PostgreSQL, autenticación/autorización, un frontend React/TypeScript/Vite, módulos relacionados con catálogo/tiendas/pedidos y configuración de propiedades de Wompi. También se identificó una página de diagnóstico basada en reglas/palabras clave del frontend, que no equivale por sí misma a una integración de IA generativa ni a un modelo predictivo.

Se observó una diferencia que debe resolverse entre documentación sobre gestión explícita del esquema y una configuración de Hibernate que parecía indicar `ddl-auto: update`. Se deben revisar las versiones de dependencias, la ubicación de copias/artefactos bajo `backend/bin/`, y el alcance real de los servicios actuales. El repositorio contiene o ha generado datos de Graphify que ayudan a navegar el código.

**Límite importante:** la revisión de contexto y archivos no constituye una compilación, una prueba completa de endpoints, una auditoría de seguridad exhaustiva ni una verificación de los flujos de pago/integración contra cuentas de prueba. No asumir que una ruta, propiedad, tabla o página implica que la función está operativa end-to-end.

La siguiente acción técnica debe ser una auditoría en un entorno controlado: confirmar la rama correcta, instalar dependencias de manera reproducible, ejecutar build y tests, inspeccionar esquema y configuración por entorno, probar flujos críticos y documentar resultados antes de realizar cambios grandes.

---

## 23. Decisiones por cerrar antes de comprometer el alcance comercial

Este documento define la dirección del producto; las siguientes decisiones requieren evidencia o validación de negocio. Se deben cerrar antes de escribir la lógica dependiente:

1. **Flujo de venta:** ¿Autopartco cobra al comprador en la plataforma o dirige inicialmente la operación al almacén? El checkout de demostración puede probarse sin dinero real, pero el modelo de producción requiere decisión.
2. **Marketplace multi-vendedor:** ¿un carrito puede mezclar productos de varios almacenes, o cada pedido queda asociado a una tienda/sucursal? Para MVP se recomienda pedido por vendedor para reducir complejidad.
3. **Autoridad de inventario:** si hay ventas en Autopartco y en el ERP, ¿qué sistema confirma/reserva stock? Recomendación provisional: declarar una política única por tienda y usar reservas y reconciliación; nunca sincronizar de forma ciega.
4. **Alcance exacto de Alegra y Siigo:** lista por endpoint/campo, límites, comportamiento de webhooks y permisos accesibles a una cuenta real de prueba.
5. **Modelo de planes:** límites incluidos para productos, sucursales, usuarios, frecuencia de sincronización, analítica y soporte; precio COP y condiciones tras el mes de prueba.
6. **Comisión comercial:** si existe, porcentaje/base de cálculo, quién asume la tarifa de pasarela, impuestos, cancelaciones, reembolsos y cómo se comunica el cobro.
7. **Entrega y logística:** entrega por tienda, recogida, integración futura con transportadoras o solo coordinación directa al comienzo.
8. **Reseñas:** si se exige compra verificada, periodo de publicación, moderación y apelaciones.
9. **Fuentes de compatibilidad:** licencias, calidad, coste y cobertura del catálogo de vehículos/referencias.
10. **Datos personales:** retención, consentimiento, eliminación/exportación y procedimientos de atención según la normativa aplicable en Colombia.
11. **Infraestructura y despliegue:** hosting, almacenamiento de imágenes, backups, monitorización y presupuesto mensual objetivo.
12. **Canales de notificación:** correo como base; WhatsApp solo después de evaluar proveedor, consentimiento, costes y plantillas.

Para avanzar sin bloquear el proyecto, se pueden usar decisiones provisionales señaladas explícitamente, pero no convertirlas silenciosamente en compromisos irreversibles.

---

### 23.1 Decisiones recomendadas por defecto para cerrar el MVP

Para reducir ambigüedad y que el equipo pueda comenzar, propongo estas decisiones provisionales como baseline. Se pueden cambiar mediante una decisión explícita, pero un agente no debe decidirlas por su cuenta.

| Tema | Decisión recomendada para MVP | Motivo |
|---|---|---|
| Pedidos multi-vendedor | Un pedido pertenece a un solo vendedor/sucursal. Si el carrito mezcla almacenes, dividirlo de forma visible o pedir al usuario finalizar pedidos por separado. | Reduce la complejidad de estados, pagos, devoluciones y conciliación. |
| Pago | La demo soporta simulador local y, cuando se haya validado, Wompi sandbox. No se procesará dinero real en la primera demo. | Permite demostrar el ciclo end-to-end sin asumir obligaciones de producción prematuramente. |
| Inventario de almacén sin ERP | Autopartco es la fuente de verdad para catálogo, precio y stock operados manualmente. | Evita flujos ambiguos en el primer piloto. |
| Inventario de almacén conectado a ERP | Por defecto, el ERP es autoridad para campos base que exponga (SKU, precio y stock físico); Autopartco conserva reservas, pedidos y datos enriquecidos del marketplace. Los pedidos confirmados se sincronizan al ERP solo mediante un flujo documentado y soportado. | Minimiza edición duplicada y evita sobrescribir compatibilidad o metadatos propios. Requiere reconciliación para ventas externas y límites de la API. |
| Conflicto de stock | Separar cantidad física, cantidad reservada y cantidad vendible. Si se detecta discrepancia, marcarla y ejecutar la política de reconciliación; nunca ocultarla. | Evita vender silenciosamente inventario inexistente. |
| Entrega | Para el MVP, recogida en la sucursal o entrega coordinada directamente por el vendedor. Integraciones con transportadoras quedan posteriores. | Evita bloquear el lanzamiento por logística compleja. |
| Facturación | Alegra/Siigo permanecen como sistema contable/fiscal del negocio. Autopartco puede enviarles pedidos/documentos si la API y la cuenta lo permiten; no intenta reemplazar la facturación tributaria en el MVP. | Evita duplicar el ERP y asumir obligaciones fiscales sin alcance definido. |
| Periodo de prueba | Prueba de 30 días, con límites y fecha de finalización visible; el precio posterior se muestra antes de activar una suscripción pagada. | Hace el proceso comercial comprensible. El requisito de tarjeta y el mecanismo de cobro se deciden después. |
| Planes | Plan pequeño, mediano y grande, con límites administrables de sucursales, colaboradores, catálogo, integraciones y analítica. No fijar precios hasta validar costos y pilotos. | Permite estructurar el producto sin inventar una tarifa. |
| Comisión | Separar en la UI la suscripción, cualquier comisión de marketplace y cualquier cargo de la pasarela. No habilitar comisión de marketplace hasta definir base de cálculo, cancelaciones y liquidación. | Evita confusión de cobros y ambigüedad operativa. |
| Reseñas | Priorizar reseñas asociadas a compras verificables; los reportes quedan sujetos a moderación y apelación. | Aumenta confianza y reduce manipulación. |
| Compatibilidad | Presentar por separado compatibilidad verificada, declarada por vendedor y sugerida por IA. Solo una fuente revisada permite marcarla como confirmada. | Evita vender una pieza incorrecta por una inferencia del modelo. |
| Notificaciones | Correo y notificaciones dentro de la aplicación como base; WhatsApp se añade tras validar proveedor, consentimiento, plantillas y coste. | Controla gasto y complejidad inicial. |
| Cliente móvil | Web responsiva primero; no construir app nativa ni asumir que se requiere PWA para MVP. | Reutiliza el stack existente y acelera la validación. |
| Analítica predictiva | Primero métricas descriptivas y alertas deterministas; forecasting solo con historial suficiente y evaluación temporal. | Evita pronósticos con falsa precisión. |
| IA diagnóstica | Empezar por orientación textual con preguntas aclaratorias y mensajes de seguridad; imagen/audio/OBD-II queda para otra fase. | Reduce riesgo técnico y mejora evaluabilidad. |

Estos defaults permiten priorizar el trabajo sin fingir que los detalles comerciales aún no validados ya están decididos por el usuario.

## 24. Recursos de referencia

### Skills y desarrollo asistido

- [UI/UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)
- [Frontend Design de Anthropic](https://github.com/anthropics/claude-code/tree/main/plugins/frontend-design)
- [Vercel Web Interface Guidelines](https://github.com/vercel-labs/web-interface-guidelines)
- [Vercel Agent Skills](https://github.com/vercel-labs/agent-skills)
- [GitHub Awesome Copilot Skills](https://github.com/github/awesome-copilot/blob/main/docs/README.skills.md)
- [obra/superpowers](https://github.com/obra/superpowers)
- [Anthropic Skill Creator](https://github.com/anthropics/skills/tree/main/skills/skill-creator)

### APIs y seguridad

- [Alegra Developer](https://developer.alegra.com/)
- [Alegra — productos y servicios](https://developer.alegra.com/docs/productos-y-servicios)
- [Siigo API](https://developers.siigo.com/docs/siigoapi/)
- [Wompi Colombia — transacciones](https://docs.wompi.co/docs/colombia/transacciones/)
- [Wompi Colombia — pruebas sandbox](https://docs.wompi.co/docs/colombia/pruebas-sandbox-pagos-a-terceros/)
- [OWASP API Security Top 10](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [Spring Boot Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [OpenCode Skills](https://docs.opencode.ai/docs/skills/)
- [Skills CLI — vercel-labs/skills](https://github.com/vercel-labs/skills)

---

## Conclusión

Autopartco debe construirse como una plataforma de negocio con tres capas de valor conectadas: **venta y descubrimiento de repuestos, herramientas de operación para los almacenes, e inteligencia basada en datos confiables**. La sincronización con Alegra y Siigo reduce el trabajo duplicado y es un diferenciador relevante para captar vendedores. La IA debe mejorar búsqueda, calidad de datos, orientación y decisiones de inventario, pero siempre con procedencia, incertidumbre, límites y pruebas. La experiencia de usuario debe ser clara y verificable en cada flujo.

La siguiente etapa no es programar todas las ideas al mismo tiempo: es realizar la auditoría ejecutable, cerrar las decisiones comerciales que afectan al diseño, confirmar el modelo de datos y convertir la hoja de ruta en historias con criterios de aceptación. El documento podrá evolucionar con evidencia, manteniendo una sola definición compartida de qué se está construyendo y por qué.
