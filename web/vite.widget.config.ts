import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    lib: {
      entry: 'src/widget/index.tsx',
      name: 'ShoppingListWidget',
      fileName: () => 'widget.js',
      formats: ['iife'],
    },
    outDir: 'dist-widget',
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
  define: {
    'process.env.NODE_ENV': '"production"',
  },
});
