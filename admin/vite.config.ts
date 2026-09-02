import { defineConfig, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Backend manzili — dev proxy uchun (miniapp bilan bir xil yondashuv).
 * `Origin` headeri olib tashlanadi: proxy brauzer emas, shuning uchun
 * so'rov CORS ro'yxatiga tushmasligi kerak (§7.2).
 */
const BACKEND = 'http://localhost:8080';

const backendProxy: ProxyOptions = {
  target: BACKEND,
  changeOrigin: true,
  configure: (proxy) => {
    proxy.on('proxyReq', (proxyReq) => {
      proxyReq.removeHeader('origin');
    });
  },
};

export default defineConfig({
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
});
