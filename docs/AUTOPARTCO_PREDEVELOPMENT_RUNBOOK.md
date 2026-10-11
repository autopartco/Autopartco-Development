# Autopartco — Runbook de preparación antes del desarrollo

**Versión:** 1.0  
**Fecha:** 11 de octubre de 2026  
**Propósito:** dejar listo el portátil, el repositorio, el contexto del producto, Graphify, las skills, los agentes y el flujo de trabajo antes de desarrollar nuevas funciones.

> **Regla de entrada:** no borrar, mover masivamente ni reescribir código hasta tener respaldo, estado de Git documentado, builds/tests de referencia y mapa actualizado del repositorio.

## Resultado esperado

La preparación termina únicamente cuando:

- Existe respaldo recuperable del repositorio y de los cambios locales.
- Se identifica la rama correcta, las diferencias con `main` y el estado real de Git.
- Se documentan las versiones de herramientas y el entorno puede reproducirse.
- Frontend y backend tienen comandos verificados para construir, probar y ejecutar; fallos iniciales quedan registrados.
- El blueprint y el contexto de agentes están versionados en una ubicación oficial.
- Graphify se ha verificado/actualizado y se ha regenerado para incluir código y documentación nueva.
- La arquitectura observada se distingue de la arquitectura propuesta.
- Existe una selección corta de skills externas evaluadas y probadas.
- Las instrucciones de los agentes son coherentes y no se contradicen.
- Hay un backlog priorizado con dependencias, responsables y criterios de aceptación.
- Se puede comprobar el entorno de forma reproducible sin pedirle a una IA que adivine comandos.

## Principios

1. GitHub es la fuente de verdad del código; los documentos del producto también deben vivir en el repo.
2. Un solo entorno por checkout. No abrir una misma carpeta a la vez desde Windows nativo y WSL.
3. Un solo agente implementador por rama/tarea. Otros agentes pueden investigar o revisar en modo lectura.
4. La existencia de una clase, ruta o configuración no demuestra que la función esté terminada.
5. Usar `git mv` para movimientos justificados; no borrar carpetas por el nombre.
6. Skills bajo demanda: no instalar decenas ni cargar todo el blueprint en cada mensaje.
7. No enviar `.env`, tokens, credenciales ERP, claves privadas o dumps con datos personales a modelos externos.
8. Los cambios de base de datos, seguridad, pagos, importación masiva y permisos requieren revisión y plan de rollback.

---

# Fase 0 — Inventario, respaldo y punto de restauración

## Paso 0.1. Revisar el portátil

En PowerShell:

```powershell
Get-ComputerInfo | Select-Object WindowsProductName, WindowsVersion, OsArchitecture
Get-CimInstance Win32_ComputerSystem | Select-Object @{Name='RAM_GB';Expression={[math]::Round($_.TotalPhysicalMemory / 1GB, 1)}}
Get-PSDrive C | Select-Object Name, @{Name='Free_GB';Expression={[math]::Round($_.Free / 1GB, 1)}}
wsl --status
```

Orientación práctica (no es un requisito oficial del producto): con 16 GB o más de RAM y 80–100 GB libres, resulta razonable usar WSL2, Docker Desktop, PostgreSQL y un agente. Con 8–16 GB, trabaja con un agente de código a la vez y levanta solo servicios necesarios. Con menos de 8 GB, evita empezar instalando Docker Desktop; valora PostgreSQL local y un flujo más liviano.

## Paso 0.2. Inspeccionar el checkout actual

En la raíz del repo:

```powershell
git status --short
git branch --show-current
git remote -v
git fetch --all --prune
git branch -avv
git log --oneline --decorate --graph --all -30
```

Se han visto referencias a `main` y `feature/inicializar-backend`; confirma qué ramas existen y qué contiene cada una antes de decidir cuál usar. No asumas que una rama recordada sigue vigente.

No ejecutar para “limpiar” el repo durante la preparación:

```text
git reset --hard
git clean -xfd
git push --force
```

Pueden destruir cambios locales o historial.

## Paso 0.3. Crear respaldo local

