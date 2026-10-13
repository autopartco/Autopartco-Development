#!/usr/bin/env node
// Guardián PreToolUse para los subagentes de Autopartco (.claude/agents/).
//
// Uso:
//   node agent-guard.mjs paths --allow <patrón> ... [--ask <patrón> ...] [--deny <patrón> ...]
//   node agent-guard.mjs bash
//
// Patrones de ruta, relativos a la raíz del repo y con "/":
//   - terminados en "/" -> prefijo de directorio ("backend/src/test/")
//   - el resto         -> glob con * (sin cruzar "/"), ** (cualquier cosa) y ?
// Precedencia: deny > ask > allow; si nada coincide, se deniega.
//
// Salida: exit 0 sin texto = permitir; JSON con permissionDecision "ask" = pedir
// confirmación al usuario; exit 2 con motivo en stderr = bloquear.

import path from "node:path";

const ALWAYS_DENY = ["**/.env", "**/.env.*", "**/*.pem", "**/*.key", "backend/bin/", "graphify-out/", ".git/"];
const ALWAYS_ALLOW_EXCEPTIONS = ["**/.env.example"];

function readStdin() {
  return new Promise((resolve) => {
    let data = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (chunk) => (data += chunk));
    process.stdin.on("end", () => resolve(data));
  });
}

function block(reason) {
  process.stderr.write(`[agent-guard] ${reason}\n`);
  process.exit(2);
}

function ask(reason) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "ask",
      permissionDecisionReason: `[agent-guard] ${reason}`,
    },
  }));
  process.exit(0);
}

function globToRegExp(pattern) {
  let re = "";
  for (let i = 0; i < pattern.length; i++) {
    const c = pattern[i];
    if (c === "*" && pattern[i + 1] === "*") {
      // "**/" también coincide con cero directorios
      if (pattern[i + 2] === "/") {
        re += "(?:.*/)?";
        i += 2;
      } else {
        re += ".*";
        i += 1;
      }
    } else if (c === "*") {
      re += "[^/]*";
    } else if (c === "?") {
      re += "[^/]";
    } else {
      re += c.replace(/[.+^${}()|[\]\\]/g, "\\$&");
    }
  }
  return new RegExp(`^${re}$`, "i");
}

function matches(relPath, pattern) {
  if (pattern.endsWith("/")) {
    return relPath.toLowerCase().startsWith(pattern.toLowerCase());
  }
  return globToRegExp(pattern).test(relPath);
}

function parseLists(args) {
  const lists = { allow: [], ask: [], deny: [] };
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i].replace(/^--/, "");
    if (!(key in lists) || args[i + 1] === undefined) {
      block(`Argumento inválido en la configuración del hook: ${args[i]}`);
    }
    lists[key].push(args[i + 1]);
  }
  return lists;
}

function checkPath(input, lists) {
  const toolInput = input.tool_input ?? {};
  const target = toolInput.file_path ?? toolInput.notebook_path ?? toolInput.path;
  if (!target) {
    block("No se pudo determinar la ruta del archivo a editar.");
  }
  const root = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const absolute = path.resolve(root, target);
  const relPath = path.relative(root, absolute).split(path.sep).join("/");
  if (relPath === "" || relPath.startsWith("..") || path.isAbsolute(relPath)) {
    block(`Ruta fuera del repositorio: ${target}`);
  }

  const exception = ALWAYS_ALLOW_EXCEPTIONS.some((p) => matches(relPath, p));
  if (!exception && ALWAYS_DENY.some((p) => matches(relPath, p))) {
    block(`Ruta protegida para todos los agentes: ${relPath}`);
  }
  if (lists.deny.some((p) => matches(relPath, p))) {
    block(`Este agente no puede editar ${relPath}.`);
  }
  if (lists.ask.some((p) => matches(relPath, p))) {
    ask(`Editar ${relPath} requiere confirmación del usuario.`);
  }
  if (exception || lists.allow.some((p) => matches(relPath, p))) {
    process.exit(0);
  }
  block(`${relPath} está fuera del área de edición de este agente.`);
}

// Comandos que nunca debe ejecutar un subagente (pedirlos al usuario).
const BASH_DENY = [
  [/\b(psql|pg_dump|pg_restore|pg_dumpall|createdb|dropdb)\b/i, "comandos contra bases de datos"],
  [/\b(flyway|liquibase)\b/i, "migraciones contra bases de datos"],
  [/\bgit\s+(push|reset|clean|rebase|checkout|restore|rm|filter-branch|filter-repo)\b/i, "git que modifica historia, remoto o el árbol de trabajo"],
  [/\bgit\s+branch\s+-D\b/, "borrado de ramas"],
  [/\b(rm|rmdir|del|Remove-Item)\b/i, "borrado de archivos"],
  [/\b(npm|pnpm|yarn)\s+(install|i|add|update|remove|uninstall)\b/i, "instalación de dependencias"],
  [/\bnpx\b/i, "ejecución de paquetes remotos con npx"],
  [/\b(docker|supabase|vercel|gh)\b/i, "servicios externos o contenedores"],
  [/\b(curl|wget|Invoke-WebRequest|Invoke-RestMethod)\b/i, "llamadas de red"],
];

function checkBash(input) {
  const command = String(input.tool_input?.command ?? "");
  for (const [pattern, label] of BASH_DENY) {
    if (pattern.test(command)) {
      block(`Bloqueado (${label}). Pide autorización al usuario y que lo ejecute él o la sesión principal.`);
    }
  }
  // V-02: BackendApplicationTests es @SpringBootTest con ddl-auto=update y usa la BD de .env.
  // Hasta aislar las pruebas de integración, solo se permiten pruebas Maven con -Dtest explícito.
  const runsMavenTests = /\b(mvnw(\.cmd)?|mvn)\b[^|;&]*\b(test|verify|install|package)\b/i.test(command);
  if (runsMavenTests) {
    const explicitTests = /-Dtest=\S+/.test(command);
    if (!explicitTests || /BackendApplicationTests/.test(command) || /-Dtest=\S*\*/.test(command)) {
      block(
        "Las pruebas Maven sin -Dtest explícito arrancan BackendApplicationTests, que modifica la BD de .env " +
        "(ddl-auto: update). Usa -Dtest=<ClaseTest>[,<OtraTest>] sin comodines ni BackendApplicationTests."
      );
    }
  }
  process.exit(0);
}

const [mode, ...rest] = process.argv.slice(2);
const raw = await readStdin();
let input;
try {
  input = JSON.parse(raw || "{}");
} catch {
  block("Entrada del hook no es JSON válido.");
}

if (mode === "paths") {
  checkPath(input, parseLists(rest));
} else if (mode === "bash") {
  checkBash(input);
} else {
  block(`Modo desconocido: ${mode}`);
}
