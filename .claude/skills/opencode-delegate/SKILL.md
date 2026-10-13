---
name: opencode-delegate
description: Delega a OpenCode (CLI no interactiva, modelos gratuitos) el trabajo que consume muchos tokens de Claude y no exige decisiones difíciles, y lo revisa leyendo solo lo imprescindible. Úsala por defecto para redactar o actualizar documentación (docs/, README, guías, ADR o planes a partir de decisiones ya tomadas), generar texto largo (descripciones de PR, changelogs, commits, resúmenes, informes, tablas o inventarios), investigar el código en modo solo lectura ("¿dónde se usa X?", "¿cómo funciona Y?"), escribir pruebas e implementar cambios acotados (CRUD, DTO, refactors mecánicos, pantallas con contrato cerrado). Úsala también cuando el usuario diga "delega", "usa opencode", "ahorra tokens" o similar, aunque no nombre la skill.
---

# Delegar a OpenCode

Claude decide, especifica y revisa; OpenCode lee, redacta, edita e itera. La regla de oro para ahorrar tokens: **no hagas tú lo que vas a delegar**. No leas archivos para preparar la tarea ni para "entender mejor": escribe la especificación con lo que ya sabes y pasa rutas; el agente explorará con sus propios tokens.

## 1. ¿Delegar?

Hazlo tú si se cumple cualquiera:
- Cambia código de autenticación, autorización, pagos/webhooks, secretos, criptografía o datos personales.
- Requiere decidir diseño o cruzar capas sin contrato. (Redactar un diseño **ya decidido** sí se delega.)
- Cambia esquema/migraciones o datos de forma no trivial.
- Es pequeño (≲30 líneas, 1–2 archivos conocidos, o un texto de pocas líneas): coordinar cuesta más que hacerlo.
- No puedes escribir criterios de aceptación verificables.
- Incluye datos sensibles o secretos: los modelos `*-free` pueden registrar lo que reciben.

En lo demás, delega. La documentación y el texto largo se delegan **por defecto**, aunque traten de seguridad o pagos, siempre que solo describan decisiones tomadas. En caso de duda, delega la parte mecánica y quédate con el diseño.

## 2. Elegir tipo (`--kind`)

| kind | Para | Agente por defecto | Entregable |
|---|---|---|---|
| `docs` | Crear/editar Markdown en `docs/`, README, CHANGELOG | `redactor` (solo edita docs) | El archivo; recibes su índice |
| `text` | PR, commit, changelog, resumen, informe, tabla | `explore` (solo lectura) | Texto en `--out` |
| `research` | Preguntas sobre el código, inventarios, impacto | `explore` (solo lectura) | Respuesta con evidencia en `--out` |
| `code` | Implementación acotada | obligatorio: `backend`, `database`, `frontend` | Diff |
| `tests` | Pruebas unitarias | obligatorio (dueño del área) | Diff + verificación |

El modelo y sus suplentes salen de `models.json` → no lo elijas ni leas `models.json` salvo que el resultado traiga `attempts` con fallos (ver `MODELS.md`). `--model` solo para forzar uno, p. ej. `opencode/cat-jam-free#high` en código difícil.
ADR o docs en `docs/adr/` o `docs/architecture/`: usa `--kind docs --agent arquitecto`.

## 3. Escribir la especificación

En el scratchpad, solo lo propio de la tarea. El script **añade solo** la plantilla del tipo (método Graphify, estilo, "no inventes", formato de respuesta); no lo repitas.

```
Tarea: <objetivo en una frase>
Contexto: <por qué; decisiones ya tomadas que hay que respetar>
Fuentes: <rutas o conceptos donde está la información> (adjunta las clave con --files)
Alcance: puedes modificar <rutas>; no toques <rutas>      ← code/tests/docs
Estructura: <secciones o formato esperado>                 ← docs/text
Criterios de aceptación:
- <verificable>
```

Cuanto más concretas sean fuentes y criterios, menos pasos da el agente (más rápido) y menos corriges después.

## 4. Ejecutar

