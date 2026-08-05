import 'react-native-url-polyfill/auto';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createClient } from '@supabase/supabase-js';
import type { Database } from '../types/database';

const supabaseUrl = process.env.EXPO_PUBLIC_SUPABASE_URL;
const supabaseAnonKey = process.env.EXPO_PUBLIC_SUPABASE_KEY;

if (!supabaseUrl || !supabaseAnonKey) {
  // Échec explicite au démarrage plutôt qu'un crash opaque plus tard :
  // en dev, vérifier le fichier .env ; en build EAS, vérifier que les
  // variables d'environnement EXPO_PUBLIC_* existent pour le profil
  // utilisé (eas env:list --environment production).
  throw new Error(
    'Configuration Supabase manquante : EXPO_PUBLIC_SUPABASE_URL et ' +
      'EXPO_PUBLIC_SUPABASE_KEY doivent être définies (.env en dev, ' +
      'variables EAS pour les builds).',
  );
}

export const supabase = createClient<Database>(supabaseUrl, supabaseAnonKey, {
  auth: {
    storage: AsyncStorage,
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: false,
  },
});
