#!/usr/bin/env node
// Delega una tarea a OpenCode en modo no interactivo y devuelve un resumen compacto.
//
// Uso:
//   node delegate.mjs --agent <agente> --model <proveedor/modelo[#variante]> --prompt-file <archivo>
//                     [--title <título>] [--session <id>] [--dir <repo>] [--timeout-min 30]
//
// Imprime un JSON con: sesión, duración, pasos, herramientas usadas, permisos rechazados,
// tokens, archivos cambiados (git diff --stat) y el último mensaje del agente.
// El flujo JSON completo se guarda en un archivo aparte para no consumir contexto.

import { spawn, execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i > -1 ? process.argv[i + 1] : fallback;
}

export function findCli() {
  const candidates = [
    process.env.OPENCODE_CLI,
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Programs", "@opencodedesktop", "resources", "opencode-cli.exe"),
    "/Applications/OpenCode.app/Contents/Resources/opencode-cli",
  ].filter(Boolean);
  for (const c of candidates) if (fs.existsSync(c)) return c;
  return "opencode"; // CLI global; debe ser compatible con la versión del escritorio si comparten datos
}

const agent = arg("agent");
const model = arg("model");
const promptFile = arg("prompt-file");
const dir = path.resolve(arg("dir", process.cwd()));
const session = arg("session");
const title = arg("title", `delegado-claude-${new Date().toISOString().slice(0, 16)}`);
const timeoutMin = Number(arg("timeout-min", "30"));

if (!agent || !model || !promptFile) {
  console.error("Faltan --agent, --model o --prompt-file");
  process.exit(1);
}

const prompt = fs.readFileSync(promptFile, "utf8");
const cli = findCli();
const logDir = path.join(os.tmpdir(), "opencode-delegate");
fs.mkdirSync(logDir, { recursive: true });
const logFile = path.join(logDir, `${Date.now()}.jsonl`);

function git(...a) {
  try { return execFileSync("git", ["-C", dir, ...a], { encoding: "utf8" }).trim(); } catch { return ""; }
}
const headBefore = git("rev-parse", "HEAD");
const dirtyBefore = new Set(git("status", "--porcelain").split("\n").filter(Boolean).map((l) => l.slice(3)));

const args = ["run", "--agent", agent, "--model", model, "--format", "json", "--title", title];
if (session) args.push("--session", session);
args.push(prompt);

const started = Date.now();
const child = spawn(cli, args, { cwd: dir });
const log = fs.createWriteStream(logFile);
let stderr = "";
child.stdout.pipe(log);
child.stderr.on("data", (c) => (stderr += c));
const killer = setTimeout(() => child.kill(), timeoutMin * 60_000);

child.on("close", (code) => {
  clearTimeout(killer);
  log.end(() => {
    const summary = {
      exitCode: code, timedOut: Date.now() - started >= timeoutMin * 60_000,
      secs: Math.round((Date.now() - started) / 1000), sessionID: null, agent, model,
      steps: 0, tools: {}, toolErrors: [], permissionRejected: [],
      tokens: { input: 0, output: 0, reasoning: 0, cacheRead: 0 }, cost: 0,
      filesChanged: [], newCommits: [], finalText: "", errors: [], logFile,
    };
    for (const line of fs.readFileSync(logFile, "utf8").split("\n")) {
      let e; try { e = JSON.parse(line); } catch { continue; }
      summary.sessionID ??= e.sessionID ?? null;
      const p = e.part || {};
      if (e.type === "step_finish") {
        summary.steps++;
        summary.cost += p.cost || 0;
        summary.tokens.input += p.tokens?.input || 0;
        summary.tokens.output += p.tokens?.output || 0;
        summary.tokens.reasoning += p.tokens?.reasoning || 0;
        summary.tokens.cacheRead += p.tokens?.cache?.read || 0;
      } else if (e.type === "tool_use") {
        summary.tools[p.tool] = (summary.tools[p.tool] || 0) + 1;
        if (p.state?.status === "error") {
          const err = String(p.state.error || "");
          const what = `${p.tool}: ${JSON.stringify(p.state.input ?? {}).slice(0, 160)}`;
          (/permission|cannot ask the user/i.test(err) ? summary.permissionRejected : summary.toolErrors).push(
            /permission|cannot ask the user/i.test(err) ? what : `${what} -> ${err.slice(0, 200)}`);
        }
      } else if (e.type === "text") {
        summary.finalText = p.text;
      } else if (e.type === "error") {
        summary.errors.push(JSON.stringify(e.error ?? e).slice(0, 300));
      }
    }
    if (stderr.trim()) summary.errors.push(...stderr.trim().split("\n").filter((l) => !/auto-rejecting/.test(l)).slice(-5));
    const headAfter = git("rev-parse", "HEAD");
    if (headBefore && headAfter && headBefore !== headAfter) {
      summary.newCommits = git("log", "--oneline", `${headBefore}..${headAfter}`).split("\n").filter(Boolean);
    }
    summary.filesChanged = git("status", "--porcelain").split("\n").filter(Boolean)
      .map((l) => ({ status: l.slice(0, 2).trim(), file: l.slice(3), preexisting: dirtyBefore.has(l.slice(3)) }));
    summary.diffStat = git("diff", "--stat");
    if (summary.finalText.length > 4000) summary.finalText = summary.finalText.slice(0, 4000) + "\n…[truncado; ver logFile]";
    console.log(JSON.stringify(summary, null, 1));
  });
});
