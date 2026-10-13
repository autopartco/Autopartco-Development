# Modelos de OpenCode para delegar

La elección es automática: `delegate.mjs --kind <tipo>` toma agente, modelo y suplentes de `models.json → roles`. Este archivo solo hace falta para **reevaluar**.

## Cuándo reevaluar

- Una delegación devuelve `attempts` con el principal fallando (deprecado, no disponible, timeout).
- `node scripts/models.mjs --dir <repo>` muestra `added`, `removed` o `rolesUsingRemoved`, o `benchAgeDays` > 30.
- Un modelo entrega trabajo malo dos veces: anótalo en `models.json → failures` (`{fecha, modelo, kind, qué pasó}`).

## Cómo reevaluar (≈2–3 min, sin coste)

```bash
node .claude/skills/opencode-delegate/scripts/bench.mjs --dir . --parallel 6
```

Ejecuta dos sondas por modelo con el agente de solo lectura `explore`:
- **read**: leer `AGENTS.md` y contar agentes (comprueba uso de herramientas y exactitud).
- **write**: resumir `docs/AUTOPARTCO_AGENT_CONTEXT.md` en 120–180 palabras + 3 viñetas. Se exige haber **leído** el archivo; un modelo que resume sin leer queda descalificado para docs.

Imprime una línea por modelo y guarda los datos en `models.json → bench`. Después ajusta `roles` a mano con este criterio:

| Rol | Prioriza |
|---|---|
| docs, text | `write` correcto (lee y respeta formato) y rápido |
| research | `read` correcto; contexto grande (≥1M) como suplente |
| code, tests | `read` correcto y variantes de razonamiento (`#medium`; `#high` solo para tareas difíciles) |

Pon como suplentes modelos de **otra familia**: si un proveedor cae, suelen caer todas sus variantes.

## Resultado del 2026-10-13 (opencode v2.0.24, 6 en paralelo)

| Modelo | read | write | Nota |
|---|---|---|---|
| cat-jam-free | ok 16 s | ok 17 s | Principal en todos los roles |
| muse-spark-1.3-contributor-free | ok 15 s | ok 37 s | Suplente fiable |
| mimo-v2.6-flash-free | ok 19 s | ok 23 s | Algo largo |
| longcat-2.5-preview-free | ok 21 s | ok 43 s | 1M ctx |
| nemotron-3.5-lightning-free | ok 30 s | ok 61 s | Lento |
| step-5-preview-free | ok 13 s | mal (largo) | Solo code |
| space-bunny-free | ok 26 s | mal (307 palabras) | — |
| nemotron-3-ultra-free | ok 31 s | mal | — |
| ling-3.1-flash-free | ok 53 s | mal, 126 s | Evitar |
| big-pickle | ok 12 s | **no leyó el archivo** | No apto para docs |
| exo-free / ling-3.0-flash-fin-free | falla | falla | Deprecado / no disponible |

Las sondas no editan código: el rol `code` se basa en `read` + metadatos. Corrígelo con la experiencia real (`failures`).
