import { colorScheme, useColorScheme } from 'nativewind';
import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Préférence de thème :
 *   'system' (défaut) suit le réglage de l'appareil,
 *   'light' / 'dark' forcent le thème.
 * Persistée pour survivre aux redémarrages (la V1 Android avait perdu
 * cette persistance à cause de deux fichiers de prefs différents —
 * ici une seule clé, un seul module).
 */
export type ThemePreference = 'system' | 'light' | 'dark';

const KEY = 'collabasket_theme_preference';

export async function loadThemePreference(): Promise<ThemePreference> {
  try {
    const value = await AsyncStorage.getItem(KEY);
    return value === 'light' || value === 'dark' ? value : 'system';
  } catch {
    return 'system';
  }
}

export async function setThemePreference(pref: ThemePreference): Promise<void> {
  colorScheme.set(pref);
  await AsyncStorage.setItem(KEY, pref);
}

/** À appeler une fois au démarrage (module racine). */
export function initTheme(): void {
  loadThemePreference().then((pref) => {
    colorScheme.set(pref);
  });
}

/**
 * Couleurs de la chrome de navigation (headers, tab bar) : les
 * navigateurs React Navigation prennent des objets style avec des hex,
 * pas des classes NativeWind — d'où ce pont réactif au thème.
 */
export function useNavColors() {
  const { colorScheme: scheme } = useColorScheme();
  const dark = scheme === 'dark';
  return {
    dark,
    background: dark ? '#111827' : '#ffffff', // gray-900 / white
    text: dark ? '#f3f4f6' : '#111827', // gray-100 / gray-900
    border: dark ? '#1f2937' : '#f3f4f6', // gray-800 / gray-100
    inactive: dark ? '#6b7280' : '#9ca3af', // gray-500 / gray-400
    tint: '#0d9488', // primary-600, identique dans les deux thèmes
  };
}