Ajusta `$Repo` para que señale al checkout actual. Guarda el respaldo en una carpeta que no esté sincronizada automáticamente con la nube:

```powershell
$Repo = (Get-Location).Path
$Stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$Backup = Join-Path (Split-Path $Repo -Parent) "autopartco-prep-backup-$Stamp"
New-Item -ItemType Directory -Path $Backup -Force | Out-Null

git bundle create (Join-Path $Backup 'autopartco-all-refs.bundle') --all
git status --porcelain=v1 | Set-Content (Join-Path $Backup 'git-status.txt')
git diff --binary | Set-Content (Join-Path $Backup 'tracked-changes.patch')
```

`git bundle` conserva commits y refs; el parche conserva cambios rastreados no confirmados. Si hay archivos sin seguimiento, copia también el árbol de trabajo completo a un respaldo local seguro, omitiendo `node_modules`, `target`, `dist`, `coverage` si necesitas ahorrar espacio. No incluyas `.env` en ningún archivo que pienses subir o compartir. Si una copia local necesita los secretos para poder recuperarse, protégelos por separado.

Si `git status --short` no está vacío, identifica cada cambio antes de crear ramas o mover archivos. No descartes cambios solo para conseguir un árbol limpio.

## Paso 0.4. Crear una rama de preparación

Después de preservar los cambios existentes:

```powershell
git switch -c chore/predevelopment-baseline
```

Si la rama ya existe, revisa su estado y elige un nombre fechado, si hace falta. No hagas la preparación directamente en `main`.

---

# Fase 1 — Escoger y estabilizar el entorno

## Paso 1.1. Elegir una sola modalidad

### Recomendada si el hardware lo permite: Windows 11 + WSL2 + Ubuntu

OpenCode recomienda WSL para una experiencia más completa en Windows; Docker Desktop también puede usar el backend WSL2. Si eliges esta opción:

1. Habilita WSL2 con las instrucciones oficiales de Microsoft y reinicia si Windows lo solicita.
2. Instala Ubuntu.
3. Mantén el repositorio dentro del sistema de archivos Linux, por ejemplo `~/src/autopartco-dev`, no en `/mnt/c/...` si vas a trabajar intensivamente desde Linux.
4. Instala en Ubuntu las herramientas CLI que realmente usarás allí: Git, Java, Maven, Node, Python/uv, Graphify y agentes CLI.
5. Instala VS Code en Windows y la extensión Remote - WSL; abre la carpeta Linux desde VS Code.
6. Si instalas Docker Desktop, activa integración con Ubuntu.

No trabajes en el mismo checkout desde PowerShell y WSL a la vez. Conserva el checkout original de Windows como respaldo hasta verificar que el nuevo checkout refleja el estado correcto.

### Modalidad nativa de Windows

También es válida si WSL/Docker resultan demasiado pesados o ya tienes un entorno estable. Usa PowerShell y mantén todas las CLI en el mismo entorno. OpenCode puede instalarse en Windows con npm, Chocolatey o Scoop, pero su documentación recomienda WSL para la mejor compatibilidad; prueba pronto los flujos que piensas usar.

## Paso 1.2. Comprobar herramientas instaladas

Instala solo lo que falte:

```powershell
git --version
gh --version
java -version
mvn -version
node --version
npm --version
python --version
uv --version
docker --version
docker compose version
wsl --version
code --version
```

No todas deben estar instaladas desde el primer minuto. Registra `no instalado` donde corresponda. Usa fuentes oficiales y revisa las versiones antes de actualizar.

Versiones y límites que debe respetar el proyecto:

- Java 21; confirma también qué JDK está usando Maven.
- Spring Boot 4.1.1, según la base descrita hasta ahora; confirmar `pom.xml` real antes de actualizar versiones.
- Maven Wrapper (`mvnw.cmd`/`mvnw`) si existe y funciona; no mezclar wrappers y Maven global sin motivo.
- PostgreSQL 16+ según la documentación de Autopartco; probar scripts, extensiones y esquema reales.
- Node.js LTS que sea compatible con el Vite del `package.json`. No actualizar a una major nueva sin validar compatibilidad.
- Tailwind CSS v4. No crear `tailwind.config.js` por costumbre ni migrar a Tailwind v3.
- Utilizar el gestor de paquetes que indiquen el lockfile y el README; no mezclar npm/pnpm/yarn.

