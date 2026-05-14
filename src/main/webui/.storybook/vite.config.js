/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { defineConfig, transformWithOxc } from 'vite';
import { mergeConfig } from 'vite';

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
  ],
  // esbuild pre-bundling phase also needs to understand JSX in .js files
  optimizeDeps: {
    esbuildOptions: {
      loader: {
        '.js': 'jsx',
      },
    },
  },
});
