#!/usr/bin/env node
// Delega una tarea a OpenCode en modo no interactivo y devuelve un resumen compacto para Claude.
//
// Uso:
//   node delegate.mjs --kind <code|tests|docs|text|research> --prompt-file <archivo>
//                     [--agent <agente>] [--model <proveedor/modelo[#variante]>] [--files a,b,c]
//                     [--out <archivo>] [--verify "<comando>"] [--session <id>] [--title <t>]
//                     [--dir <repo>] [--timeout-min N] [--no-fallback] [--auto] [--full]
//
// - --kind añade la plantilla templates/<kind>.md (método, estilo y formato de respuesta) al prompt,
//   y elige agente y modelo por defecto desde models.json → roles.<kind> (con suplentes).
// - Si el modelo falla sin haber cambiado archivos, reintenta solo con el siguiente suplente.
// - --files adjunta archivos de contexto (evita que el agente gaste pasos buscándolos).
// - --out guarda la respuesta final en un archivo (texto/research): Claude no necesita leerla entera.
// - --verify ejecuta un comando POSIX (Git Bash en Windows) al terminar y devuelve solo ok/fallo y las últimas líneas.
// El flujo JSON completo queda en logFile; no lo leas salvo para depurar.
import { execSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { arg, git, logDir, outline, readModels, runOpencode, skillDir } from "./lib.mjs";

const kind = arg("kind", "code");
const promptFile = arg("prompt-file");
const dir = path.resolve(arg("dir", process.cwd()));
const out = arg("out") ? path.resolve(arg("out")) : null;
const verify = arg("verify");
const full = !!arg("full");
const index = readModels();
const role = index.roles?.[kind] || index.roles?.code || {};
const agent = arg("agent", role.agent);
const timeoutMin = Number(arg("timeout-min", role.timeoutMin ?? 30));
// --model admite una lista "a,b" (principal y suplentes) para sustituir la del rol.
const models = arg("model") ? String(arg("model")).split(",").map((m) => m.trim()) : (role.models || []);
const fallback = !arg("no-fallback") && !arg("session");

const missing = !promptFile ? "Falta --prompt-file"
  : !models.length ? `No hay modelo: pasa --model o define roles.${kind} en models.json`
  : !agent ? `--kind ${kind} exige --agent (backend, database, frontend…): elige el dueño del área` : null;
if (missing) { console.error(missing); process.exit(1); }

const tpl = (name) => { const f = path.join(skillDir, "templates", `${name}.md`); return fs.existsSync(f) ? fs.readFileSync(f, "utf8").trim() : ""; };
const template = [tpl(kind), tpl("_common")].filter(Boolean).join("\n\n");
// En una ronda de corrección (--session) el agente ya conoce la plantilla.
let message = fs.readFileSync(promptFile, "utf8").trim() + (template && !arg("session") ? `\n\n${template.trim()}` : "");
const files = arg("files") ? String(arg("files")).split(",").map((f) => f.trim()).filter(Boolean) : [];
// --diff [rutas]: adjunta el diff sin commit (y los archivos nuevos) ya calculado. Para PR, commits y changelogs:
// ahorra pasos al agente y evita que le rechacen `git diff` sobre rutas protegidas (.claude/, .opencode/).
const diffArg = arg("diff");
if (diffArg) {
  const paths = diffArg === true ? [] : String(diffArg).split(",").map((p) => p.trim()).filter(Boolean);
  const untracked = git(dir, "ls-files", "--others", "--exclude-standard", "--", ...paths);
  const body = [`# git diff HEAD -- ${paths.join(" ") || "(todo)"}`, git(dir, "diff", "HEAD", "--stat", "--", ...paths), "",
    git(dir, "diff", "HEAD", "--", ...paths), "", "# Archivos nuevos sin seguimiento", untracked || "(ninguno)"].join("\n");
  const diffFile = path.join(logDir, `${Date.now()}-diff.txt`);
  fs.mkdirSync(logDir, { recursive: true });
  fs.writeFileSync(diffFile, body);
  files.push(diffFile);
}
// La línea de comandos de Windows admite ~32K caracteres: los prompts largos van como adjunto.
if (message.length > 24_000) {
  const big = path.join(logDir, `${Date.now()}-prompt.md`);
  fs.mkdirSync(logDir, { recursive: true });
  fs.writeFileSync(big, message);
  files.unshift(big);
  message = "Tus instrucciones completas están en el primer archivo adjunto. Síguelas al pie de la letra.";
}

const porcelain = () => git(dir, "status", "--porcelain", "--untracked-files=all").split("\n").filter(Boolean)
  .map((l) => ({ status: l.slice(0, 2).trim(), file: l.slice(3).replace(/^"|"$/g, "") }));
const dirtyBefore = new Set(porcelain().map((f) => f.file));
const headBefore = git(dir, "rev-parse", "HEAD");
const rel = (f) => path.relative(dir, path.resolve(dir, f)).split(path.sep).join("/");

const attempts = [];
const touched = new Set();
const started = Date.now();
let r;
for (const model of fallback ? models : models.slice(0, 1)) {
  const logFile = path.join(logDir, `${Date.now()}-${kind}.jsonl`);
  r = await runOpencode({ dir, agent, model, message, files, logFile, timeoutMin, auto: !!arg("auto"),
    session: arg("session"), title: arg("title", `claude-${kind}-${new Date().toISOString().slice(0, 16)}`) });
  r.model = model; r.logFile = logFile;
  for (const f of r.touched) touched.add(rel(f));
  const failed = r.timedOut || r.exitCode !== 0 || r.errors.length > 0 || !r.finalText.trim();
  attempts.push(`${model}: ${failed ? (r.timedOut ? "timeout" : r.errors[0]?.slice(0, 100) || `exit ${r.exitCode}, sin respuesta`) : "ok"} (${r.secs}s)`);
  // Reintentar con otro modelo solo si este no editó nada: así no se duplican cambios a medias.
  if (!failed || r.timedOut || r.touched.size) break;
}

// Atribución por los registros del agente (no por git): otras delegaciones en paralelo no se confunden con esta.
const status = new Map(porcelain().map((f) => [f.file, f.status]));
const others = [...status.keys()].filter((f) => !dirtyBefore.has(f) && !touched.has(f));
const summary = {
  ok: !r.timedOut && r.exitCode === 0 && !r.errors.length && !!r.finalText.trim(),
  kind, agent, model: r.model, sessionID: r.sessionID, secs: Math.round((Date.now() - started) / 1000),
  attempts: attempts.length > 1 ? attempts : undefined,
  steps: r.steps, tools: r.tools,
  tokens: r.tokens.output ? `${r.tokens.input}in/${r.tokens.output}out` : undefined,
  cost: r.cost || undefined,
  permissionRejected: r.permissionRejected, toolErrors: r.toolErrors, errors: r.errors,
  filesChanged: [...touched].map((f) => `${status.get(f) ?? "="} ${f}${dirtyBefore.has(f) ? " (ya tenía cambios previos)" : ""}`),
  // Cambios nuevos que el agente no declaró: de otra tarea en paralelo, o de un comando shell (--auto).
  otherNewChanges: others.length ? others.slice(0, 10) : undefined,
};
const headAfter = git(dir, "rev-parse", "HEAD");
if (headBefore && headAfter !== headBefore) summary.newCommits = git(dir, "log", "--oneline", `${headBefore}..${headAfter}`).split("\n");

// Documentos: índice de encabezados en vez del contenido.
const docs = [...touched].filter((f) => /\.mdx?$/i.test(f) && fs.existsSync(path.join(dir, f)));
if (docs.length) summary.docs = Object.fromEntries(docs.map((f) => [f, outline(path.join(dir, f))]));

// --out es para la respuesta del agente, nunca para un archivo que el propio agente editó o que está en el repo bajo seguimiento.
const outRel = out && rel(out);
if (out && (touched.has(outRel) || (!outRel.startsWith("..") && git(dir, "ls-files", "--", outRel)))) {
  summary.warning = `--out ignorado: ${outRel} es un archivo del repo editado o versionado; no se sobrescribe.`;
} else if (out && r.finalText.trim()) {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, r.finalText.trim() + "\n");
  summary.out = { file: out, ...outline(out, 25) };
  summary.finalText = full ? r.finalText : r.finalText.slice(0, 300) + (r.finalText.length > 300 ? " …" : "");
} else {
  const lim = full ? Infinity : 1500;
  summary.finalText = r.finalText.length > lim ? r.finalText.slice(0, lim) + "\n…[truncado; usa --out o --full]" : r.finalText;
}

