Autopartco: plan para convertirlo en un producto tecnológico profesional
He comenzado a revisar el repositorio privado autopartco/autopartco-dev, incluyendo el README principal, las convenciones de API, la documentación de seguridad, la configuración de Maven, las instrucciones de Copilot y partes del esquema SQL y del frontend.
Mi recomendación inicial es evolucionar el Autopartco que ya tienes, no empezar desde cero ni reescribir todo con IA. Ya existe una base de negocio y de arquitectura que conviene conservar, pero antes de incorporar muchas funcionalidades debemos determinar qué está implementado, qué funciona de extremo a extremo, qué necesita refactorización y qué todavía es solo una idea.
El objetivo debería ser construir un marketplace de autopartes para Colombia que pueda demostrar flujos empresariales reales, con inteligencia artificial útil, datos que permitan tomar decisiones y una arquitectura que pueda crecer sin convertirse en un sistema inmanejable.
1. Lo que ya tienes: auditoría preliminar del repositorio
Base de backend y seguridad
Spring Boot 4.1.1, Java 21, Maven, PostgreSQL, Spring Security, JWT, verificación de correo, sesiones y permisos. Hay documentación específica para estos flujos.
Explorar backend



Modelo de negocio multi-vendedor
Ya se contemplan almacenes, sucursales, inventarios, compatibilidad vehicular, productos, pedidos, ventas, planes comerciales, solicitudes de vendedores y colaboradores con permisos.
Consultar esquema SQL



Frontend con áreas de negocio diferenciadas
React, TypeScript, Vite y Tailwind CSS v4. El enrutador ya contempla catálogo, diagnóstico, autenticación, solicitud de vendedor, configuración de tienda y panel de administración comercial con productos, sucursales, pedidos, promociones y colaboradores.
Consultar rutas del frontend



El diagnóstico no es todavía un diagnóstico con IA
La página DiagnosticPage.tsx utiliza reglas y palabras clave en el frontend para asociar síntomas con posibles fallas. Es un punto de partida útil, pero no equivale a un modelo de IA integrado con el backend, datos del vehículo, historial y evaluación de incertidumbre.
Revisar implementación actual



La pasarela ya tiene indicios de integración
Encontré configuración para Wompi, claves, URL de checkout, secretos de eventos y una ruta de retorno de pagos. Debemos revisar el servicio y el webhook para saber si el flujo está completo, si puede operar en sandbox y qué ocurre con la confirmación del pago.
Consultar configuración de Wompi



Hallazgos técnicos que atendería antes de añadir complejidad
- Configuración de la base de datos: application.yaml utiliza ddl-auto: update, mientras que la documentación también describe un esquema administrado mediante SQL. Para un producto serio, conviene adoptar migraciones versionadas y desactivar la modificación automática del esquema en producción.
- Dependencias desalineadas: el pom.xml declara versiones explícitas para algunas dependencias, como Jackson y el procesador de configuración, que no siguen la misma versión principal de Spring Boot. Hay que comprobar su compatibilidad antes de actualizar.
- Duplicación de artefactos: Graphify detecta copias de documentación y código bajo backend/bin/. Debemos comprobar si son artefactos generados, copias accidentales o parte del flujo de compilación, y evitar mantener varias fuentes de verdad.
- Arquitectura modular: ya existe separación por dominios y capas. No hace falta saltar inmediatamente a microservicios: primero debemos definir límites claros entre módulos, contratos y responsabilidades.
- Calidad verificable: el repositorio incluye dependencias de testing, pero todavía no he confirmado la cobertura real de las pruebas, el éxito de la compilación ni el comportamiento de todos los flujos de negocio.
Estas observaciones son una auditoría inicial del código y la documentación accesibles por GitHub; no significan que haya ejecutado el proyecto ni que haya validado cada endpoint en tiempo de ejecución.
2. Qué producto construiría
No haría simplemente un marketplace con más pantallas. Construiría Autopartco como una plataforma con cuatro productos conectados:









A. Marketplace para conductores y mecánicos
Catálogo, búsqueda por vehículo, comparación entre almacenes, compatibilidad de repuestos, cotizaciones, carrito, pedidos, seguimiento, reseñas y atención.












