## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Agentes especializados (OpenCode y Claude Code)

OpenCode: definidos en `.opencode/agents/`; permisos globales en `.opencode/opencode.json` (esquema `permission`/`agent` de https://opencode.ai/config.json; la última regla que coincide gana).

Claude Code: los mismos subagentes en `.claude/agents/` (salvo `orquestador`, que es la sesión principal). Las áreas de edición y los comandos prohibidos los aplica el hook `.claude/hooks/agent-guard.mjs`; mantén ambas definiciones sincronizadas.

Contexto de producto en `docs/AUTOPARTCO_AGENT_CONTEXT.md`.

- `orquestador` (primario): planifica y delega; no edita.
- `arquitecto`: decisiones e integración entre módulos; solo escribe en `docs/adr/` y `docs/architecture/`.
- `backend`: `backend/src/main/java/` y `backend/src/test/`.
- `database`: `database/migrations/`.
- `frontend`: `frontend/`.
- `qa-seguridad`: solo lectura; ejecuta pruebas y reporta hallazgos.
- `redactor` (solo OpenCode, lo usa la skill `opencode-delegate`): documentación Markdown en `docs/`, `README.md` y `CHANGELOG.md`; no edita código.

Reglas comunes:
- Cada agente edita solo su área. Nunca dos agentes con edición sobre la misma área a la vez; los cambios entre capas van en secuencia `database` → `backend` → `frontend` → `qa-seguridad`.
- Requieren autorización del usuario: operaciones destructivas, despliegues, git que reescriba historia o publique, comandos contra bases de datos, instalación de dependencias, servicios de pago o externos, y cambios en seguridad, secretos, `.opencode/` o `.claude/`.
- Pruebas backend solo con `-Dtest=<Clase>` explícito: `BackendApplicationTests` (`@SpringBootTest` + `ddl-auto: update`) modifica la BD del `.env` raíz hasta que se aíslen las pruebas de integración.

## Flujo de trabajo: diseño con Opus, ejecución por modelo

Tareas pequeñas (cambio de una o dos líneas, pregunta de sintaxis): respuesta directa, sin plan.

Tareas medianas o grandes de diseño, planificación o desarrollo:

1. Actuar como arquitecto y, antes de proponer código, entregar un plan autocontenido en Markdown listo para guardarse como `PLAN.md`, con:
   - Objetivo y alcance (qué entra y qué no).
   - Componentes y responsabilidades.
   - Contratos entre componentes (firmas, endpoints, esquemas de datos).
   - Decisiones clave con justificación breve y alternativas descartadas.
   - Tareas numeradas, cada una como instrucción copiable ejecutable en un chat nuevo sin depender de la conversación, indicando: modelo recomendado, agente del proyecto que la ejecuta, archivos que toca, contexto mínimo y criterio de aceptación verificable.
   - Riesgos y puntos a verificar.
2. Asignación de modelos:
   - Haiku: tareas mecánicas, tests simples, docstrings, commits, formateo, extracción de datos.
   - Sonnet: implementación de funciones y componentes, refactors medianos, depuración con contexto moderado, tests de integración, documentación técnica.
   - Opus: arquitectura, decisiones entre alternativas, bugs que Sonnet no resolvió tras uno o dos intentos, refactors grandes, revisión de seguridad y lógica crítica.
   - Las tareas que encajan en la skill `opencode-delegate` pueden marcarse con OpenCode como alternativa a Haiku o Sonnet.
3. No escribir la implementación completa de una tarea delegable: solo contratos, esqueletos y criterios de aceptación.
4. Respetar la secuencia entre capas `database` → `backend` → `frontend` → `qa-seguridad`.
5. Si durante la ejecución surge un problema de diseño, señalarlo y proponer solo la decisión puntual que lo resuelve, sin rehacer el plan.
6. Antes de cerrar el plan, verificar que cada tarea sea independiente, que los contratos sean consistentes entre sí y que cada criterio de aceptación sea comprobable con tests o una revisión concreta.

## Ramas y despliegue

- `develop` es la rama de integración. Todo cambio sale de una rama `feature/*`, `fix/*` o `chore/*` creada desde `develop` y vuelve a `develop` por PR.
- `main` es producción y es generada: nunca recibe commits, merges ni PR directos. Solo la escribe el workflow `.github/workflows/promote-to-main.yml`, que publica una copia filtrada de `develop`.
- `main` contiene únicamente lo necesario para deploy: `backend/` (sin `bin/` ni `src/test/`), `frontend/`, `database/`, `.gitignore` y `README.md`. La lista blanca está en `tools/release/promote-to-main.sh`; si el deploy necesita otra ruta, se añade ahí.
- Publicar en producción es un despliegue y requiere autorización del usuario: `git tag vX.Y.Z origin/develop` y `git push origin vX.Y.Z`. Para revisar antes: `DRY_RUN=1 bash tools/release/promote-to-main.sh`.