if (verify && typeof verify === "string") {
  const t = Date.now();
  try {
    const o = execSync(verify, { cwd: dir, shell: verifyShell(), encoding: "utf8", stdio: ["ignore", "pipe", "pipe"], maxBuffer: 64 << 20, timeout: 20 * 60_000 });
    summary.verify = { ok: true, secs: Math.round((Date.now() - t) / 1000), tail: o.trim().split("\n").slice(-5).join("\n") };
  } catch (e) {
    const o = `${e.stdout || ""}\n${e.stderr || ""}`.trim().split("\n");
    summary.verify = { ok: false, secs: Math.round((Date.now() - t) / 1000), tail: o.filter((l) => /error|fail|exception|assert/i.test(l)).slice(-20).join("\n") || o.slice(-25).join("\n") };
  }
}
summary.logFile = r.logFile;

// --verify usa sintaxis POSIX (cd x && ./mvnw ...). En Windows execSync usaría cmd.exe, así que se busca
// Git Bash (no el bash.exe de System32, que es WSL). VERIFY_SHELL permite forzar otra shell.
function verifyShell() {
  if (process.env.VERIFY_SHELL) return process.env.VERIFY_SHELL;
  if (process.platform !== "win32") return "/bin/sh";
  const candidates = [
    path.join(process.env.ProgramFiles || "C:\Program Files", "Git", "bin", "bash.exe"),
    path.join(process.env.LOCALAPPDATA || "", "Programs", "Git", "bin", "bash.exe"),
  ];
  return candidates.find((c) => fs.existsSync(c)) || "bash";
}


// Quita campos vacíos para no gastar tokens en ruido.
for (const [k, v] of Object.entries(summary)) {
  if (v === undefined || v === "" || (Array.isArray(v) && !v.length) || (v && typeof v === "object" && !Array.isArray(v) && !Object.keys(v).length)) delete summary[k];
}
console.log(JSON.stringify(summary, null, 1));