## Paso 1.3. Elegir cómo ejecutar PostgreSQL

Si el portátil tiene recursos suficientes, es preferible evaluar Docker Compose para la base de datos de desarrollo porque facilita repetir el entorno. No hace falta contenerizar frontend y backend desde el primer día.

Antes de crear `docker-compose.dev.yml`, inspecciona si ya existen archivos Compose, scripts SQL, seeds o configuraciones. Reutiliza lo que tenga sentido y documenta puertos, volumen y procedimiento de reset.

La base local debe ser independiente de producción, usar credenciales locales, tener datos de prueba ficticios y nunca ejecutar scripts destructivos sobre datos útiles.

---

# Fase 2 — Preparar el uso de todas las IAs

## Paso 2.1. Inventario de CLI

Antes de instalar nada, prueba qué está disponible:

```powershell
claude --version
gemini --version
opencode --version
copilot --version
graphify --version
```

El comando puede no existir o variar por versión. Registra la situación en vez de instalar paquetes no verificados. Descarga herramientas de su documentación o repositorios oficiales.

## Paso 2.2. Asignar una función a cada herramienta

| Herramienta | Uso preferido | Límite importante |
|---|---|---|
| ChatGPT | Definir alcance, convertir requisitos en tareas, comparar decisiones y sintetizar revisiones. | No puede afirmar que ejecutó builds locales si no se ejecutaron. |
| Claude Pro / Claude Code | Arquitectura compleja, refactors acotados, implementación larga y revisión profunda. | No permitir una reescritura masiva sin plan ni baseline. |
| Gemini Pro / Gemini CLI | Contrastar documentación, analizar contexto amplio y hacer una segunda revisión independiente. | Confirmar con documentación oficial todo detalle de APIs/versiones. |
| GitHub Copilot en VS Code/CLI | Navegar, hacer cambios pequeños, seguir instrucciones por ruta y trabajar en el editor. | No duplicar el blueprint completo en cada instrucción. |
| OpenCode | Enrutar tareas acotadas a modelos/proveedores disponibles y comparar calidad/coste. | Una suscripción no habilita automáticamente todos los modelos por API. |
| DeepSeek y otros modelos gratuitos disponibles | Segunda opinión, análisis, generación de casos de prueba y documentación no sensible. | No enviar secretos/datos privados y comprobar sus respuestas. |
| Google Colab | Experimentos de demanda/anomalías cuando exista un dataset adecuado y métrica de evaluación. | No convertirlo en el backend productivo ni afirmar precisión no medida. |
| NotebookLM | Consultas fundamentadas sobre blueprint, documentación oficial, ADR y manuales. | No es la fuente definitiva del comportamiento real del código. |

Autenticación CLI, suscripción web y acceso de API son cosas distintas. Antes de usar un modelo mediante API, revisa cuotas, límites, coste y datos enviados. No compartas claves entre proveedores salvo que sea necesario y seguro.

## Paso 2.3. Comparar modelos con la misma tarea

Usa primero un prompt de solo lectura:

> Analiza el repositorio sin modificar archivos. Resume la arquitectura actual con referencias a rutas reales, identifica cómo se ejecutan frontend y backend, enumera las pruebas existentes y señala qué no pudiste verificar. No infieras que una función está terminada solo porque existe una clase o ruta. Devuelve evidencias y comandos reproducibles.

Pasa el mismo prompt a cada agente. Puntúa rutas correctas, claridad de incertidumbres, respeto a restricciones, comandos válidos, calidad de pruebas, coste/tiempo y problemas inventados. Después repite la comparación con una prueba de tests y otra de revisión de diff. El mejor modelo para arquitectura no tiene que ser el mejor para UI o tareas rutinarias.

---

# Fase 3 — Incorporar los documentos y definir una fuente de verdad

