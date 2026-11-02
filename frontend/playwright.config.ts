import { defineConfig } from '@playwright/test';
const port = Number(process.env.PDV_FRONTEND_PORT);
if (!port || !process.env.PDV_E2E_API) throw new Error('Use npm run e2e: ambiente isolado obrigatório.');
export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, retries: 0, timeout: 60_000,
  reporter: 'list',
  use: { baseURL: `http://127.0.0.1:${port}`, browserName: 'chromium', trace: 'off', video: 'off', screenshot: 'off' },
  webServer: { command: 'npm run dev', url: `http://127.0.0.1:${port}`, reuseExistingServer: false, timeout: 60_000 },
});
