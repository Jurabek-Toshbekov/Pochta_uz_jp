import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from './App';
import { AuthProvider } from './auth/AuthContext';
import './styles/global.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      // Admin raqamlari tez-tez o'zgarmaydi; ortiqcha so'rov yubormaymiz.
      staleTime: 60_000,
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
      <AuthProvider>
        {/* Admin nginx ostida turadi — BrowserRouter uchun rewrite sozlangan. */}
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