## Paso 3.1. Copiar los documentos al repositorio

Copia los archivos descargados desde esta conversación al checkout de trabajo:

- `AUTOPARTCO_PRODUCT_BLUEPRINT.md` → `docs/product/PRODUCT_BLUEPRINT.md`
- `AUTOPARTCO_AGENT_CONTEXT.md` → `docs/engineering/AGENT_CONTEXT.md`
- Este runbook → `docs/engineering/PREDEVELOPMENT_RUNBOOK.md`

Crea las carpetas si hacen falta. Antes de reemplazar README u otros documentos existentes, compara su contenido y fusiona lo que siga siendo válido.

## Paso 3.2. Estructura documental objetivo

Adaptar esta propuesta al repo real; no crear archivos vacíos solo para llenar carpetas:

```text
docs/
  product/        PRODUCT_BLUEPRINT.md, user-journeys.md, glossary.md
  architecture/   system-context.md, module-map.md, data-model.md, security-model.md
  integrations/   alegra.md, siigo.md, wompi-sandbox.md, csv-import.md
  decisions/      ADR-0001-modular-monolith.md, ADR-0002-source-of-truth-per-field.md
  engineering/    AGENT_CONTEXT.md, PREDEVELOPMENT_RUNBOOK.md, local-development.md, baseline/
  testing/        test-strategy.md, acceptance-criteria.md
  operations/     secrets-and-environments.md, backup-and-recovery.md
  planning/       milestones.md, backlog.md
```

Separar arquitectura **observada** de arquitectura **propuesta**. Marcar cada función como `IMPLEMENTADO Y VERIFICADO`, `IMPLEMENTADO SIN VERIFICAR`, `PARCIAL`, `PROPUESTO` o `BLOQUEADO`.

## Paso 3.3. Instrucciones de agentes

- `AGENTS.md` en la raíz: reglas transversales breves.
- `.github/copilot-instructions.md`: reglas de Copilot y enlaces al contexto de producto.
- Instrucciones por ruta para frontend/backend/SQL/integraciones, después de revisar las actuales.
- `docs/engineering/AGENT_CONTEXT.md`: resumen cargado cuando sea necesario.
- `docs/product/PRODUCT_BLUEPRINT.md`: fuente completa; no cargarlo entero en cada tarea.
- `graphify-out/GRAPH_REPORT.md` y `graphify-out/graph.json`: artefactos generados, con fecha/commit identificados.

Si un asistente no detecta `AGENTS.md` o `.agents/skills`, instala/configura su mecanismo específico. No asumir que todos descubren los mismos archivos.

---

# Fase 4 — Rehacer Graphify con el código y documentos nuevos

## Paso 4.1. Verificar la instalación

La documentación vigente de Graphify identifica el paquete oficial como `graphifyy` (con dos “y”) y el comando como `graphify`. Si tienes una versión anterior, registra su versión y ubicación antes de cambiarla. En un entorno con `uv` se documenta:

```powershell
winget install astral-sh.uv
# Si uv no aparece, cierra y vuelve a abrir la terminal.
uv --version
uv tool list
uv tool install graphifyy
graphify --version
graphify --help
```

Si ya está administrado por `uv`, revisa primero `uv tool list` y actualiza la misma instalación conscientemente. No mezcles `pip`, `pipx` y `uv` sin necesidad.

## Paso 4.2. Crear/revisar `.graphifyignore`

Ejemplo inicial a adaptar a la sintaxis vigente:

```text
.git/
**/node_modules/
**/target/
**/dist/
**/build/
**/coverage/
**/.next/
**/.vite/
**/.idea/
**/.venv/
**/venv/
**/*.log
**/.env
**/.env.local
**/.env.development.local
**/.env.production.local
**/*.pem
**/*.key
```

Excluir los outputs del grafo de su propio análisis para evitar autorreferencias. Confirmar con una prueba que `.env` está excluido. Mantener `.env.example` solo si sus valores son placeholders y no secretos.

## Paso 4.3. Conservar el grafo anterior y reconstruir

