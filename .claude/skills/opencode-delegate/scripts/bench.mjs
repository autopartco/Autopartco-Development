#!/usr/bin/env node
// Mide disponibilidad, velocidad y fiabilidad básica de los modelos de OpenCode con dos sondas reales
// (lectura con herramientas y redacción) y guarda el resultado en models.json → "bench".
// No edita el repo: usa el agente de solo lectura `explore`.
//
// Uso: node bench.mjs [--provider opencode] [--models a,b] [--dir <repo>] [--parallel 4]
// Salida: tabla compacta (un modelo por línea). Los roles de models.json no se tocan: revísalos a mano.
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { arg, findCli, logDir, modelsFile, readModels, runOpencode } from "./lib.mjs";

const dir = path.resolve(arg("dir", process.cwd()));
const provider = arg("provider", "opencode");
const parallel = Number(arg("parallel", "4"));
const cli = findCli();

let ids = arg("models") ? String(arg("models")).split(",").map((m) => (m.includes("/") ? m : `${provider}/${m}`)) : null;
if (!ids) {
  const raw = execFileSync(cli, ["api", "model.list", "--param", `location[directory]=${dir}`], { encoding: "utf8", maxBuffer: 64 << 20 });
  ids = JSON.parse(raw).data.filter((m) => m.providerID === provider && m.status === "active").map((m) => `${provider}/${m.id}`);
}

const probes = [
  { name: "read", message: "Lee el archivo AGENTS.md con tu herramienta de lectura y responde SOLO con un entero: cuántos agentes aparecen como viñeta en la sección 'Agentes especializados'.",
    check: (t) => /^\D*6\D*$/.test(t.trim()) },
  { name: "write", message: "Lee docs/AUTOPARTCO_AGENT_CONTEXT.md y escribe en español un resumen de 120 a 180 palabras seguido de exactamente 3 viñetas con '- '. Responde solo con el resumen, sin preámbulo.",
    // Exige haber leído el archivo: un modelo que "resume" sin leer inventa, y eso descalifica para documentación.
    check: (t, r) => { const w = t.split(/\s+/).filter(Boolean).length; const b = t.split("\n").filter((l) => /^\s*- /.test(l)).length; return !!r.tools.read && w >= 100 && w <= 230 && b === 3; } },
];

const jobs = ids.flatMap((id) => probes.map((p) => ({ id, p })));
const results = {};
const stamp = Date.now();
async function worker() {
  while (jobs.length) {
    const { id, p } = jobs.shift();
    const r = await runOpencode({ cli, dir, agent: "explore", model: id, message: p.message, title: `bench-${p.name}`, timeoutMin: 6,
      logFile: path.join(logDir, "bench", `${stamp}-${id.replace(/\W/g, "_")}-${p.name}.jsonl`) });
    const ok = r.exitCode === 0 && !r.timedOut && !r.errors.length && !!r.finalText.trim();
    (results[id] ??= {})[p.name] = { ok, correct: ok && p.check(r.finalText, r), secs: r.secs, readFile: !!r.tools.read,
      words: r.finalText.split(/\s+/).filter(Boolean).length,
      ...(ok ? {} : { error: (r.errors[0] || r.stderr.trim().split("\n").pop() || (r.timedOut ? "timeout" : "sin respuesta")).slice(0, 120) }) };
  }
}
await Promise.all(Array.from({ length: Math.min(parallel, jobs.length) }, worker));

const index = readModels();
index.bench = { ...(index.bench || {}), ...results };
index.benchAt = new Date().toISOString();
fs.writeFileSync(modelsFile, JSON.stringify(index, null, 2) + "\n");

for (const id of ids) {
  const r = results[id];
  console.log([id, ...probes.map((p) => { const x = r[p.name]; return `${p.name}:${x.correct ? "ok" : x.ok ? "mal" : "falla"} ${x.secs}s${x.error ? ` (${x.error})` : ""}`; })].join(" | "));
}
