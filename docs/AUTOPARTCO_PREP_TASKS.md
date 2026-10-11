# Autopartco — Backlog de preparación (antes del desarrollo funcional)

**Objetivo del tablero:** completar una preparación reproducible antes de empezar a implementar nuevas funcionalidades. Estas tareas no deben convertirse en features de producto; son habilitadoras.

## Orden de ejecución

| ID | Prioridad | Tarea | Depende de | Responsable principal | Salida verificable |
|---|---|---|---|---|---|
| PREP-001 | P0 | Inventariar portátil y entorno | — | Usuario + asistente de preparación | Informe de Windows/RAM/espacio y versiones instaladas. |
| PREP-002 | P0 | Respaldar checkout, refs y cambios locales | PREP-001 | Usuario | Git bundle, status, parche de cambios rastreados y copia local segura de archivos no rastreados que se necesitan conservar. |
| PREP-003 | P0 | Confirmar rama de referencia y rama de preparación | PREP-002 | Usuario + investigador | Tabla de ramas/commits y propósito; ninguna modificación accidental en `main`. |
| PREP-004 | P0 | Elegir una modalidad única: Windows nativo o WSL2 | PREP-001 | Usuario | Decisión documentada y un único checkout de trabajo. |
| PREP-005 | P0 | Instalar/comprobar herramientas del stack | PREP-004 | Usuario | Java 21, Maven o wrapper, Node compatible, Git y herramientas necesarias confirmadas; no hay versiones contradictorias. |
| PREP-006 | P0 | Copiar blueprint, contexto de agentes y runbook al repo | PREP-003 | Usuario + agente docs | Archivos versionados; documentación existente revisada para evitar reemplazos/duplicados. |
| PREP-007 | P0 | Auditar instrucciones de agentes existentes | PREP-006 | Agente investigador | Inventario de `AGENTS.md`, Copilot instructions, skills y reglas por ruta; conflictos documentados. |
| PREP-008 | P0 | Verificar instalación e ignores de Graphify | PREP-004, PREP-006 | Usuario + agente investigador | Versión registrada, `.graphifyignore` validado, secretos y artefactos excluidos. |
| PREP-009 | P0 | Regenerar Graphify incluyendo los documentos nuevos | PREP-008 | Agente investigador | Nuevo `graph.json`/informe, versión/commit/fecha y advertencias documentadas; grafo antiguo respaldado. |
| PREP-010 | P0 | Validar consultas del grafo | PREP-009 | Agente investigador | Consultas verificadas para módulos, auth, catálogo/inventario/pedidos/pagos, SQL e integraciones. |
| PREP-011 | P0 | Ejecutar baseline de frontend | PREP-003, PREP-005 | Agente QA + usuario | Comandos reales, build/tests/lint ejecutados y salida registrada; fallos reproducibles creados como issues. |
| PREP-012 | P0 | Ejecutar baseline de backend y DB | PREP-003, PREP-005 | Agente QA + usuario | Build/tests ejecutados; arranque y esquema local verificados o bloqueos registrados. |
| PREP-013 | P0 | Crear informe de arquitectura observada y riesgos | PREP-010, PREP-011, PREP-012 | Agente investigador | `BASELINE_REPORT.md` con rutas/evidencias, estados de función y riesgos P0/P1/P2. |
| PREP-014 | P1 | Proponer estructura definitiva de docs/instrucciones | PREP-013 | Agente arquitectura + usuario | Plan de movimientos con justificación y sin cambios funcionales mezclados. |
| PREP-015 | P1 | Probar skills externas de UX/UI | PREP-004, PREP-007 | Revisor UX | UI/UX Pro Max, Frontend Design y Web Design Guidelines evaluadas; instaladas solo las aprobadas y verificada su detección. |
| PREP-016 | P1 | Probar skills de testing, seguridad y backend | PREP-007, PREP-011, PREP-012 | Revisor independiente | Skills de TDD/debug, Spring testing, PostgreSQL, security review y E2E comprobadas por utilidad/compatibilidad. |
| PREP-017 | P1 | Configurar instrucciones compactas y fuente de verdad | PREP-007, PREP-013 | Usuario + agente docs | `AGENTS.md` y configuraciones de agentes sin contradicciones; blueprint completo bajo demanda. |
| PREP-018 | P1 | Definir flujo mult agente y política de coste/privacidad | PREP-001, PREP-015, PREP-016 | Usuario | Matriz de herramientas/modelos, límites de datos, tarea de benchmark y regla de un implementador por rama. |
| PREP-019 | P1 | Crear GitHub Project, milestones y issues | PREP-013, PREP-018 | Usuario + agente planificador | Epics del producto, tareas con criterios de aceptación, labels y dependencias. |
| PREP-020 | P1 | Añadir automatización de verificación base | PREP-011, PREP-012 | Agente CI/QA | CI inicial para los checks que realmente funcionan; plantilla de PR y protección de secretos revisadas. |
| PREP-021 | P0 | Ejecutar checklist final de “listo para desarrollar” | Todas P0; P1 bloqueantes | Usuario + revisor independiente | Todos los gates P0 aprobados; pendientes P1 visibles y priorizados. |

## Criterio de aceptación compartido

No cerrar una tarea por haber escrito código o documentación solamente. Adjuntar evidencia reproducible: comando, archivo, prueba, captura o diff. Si algo no se pudo ejecutar, se registra como **bloqueado/no verificado**, con razón y siguiente acción.

## Definición del estado

- **Pendiente:** aún no empezó.
- **En curso:** una persona/agente tiene un alcance delimitado.
- **Bloqueada:** falta un requisito, acceso, decisión o entorno.
- **En revisión:** hay salida y evidencia, falta revisor independiente.
- **Terminada:** criterios cumplidos y evidencia adjunta.

## Reglas de asignación

- PREP-001 a PREP-005: trabajo en el portátil, bajo control del usuario.
- PREP-006 a PREP-010 y PREP-013/014/017/019: agente investigador/documentación; evitar edición simultánea.
- PREP-011/012/020: QA con el usuario capaz de ejecutar comandos localmente; el agente no debe afirmar que corrió pruebas si no tiene ese entorno.
- PREP-015/016/018: evaluaciones independientes, sin instalar paquetes de forma masiva.
- PREP-021: el usuario aprueba; un revisor independiente busca fallos del checklist.

## Qué no hacer durante esta fase

- No reescribir el backend ni rehacer el frontend completo.
- No cambiar múltiples entidades/migraciones a la vez.
- No construir aún predicciones ML ni conectar pagos reales.
- No activar sincronización bidireccional ERP sin mapear la autoridad por campo.
- No permitir que agentes modifiquen simultáneamente la misma rama/archivos.
- No borrar `backend/bin/`, `graphify-out/` ni archivos sospechosos hasta probar si son necesarios.
- No guardar secretos en GitHub, prompts, capturas, informes de ambiente ni material enviado a terceros.

## Gates para iniciar desarrollo funcional

1. **Gate de entorno:** runtimes y comandos verificados.
2. **Gate de repositorio:** respaldo, ramas y cambios explicados.
3. **Gate de conocimiento:** blueprint/contexto versionados y Graphify nuevo y validado.
4. **Gate de calidad:** baseline de frontend/backend documentado.
5. **Gate de operación IA:** roles, privacidad, coste, skills y revisión independiente establecidos.
6. **Gate de planificación:** backlog aprobado con prioridad/dependencias/criterios.

El primer trabajo funcional después de estos gates es la estabilización de la plataforma (BASE), no una característica de IA avanzada.
