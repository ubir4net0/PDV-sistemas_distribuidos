import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const read = (path) => readFileSync(resolve(root, path), 'utf8');
const json = (path) => JSON.parse(read(path));
const spec = read('docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md');
const { tasks } = json('docs/planning/backlog.proposed.json');
const registry = json('docs/integrations/notion-registry.json');
const { order } = json('docs/planning/implementation-order.json');
const { coverage } = json('docs/planning/requirements-coverage.json');
const manifest = json('docs/sources/manifest.json');

const expectedIds = Array.from({ length: 60 }, (_,i) => 'PDV-' + String(i + 1).padStart(3, '0'));
const ids = tasks.map((t) => t.id);
assert.deepEqual(ids, expectedIds, 'The proposed backlog must preserve exactly PDV-001 through PDV-060.');
assert.deepEqual(registry.tasks.map((t) => t.id), expectedIds, 'Registry identities must match the backlog.');
assert.equal(new Set(registry.tasks.map((t) => t.pageId)).size, 60);
assert.equal(new Set(registry.tasks.map((t) => t.url)).size, 60);
assert.deepEqual([...order].sort(), expectedIds, 'Order must include all tasks exactly once.');

let count = 0;
let phase;
for (const line of spec.split(/\r?\n/)) {
  if (line.startsWith('### ')) phase = line.slice(4).split(' · ');
  if (line.startsWith('#### PDV-')) {
    const match = line.match(/^#### (PDV-\d{3}) — (.+)$/);
    const task = tasks[count++];
    assert.equal(task.id, match[1]);
    assert.equal(task.title, match[2]);
    assert.equal(task.phase, phase[0]);
    assert.equal(task.module, phase[1]);
  }
  if (line.startsWith('**Prioridade:**')) {
    const match = line.match(/\*\*Prioridade:\*\* (P\d) · \*\*Dependências:\*\* (.+?) · \*\*Fontes:\*\* (.+)/);
    const task = tasks[count - 1];
    assert.equal(task.priority, match[1]);
    assert.deepEqual(task.dependencies, match[2] === '—' ? [] : match[2].split(',').map((n) => 'PDV-' + n.trim()));
    assert.equal(task.sources, match[3]);
  }
  if (line.startsWith('**Critério de aceite:**')) {
    assert.equal(tasks[count - 1].acceptance, line.replace('**Critério de aceite:** ', ''));
  }
}
assert.equal(count, 60);
const priorities = tasks.reduce((counts, t) => ({ ...counts, [t.priority]: (counts[t.priority] || 0) + 1 }), {});
assert.deepEqual(priorities, { P0: 37, P1: 19, P2: 4 });
for (const task of tasks) {
  assert(task.title && task.acceptance && task.sources, 'Task source/acceptance must not be empty.');
  assert(!Object.hasOwn(task, 'status'), 'Local proposal must not pretend to be the live task status.');
  assert(!Object.hasOwn(task, 'assignee'), 'Do not invent owners.');
  for (const dependency of task.dependencies) {
    assert(ids.includes(dependency), 'Unknown dependency: ' + dependency);
    assert(order.indexOf(dependency) < order.indexOf(task.id), 'Dependency cycle or invalid execution order.');
  }
}

const requirementsSection = spec.slice(spec.indexOf('## 4.'), spec.indexOf('## 6.'));
const requirements = [...requirementsSection.matchAll(/\*\*(RF-[VERCA]\d{2}|RNF\d{2})/g)].map((m) => m[1]);
assert.equal(requirements.filter((r) => r.startsWith('RF-')).length, 19);
assert.equal(requirements.filter((r) => r.startsWith('RNF')).length, 20);
assert.deepEqual(coverage.map((r) => r.requirement), requirements);
for (const item of coverage) {
  assert(item.tasks.length > 0, 'Unmapped requirement: ' + item.requirement);
  for (const id of item.tasks) assert(ids.includes(id), 'Unknown coverage task: ' + id);
}
for (const entry of manifest.files.filter((f) => f.file !== 'PDV_Codex.zip')) {
  const actual = createHash('sha256').update(readFileSync(resolve(root, 'docs/sources', entry.file))).digest('hex');
  assert.equal(actual, entry.sha256, 'Original source changed: ' + entry.file);
}
assert.equal(new Set(manifest.files.map((f) => f.file)).size, 4);
console.log('PASS: 60 tasks/IDs/registry links; original titles, acceptances and dependencies; acyclic order; 19 RF + 20 RNF mapped; 3 source SHA256 checks. This validates planning, not product implementation.');
