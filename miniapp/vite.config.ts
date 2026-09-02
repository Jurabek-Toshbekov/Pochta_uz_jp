import { defineConfig, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Backend manzili — dev proxy uchun.
 * `.env.local`dagi `VITE_API_BASE_URL` bo'sh qoldiriladi, ya'ni klient
 * nisbiy yo'l bilan so'rov yuboradi va Vite uni shu manzilga uzatadi.
 */
const BACKEND = 'http://localhost:8080';

/**
 * Backendga uzatish sozlamasi.
 *
 * `Origin` headeri ataylab olib tashlanadi: proxy brauzer emas. Agar
 * brauzerning Origin'i (tunnel domeni) o'zgarishsiz uzatilsa, backend uni
 * cross-origin so'rov deb hisoblaydi va CORS ro'yxatida yo'qligi uchun
 * 403 qaytaradi (§7.2 — CORS faqat MINIAPP_URL uchun ochiq).
 *
 * Origin'siz so'rov CORS tekshiruviga umuman tushmaydi — bu to'g'ri,
 * chunki so'rovni brauzer emas, dev server yuboradi.
 */
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
    port: 5173,
    host: true,

    /**
     * Vite 5.4 DNS rebinding himoyasi sifatida xostni tekshiradi va
     * tashqi domenni bloklaydi. Telegram Mini App'ni sinash uchun tunnel
     * shart (HTTPS talab qilinadi), shuning uchun keng tarqalgan tunnel
     * provayderlariga ruxsat berilgan.
     *
     * Boshidagi nuqta — domen va uning barcha subdomenlari (ngrok bepul
     * URL'i har ishga tushirishda o'zgaradi, shuning uchun aniq xost
     * yozib qo'yish ishlamaydi).
     *
     * Bu faqat dev serverga tegishli — prod'da statik fayllar nginx
     * orqali beriladi.
     */
    allowedHosts: [
      '.ngrok-free.dev',
      '.ngrok-free.app',
      '.ngrok.app',
      '.ngrok.io',
      '.trycloudflare.com',
      '.loca.lt',
      '.serveo.net',
    ],

    /**
     * Backend ayni domen ostida ko'rinadi.
     *
     * Nima uchun: telefondagi Telegram Mini App'ni ochganda `localhost:8080`
     * telefonning o'zini bildiradi va backend topilmaydi. Ikkinchi tunnel
     * ochish esa ngrok bepul rejasida qiyin. Proxy bilan bitta tunnel
     * yetadi va CORS muammosi butunlay yo'qoladi — so'rov same-origin.
     */
    proxy: {
      '/api': backendProxy,
      '/health': backendProxy,
      '/webhook': backendProxy,
    },
  },

  build: {
    // §9.5 — birinchi yuklanish < 200KB gzip. Chunk ajratish shunga yordam beradi.
    target: 'es2020',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          query: ['@tanstack/react-query'],
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
