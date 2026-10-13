---
name: opencode-delegate
description: Decide si una tarea de implementación la hace Claude o se delega a OpenCode (CLI no interactiva) eligiendo agente, modelo y skills de OpenCode, y revisa el resultado leyendo solo el diff necesario. Úsala cuando el usuario pida implementar, cambiar o probar código y quiera ahorrar tokens de Claude Code, o cuando diga "delega", "usa opencode" o similar.
---

# Delegar tareas a OpenCode

Objetivo: gastar tokens de Claude en decidir, especificar y revisar; que OpenCode gaste los suyos en leer código, iterar y editar. Solo se consideran modelos del proveedor `opencode`, salvo que el usuario diga otra cosa.

## 0. Requisitos (comprobar una vez por sesión)

1. CLI: `node scripts/delegate.mjs` la busca en `OPENCODE_CLI`, en el binario de la app de escritorio (`%LOCALAPPDATA%\Programs\@opencodedesktop\resources\opencode-cli.exe` en Windows) y por último en `opencode` del PATH. Si el escritorio es 2.x, **no uses** el `opencode` de npm 1.x: comparten la base de datos y la 1.x falla con "Database is not empty and has no session table".
2. En Git Bash exporta `MSYS_NO_PATHCONV=1` antes de `opencode-cli api GET /ruta`, o la ruta se reescribe como ruta de Windows.
3. Agentes del proyecto: `opencode-cli api agent.list --param "location[directory]=<repo>"`. Skills: `opencode-cli api skill.list --param "location[directory]=<repo>"`.

## 1. ¿Delegar o hacerlo yo?

Hazlo tú (no delegues) si se cumple **cualquiera**:
- Toca autenticación, autorización, pagos/webhooks, secretos, criptografía o datos personales.
- Requiere una decisión de diseño o ADR, o cruza capas sin contrato definido.
- Cambia esquema/migraciones o datos de forma no trivial.
- Es pequeña (≲ 30 líneas o 1–2 archivos conocidos): coordinar cuesta más que hacerla.
- La especificación es ambigua y no puedes escribir criterios de aceptación verificables.
- Contiene datos sensibles que no deben salir a un proveedor gratuito (los modelos `*-free` pueden registrar los datos).

Delega si la tarea es acotada y verificable: pruebas unitarias, DTO/validaciones, refactors mecánicos, endpoints CRUD con contrato claro, pantallas de frontend con contrato de API cerrado, documentación técnica, búsquedas amplias de solo lectura.

En caso de duda, delega solo la parte mecánica y quédate con el diseño.

## 2. Elegir modelo

1. Lee `models.json` (clasificación con evidencia) y `MODELS.md` (resumen legible) de esta skill.
2. Ejecuta `node scripts/models.mjs --dir <repo>`. Reevalúa (ver `MODELS.md` → "Cómo reevaluar") si:
   - `indexAgeDays` > 30, o hay modelos en `added` / `removed`;
   - el modelo elegido falla dos veces seguidas (error, timeout o resultado inválido).
3. Escoge según el tipo de tarea (`roles` en `models.json`) y usa el suplente si el principal falla. Variante de razonamiento con `--model proveedor/modelo#variante` cuando el modelo la ofrezca (p. ej. `#high` para tareas difíciles).

## 3. Elegir agente y skills

- Usa los agentes del proyecto (en Autopartco: `backend`, `database`, `frontend`, `arquitecto`, `qa-seguridad`; ver `AGENTS.md`). Un solo agente con edición por área a la vez y **nunca** mientras tú editas esos mismos archivos.
- Cambios entre capas: en secuencia `database` → `backend` → `frontend`, una delegación por capa, revisando cada una antes de la siguiente.
- Skills de OpenCode: nómbralas en el prompt ("Carga la skill `X` antes de empezar") solo si existen en `skill.list` y aportan algo concreto.

## 4. Escribir el prompt (archivo temporal)

Escríbelo en el scratchpad y pásalo con `--prompt-file`. Plantilla:

```
Tarea: <objetivo en una frase>
Contexto: <por qué; hallazgo/ID; decisiones ya tomadas>
Archivos que puedes modificar: <lista concreta>
Archivos que NO debes tocar: <lista>
Pasos esperados: <3–6 pasos>
Criterios de aceptación:
- <verificable 1>
- <verificable 2>
Verificación: <comando exacto, p. ej. ./mvnw test -Dtest=FooTest> (si te lo rechazan por permisos, indícalo y continúa; lo ejecutará el revisor)
Método: usa Graphify (graphify query/path/explain) antes de leer archivos completos.
Skills: <ninguna | nombre>
Al terminar responde con: archivos modificados, qué hiciste en cada uno, comandos ejecutados con resultado, dudas o pendientes. Sin repetir el código.
```

## 5. Ejecutar

```bash
node .claude/skills/opencode-delegate/scripts/delegate.mjs --agent <agente> --model opencode/<modelo> --prompt-file <archivo> --title "<título corto>" --timeout-min 30
```

- Lánzalo con `run_in_background: true` y espera la notificación; no hagas sondeo.
- Modo no interactivo: todo permiso `ask` se **rechaza automáticamente** (el agente sigue sin esa acción). Por eso las pruebas marcadas como `ask` las ejecutas tú después. No uses `--auto` sin autorización explícita del usuario para esa tarea.
- La salida es un JSON compacto; el flujo completo queda en `logFile` (no lo leas salvo para depurar).

## 6. Revisar con el mínimo de tokens

1. Lee el resumen: `filesChanged`, `permissionRejected`, `toolErrors`, `errors`, `finalText`.
2. Si tocó archivos fuera del alcance → revierte solo esos tras confirmarlo con el usuario, y anótalo.
3. `git diff --stat` y luego `git diff -U3 -- <archivo>` solo de los archivos del alcance. Amplía contexto (`-U15` o `--function-context`) solo en los bloques dudosos.
4. Impacto: `graphify query "<clase o método cambiado>"` / `graphify path "<A>" "<B>"` en lugar de abrir archivos vecinos.
5. Ejecuta tú la verificación (pruebas acotadas) y compara con los criterios de aceptación.
6. ¿Faltan cosas? Una ronda de corrección en la misma sesión: `--session <sessionID>` con una lista precisa de defectos (archivo:línea → qué cambiar). Máximo 2 rondas; después termínalo tú.
7. Tras cambios de código: `graphify update .`.

## 7. Informe al usuario

- Decisión (delegado o no) y por qué; agente, modelo y skills usados.
- Qué hizo OpenCode (archivos y resumen), qué corregiste tú, verificación ejecutada con su resultado real.
- Tokens de OpenCode (`tokens`, `cost`), duración, permisos rechazados y pendientes.
- Si el modelo falló o rindió mal, actualiza `models.json` (`notes`/`failures`) para la próxima vez.
