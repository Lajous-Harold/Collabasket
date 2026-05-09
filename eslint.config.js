// ESLint flat config (v9+) — minimal pour faire passer la CI.
// Aucune regle stricte activee : parsing TS/TSX uniquement.
// Tightening progressif a faire dans une wave dediee.

const tseslint = require('@typescript-eslint/eslint-plugin');
const tsparser = require('@typescript-eslint/parser');

module.exports = [
  {
    ignores: [
      'node_modules/**',
      '.expo/**',
      'dist/**',
      'web-build/**',
      'eas-build-cache/**',
      'supabase/functions/**', // runtime Deno, pas Node
      'expo-env.d.ts',
      'metro.config.js',
      'babel.config.js',
      'tailwind.config.js',
      'eslint.config.js',
    ],
  },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsparser,
      parserOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      '@typescript-eslint': tseslint,
    },
    rules: {
      // Aucune regle activee pour l'instant.
      // Le simple parsing TS via @typescript-eslint/parser permet de capter
      // les erreurs de syntaxe. Les regles seront ajoutees progressivement
      // dans une wave dediee pour eviter un mur d'erreurs sur le code existant.
    },
  },
];
