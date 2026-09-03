import { defineConfig, loadEnv, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Backend manzili — dev proxy uchun (miniapp bilan bir xil yondashuv).
 * `Origin` headeri olib tashlanadi: proxy brauzer emas, shuning uchun
 * so'rov CORS ro'yxatiga tushmasligi kerak (§7.2).
 *
 * `ADMIN_DEV_BACKEND` bilan boshqa manzilga qaratsa bo'ladi (masalan prod:
 * `https://api99.e-tex.uz`). Proxy `Origin`ni olib tashlagani uchun serverda
 * `ADMIN_URL` sozlanmagan bo'lsa ham ishlaydi — panelni deploy qilmasdan
 * jonli ma'lumotni ko'rishning yo'li shu.
 */
const DEFAULT_BACKEND = 'http://localhost:8080';

export default defineConfig(({ mode }) => {
  // envDir sifatida '.' — `process` ishlatilsa @types/node kerak bo'ladi
  // va `tsc -b` "Cannot find name 'process'" bilan yiqiladi.
  const backend = loadEnv(mode, '.', 'ADMIN_').ADMIN_DEV_BACKEND || DEFAULT_BACKEND;

  const backendProxy: ProxyOptions = {
    target: backend,
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => {
        proxyReq.removeHeader('origin');
      });
    },
  };

  return {
    plugins: [react()],

    server: {
      // Mini App 5173 da turadi — admin alohida portda.
      port: 5174,
      host: true,
      proxy: {
        '/api': backendProxy,
        '/health': backendProxy,
      },
    },

    build: {
      target: 'es2020',
      sourcemap: false,
      rollupOptions: {
        output: {
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
            query: ['@tanstack/react-query', '@tanstack/react-table'],
            charts: ['recharts'],
          },
        },
      },
    },

    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  };
});
