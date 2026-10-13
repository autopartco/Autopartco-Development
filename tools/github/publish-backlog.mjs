#!/usr/bin/env node
// Publica docs/backlog/backlog.json en GitHub: labels, milestones (sprints), épicas, issues,
// sub-issues y un GitHub Project con campos Prioridad / Tamaño / Área.
//
// Uso:
//   node tools/github/publish-backlog.mjs [--repo owner/name] [--apply] [--no-project]
//
// Sin --apply solo muestra lo que haría. Es idempotente: no duplica labels, milestones,
// issues (se identifican por el prefijo [AP-xxx] / [EP-xx] del título) ni items del proyecto.
// Requisitos: gh CLI autenticado (GH_TOKEN en GitHub Actions, o gh auth login en local).
// El proyecto requiere scope project y permisos en la organización; desde Actions usa --no-project.

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const WRITE_PAUSE_MS = Number(process.env.BACKLOG_WRITE_PAUSE_MS ?? 1200);
const pause = () => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, WRITE_PAUSE_MS);
const backlog = JSON.parse(fs.readFileSync(path.join(root, "docs", "backlog", "backlog.json"), "utf8"));
const apply = process.argv.includes("--apply");
const withProject = !process.argv.includes("--no-project");
const repoArg = process.argv.indexOf("--repo");

// GH_BIN permite sustituir gh (p. ej. por un simulador .mjs en pruebas locales).
const GH_BIN = process.env.GH_BIN || "gh";
function sh(args, { input, json } = {}) {
  const [cmd, cmdArgs] = /\.m?js$/.test(GH_BIN) ? [process.execPath, [GH_BIN, ...args]] : [GH_BIN, args];
  const out = execFileSync(cmd, cmdArgs, { encoding: "utf8", input, maxBuffer: 64 << 20 }).trim();
  return json ? JSON.parse(out || "null") : out;
}
// Lecturas: en simulación se toleran fallos (p. ej. gh sin instalar); al aplicar, no.
function read(fn) {
  try { return fn(); } catch (error) {
    if (apply) throw error;
    console.log(`[dry-run] no se pudo leer el estado actual de GitHub (${error.code ?? error.message.split("\n")[0]}); se asume vacío`);
    return null;
  }
}
function act(desc, fn) {
  console.log(`${apply ? "→" : "[dry-run]"} ${desc}`);
  if (!apply) return null;
  const result = fn();
  pause();
  return result;
}

const repo = repoArg > -1 ? process.argv[repoArg + 1]
  : process.env.GITHUB_REPOSITORY || execFileSync("git", ["-C", root, "remote", "get-url", "origin"], { encoding: "utf8" })
      .trim().replace(/^.*github\.com[/:]/, "").replace(/\.git$/, "");
const [owner] = repo.split("/");
console.log(`Repositorio: ${repo} · modo: ${apply ? "APLICAR" : "simulación"}`);

// 1. Labels
for (const l of backlog.labels) {
  act(`label ${l.name}`, () => sh(["label", "create", l.name, "--repo", repo, "--color", l.color, "--description", l.description || "", "--force"]));
}

// 2. Milestones
const existingMilestones = read(() => sh(["api", `repos/${repo}/milestones?state=all&per_page=100`], { json: true })) ?? [];
const milestoneTitle = {};
for (const m of backlog.milestones) {
  milestoneTitle[m.key] = m.title;
  if (existingMilestones.some((x) => x.title === m.title)) continue;
  const args = ["api", `repos/${repo}/milestones`, "-f", `title=${m.title}`, "-f", `description=${m.goal}`];
  if (m.due) args.push("-f", `due_on=${m.due}T23:59:59Z`);
  act(`milestone ${m.title}${m.due ? ` (${m.due})` : ""}`, () => sh(args));
}

// 3. Issues existentes
const existing = read(() => sh(["issue", "list", "--repo", repo, "--state", "all", "--limit", "1000", "--json", "number,title,url"], { json: true })) ?? [];
const byKey = {};
for (const i of existing) {
  const k = i.title.match(/^\[((?:AP|EP)-\d+)\]/)?.[1];
  if (k) byKey[k] = i;
}

function createIssue(key, title, body, labels, milestoneKey) {
  if (byKey[key]) { console.log(`= ${key} ya existe (#${byKey[key].number})`); return byKey[key]; }
  const file = path.join(os.tmpdir(), `backlog-${key}.md`);
  fs.writeFileSync(file, body);
  const args = ["issue", "create", "--repo", repo, "--title", `[${key}] ${title}`, "--body-file", file];
  for (const l of labels) args.push("--label", l);
  if (milestoneKey) args.push("--milestone", milestoneTitle[milestoneKey]);
  const url = act(`issue [${key}] ${title} · ${labels.join(", ")} · ${milestoneTitle[milestoneKey] ?? "-"}`, () => sh(args));
  if (!url) return null;
  const issue = { url, number: Number(url.split("/").pop()), title };
  byKey[key] = issue;
  return issue;
}

const ref = (k) => (byKey[k] ? `#${byKey[k].number}` : `\`${k}\``);

// 4. Épicas
for (const e of backlog.epics) {
  const body = [`## Objetivo`, e.goal, ``, `## Historias y tareas`, `_Se completa al crear las issues._`].join("\n");
  createIssue(e.id, e.title, body, ["tipo:épica", `prioridad:${e.priority}`], e.milestone);
}

