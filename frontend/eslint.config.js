import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import prettier from 'eslint-config-prettier'

export default [
  { ignores: ['dist', 'node_modules'] },
  js.configs.recommended,
  react.configs.flat.recommended,
  react.configs.flat['jsx-runtime'],
  {
    files: ['**/*.{js,jsx}'],
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
    },
    settings: { react: { version: 'detect' } },
    rules: {
      // The two classic hook rules. The newer React Compiler rules flag patterns this app uses on purpose
      // (loading data in an effect on mount, Date.now() as an initial state).
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      'react-refresh/only-export-components': 'warn',
      // Components here are not typed with PropTypes.
      'react/prop-types': 'off',
    },
  },
  {
    // Tooling and tests run in Node, and the test setup defines a jest shim on globalThis.
    files: ['*.config.js', 'src/test/**', 'src/**/*.test.{js,jsx}'],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
  },
  // Must stay last: switches off lint rules that would fight Prettier.
  prettier,
]
