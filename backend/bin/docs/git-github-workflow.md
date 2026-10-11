# Guía de trabajo con Git y GitHub

Esta guía describe el flujo recomendado para contribuir al backend de Autopartco: preparar el equipo, obtener el código, trabajar a partir de un Issue y entregar los cambios mediante un Pull Request.

## Índice

- [Requisitos](#1-requisitos)
- [Configurar Git una sola vez](#2-configurar-git-una-sola-vez)
- [Clonar el proyecto](#3-clonar-el-proyecto)
- [Preparar la configuración local](#4-preparar-la-configuración-local)
- [Trabajar desde un Issue](#5-trabajar-desde-un-issue)
- [Crear y actualizar una rama](#6-crear-y-actualizar-una-rama)
- [Desarrollar y guardar cambios](#7-desarrollar-y-guardar-cambios)
- [Actualizar la rama antes de entregar](#8-actualizar-la-rama-antes-de-entregar)
- [Entregar el trabajo mediante Pull Request](#9-entregar-el-trabajo-mediante-pull-request)
- [Después del merge](#10-después-del-merge)
- [Checklist de entrega](#11-checklist-de-entrega)

## 1. Requisitos

Instala las siguientes herramientas:

- Git.
- Java 21 o superior.
- PostgreSQL 16 o superior.
- Visual Studio Code u otro IDE compatible con Java.

Comprueba la instalación:

```bash
git --version
java -version
psql --version
```

El proyecto incluye Maven Wrapper, así que no es obligatorio instalar Maven globalmente. En Windows se usa `mvnw.cmd`; en Linux/macOS, `./mvnw`.

## 2. Configurar Git una sola vez

Configura el nombre y el correo que aparecerán en tus commits. El correo debe estar asociado a tu cuenta de GitHub o configurado como correo privado de GitHub:

```bash
git config --global user.name "Tu nombre"
git config --global user.email "tu-correo@example.com"
```

Verifica la configuración:

```bash
git config --global --list
git config user.name
git config user.email
```

### Autenticación con GitHub

Para clonar y hacer `push`, utiliza una de estas opciones:

- **HTTPS:** GitHub solicitará autenticación mediante un Personal Access Token; no uses tu contraseña de GitHub como token.
- **SSH:** configura una clave SSH en tu equipo, agrega la clave pública a GitHub y usa una URL `git@github.com:...`.

Comprueba una conexión SSH con:

```bash
ssh -T git@github.com
```

Nunca compartas tokens, claves privadas, contraseñas ni archivos `.env`.

## 3. Clonar el proyecto

Copia la URL del repositorio desde GitHub y clónalo:

```bash
git clone <URL_DEL_REPOSITORIO>
cd backend
```

Comprueba que el repositorio esté limpio y que estés en `main`:

```bash
git status
git branch --show-current
```

Si el repositorio contiene más de un proyecto, entra en la carpeta del backend antes de ejecutar los comandos Maven.

## 4. Preparar la configuración local

1. Crea el archivo local de variables a partir del ejemplo:

   ```bash
   copy .env.example .env
   ```

   En Linux/macOS:

   ```bash
   cp .env.example .env
   ```

2. Edita `.env` con tus credenciales locales de PostgreSQL y los valores necesarios para correo y claves RSA.
3. Crea la base de datos y ejecuta los scripts desde la raíz del proyecto:

   ```bash
   psql -U postgres -h localhost -d postgres -c "CREATE DATABASE autopartco OWNER postgres;"
   psql -U postgres -h localhost -d autopartco -f database/init.sql
   psql -U postgres -h localhost -d autopartco -f database/data-init.sql
   ```

   Si la base de datos ya existe, omite el primer comando. `data-init.sql` es solo para desarrollo y pruebas.

4. Genera el par de claves RSA si todavía no existe. Las claves privadas no deben subirse a GitHub.
5. Arranca el backend para comprobar la instalación:

   Windows:

   ```cmd
   mvnw.cmd spring-boot:run
   ```

   Linux/macOS:

   ```bash
   ./mvnw spring-boot:run
   ```

6. Verifica `http://localhost:8080/actuator/health` y confirma que la respuesta indique `"status": "UP"`.

El archivo `.gitignore` excluye `.env`, `target/`, archivos `.pem` y configuraciones del IDE. Revisa siempre los archivos antes de hacer `commit`.

## 5. Trabajar desde un Issue

Cada cambio debe estar relacionado con un Issue de GitHub. Antes de programar:

- Lee la descripción completa y los criterios de aceptación.
- Revisa comentarios, dependencias e Issues relacionados.
- Confirma el alcance con el equipo si falta información.
- Identifica los módulos, tablas y convenciones de API afectados.
- No mezcles varios Issues independientes en una misma rama.

Si encuentras un problema que no pertenece al Issue actual, crea un nuevo Issue y relaciónalo. No amplíes el alcance silenciosamente.

## 6. Crear y actualizar una rama

Nunca trabajes directamente sobre `main`. Primero actualízala:

```bash
git switch main
git pull --ff-only origin main
```

Crea una rama vinculada al Issue:

```bash
git switch -c feature/25-register-account
```

Prefijos recomendados:

- `feature/25-descripcion` para funcionalidades.
- `fix/42-descripcion` para correcciones.
- `docs/30-descripcion` para documentación.
- `chore/18-descripcion` para tareas técnicas.

Confirma la rama activa antes de editar:

```bash
git branch --show-current
git status
```

## 7. Desarrollar y guardar cambios

Durante el trabajo, consulta con frecuencia el estado del repositorio:

```bash
git status
git diff
git diff --check
```

Ejecuta las pruebas del backend:

Windows:

```cmd
mvnw.cmd test
```

Linux/macOS:

```bash
./mvnw test
```

Agrupa cambios relacionados en commits pequeños. Usa mensajes claros con este formato:

```text
tipo(modulo): descripcion breve
```

Ejemplos:

```bash
git add src/main/java/com/autopartco/backend/auth
git commit -m "feat(auth): add account registration"

git add docs/git-github-workflow.md
git commit -m "docs: add GitHub contribution guide"
```

Tipos habituales: `feat`, `fix`, `refactor`, `test`, `docs` y `chore`. No incluyas `.env`, claves privadas, contraseñas, logs ni archivos generados.

## 8. Actualizar la rama antes de entregar

Antes de abrir el Pull Request, integra los cambios más recientes de `main`:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch feature/25-register-account
git merge main
```

Si aparecen conflictos, ejecuta `git status`, resuelve manualmente los archivos indicados, elimina los marcadores de conflicto y valida el resultado:

```bash
git add <archivo-resuelto>
git commit -m "chore: resolve merge conflicts"
mvnw.cmd test
```

En Linux/macOS usa `./mvnw test`. Nunca confirmes una resolución de conflictos sin revisar el código resultante.

## 9. Entregar el trabajo mediante Pull Request

Revisa qué se enviará:

```bash
git status
git diff main...HEAD
git log --oneline main..HEAD
```

Publica la rama:

```bash
git push -u origin feature/25-register-account
```

En GitHub, crea un Pull Request hacia `main`. El Pull Request debe incluir:

- Un título que describa el cambio.
- Un resumen de lo implementado.
- Las pruebas ejecutadas y su resultado.
- El Issue relacionado, por ejemplo `Closes #25`.
- Notas sobre decisiones, limitaciones o configuración adicional.

Espera a que terminen las validaciones automáticas y solicita la revisión de otro integrante. Si recibes comentarios, corrige en la misma rama, ejecuta nuevamente las pruebas y publica los cambios:

```bash
git add <archivos-corregidos>
git commit -m "fix(auth): address review comments"
git push
```

No crees otro Pull Request para las correcciones del mismo trabajo.

## 10. Después del merge

Cuando el Pull Request haya sido aprobado e integrado:

```bash
git switch main
git pull --ff-only origin main
git status
git branch -d feature/25-register-account
git fetch --prune
```

Si GitHub no eliminó la rama remota, puedes eliminarla con:

```bash
git push origin --delete feature/25-register-account
```

## 11. Checklist de entrega

- [ ] El cambio está asociado a un Issue.
- [ ] La rama no parte de código desactualizado.
- [ ] No se trabajó directamente sobre `main`.
- [ ] No hay secretos, `.env`, claves privadas ni archivos generados.
- [ ] Se revisaron `git diff` y `git status`.
- [ ] Las pruebas (`mvnw.cmd test` o `./mvnw test`) pasan.
- [ ] La documentación y los scripts de base de datos están actualizados si aplica.
- [ ] El Pull Request explica el cambio y enlaza el Issue.
- [ ] Se atendieron los comentarios de revisión.
