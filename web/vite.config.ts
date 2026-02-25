import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: process.env.VITE_PROXY_TARGET
      ? {
          '/api': {
            target: process.env.VITE_PROXY_TARGET,
            changeOrigin: true,
            ws: true,
          },
        }
      : undefined,
  },
});
