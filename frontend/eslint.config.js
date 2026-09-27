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
  react.configs.flat['jsx-runtime'], // React 17+ JSX transform: no `import React` needed
  reactHooks.configs.flat.recommended,
  reactRefresh.configs.vite,

  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
    },
    settings: { react: { version: 'detect' } },
    rules: {
      'react/prop-types': 'off', // plain JS project without PropTypes
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
  },

  // config files run in Node
  { files: ['*.config.js'], languageOptions: { globals: globals.node } },

  // turns off stylistic rules that Prettier owns; keep last
  prettier,
]
