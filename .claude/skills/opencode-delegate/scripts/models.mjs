#!/usr/bin/env node
// Compara los modelos disponibles en OpenCode con el índice de la skill (models.json).
// Solo hace falta cuando una delegación falla por el modelo o el índice tiene más de 30 días.
//
// Uso: node models.mjs [--provider opencode] [--dir <repo>] [--full]
// Salida compacta: { cliVersion, benchAgeDays, added[], removed[], roles }  (--full añade metadatos)
import { execFileSync } from "node:child_process";
import path from "node:path";
import { arg, findCli, readModels } from "./lib.mjs";

const provider = arg("provider", "opencode");
const dir = path.resolve(arg("dir", process.cwd()));
const cli = findCli();

function list() {
  const raw = execFileSync(cli, ["api", "model.list", "--param", `location[directory]=${dir}`], { encoding: "utf8", maxBuffer: 64 << 20 });
  return JSON.parse(raw).data;
}
let data;
// El servicio de fondo de OpenCode puede tardar en arrancar: un reintento antes de rendirse.
try { data = list(); } catch { try { data = list(); } catch (e) { console.error(`model.list falló: ${e.message.slice(0, 200)}`); process.exit(1); } }

const models = data.filter((m) => m.providerID === provider && m.status === "active").map((m) => ({
  id: `${m.providerID}/${m.id}`, context: m.limit?.context, tools: m.capabilities?.tools,
  variants: (m.variants || []).map((v) => v.id),
  free: !m.cost?.[0] || (m.cost[0].input === 0 && m.cost[0].output === 0),
  released: m.time?.released ? new Date(m.time.released).toISOString().slice(0, 10) : null,
}));

const index = readModels();
const known = new Set(Object.keys(index.bench || {}));
const current = new Set(models.map((m) => m.id));
const used = new Set(Object.values(index.roles || {}).flatMap((r) => r.models || []).map((m) => m.split("#")[0]));

console.log(JSON.stringify({
  cliVersion: execFileSync(cli, ["--version"], { encoding: "utf8" }).trim(),
  benchAgeDays: index.benchAt ? Math.floor((Date.now() - Date.parse(index.benchAt)) / 86_400_000) : null,
  added: [...current].filter((id) => !known.has(id)),
  removed: [...known].filter((id) => !current.has(id)),
  rolesUsingRemoved: [...used].filter((id) => !current.has(id)),
  roles: Object.fromEntries(Object.entries(index.roles || {}).map(([k, r]) => [k, `${r.agent} · ${(r.models || []).join(" → ")}`])),
  ...(arg("full") ? { models } : {}),
}, null, 1));