B. SaaS para almacenes
Gestión de sucursales, inventario, ventas, colaboradores, compras a proveedores, alertas de stock, precios, analítica, suscripciones y automatización comercial.












C. Inteligencia automotriz
Asistente de diagnóstico, identificación de repuestos, recomendación de compatibilidad, historial del vehículo, mantenimiento preventivo y predicción de demanda.












D. Operaciones internas de Autopartco
Administración root, moderación de tiendas, verificación comercial, soporte, disputas, gestión de planes, pagos, auditoría, métricas y detección de anomalías.



La ventaja competitiva no sería únicamente incorporar un chatbot. Sería conectar el diagnóstico con el vehículo correcto, los repuestos realmente compatibles, el inventario disponible cerca del comprador y los datos operativos que mejoran las recomendaciones con el tiempo.

3. Funcionalidades diferenciadoras
Estas son las que priorizaría por su valor para el negocio, no solo por lo llamativas que resultan en una demostración.









1. Autopartco Copiloto — diagnóstico asistido por IA
Prioridad alta
El usuario describe los síntomas, selecciona marca, modelo, año y motorización, añade cuándo ocurre la falla y, opcionalmente, adjunta audio, imágenes o códigos OBD-II.
El sistema devuelve hipótesis ordenadas, nivel de incertidumbre, preguntas de seguimiento, comprobaciones recomendadas, señales de peligro y repuestos relacionados. Debe advertir cuándo no es seguro seguir conduciendo y evitar afirmar que ha identificado una avería con certeza cuando no hay evidencia suficiente.












2. Compatibilidad de repuestos con evidencia
No recomendar piezas solo porque coinciden en nombre o categoría. Combinar marca, referencia OEM, equivalencias, generación, motor, año y atributos técnicos.
Cada coincidencia debería indicar si está confirmada por catálogo, declarada por el vendedor o inferida por un modelo. Así se reducen devoluciones y errores de compra.












3. Autopartco Intelligence — analítica y predicción
Pronóstico de demanda por producto y sucursal, riesgo de agotamiento, inventario inmovilizado, estacionalidad, ventas esperadas y sugerencias de reposición.
Cada pronóstico debe mostrar horizonte, fecha de actualización, calidad del modelo y datos utilizados. Si no hay suficiente historial, se deben mostrar reglas y tendencias descriptivas, no predicciones artificialmente precisas.












4. Inventario inteligente multi-sucursal
Importación de CSV, alertas de stock mínimo, sugerencias de transferencia entre sucursales, detección de diferencias de inventario y trazabilidad de ajustes.
A futuro, puede recomendar compras a proveedores teniendo en cuenta la demanda estimada, el tiempo de reposición y el capital inmovilizado.












5. Buscador de repuestos por necesidad
El usuario explica qué necesita sin conocer el nombre técnico. Autopartco interpreta la consulta, pide aclaraciones cuando sean necesarias y compara disponibilidad, compatibilidad, precio, distancia, entrega y reputación.
La búsqueda debería distinguir una coincidencia exacta de una alternativa compatible, y no ocultar limitaciones para aumentar conversiones.












6. Garaje digital e historial de mantenimiento
El cliente registra sus vehículos, reparaciones, piezas instaladas, kilometraje y próximos mantenimientos. Las recomendaciones deben basarse en el manual o intervalos documentados cuando estén disponibles, no en intervalos inventados por la IA.
Esto crea recurrencia: Autopartco deja de ser un sitio al que se entra solo cuando se necesita una pieza.



Otras funciones de alto valor: cotizaciones para talleres con varios repuestos, listas de compra compartidas, seguimiento de garantías y devoluciones, verificación de vendedores, detección de publicaciones duplicadas, recomendaciones de productos complementarios y un centro de soporte con contexto del pedido.
4. Arquitectura: separar responsabilidades sin crear microservicios innecesarios
Tu idea de separar la tienda, los empleados, los administradores y el soporte es correcta. Sin embargo, hay que distinguir entre separar interfaces, separar módulos de negocio y desplegar servicios independientes.
No son la misma cosa.
Arquitectura que recomiendo
Empezaría con un monolito modular en Spring Boot, un frontend organizado por aplicaciones funcionales y un servicio de IA independiente cuando realmente lo necesitemos. Así mantienes un proyecto manejable, con límites internos claros y la posibilidad de extraer servicios posteriormente.
Frontend React + TypeScript
Marketplace · Portal de almacenes · Consola root · Soporte

