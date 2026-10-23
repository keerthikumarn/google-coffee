import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.js'

// Test settings live here so vite.config.js stays about the dev server and the production build.
// mergeConfig reuses the React and Tailwind plugins from vite.config.js.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'happy-dom',
      setupFiles: ['./src/test/setup.js'],
      restoreMocks: true,
    },
  }),
)
