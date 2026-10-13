#!/usr/bin/env node
// Lista los modelos de OpenCode de un proveedor (por defecto "opencode") con sus metadatos
// y los compara con el índice guardado (MODELS.md / models.json de la skill).
//
// Uso: node models.mjs [--provider opencode] [--dir <repo>]
// Salida: JSON con { checkedAt, cliVersion, models[], added[], removed[], indexAgeDays }

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const indexFile = path.join(here, "..", "models.json");

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i > -1 ? process.argv[i + 1] : fallback;
}
function findCli() {
  const c = [process.env.OPENCODE_CLI,
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Programs", "@opencodedesktop", "resources", "opencode-cli.exe"),
    "/Applications/OpenCode.app/Contents/Resources/opencode-cli"].filter(Boolean);
  return c.find((p) => fs.existsSync(p)) ?? "opencode";
}

const provider = arg("provider", "opencode");
const dir = path.resolve(arg("dir", process.cwd()));
const cli = findCli();
const cliVersion = execFileSync(cli, ["--version"], { encoding: "utf8" }).trim();

let models;
try {
  // OpenCode 2.x: API local con metadatos completos
  const raw = execFileSync(cli, ["api", "model.list", "--param", `location[directory]=${dir}`], { encoding: "utf8", maxBuffer: 64 << 20 });
  const data = JSON.parse(raw).data;
  models = data.filter((m) => m.providerID === provider).map((m) => ({
    id: `${m.providerID}/${m.id}`,
    name: m.name,
    context: m.limit?.context, output: m.limit?.output,
    input: m.capabilities?.input, tools: m.capabilities?.tools,
    variants: (m.variants || []).map((v) => v.id),
    cost: m.cost?.[0] ? { input: m.cost[0].input, output: m.cost[0].output } : null,
    released: m.time?.released ? new Date(m.time.released).toISOString().slice(0, 10) : null,
    status: m.status,
  }));
} catch {
  // OpenCode 1.x: solo nombres
  const raw = execFileSync(cli, ["models", provider], { encoding: "utf8" });
  models = raw.split("\n").map((l) => l.trim()).filter((l) => l.startsWith(`${provider}/`)).map((id) => ({ id }));
}

let index = null;
try { index = JSON.parse(fs.readFileSync(indexFile, "utf8")); } catch { /* sin índice */ }
const known = new Set((index?.models || []).map((m) => m.id));
const current = new Set(models.map((m) => m.id));

console.log(JSON.stringify({
  checkedAt: new Date().toISOString(),
  cliVersion,
  indexAgeDays: index?.evaluatedAt ? Math.floor((Date.now() - Date.parse(index.evaluatedAt)) / 86_400_000) : null,
  added: [...current].filter((id) => !known.has(id)),
  removed: [...known].filter((id) => !current.has(id)),
  models,
}, null, 1));