API REST — Spring Boot
Autenticación JWT · Autorización · Validación · Auditoría

Catálogo y vehículos
Productos · Compatibilidad


Ventas y pagos
Carrito · Pedidos · Wompi


Organizaciones
Almacenes · Sucursales · Roles


Plataforma
Root · Soporte · Planes


Inteligencia
Diagnóstico · Recomendaciones


Analítica
Eventos · Indicadores · Forecast



Persistencia e integraciones
PostgreSQL · Almacenamiento de archivos · Proveedor de IA · Pasarela de pagos · Correo


Las interfaces que separaría
Aplicación	Responsabilidad
Marketplace	Buscar, comparar, comprar, consultar pedidos y administrar vehículos personales.
Portal comercial	Configurar el almacén, gestionar productos, inventarios, sucursales, empleados, ventas y analítica.
Consola root	Administrar la plataforma completa, aprobar vendedores, suspender cuentas, gestionar planes y consultar auditorías.
Centro de soporte	Gestionar tickets, conversaciones, incidencias, devoluciones y escalaciones.
Pueden compartir autenticación, componentes de diseño y contratos de API sin compartir todas las pantallas ni las reglas de autorización.
Regla de seguridad importante: ocultar una ruta en React no protege sus datos. Spring Security debe comprobar los permisos en el servidor y verificar que cada usuario tenga acceso al almacén, sucursal, pedido o ticket solicitado. Las operaciones root deben tener privilegios específicos, auditoría y controles reforzados.
Estructura del backend
Conservaría tu organización actual por dominios, pero definiría módulos explícitos con dependencias controladas:
backend/src/main/java/com/autopartco/backend/
├── auth/
├── organization/
├── store/
├── catalog/
├── vehicle/
├── inventory/
├── order/
├── payment/
├── subscription/
├── intelligence/
├── analytics/
├── support/
├── administration/
├── notification/
├── audit/
├── common/
└── config/