Antes de reemplazar `graphify-out`, renómbralo o cópialo fuera del repo con fecha. No borrar hasta validar la nueva salida.

1. Asegura que blueprint, contexto y runbook ya estén dentro de la raíz del repo.
2. Abre la raíz en el asistente donde está instalada la skill de Graphify.
3. Sigue el flujo de la versión instalada para generar el grafo. La documentación describe `/graphify .` en asistentes compatibles; en PowerShell recuerda que el CLI se invoca como `graphify .`, sin barra inicial.
4. Si vas a operar solo con CLI, revisa `graphify --help` y la documentación de esa versión; no asumir que un comando antiguo sigue vigente.
5. Confirma que el grafo incluye backend, frontend, SQL y los nuevos Markdown, y excluye dependencias, outputs y secretos.
6. Registra versión, fecha, commit, número de nodos/aristas/comunidades y advertencias en `docs/engineering/baseline/graphify-baseline.md`.

Graphify puede extraer la estructura del código localmente. La extracción semántica de documentos/medios puede utilizar un modelo y consumir cuota. Antes de incluir documentos, verifica backend, autenticación, coste y política de datos. No actives automáticamente una API de pago para generar un grafo.

## Paso 4.4. Validar el resultado

Haz consultas sobre:

- Módulos y conexiones principales.
- Autenticación/autorización y flujo de usuarios.
- Producto → sucursal → inventario → pedido → pago.
- Configuración Wompi, callbacks/webhooks y estado del pago.
- Esquema SQL, claves externas e índices.
- Duplicados, clases con demasiadas dependencias y artefactos generados.
- Ubicación de instrucciones, skills y documentos nuevos.

Revisar nodos con demasiados enlaces, aristas irresueltas o referencias ambiguas. El grafo sirve para navegar y formular preguntas; no demuestra que el comportamiento esté correcto.

## Paso 4.5. Decidir qué se versiona

Versionar `GRAPH_REPORT.md` y `graph.json` si el tamaño es razonable, no contienen datos sensibles y se usarán para navegar el repo. Es posible ignorar `graph.html` si es grande y regenerable. Si se decide ignorar toda la salida, documentar la regeneración y exigirla en tareas que dependan del grafo. Nunca presentar un grafo viejo como si correspondiera al código actual.

---

# Fase 5 — Crear la línea base técnica antes de reorganizar código

## Paso 5.1. Descubrir los comandos reales

Lee README, `backend/pom.xml`, `frontend/package.json`, lockfiles, `application.yaml`, `.env.example`, scripts SQL y CI. No inventes comandos sin inspeccionar el proyecto.

Ejemplos habituales **solo después de confirmar que aplican**:

```powershell
# En backend; si existe el wrapper:
.\mvnw.cmd test
# Si no existe, valorar el Maven instalado:
mvn test

# En frontend, desde donde vive package.json:
npm ci
npm run
npm run build
```

Usa `npm ci` solo si `package-lock.json` corresponde al gestor del repo; no crear un lockfile distinto por accidente. Ejecuta los tests/lint que realmente existan en `package.json`.

Para cada comando, registra carpeta, versión, salida/código, fallos y causa raíz si está clara. No arregles todas las fallas durante la primera medición: convertirlas en tareas separadas permite ver qué cambió.

## Paso 5.2. Revisar base de datos

- Verificar el `ddl-auto` efectivo por perfil.
- Revisar scripts SQL, relaciones, constraints, índices, seeds y esquema real.
- Identificar contradicciones entre documentación y configuración.
- Confirmar si se utiliza Flyway/Liquibase o scripts propios.
- Probar que la inicialización funciona en una DB vacía y aislada.
- No enviar a IA externa dumps con datos personales o reales.

## Paso 5.3. Revisar archivos duplicados/generados

Inspeccionar `backend/bin/`, carpetas de build, copias y output de herramientas. No borrar una carpeta por su nombre. Confirmar si forma parte de compilación, despliegue, scripts o si está versionada accidentalmente.

## Paso 5.4. Informe de baseline

