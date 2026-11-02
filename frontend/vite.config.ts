import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const target = process.env.PDV_API_TARGET ?? 'http://127.0.0.1:8081';
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(target)) throw new Error('Use a API local em loopback.');
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '127.0.0.1', port: Number(process.env.PDV_FRONTEND_PORT ?? 5173), strictPort: true,
    proxy: Object.fromEntries(['/auth', '/customers', '/products', '/carts', '/sales'].map(path => [path, { target, changeOrigin: false }])),
  },
  build: { sourcemap: false },
});