Esto es una estructura objetivo, no una instrucción para renombrar todo de inmediato. Por ejemplo, product, seller y store ya existen en el repositorio: antes de moverlos, hay que revisar sus dependencias y contratos.
Cada módulo debe exponer servicios y operaciones bien definidos. Evitaría que cualquier módulo acceda directamente a las entidades y repositorios internos de los demás.
5. Cómo mejoraría la base de datos
Tu esquema actual ya tiene fundamentos útiles: UUID, relaciones entre entidades, estados, borrado lógico en varias tablas, campos de auditoría, inventarios por sucursal y tablas de compatibilidad. No conviene reemplazarlo indiscriminadamente.
La mejora consiste en identificar qué datos necesita realmente un negocio, qué relaciones faltan, qué información debe conservarse históricamente y cómo se analizarán los resultados.
Entidades y capacidades que revisaría
Dominio	Mejoras propuestas
Identidad y acceso	Organizaciones, membresías, roles, permisos por recurso, invitaciones, sesiones, auditoría y aislamiento entre almacenes.
Catálogo automotriz	Vehículos normalizados, generaciones, motores, referencias OEM, equivalencias, especificaciones y fuentes de compatibilidad.
Inventario	Existencias, reservas, movimientos, entradas, salidas, transferencias, ajustes y costos de adquisición.
Compras y proveedores	Proveedores, órdenes de compra, recepciones, tiempos de reposición y costos históricos.
Ventas	Pedidos, líneas de pedido, ventas por vendedor, precios históricos, descuentos, devoluciones, garantías y estados de entrega.
Pagos	Intentos, transacciones, referencias externas, eventos de webhook, reembolsos simulados o reales según el entorno.
Suscripciones	Planes versionados, suscripciones, períodos de prueba, facturas, límites de uso y eventos de renovación.
Inteligencia artificial	Sesiones de diagnóstico, síntomas, hipótesis, fuentes, nivel de confianza, evaluaciones y feedback.
Analítica	Eventos de negocio, métricas agregadas, snapshots de inventario y resultados de modelos predictivos.
Soporte	Tickets, mensajes, adjuntos, responsables, SLA, categorías y relación con pedidos o cuentas.
Tres cambios especialmente importantes
1. Historial de movimientos de inventario.
No basta con conocer el stock actual. Debemos registrar por qué cambió:
- Venta o reserva.
- Recepción de una compra.
- Transferencia entre sucursales.
- Devolución.
- Ajuste manual con motivo y responsable.
Así puedes reconstruir existencias históricas, medir pérdidas, detectar errores y entrenar modelos de demanda con datos útiles.
2. Catálogo de compatibilidad con procedencia.
Una relación entre producto y vehículo debería guardar, según corresponda, la fuente, el tipo de equivalencia, la evidencia, la fecha de validación y quién confirmó el dato. Esto permite diferenciar información confiable de una sugerencia automática.
3. Modelo analítico separado del transaccional.
PostgreSQL puede servir inicialmente para ambas cosas. Empezaría con vistas y consultas agregadas; después incorporaría tablas de hechos y dimensiones si el volumen lo justifica.
Por ejemplo, un modelo analítico podría tener:
- fact_sales: unidades, ingresos, descuentos y costos por línea de venta.
- fact_inventory_daily: existencias y valoración por sucursal y producto.
- fact_product_searches: búsquedas, clics, resultados sin coincidencias y conversiones.
- Dimensiones de fecha, producto, sucursal, vendedor y categoría.
Es importante definir bien los indicadores: ingresos no son lo mismo que margen, pedidos no son lo mismo que unidades vendidas y un producto sin ventas durante una semana no necesariamente es inventario obsoleto.
Migraciones y calidad de datos
Adoptaría Flyway o Liquibase, previa comprobación de las dependencias y del esquema existente. El principio es que cada cambio de base de datos tenga una migración versionada, revisable y probada.
También establecería:
- Restricciones CHECK, UNIQUE y claves foráneas donde correspondan.
- Índices basados en consultas reales.
- Fechas con una política coherente de zona horaria.
- Importes en unidades monetarias enteras o tipos decimales adecuados, sin cálculos monetarios con float.
- Registro de cambios de precio, stock y estados.
- Estrategias de retención, privacidad y eliminación de datos personales.
- Pruebas de migración desde una base vacía y desde la versión anterior.
Antes de modificar init.sql, haría un inventario de todas las tablas existentes, sus entidades JPA, sus referencias y las migraciones históricas. No aplicaría cambios destructivos sobre una base que contenga datos importantes.

6. IA y modelos predictivos: qué usaría realmente
No usaría un LLM para todo. Hay tareas que requieren lenguaje e interpretación; otras necesitan reglas deterministas; y otras necesitan modelos entrenados con datos históricos.
IA generativa: diagnóstico y asistencia
Interpreta síntomas en lenguaje natural, hace preguntas, resume evidencias y propone hipótesis. Debe trabajar con un esquema de salida estructurado y herramientas autorizadas para consultar el catálogo de vehículos y repuestos.


Modelos predictivos: demanda e inventario
Predicen unidades vendidas por producto y sucursal, riesgo de agotamiento y tiempos de reposición. Se entrenan con ventas, disponibilidad, precios, promociones, estacionalidad y datos de contexto relevantes.


Sistemas de recomendación
Ordenan alternativas compatibles usando precio, distancia, disponibilidad, calidad de datos, reputación y preferencias. Deben explicar por qué una pieza se recomienda y evitar presentar compatibilidad incierta como confirmada.


Detección de anomalías
Señala posibles errores de inventario, precios atípicos, patrones inusuales de pedidos y comportamientos que merecen revisión humana. Una anomalía no demuestra por sí sola fraude.


El diagnóstico automotriz que implementaría
Flujo propuesto:
1. El usuario selecciona su vehículo o lo registra en su garaje.
2. Describe síntomas y circunstancias; puede añadir códigos de error y archivos.
3. El backend valida los datos y consulta las fuentes disponibles.
4. El servicio de IA produce hipótesis estructuradas y solicita información adicional si falta evidencia.
5. Se aplican reglas de seguridad para identificar síntomas que requieren detenerse o acudir a un profesional.
6. Se relacionan las hipótesis con componentes y repuestos, distinguiendo compatibilidad confirmada de coincidencias tentativas.
7. El usuario puede guardar el informe, compartirlo con un mecánico o continuar hacia la búsqueda de piezas.
8. El sistema registra feedback y resultados posteriores cuando se disponga de ellos.
La IA no debe fingir que escucha un motor si solo recibió texto, ni afirmar que una pieza está dañada porque una descripción coincide con un patrón. La evaluación debe contemplar diagnósticos erróneos, incertidumbre y casos en los que corresponde recomendar inspección profesional.
¿Qué modelos predictivos implementaría primero?