Crear `docs/engineering/baseline/BASELINE_REPORT.md` con rama/commit, arquitectura observada, comandos de arranque, builds/tests, flujos ejecutados frente a flujos solo leídos en código, discrepancias, riesgos priorizados y propuesta justificada de reorganización.

---

# Fase 6 — Reorganizar el repositorio con cambios controlados

1. Empezar por documentación e instrucciones, no por una reorganización masiva del backend.
2. Mantener `frontend/` y `backend/` si ya son las carpetas reales.
3. Mover archivos con `git mv` y en commits separados.
4. Separar commits de documentación, configuración de agentes, Graphify, refactors de código y migraciones SQL.
5. Comparar `git diff --stat` y el diff completo antes de cada commit; repetir las pruebas pertinentes tras cada movimiento.
6. Crear una decisión ADR solo para decisiones arquitectónicas con consecuencias duraderas; no documentar cada detalle menor.

Secuencia de commits sugerida:

- `docs: add product blueprint and agent context`
- `docs: record baseline and local development workflow`
- `chore: standardize agent instructions and ignore rules`
- `chore: refresh graphify knowledge graph`
- `refactor: reorganize documentation without behavior changes`
- `test: establish missing baseline checks`

Son ejemplos; adaptar al estado real. No mezclar movimientos de archivos, refactor funcional y cambios de DB en un único cambio.

---

# Fase 7 — Seleccionar skills de terceros

No instalar una skill porque tenga muchas estrellas o un nombre llamativo. Verificar repositorio de origen, actividad, licencia, instrucciones, requisitos, permisos y scripts. Probar una skill a la vez y revisar los archivos que el instalador crea/modifica.

## Lote inicial recomendado

### UX/UI — prioridad máxima

1. **UI/UX Pro Max** — <https://github.com/nextlevelbuilder/ui-ux-pro-max-skill>. Para explorar dirección visual, tokens, patrones, tipografía y sistema de diseño.
2. **Frontend Design de Anthropic** — <https://github.com/anthropics/skills/tree/main/skills/frontend-design>. Para construir/rediseñar pantallas con decisiones visuales intencionadas y específicas del dominio de autopartes.
3. **Web Design Guidelines de Vercel** — guía: <https://github.com/vercel-labs/web-interface-guidelines>; skill: <https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines>. Para auditar teclado, foco, responsive, estados, etiquetas, accesibilidad y microcopy.

Flujo recomendado: UI/UX Pro Max para explorar y definir el sistema; Frontend Design al crear/rediseñar una vista importante; Vercel Guidelines para revisar la implementación. No hace falta cargar las tres en cada tarea.

### Ingeniería, pruebas y operación

4. **Graphify** — navegar el código y rastrear dependencias; regenerar tras cambios estructurales.
5. **Superpowers** — <https://github.com/obra/superpowers>; evaluar planificación, TDD y depuración sistemática.
6. **`acquire-codebase-knowledge`** — catálogo <https://github.com/github/awesome-copilot/blob/main/docs/README.skills.md>; documentar arquitectura a partir de evidencia.
7. **`java-springboot` / `spring-boot-testing`** — candidatas del catálogo para backend/pruebas, verificando compatibilidad con Java 21 y la versión real de Spring Boot.
8. **`postgresql-code-review`** — revisar consultas/esquema, sin aceptar cambios de modelo automáticamente.
9. **`security-review`** — revisar autenticación, autorización, secretos y endpoints sensibles.
10. **`webapp-testing` / Playwright** — E2E de login, búsqueda, pedidos, inventario y conexión ERP; habilitar cuando el baseline funcione.
11. **`openapi-to-application-code`** — <https://github.com/github/awesome-copilot/tree/main/skills/openapi-to-application-code>; usar si hay contrato OpenAPI claro, no para generar toda la aplicación a ciegas.
12. **`doc-and-modernize`** — <https://github.com/github/awesome-copilot/tree/main/skills/doc-and-modernize>; documentar arquitectura observada y planear modernizaciones por fases.

