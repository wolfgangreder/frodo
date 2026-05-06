import { defineConfig, transformWithOxc } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [
    // CRA used .js for JSX; Vite/Rollup only parse JSX in .jsx by default.
    // This pre-plugin runs oxc with the jsx loader on every .js file under
    // src/ so Rollup sees valid JS before any other transform runs.
    {
      name: 'treat-js-files-as-jsx',
      enforce: 'pre',
      async transform(code, id) {
        if (!id.match(/src\/.*\.js$/)) return null;
        return transformWithOxc(code, id, { lang: 'jsx' });
      },
    },
    react(),
  ],
  server: {
    host: 'localhost',
    port: 3001,
    strictPort: true,
  },
  build: {
    outDir: 'dist',
  },
  // esbuild pre-bundling phase also needs to understand JSX in .js files
  optimizeDeps: {
    esbuildOptions: {
      loader: {
        '.js': 'jsx',
      },
    },
  },
});