Predicción de demanda
Primer modelo
Pronóstico de ventas semanales por producto y sucursal. Comenzaría con una línea base sencilla y modelos tabulares o de series temporales, evaluados contra datos históricos que no hayan sido usados en entrenamiento.



Riesgo de agotamiento
Combina stock disponible, demanda prevista y plazo de reposición para calcular cuándo conviene reabastecer. Las sugerencias deben tener en cuenta pedidos pendientes y stock reservado.



Rendimiento comercial
Analiza conversión, margen, rotación, devoluciones y rendimiento por categoría. Al inicio bastan indicadores bien definidos; no todo problema necesita machine learning.



Limitación fundamental: un modelo predictivo serio necesita datos suficientes y representativos. Para presentar la función, puedes usar datos sintéticos claramente identificados y comparar resultados con una línea base. No debes mostrar esas predicciones como resultados comerciales reales ni usarlas para afirmar que Autopartco ya ha conseguido cierta precisión.
7. Pasarela de pagos sin mover dinero real
No necesitas inventar una pasarela ficticia ni procesar dinero real para demostrar una integración profesional.
Ya existe configuración para Wompi en tu backend. Revisaría si puedes utilizar su entorno de pruebas y cómo se manejan los estados, las firmas de integridad y los webhooks. Consulta la documentación oficial de Wompi Colombia para confirmar las condiciones vigentes del sandbox.
Diseñaría el módulo con una interfaz de proveedor, de forma que el resto del sistema no dependa directamente de Wompi.
Checkout
   ↓
PaymentService
   ↓
PaymentProvider
   ├── Wompi sandbox
   └── SimulatedPaymentProvider


El flujo debe incluir:
- Creación de una orden y su importe en el servidor.
- Creación de un intento de pago con referencia única.
- Redirección al checkout de pruebas o a un simulador controlado.
- Recepción y validación de la notificación.
- Transiciones de estado idempotentes.
- Actualización del pedido solo después de confirmar el resultado.
- Registro de fallos, cancelaciones y pagos duplicados.
- Pantalla de resultado y trazabilidad desde el panel comercial.
En el simulador local puedes ofrecer resultados controlados como aprobado, rechazado o pendiente, sin contactar una entidad financiera. Debe quedar explícito que se trata de una simulación.
Nunca consideres suficiente el regreso del navegador para declarar un pago aprobado. Tampoco expongas secretos de integridad, eventos o credenciales privadas al frontend.

8. Cómo aprovechar tus IAs y agentes sin desperdiciar tokens
Tu ventaja no está en pedirle a cinco modelos que programen lo mismo. Está en asignarles responsabilidades concretas, proporcionarles contexto acotado y exigir evidencias antes de aceptar sus cambios.
Usaría Claude Pro y Gemini Pro como asistentes principales según la tarea, OpenCode como entorno para probar los modelos que tienes disponibles y DeepSeek como revisor o generador de alternativas de bajo costo. No daría por hecho que todos los modelos de OpenCode tienen la misma calidad de razonamiento, herramientas o capacidad para trabajar con repositorios grandes: primero haría una pequeña evaluación con tareas reales de Autopartco.
Flujo de agentes propuesto
Tú: arquitecto y orquestador
Defines prioridades, apruebas decisiones y revisas los resultados.


Agente 1
Arquitectura
Investiga código, dependencias y límites entre módulos.


Agente 2
Implementación
Construye una tarea pequeña y acotada.


Agente 3
QA y seguridad
Busca errores, permisos incorrectos y casos límite.


Agente 4
Diseño UX/UI
Evalúa consistencia visual, accesibilidad y flujos.




Integración y revisión humana
Pruebas · Build · Revisión de diff · Pull Request · Aprobación


