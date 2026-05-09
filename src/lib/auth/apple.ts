import { Platform } from 'react-native';

/**
 * Stub Apple Sign In.
 *
 * Active dans une wave dediee avant submission App Store.
 * Pre-requis a ce moment-la :
 *   - Apple Developer Account ($99/an)
 *   - Apple Sign In capability activee dans Apple Developer Portal
 *   - Provider Apple configure dans Supabase Auth
 *   - lib expo-apple-authentication installee
 *   - plugin expo-apple-authentication dans app.json
 *   - ios.usesAppleSignIn = true dans app.json
 *
 * Tant que cette wave n'est pas faite :
 *   - APPLE_SIGN_IN_AVAILABLE = false
 *   - signInWithApple() throw une erreur explicite
 *   - le bouton login peut s'afficher disabled (UX preview) ou
 *     etre cache, au choix du composant.
 *
 * Apple App Store Guideline 4.8 : si Google est propose sur iOS,
 * Apple Sign In DOIT l'etre aussi. La review App Store rejettera
 * une app qui ne respecte pas cette regle. Aucun impact en dev /
 * TestFlight interne.
 */

export const APPLE_SIGN_IN_AVAILABLE = false;

export interface AppleIdTokenPayload {
  idToken: string;
  email: string | null;
  name: string | null;
}

export async function signInWithApple(): Promise<AppleIdTokenPayload | null> {
  if (Platform.OS !== 'ios') {
    throw new Error('Apple Sign In disponible uniquement sur iOS.');
  }
  throw new Error(
    'Apple Sign In stub : a activer dans la wave preparation App Store.',
  );
}

export async function signOutApple(): Promise<void> {
  // No-op tant que stub.
}