La ubicación de las skills varía según el agente (`.agents/skills`, `.github/skills`, `.claude/skills`, etc.). Escoge las rutas compatibles con tus herramientas principales, verifica que se detecten y evita mantener copias divergentes de la misma skill. No instalar todas mediante una opción global `--all`.

### Capacidades para evaluar más adelante

- CI/CD y automatización de PR.
- Diseño y validación de API/OpenAPI.
- Accesibilidad y pruebas visuales.
- Dependencias, secretos y análisis estático.
- Observabilidad y logs.
- Migraciones SQL y rendimiento.
- Evaluación de LLM (calidad, latencia, coste y alucinaciones).
- Documentación técnica, ADR y refinamiento de criterios de aceptación.

Añadir una skill cuando exista un flujo repetible y un criterio de mejora comprobable. Las skills de ML llegan después de definir datasets/métricas.

---

# Fase 8 — Definir las tareas

Usa GitHub Issues + GitHub Projects, o el tablero existente si ya funciona. El backlog no debe vivir solo en chats ni notas locales.

| Código | Épica | Resultado esperado |
|---|---|---|
| PREP | Preparación | Entorno, respaldo, contexto, Graphify, skills y flujo de trabajo listos. |
| BASE | Estabilización | Builds, tests, configuración, migraciones, permisos y errores con riesgos priorizados. |
| ID | Identidad/permisos | Cuentas, roles, acceso por empresa/sucursal/recurso y auditoría. |
| CAT | Catálogo automotriz | Productos, especificaciones, vehículos, referencias y procedencia de compatibilidad. |
| INV | Inventario por sucursal | Movimientos, reservas, ajustes, transferencias y reconciliación. |
| ERP | Integraciones | CSV; después Alegra y Siigo con credenciales seguras, mapeo, sincronización y trazabilidad. |
| ORD | Marketplace/pedidos | Búsqueda, carrito/pedido, estados, seguimiento y permisos por vendedor. |
| PAY | Pagos | Simulador y/o Wompi sandbox, webhooks seguros, idempotencia y estados correctos. |
| STORE | Portal comercial | Sucursales, catálogo, equipo, pedidos, promociones y analítica. |
| OPS | Operaciones internas | Verificación, moderación, planes, soporte y auditoría. |
| UX | Experiencia de usuario | Sistema visual, journeys, usabilidad, accesibilidad y estados completos. |
| AI | Inteligencia | Búsqueda asistida, diagnóstico orientativo, recomendación y evaluación. |
| BI | Analítica | Eventos confiables, métricas y predicción solo cuando haya datos suficientes. |
| REL | Preparación productiva | Backups, observabilidad, seguridad, rendimiento, despliegue y runbooks. |

Prioridad: **P0** bloqueos, seguridad y pérdida de datos; **P1** flujos centrales; **P2** mejoras operativas/diferenciadoras; **P3** experimentos dependientes de datos o validación de mercado.

Plantilla obligatoria por tarea:

```markdown
## Objetivo
## Problema de usuario/negocio
## Estado actual comprobado (rutas y evidencia)
## Dentro del alcance
## Fuera del alcance
## Dependencias
## Riesgos de datos, seguridad, coste o UX
## Criterios de aceptación verificables
- [ ] ...
## Pruebas que se ejecutarán
## Documentación que debe actualizarse
## Implementador / revisor independiente
## Decisiones que requieren aprobación humana
```

La tarea debe ser lo bastante pequeña para que un revisor entienda el cambio. Seguridad, pagos, datos y permisos requieren revisión humana antes de fusionarse.

---

# Fase 9 — Método operativo para varias IAs

1. **Tú orquestas y apruebas** alcance, decisiones de negocio, cambios de alto riesgo y merges.
2. **Investigador (solo lectura):** inspecciona repo/Graphify y entrega evidencias, no código.
3. **Implementador:** cambia una tarea delimitada en su propia rama/worktree.
4. **Revisor independiente:** revisa diff, permisos, errores, pruebas, UX y consecuencias sin heredar ciegamente el razonamiento del implementador.
5. **QA/CI:** ejecuta builds, unit tests, integración, lint y E2E disponibles.
6. Corrige hallazgos, documenta resultado y fusiona.