Reparto de herramientas
Herramienta	Uso que priorizaría
Claude Pro	Diseño de arquitectura, refactorizaciones complejas, revisión de código y planificación técnica.
Gemini Pro	Análisis de documentación extensa, comparación de alternativas y apoyo con material de referencia.
OpenCode	Ejecutar agentes sobre tareas delimitadas, probar distintos modelos y automatizar ciclos de implementación y revisión.
DeepSeek gratis	Segundas opiniones, análisis de errores acotados y generación de casos de prueba.
Google Colab	Entrenar, evaluar y comparar modelos predictivos en Python con datasets experimentales.
NotebookLM	Consultar documentación técnica, decisiones de arquitectura y manuales usando un corpus de fuentes controlado.
Las asignaciones son recomendaciones de flujo de trabajo, no afirmaciones de que un modelo concreto siempre será superior. Las probaría con un conjunto de tareas representativas y mediría corrección, tiempo, coste y calidad de las pruebas.
Cómo organizar el trabajo en Git
1. Un agente investiga el módulo y propone el cambio sin tocar archivos.
2. Tú apruebas el diseño y sus criterios de aceptación.
3. El agente implementador trabaja en una rama dedicada.
4. Un segundo agente revisa el diff de forma independiente.
5. CI ejecuta compilación, pruebas, lint y verificaciones de seguridad.
6. Se corrigen los hallazgos y tú apruebas el merge.
No permitiría que agentes diferentes modifiquen simultáneamente las mismas entidades JPA, migraciones SQL o configuraciones centrales. Para trabajos paralelos, dividiría las tareas por módulos y contratos previamente acordados.

10. Plan de ejecución por fases
No implementaría todas las funcionalidades a la vez. Este orden reduce retrabajo y permite mantener una versión funcional durante la evolución.
01

Auditoría técnica y línea base
Inventario de módulos, rutas, endpoints, tablas, migraciones, dependencias, tests, compilación y flujos incompletos. Identificar duplicaciones y problemas de seguridad.
Entregable: diagnóstico del estado real, riesgos priorizados y backlog técnico.



02

Estabilización de la plataforma
Migraciones de base de datos, contratos de API, autorización por recurso, pruebas automatizadas, gestión de errores, configuración por ambiente y CI.
Entregable: base mantenible que se puede modificar con seguridad.



03

Producto de extremo a extremo
Completar compra, inventario, pedido, ventas por almacén, suscripción, pagos de prueba, roles, soporte y administración root.
Entregable: demo funcional con datos consistentes y flujos verificables.



04

Inteligencia automotriz
Servicio de IA, diagnóstico estructurado, catálogo de compatibilidad con procedencia, historial de vehículo y recomendaciones conectadas al inventario.
Entregable: IA útil integrada en el flujo comercial, no un chatbot aislado.



05

Analítica y predicción
Instrumentación de eventos, métricas comerciales, datos históricos, modelos de demanda y evaluación de resultados.
Entregable: decisiones basadas en datos y predicciones evaluadas.



06

Preparación para operación real
Observabilidad, copias de seguridad, gestión de secretos, límites de uso de IA, protección de archivos, pruebas de carga, accesibilidad, documentación y despliegue.
Entregable: producto preparado para una prueba piloto controlada.



11. Cómo empezar sin perder el control
El primer objetivo no debe ser generar cientos de archivos. Debe ser conseguir una auditoría reproducible que diga qué funciona y qué no, y que permita decidir dónde invertir el esfuerzo.
Mi siguiente paso sería trabajar sobre la rama main como referencia de lectura y preparar una auditoría que cubra:
- Estructura real del frontend y del backend.
- Estado de compilación y pruebas.
- Correspondencia entre las pantallas y los endpoints.
- Funcionalidad real de pedidos, inventario, roles, suscripciones y pagos.
- Calidad del esquema SQL y de las migraciones.
- Riesgos de seguridad y problemas de mantenibilidad.
- Funciones que se pueden conservar, refactorizar o construir desde cero.
Después convertiría esa auditoría en un backlog de tareas pequeñas con criterios de aceptación, dependencias y un agente responsable por tarea. No modificaría masivamente el repositorio antes de tener esa línea base.
La meta es que tú dirijas la arquitectura y las prioridades mientras los agentes investigan, implementan, prueban y revisan cambios acotados. Eso te permite aprovechar las herramientas que ya pagas sin convertir Autopartco en una colección de código generado difícil de mantener.