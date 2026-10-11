# Autopartco — Contexto base para agentes de IA

> Leer `AUTOPARTCO_PRODUCT_BLUEPRINT.md` para el alcance completo. Este archivo resume las instrucciones que deben acompañar tareas cotidianas y evita recargar el contexto con toda la especificación.

## Qué es el producto

Autopartco es una plataforma comercial de autopartes para Colombia. Conecta conductores y mecánicos con almacenes verificados y ofrece a esos almacenes un SaaS para administrar catálogo, inventario por sucursal, pedidos, empleados, integraciones y ventas. La plataforma incluye operaciones internas de Autopartco y funciones de IA sujetas a evidencia, evaluación y límites de coste.

## Superficies

1. Marketplace público: búsqueda, filtros, detalle, compatibilidad, tiendas, carrito/pedidos, seguimiento, reseñas y reportes.
2. Portal comercial: perfil y verificación del almacén, sucursales, productos, inventario, empleados/permisos, pedidos, integraciones ERP, suscripciones y analítica.
3. Consola root/admin: verificación, moderación, gestión de planes, reportes y auditoría.
4. Centro de soporte: tickets y consultas con mínimo privilegio.

## Prioridades del producto

- Gestionar inventario por sucursal, con historial de movimientos; no guardar solo un saldo que cambie sin trazabilidad.
- Habilitar importación CSV con validación, previsualización, errores por fila y reintento.
- Implementar conectores reales con Alegra y Siigo: credenciales protegidas, mapeo de bodegas/sucursales, identificadores externos, política de autoridad por campo, sincronización idempotente, webhooks cuando estén disponibles, reconciliación y logs.
- Completar el ciclo marketplace → pedido → pago de demostración → seguimiento.
- Crear planes comerciales configurables con periodo de prueba gratuito de un mes; precios y límites se configuran tras validar economía y negocio.
- La IA ayuda en búsqueda, calidad de catálogo, compatibilidad orientativa, diagnóstico y analítica, pero no inventa stock/precios/datos mecánicos ni presenta hipótesis como certeza.
- La UX es prioritaria: interfaz clara, responsive, accesible, coherente, con estados de carga/vacío/error/éxito y microcopy accionable.

## Stack y límites

Frontend actual/objetivo: React, TypeScript, Vite, Tailwind CSS v4, shadcn/ui con Base UI, Lucide React, React Router e Inter. Logo: `public/logo.svg`.

Backend actual/objetivo: Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 16+, Spring Security/JWT, paquetes `com.autopartco`.

- No migrar Tailwind v4 a v3.
- No crear `tailwind.config.js` por costumbre.
- No añadir dependencias si el stack existente resuelve la necesidad; justificar cada nueva dependencia.
- No reemplazar el logo real con un placeholder.
- No rehacer módulos existentes sin inspeccionarlos primero.
- No asumir que una propiedad/configuración/página implica que la función esté terminada.
- No confiar en el frontend para autorización, precios, stock o estado de pago.
- No modificar datos/esquema de forma destructiva sin revisar migraciones, relaciones y estrategia de rollback.
- No declarar build/tests/flujo como correcto sin ejecutar los comandos y revisar la salida.

## Principios de arquitectura

- Preferir monolito modular a microservicios prematuros.
- Separar responsabilidades por dominio: `auth`, `organization/store/branch`, `catalog/vehicle/compatibility`, `inventory`, `order`, `payment`, `subscription`, `integration`, `intelligence`, `analytics`, `support`, `administration`, `notification`, `audit` y `common/config`, adaptándose al código real en vez de renombrarlo todo.
- API con DTO/validación, errores consistentes, transacciones y autorización por organización/sucursal/recurso.
- Usar migraciones versionadas y desactivar mutaciones automáticas de esquema en producción tras verificar configuración y estado actual.
- Las integraciones externas van detrás de adaptadores; el dominio de inventario/catálogo no debe contener llamadas directas a Alegra/Siigo dispersas.
- Webhooks y jobs deben ser idempotentes, observables y reintentables.
- Mantener secretos fuera del repo, respuestas API, frontend y logs.

## Diseño/UX

Usar de forma contextual las skills externas más apropiadas:
- UI/UX Pro Max: referencias/patrones, tokens y estructura de diseño.
- Frontend Design de Anthropic: dirección visual distintiva en cambios de UI importantes.
- Vercel Web Interface Guidelines: revisión de interacciones, etiquetas y estados.
- Design MD Guidelines: checklist adicional de accesibilidad, foco, teclado y responsive.
- Playwright/webapp-testing: probar flujos reales y capturar evidencia.

No instalar todas las skills de forma indiscriminada. Verificar origen, licencia, compatibilidad, scripts y cambios del instalador; usar carga bajo demanda. Conservar la identidad de Autopartco y las reglas del stack.

## Skills de ingeniería a considerar

- Superpowers: planificación, TDD, depuración sistemática, revisión y verificación.
- `acquire-codebase-knowledge` + Graphify: entender el repo y localizar dependencias.
- `java-springboot` y `spring-boot-testing`: backend y pruebas acordes a la versión real.
- `postgresql-code-review`: revisión de esquema/consultas/índices.
- `security-review`: autorización, secretos, inyecciones y dependencias.
- `webapp-testing`: pruebas frontend con Playwright.
- `react-best-practices` de Vercel: optimización React aplicable a Vite.
- `skill-creator` solo si una necesidad no queda cubierta por skills existentes y podemos evaluar el resultado.
- `mcp-builder` solo para crear herramientas MCP para agentes; no se necesita para conectores productivos Alegra/Siigo, que usan las APIs oficiales.

## Seguridad y calidad no negociables

- Probar accesos indebidos entre organizaciones y sucursales.
- Root admin y soporte tienen funciones y permisos distintos.
- Validar entradas y permisos en servidor; limitar frecuencia y tamaño.
- Webhooks con verificación adecuada, referencias únicas e idempotencia.
- Nunca aprobar un pago solo porque el usuario vuelve desde la pasarela.
- Separar sandbox/simulador de pagos reales.
- No usar `float` para dinero.
- Añadir pruebas unitarias/integración/contrato/E2E según el riesgo.
- Evitar datos personales en logs y métricas sin necesidad.
- La IA muestra incertidumbre, usa datos recuperados y tiene límites de coste.

## Flujo recomendado para cada tarea

1. Consultar las instrucciones del proyecto y Graphify si corresponde a la rama actual.
2. Inspeccionar los archivos y las pruebas relevantes antes de proponer cambios.
3. Definir objetivo, alcance, límites y criterios de aceptación.
4. Verificar documentación oficial para APIs/dependencias que puedan cambiar.
5. Implementar en una tarea/branch acotada; evitar edición simultánea de los mismos archivos críticos.
6. Ejecutar pruebas/build/lint disponibles y reportar la evidencia.
7. Revisar seguridad, UX, migraciones y errores.
8. Informar archivos modificados, decisiones, supuestos, pruebas ejecutadas, resultados y pendientes.

## Plantilla de tarea

```text
Objetivo:
Problema de usuario/negocio:
Archivos relevantes inspeccionados:
Dentro del alcance:
Fuera del alcance:
Restricciones del stack/arquitectura:
Riesgos de datos/seguridad:
Criterios de aceptación:
Pruebas que se ejecutarán:
Entregables:
Decisiones que requieren aprobación:
```

## Definición de terminado

Una tarea no está terminada solo porque el código fue generado. Debe cumplir criterios funcionales, permisos server-side, estados UX, pruebas relevantes, configuración/documentación actualizada y verificación reproducible. Si algo no pudo ejecutarse, reportarlo explícitamente.