Reglas:

- Nunca dos implementadores editando la misma rama/archivos simultáneamente.
- Para paralelo, usar ramas/worktrees separados, interfaces acordadas y tareas sin solapamiento.
- Cada migración/esquema tiene un dueño por tarea.
- No pedir a cinco modelos que escriban cinco versiones del mismo archivo; pedirles que comparen propuestas y luego elegir una.
- Registrar modelo/proveedor, tarea, coste estimado/real, resultado y defectos relevantes.

### Benchmark de modelos

Probar los modelos disponibles con tareas idénticas: (1) explicar un flujo sin modificar; (2) proponer tests; (3) implementar una validación pequeña en branch de prueba; (4) revisar un diff por seguridad/UX. Puntuar exactitud, obediencia a instrucciones, tests reproducibles, seguridad, calidad del diff, claridad de incertidumbre, tiempo y coste. Seleccionar por categoría, no por fama.

---

# Fase 10 — Automatización mínima

Antes de nuevas funciones:

- Builds de frontend y backend reproducibles.
- Lint/formato/tests según el proyecto real.
- Tests negativos de autorización entre dos almacenes y sucursales distintas.
- Fixtures ficticios y reset de DB de pruebas reproducible.
- Comprobaciones para impedir subir `.env`, claves, certificados y dumps.
- CI en GitHub Actions con servicios de prueba aislados.
- Plantilla de PR con resumen, pruebas, capturas UX cuando correspondan, migraciones, riesgos y rollback.
- Revisiones de dependencias/seguridad disponibles habilitadas sin asumir servicios de pago.

Primero detecta scripts existentes; luego crea los que falten. No declarar un flujo probado si solo se leyó el código.

---

# Fase 11 — Lista de autorización para empezar desarrollo funcional

- [ ] Respaldo creado y procedimiento de recuperación identificado.
- [ ] Branch principal, rama de trabajo y cambios locales documentados.
- [ ] Secretos fuera del historial y del material compartido.
- [ ] Informe de versiones de herramientas guardado.
- [ ] Comandos de build/test/arranque del frontend y backend documentados.
- [ ] Builds y tests iniciales ejecutados o fallos registrados con evidencia.
- [ ] Blueprint, resumen de agentes y runbook versionados.
- [ ] Grafo nuevo contiene los documentos y excluye secretos/builds/dependencias.
- [ ] Arquitectura observada separada del diseño futuro.
- [ ] Skills principales instaladas y probadas o descartadas con motivo.
- [ ] Backlog con épicas, PREP priorizado y criterios de aceptación.
- [ ] `AGENTS.md` e instrucciones por agente sin contradicciones.
- [ ] Política de coste, proveedor, privacidad y revisión independiente acordada.

Al completar esta lista, empezar por **BASE (estabilización)** y un flujo vertical mínimo. No iniciar por predicción de demanda ni por una reorganización masiva.

## Fuentes oficiales

- Graphify: <https://github.com/Graphify-Labs/graphify>
- Graphify configuración y exclusiones: <https://docs.graphify.com/reference/configuration>
- Graphify formatos de entrada: <https://docs.graphify.com/concepts/supported-inputs>
- OpenCode: <https://docs.opencode.ai/docs/>
- Gemini CLI instalación y autenticación: <https://geminicli.com/docs/get-started/installation/> y <https://geminicli.com/docs/get-started/authentication/>
- Claude Code: <https://docs.anthropic.com/en/docs/claude-code/getting-started>
- Skills para GitHub Copilot: <https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-skills>
- UI/UX Pro Max: <https://github.com/nextlevelbuilder/ui-ux-pro-max-skill>
- Anthropic Frontend Design: <https://github.com/anthropics/skills/tree/main/skills/frontend-design>
- Vercel Web Interface Guidelines: <https://github.com/vercel-labs/web-interface-guidelines>
- Docker Desktop/WSL: <https://docs.docker.com/desktop/setup/install/windows-install/>
- Spring Boot requirements: <https://docs.spring.io/spring-boot/system-requirements.html>
