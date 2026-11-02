import { test, expect, type Page } from '@playwright/test';
const email = process.env.PDV_E2E_EMAIL!;
const password = process.env.PDV_E2E_PASSWORD!;
async function fill(page: Page) { await page.getByLabel('E-mail', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password); }
async function enter(page: Page) { await page.goto('/login'); await fill(page); await page.getByRole('button', { name: 'Entrar na minha conta' }).click(); await expect(page.getByRole('heading', { name: 'Seu perfil', exact: true })).toBeVisible(); }
async function noOverflow(page: Page) { expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); }
test('anonymous guard, safe errors, keyboard and responsive login', async ({ page }, info) => {
  await page.goto('/app?returnTo=https://example.invalid'); await expect(page).toHaveURL(/\/login$/);
  await page.setViewportSize({ width: 1440, height: 900 }); await noOverflow(page);
  await page.screenshot({ path: info.outputPath('login-desktop.png') });
  await page.getByRole('button', { name: 'Entrar na minha conta' }).click(); await expect(page.getByRole('status')).toContainText('Informe');
  await fill(page); await page.getByLabel('Senha', { exact: true }).fill('synthetic-wrong-password');
  await page.getByRole('button', { name: 'Entrar na minha conta' }).click(); await expect(page.getByRole('status')).toContainText('inválidos');
  await page.setViewportSize({ width: 390, height: 844 }); await noOverflow(page);
  await page.getByLabel('Senha', { exact: true }).fill(''); await page.getByLabel('E-mail', { exact: true }).fill('');
  await page.screenshot({ path: info.outputPath('login-mobile.png') });
  await page.getByLabel('E-mail', { exact: true }).focus(); await page.keyboard.press('Tab'); await expect(page.getByLabel('Senha', { exact: true })).toBeFocused();
  await page.keyboard.press('Tab'); await expect(page.getByRole('button', { name: 'Mostrar senha' })).toBeFocused();
});
test('real login, own profile, no persistence and server revocation after logout', async ({ page, request }) => {
  let bearer = ''; page.on('request', r => { if (r.url().endsWith('/auth/me')) bearer = r.headers()['authorization'] ?? ''; });
  await enter(page); await page.setViewportSize({ width: 390, height: 844 }); await noOverflow(page);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]); expect(await page.context().cookies()).toEqual([]);
  const active = await request.get(`${process.env.PDV_E2E_API}/auth/me`, { headers: { Authorization: bearer } }); expect(active.status()).toBe(200);
  await page.getByRole('button', { name: 'Sair da conta' }).click(); await expect(page).toHaveURL(/\/login$/);
  const revoked = await request.get(`${process.env.PDV_E2E_API}/auth/me`, { headers: { Authorization: bearer } }); expect(revoked.status()).toBe(401);
  await page.goto('/app'); await expect(page).toHaveURL(/\/login$/);
});
test('reload discards memory session', async ({ page }) => {
  await enter(page); await page.reload(); await expect(page).toHaveURL(/\/login$/);
});
test('loading and failed network allow login retry', async ({ page }) => {
  await page.goto('/login'); let release!: () => void;
  await page.route('**/auth/login', async route => { await new Promise<void>(resolve => { release = resolve; }); await route.abort('failed'); });
  await fill(page); await page.getByRole('button', { name: 'Entrar na minha conta' }).click(); await expect(page.getByRole('button', { name: 'Entrando…' })).toBeDisabled();
  release(); await expect(page.getByRole('status')).toContainText('conectar'); await page.unroute('**/auth/login');
  await fill(page); await page.getByRole('button', { name: 'Entrar na minha conta' }).click(); await expect(page.getByRole('heading', { name: 'Seu perfil', exact: true })).toBeVisible();
});
test('failed logout retains access and retry revokes on server', async ({ page }) => {
  await enter(page); await page.route('**/auth/logout', route => route.abort('failed')); await page.getByRole('button', { name: 'Sair da conta' }).click();
  await expect(page.getByRole('alert')).toContainText('confirmar'); await expect(page.getByRole('heading', { name: 'Seu perfil', exact: true })).toBeVisible();
  await page.unroute('**/auth/logout'); await page.getByRole('button', { name: 'Sair da conta' }).click(); await expect(page).toHaveURL(/\/login$/);
});
test('actual server TTL and local session expiration', async ({ page, request }) => {
  let bearer = ''; page.on('request', r => { if (r.url().endsWith('/auth/me')) bearer = r.headers()['authorization'] ?? ''; });
  await enter(page); await expect(page).toHaveURL(/\/login$/, { timeout: 40_000 }); await expect(page.getByRole('status')).toContainText('expirou');
  const expired = await request.get(`${process.env.PDV_E2E_API}/auth/me`, { headers: { Authorization: bearer } }); expect(expired.status()).toBe(401);
});
