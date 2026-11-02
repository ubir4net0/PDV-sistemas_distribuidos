import { spawnSync, spawn } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { createServer } from 'node:net';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const front = resolve(root, 'frontend');
const prefix = `pdv-e2e-${randomUUID().replaceAll('-', '')}`;
const network = `${prefix}-net`, postgres = `${prefix}-pg`, api = `${prefix}-api`;
const created = new Set();
const privateEnv = { ...process.env, POSTGRES_USER: 'pdv_e2e', POSTGRES_DB: 'pdv_e2e', POSTGRES_PASSWORD: randomBytes(32).toString('hex') };
const toolchain = JSON.parse(readFileSync(resolve(root, 'infra/java-toolchain.json'), 'utf8'));
const jar = resolve(root, 'backend/api/target/pdv-api-0.0.1-SNAPSHOT.jar');
const email = `${randomUUID()}@example.test`, password = randomBytes(24).toString('hex');
const adminEmail = `${randomUUID()}@example.test`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
function docker(args, options = {}) {
  const r = spawnSync('docker', args, { encoding: 'utf8', env: privateEnv, maxBuffer: 4_000_000, ...options });
  // Never echo Docker/API/SQL environment or captured errors containing credentials.
  if (r.status !== 0) throw new Error(`Docker operation failed (${args[0]}). Private output suppressed.`);
  return r.stdout.trim();
}
function owned(name) { if (!name.startsWith(`${prefix}-`) || !/^pdv-e2e-[a-f0-9]{32}-(net|pg|api)$/.test(name)) throw new Error('Resource ownership mismatch'); }
async function freePort() {
  const s = createServer(); await new Promise((resolve, reject) => { s.once('error', reject); s.listen(0, '127.0.0.1', resolve); });
  const port = s.address().port; await new Promise(r => s.close(r)); return port;
}
async function main() {
  if (!process.env.npm_execpath || !existsSync(process.env.npm_execpath)) throw new Error('Run using npm --prefix frontend run e2e.');
  {
    console.log('Building API JAR using pinned Java21/Maven toolchain (no test skip claim).');
    docker(['run', '--rm', '--init', '--mount', `type=bind,source=${resolve(root, 'backend/api')},target=/workspace`,
      '--mount', `type=bind,source=${resolve(root, 'config')},target=/config,readonly`,
      '--mount', 'type=volume,source=pdv-api-maven-cache,target=/root/.m2', '--workdir', '/workspace', toolchain.image,
      'sh', './mvnw', '-B', '-ntp', '-DskipTests', 'package']);
  }
  docker(['network', 'create', network]); created.add(network);
  docker(['run', '--detach', '--rm', '--name', postgres, '--network', network, '--network-alias', 'database',
    '--tmpfs', '/var/lib/postgresql/data', '--env', 'POSTGRES_USER', '--env', 'POSTGRES_PASSWORD', '--env', 'POSTGRES_DB',
    'postgres:16.15-alpine@sha256:0cefaabd882e42bcb2a2f3dc56d3ce4770560ba62fd7f197f1a2e008fd5731ab']); created.add(postgres);
  let ready = false;
  for (let i = 0; i < 60; i++) {
    const r = spawnSync('docker', ['exec', postgres, 'pg_isready', '-U', 'pdv_e2e', '-d', 'pdv_e2e'], { stdio: 'ignore' });
    if (r.status === 0) { ready = true; break; } await sleep(1000);
  }
  if (!ready) throw new Error('Isolated PostgreSQL not ready');
  Object.assign(privateEnv, { PDV_DB_URL: 'jdbc:postgresql://database:5432/pdv_e2e', PDV_DB_USER: 'pdv_e2e', PDV_DB_PASSWORD: privateEnv.POSTGRES_PASSWORD,
    PDV_AUTH_TOKEN_TTL_SECONDS: '30', PDV_RESERVATION_TTL_SECONDS: '900', SERVER_ADDRESS: '0.0.0.0' });
  const envArgs = ['PDV_DB_URL', 'PDV_DB_USER', 'PDV_DB_PASSWORD', 'PDV_AUTH_TOKEN_TTL_SECONDS', 'PDV_RESERVATION_TTL_SECONDS', 'SERVER_ADDRESS'].flatMap(key => ['--env', key]);
  docker(['run', '--detach', '--rm', '--name', api, '--network', network, '--publish', '127.0.0.1::8081',
    '--mount', `type=bind,source=${jar},target=/app.jar,readonly`, ...envArgs, toolchain.image, 'java', '-jar', '/app.jar']); created.add(api);
  const address = docker(['port', api, '8081/tcp']); if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Unexpected API binding');
  const apiUrl = `http://${address}`; ready = false;
  for (let i = 0; i < 150; i++) {
    try { if ((await fetch(`${apiUrl}/actuator/health/readiness`, { signal: AbortSignal.timeout(1000) })).ok) { ready = true; break; } } catch { /* not ready */ }
    await sleep(1000);
  }
  if (!ready) throw new Error('Isolated API not ready; private logs suppressed');
  // Private synthetic identity exists only in owned tmpfs database. No real credentials or persistent seeds.
  const sql = `CREATE EXTENSION pgcrypto;
    INSERT INTO pdv.roles(code,name) VALUES ('OPERATOR','Operador'),('ADMIN','Administrador');
    INSERT INTO pdv.users(name,email,password_hash,active) VALUES ('Pessoa de Teste','${email}',crypt('${password}',gen_salt('bf',4)),true);
    INSERT INTO pdv.users(name,email,password_hash,active) VALUES ('Admin de Teste','${adminEmail}',crypt('${password}',gen_salt('bf',4)),true);
    INSERT INTO pdv.user_roles(user_id,role_id) SELECT u.id,r.id FROM pdv.users u CROSS JOIN pdv.roles r WHERE (u.email='${email}' AND r.code='OPERATOR') OR (u.email='${adminEmail}' AND r.code='ADMIN');
    INSERT INTO pdv.permissions(resource,action) VALUES ('customer','read'),('customer','create'),('customer','update'),('customer','status');
    INSERT INTO pdv.role_permissions(role_id,permission_id) SELECT r.id,p.id FROM pdv.roles r CROSS JOIN pdv.permissions p;`;
  docker(['exec', '-i', postgres, 'psql', '-X', '-U', 'pdv_e2e', '-d', 'pdv_e2e', '-v', 'ON_ERROR_STOP=1'], { input: sql });
  console.log('Isolated PostgreSQL/API ready, Flyway V1–V18 rebuilt; synthetic identity seeded privately.');
  const port = await freePort();
  const env = { ...process.env, PDV_API_TARGET: apiUrl, PDV_E2E_API: apiUrl, PDV_FRONTEND_PORT: String(port), PDV_E2E_EMAIL: email, PDV_E2E_ADMIN_EMAIL: adminEmail, PDV_E2E_PASSWORD: password };
  const code = await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [process.env.npm_execpath, 'exec', '--', 'playwright', 'test'], { cwd: front, env, stdio: 'inherit' });
    child.once('error', reject); child.once('exit', resolve);
  });
  if (code !== 0) throw new Error('Browser E2E failed.');
}
try { await main(); }
catch (error) { console.error(error instanceof Error ? error.message : 'E2E failed'); process.exitCode = 1; }
finally {
  let cleanupFailed = false;
  for (const name of [api, postgres]) if (created.has(name)) {
    owned(name); try { docker(['stop', '--time', '10', name]); } catch { cleanupFailed = true; }
  }
  if (created.has(network)) { owned(network); try { docker(['network', 'rm', network]); } catch { cleanupFailed = true; } }
  if (cleanupFailed) { console.error(`Cleanup incomplete for owned prefix ${prefix}; inspect these resources only.`); process.exitCode = 1; }
  else console.log('Only resources created by this E2E run were removed.');
}