```bash
node .claude/skills/opencode-delegate/scripts/delegate.mjs --kind <kind> [--agent <a>] --prompt-file <spec> [--files a,b] [--diff [rutas]] [--out <scratchpad>/<x>.md] [--verify "<cmd>"] --title "<corto>"
```

- Lánzalo con `run_in_background: true` y espera la notificación. Nada de sondeos. Mientras, avanza con trabajo que no toque esos archivos.
- **Tareas independientes en paralelo**: varios documentos distintos, research + docs, o capas sin dependencia. Lánzalas todas a la vez (cada una en su llamada de fondo). Dos agentes con edición nunca sobre los mismos archivos; entre capas dependientes, en secuencia `database` → `backend` → `frontend`.
- **Nunca detengas una tarea ya iniciada**, aunque tarde: no uses `TaskStop`, no mates el proceso, no la relances en paralelo.
- `--diff [rutas]` (para PR, commit, changelog) adjunta el diff sin commit y los archivos nuevos ya calculados. No le pidas al agente que ejecute `git diff`: le rechazan las rutas protegidas y pierde pasos.
- `--verify` ejecuta la verificación al terminar y devuelve solo ok/fallo y las líneas de error, sin que tengas que lanzar otro comando. Pruebas backend siempre con `-Dtest=<Clase>` explícito (nunca `BackendApplicationTests`).
- Permisos `ask` se rechazan solos en modo no interactivo; aparecen en `permissionRejected`. No uses `--auto` sin autorización explícita del usuario para esa tarea.
- Si el modelo principal falla sin editar nada, el script reintenta solo con el suplente (`attempts`).

## 5. Revisar leyendo lo mínimo

La salida es un JSON compacto: `ok`, `filesChanged`, `docs`/`out` (índices), `permissionRejected`, `toolErrors`, `verify`, `finalText`, `sessionID`. No abras `logFile` salvo para depurar un fallo.

- **docs**: revisa el índice (`headings`, `pendientes` = marcas `[POR CONFIRMAR]`). Lee solo 1–2 secciones críticas con `Read` + `offset/limit` usando las líneas del índice, no el documento entero. Contrasta una o dos afirmaciones clave con `graphify query`.
- **text / research**: el entregable está en `--out` (siempre una ruta del scratchpad; con `docs` no uses `--out`, el entregable es el propio archivo). Para entregarlo, no lo copies en el chat: pásalo tal cual (`gh pr create --body-file <out>`, `git commit -F <out>`) o enlaza el archivo. Léelo solo si lo vas a resumir o si `out.headings` parece incompleto.
- **code / tests**: `filesChanged` fuera del alcance → reviértelos tras confirmarlo con el usuario. Luego `git diff -U3 -- <archivo>` solo de los archivos del alcance; amplía contexto solo en bloques dudosos. Impacto con `graphify query/path`, no abriendo vecinos. Si no usaste `--verify`, ejecuta tú la prueba acotada.
- ¿Faltan cosas? Una ronda de corrección en la misma sesión (conserva contexto, es más rápida): mismo comando con `--session <sessionID>` y una spec con la lista exacta de defectos (`archivo:línea → qué cambiar`). Máximo 2 rondas; después termínalo tú.
- Tras cambios de código: `graphify update .`.

## 6. Informe al usuario (breve)

Qué se delegó y a quién (kind, agente, modelo), qué cambió (archivos o ruta del entregable), qué corregiste tú, verificación con su resultado real, y pendientes (`permissionRejected`, `[POR CONFIRMAR]`). Si un modelo rindió mal, anótalo en `models.json → failures`.

## Requisitos y problemas conocidos

- La CLI se busca en `OPENCODE_CLI`, luego en el binario del escritorio (`%LOCALAPPDATA%\Programs\@opencodedesktop\resources\opencode-cli.exe`) y por último en `opencode` del PATH. Con escritorio 2.x no uses el `opencode` 1.x de npm: comparten base de datos y falla con "Database is not empty and has no session table".
- `opencode run` se cuelga sin emitir nada si stdin queda abierto; los scripts lo cierran. Si lanzas la CLI a mano, redirige `< /dev/null`.
- En Git Bash exporta `MSYS_NO_PATHCONV=1` antes de `opencode-cli api GET /ruta`.
