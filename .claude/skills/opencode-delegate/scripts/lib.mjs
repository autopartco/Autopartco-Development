// Utilidades compartidas: localizar la CLI de OpenCode, ejecutar `opencode run` y resumir su flujo JSON.
import { spawn, execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const skillDir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");
export const modelsFile = path.join(skillDir, "models.json");
export const logDir = path.join(os.tmpdir(), "opencode-delegate");

export function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  if (i === -1) return fallback;
  const v = process.argv[i + 1];
  return v === undefined || v.startsWith("--") ? true : v;
}

export function findCli() {
  const candidates = [
    process.env.OPENCODE_CLI,
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Programs", "@opencodedesktop", "resources", "opencode-cli.exe"),
    "/Applications/OpenCode.app/Contents/Resources/opencode-cli",
  ].filter(Boolean);
  // Si el escritorio es 2.x, el `opencode` 1.x de npm rompe la base de datos compartida: se usa solo como último recurso.
  return candidates.find((c) => fs.existsSync(c)) ?? "opencode";
}

export function readModels() {
  try { return JSON.parse(fs.readFileSync(modelsFile, "utf8")); } catch { return { roles: {}, models: {} }; }
}

export function git(dir, ...a) {
  // trimEnd, no trim: `git status --porcelain` empieza con espacio (" M ruta") y recortarlo desplaza la columna de la ruta.
  try { return execFileSync("git", ["-C", dir, ...a], { encoding: "utf8" }).trimEnd(); } catch { return ""; }
}

// Ejecuta `opencode run` y devuelve un resumen. stdin debe ir cerrado ("ignore"):
// con stdin abierto la CLI espera entrada y se queda colgada sin emitir nada hasta el timeout.
export function runOpencode({ cli = findCli(), dir, agent, model, message, files = [], title, session, timeoutMin = 30, auto = false, logFile }) {
  const args = ["run", "--format", "json"];
  if (agent) args.push("--agent", agent);
  if (model) args.push("--model", model);
  if (title) args.push("--title", title);
  if (session) args.push("--session", session);
  if (auto) args.push("--auto");
  for (const f of files) args.push("--file", f);
  args.push(message);

  fs.mkdirSync(path.dirname(logFile), { recursive: true });
  const started = Date.now();
  return new Promise((resolve) => {
    // PWD explícito: desde Git Bash llega en formato POSIX (/c/...) y la CLI falla al cambiar de directorio.
    const child = spawn(cli, args, { cwd: dir, env: { ...process.env, PWD: dir }, stdio: ["ignore", "pipe", "pipe"] });
    const log = fs.createWriteStream(logFile);
    let stderr = "";
    child.stdout.pipe(log);
    child.stderr.on("data", (c) => (stderr += c));
    let timedOut = false;
    const killer = setTimeout(() => { timedOut = true; child.kill(); }, timeoutMin * 60_000);
    child.on("error", (e) => (stderr += `\n${e.message}`));
    child.on("close", (code) => {
      clearTimeout(killer);
      log.end(() => resolve({ ...parseLog(logFile), exitCode: code, timedOut, secs: Math.round((Date.now() - started) / 1000), stderr }));
    });
  });
}

export function parseLog(logFile) {
  const s = { sessionID: null, steps: 0, tools: {}, toolErrors: [], permissionRejected: [],
    tokens: { input: 0, output: 0, reasoning: 0, cacheRead: 0 }, cost: 0, finalText: "", errors: [], touched: new Set() };
  let text = "";
  for (const line of fs.readFileSync(logFile, "utf8").split("\n")) {
    let e; try { e = JSON.parse(line); } catch { continue; }
    s.sessionID ??= e.sessionID ?? null;
    const p = e.part || {};
    if (e.type === "step_start") { s.steps++; text = ""; }
    else if (e.type === "step_finish") {
      s.cost += p.cost || 0;
      s.tokens.input += p.tokens?.input || 0;
      s.tokens.output += p.tokens?.output || 0;
      s.tokens.reasoning += p.tokens?.reasoning || 0;
      s.tokens.cacheRead += p.tokens?.cache?.read || 0;
    } else if (e.type === "tool_use") {
      s.tools[p.tool] = (s.tools[p.tool] || 0) + 1;
      // Archivos editados según el propio agente: fiable aunque haya otras delegaciones en paralelo.
      const inp = p.state?.input || {};
      if (p.state?.status === "completed" && /write|edit|patch/i.test(p.tool)) {
        for (const f of [inp.path, inp.filePath, inp.file_path].filter(Boolean)) s.touched.add(f);
        for (const m of String(inp.patchText || inp.patch || "").matchAll(/^\*\*\* (?:Add|Update|Delete) File: (.+)$/gm)) s.touched.add(m[1].trim());
      }
      if (p.state?.status === "error") {
        const err = String(p.state.error || "");
        const what = `${p.tool}: ${JSON.stringify(p.state.input ?? {}).slice(0, 140)}`;
        if (/permission|cannot ask the user|rejected/i.test(err)) s.permissionRejected.push(what);
        else s.toolErrors.push(`${what} -> ${err.slice(0, 160)}`);
      }
    } else if (e.type === "text") {
      text += (text ? "\n" : "") + (p.text || "");
      s.finalText = text; // texto del último paso = respuesta final
    } else if (e.type === "error") {
      s.errors.push(JSON.stringify(e.error ?? e).slice(0, 300));
    }
  }
  return s;
}

// Índice de un Markdown: encabezados con su línea, para revisar sin leer el documento entero.
export function outline(file, max = 40) {
  try {
    const lines = fs.readFileSync(file, "utf8").split("\n");
    const heads = lines.map((l, i) => [l, i + 1]).filter(([l]) => /^#{1,4} /.test(l)).map(([l, n]) => `${n}: ${l.trim()}`);
    const todo = lines.filter((l) => /POR CONFIRMAR|TODO|TBD/.test(l)).length;
    return { lines: lines.length, words: lines.join(" ").split(/\s+/).filter(Boolean).length, pendientes: todo, headings: heads.slice(0, max) };
  } catch { return null; }
}
