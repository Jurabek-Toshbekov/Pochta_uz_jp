import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { initTelegram } from './hooks/useTelegram';
import './styles/global.css';

initTelegram();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});

const container = document.getElementById('root');
if (!container) {
  throw new Error('#root topilmadi');
}

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* HashRouter: statik hosting uchun server tomonida rewrite kerak emas. */}
      <HashRouter>
        <App />
      </HashRouter>
    </QueryClientProvider>
  </StrictMode>,
);
