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

Reglas comunes:
- Cada agente edita solo su área. Nunca dos agentes con edición sobre la misma área a la vez; los cambios entre capas van en secuencia `database` → `backend` → `frontend` → `qa-seguridad`.
- Requieren autorización del usuario: operaciones destructivas, despliegues, git que reescriba historia o publique, comandos contra bases de datos, instalación de dependencias, servicios de pago o externos, y cambios en seguridad, secretos, `.opencode/` o `.claude/`.
- Pruebas backend solo con `-Dtest=<Clase>` explícito: `BackendApplicationTests` (`@SpringBootTest` + `ddl-auto: update`) modifica la BD de `backend/.env` hasta que se aíslen las pruebas de integración.