// 5. Issues
for (const i of backlog.issues) {
  const labels = [`tipo:${i.type}`, `prioridad:${i.priority}`, ...i.areas.map((a) => `area:${a}`)];
  if (i.approval) labels.push("requiere-aprobación");
  if (i.delegable) labels.push("delegable");
  const body = [
    `**Épica:** ${ref(i.epic)} · **Tamaño:** ${i.size} · **Agente sugerido:** \`${i.agent}\`${i.delegable ? " · candidata a OpenCode" : ""}`,
    ``,
    i.context ? `## Contexto\n${i.context}\n` : null,
    `## Criterios de aceptación`,
    ...i.ac.map((c) => `- [ ] ${c}`),
    ``,
    i.depends?.length ? `## Depende de\n${i.depends.map((d) => `- ${ref(d)}`).join("\n")}\n` : null,
    i.approval ? `> ⚠️ Requiere aprobación del usuario (dependencias, datos, despliegue, seguridad o servicios externos).\n` : null,
    `## Definición de terminado`,
    `- [ ] Pruebas ejecutadas y salida real adjunta en el PR`,
    `- [ ] Revisión de \`qa-seguridad\` sobre el diff`,
    `- [ ] \`graphify update .\` tras cambios de código`,
  ].filter((s) => s !== null).join("\n");
  createIssue(i.id, i.title, body, labels, i.milestone);
}

// 6. Épicas: lista de hijas y sub-issues
for (const e of backlog.epics) {
  const epic = byKey[e.id];
  const children = backlog.issues.filter((i) => i.epic === e.id);
  if (!epic || !apply) { console.log(`[dry-run] épica ${e.id}: ${children.length} hijas`); continue; }
  const body = [`## Objetivo`, e.goal, ``, `## Historias y tareas`, ...children.map((c) => `- [ ] ${ref(c.id)} ${c.title}`)].join("\n");
  const file = path.join(os.tmpdir(), `backlog-${e.id}-body.md`);
  fs.writeFileSync(file, body);
  sh(["issue", "edit", String(epic.number), "--repo", repo, "--body-file", file]);
  pause();
  const nodeId = (n) => sh(["api", `repos/${repo}/issues/${n}`, "--jq", ".node_id"]);
  const epicNode = nodeId(epic.number);
  for (const c of children) {
    if (!byKey[c.id]) continue;
    try {
      sh(["api", "graphql", "-H", "GraphQL-Features: sub_issues", "-f",
        `query=mutation { addSubIssue(input: {issueId: "${epicNode}", subIssueId: "${nodeId(byKey[c.id].number)}"}) { issue { number } } }`]);
    } catch { /* ya vinculada o sub-issues no disponibles */ }
    pause();
  }
  console.log(`→ épica ${e.id} vinculada con ${children.length} hijas`);
}

// 7. Proyecto
if (withProject) {
  const p = backlog.project;
  const projects = apply ? sh(["project", "list", "--owner", owner, "--format", "json"], { json: true }).projects : [];
  let project = projects.find((x) => x.title === p.title);
  if (!project) {
    project = act(`proyecto "${p.title}" en ${owner}`, () => sh(["project", "create", "--owner", owner, "--title", p.title, "--format", "json"], { json: true }));
  }
  if (project && apply) {
    try { sh(["project", "link", String(project.number), "--owner", owner, "--repo", repo]); } catch { /* ya enlazado */ }
    let fields = sh(["project", "field-list", String(project.number), "--owner", owner, "--format", "json"], { json: true }).fields;
    for (const [name, options] of Object.entries(p.fields)) {
      if (!fields.some((f) => f.name === name)) {
        sh(["project", "field-create", String(project.number), "--owner", owner, "--name", name, "--data-type", "SINGLE_SELECT", "--single-select-options", options.join(",")]);
      }
    }
    fields = sh(["project", "field-list", String(project.number), "--owner", owner, "--format", "json"], { json: true }).fields;
    const items = sh(["project", "item-list", String(project.number), "--owner", owner, "--limit", "1000", "--format", "json"], { json: true }).items;
    const setField = (itemId, name, value) => {
      const f = fields.find((x) => x.name === name);
      const opt = f?.options?.find((o) => o.name === value);
      if (f && opt) sh(["project", "item-edit", "--id", itemId, "--project-id", project.id, "--field-id", f.id, "--single-select-option-id", opt.id]);
    };
    for (const entry of [...backlog.epics.map((e) => ({ ...e, size: null, areas: [] })), ...backlog.issues]) {
      const issue = byKey[entry.id];
      if (!issue) continue;
      let item = items.find((x) => x.content?.url === issue.url);
      if (!item) item = sh(["project", "item-add", String(project.number), "--owner", owner, "--url", issue.url, "--format", "json"], { json: true });
      setField(item.id, "Prioridad", entry.priority);
      if (entry.size) setField(item.id, "Tamaño", entry.size);
      if (entry.areas?.[0]) setField(item.id, "Área", entry.areas[0]);
    }
    console.log(`→ proyecto ${project.url ?? project.number} actualizado`);
  }
}
console.log("Listo.");
