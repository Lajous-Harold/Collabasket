import { Platform } from 'react-native';
import {
  GoogleSignin,
  statusCodes,
  isErrorWithCode,
} from '@react-native-google-signin/google-signin';

/**
 * Wrapper Google Sign-In isole.
 * Utilise la lib native @react-native-google-signin/google-signin v13+
 * (compatible newArch) qui retourne un idToken pour Supabase via
 * supabase.auth.signInWithIdToken.
 *
 * Configuration :
 *   - webClientId (EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID) : OBLIGATOIRE.
 *     C'est ce client ID qui sert d'audience aux idToken generes par
 *     les SDK natifs (Android et iOS).
 *   - iosClientId (EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID) : iOS uniquement.
 *
 * Aucune configuration Android specifique n'est passee a configure() :
 *   le SDK Android utilise le SHA-1 + package name enregistres dans
 *   le client OAuth Android Google Cloud Console.
 */

let configured = false;

export function configureGoogleSignIn(): boolean {
  if (configured) return true;

  const webClientId = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  const iosClientId = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;

  if (!webClientId) {
    console.warn(
      '[auth/google] EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID manquant — Sign In Google indisponible.',
    );
    return false;
  }

  GoogleSignin.configure({
    webClientId,
    ...(Platform.OS === 'ios' && iosClientId ? { iosClientId } : {}),
    scopes: ['profile', 'email'],
    offlineAccess: false, // pas besoin de refresh token cote app
  });

  configured = true;
  return true;
}

export interface GoogleIdTokenPayload {
  idToken: string;
  email: string | null;
  name: string | null;
}

/**
 * Lance le flow Google Sign-In natif.
 * Retourne null si l'utilisateur annule, throw sur toute autre erreur.
 */
export async function signInWithGoogle(): Promise<GoogleIdTokenPayload | null> {
  if (!configureGoogleSignIn()) {
    throw new Error('Sign In Google non configure (webClientId manquant).');
  }

  if (Platform.OS === 'android') {
    await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
  }

  try {
    const result = await GoogleSignin.signIn();

    // v13+ : { type: 'success' | 'cancelled', data: ... | null }
    if (result.type === 'cancelled' || !result.data?.idToken) {
      return null;
    }

    return {
      idToken: result.data.idToken,
      email: result.data.user.email ?? null,
      name: result.data.user.name ?? null,
    };
  } catch (err: unknown) {
    if (isErrorWithCode(err)) {
      // Annulation utilisateur = cas non-erreur
      if (err.code === statusCodes.SIGN_IN_CANCELLED) return null;
    }
    throw err;
  }
}

/**
 * Best-effort : deconnecte le SDK Google de la session courante.
 * Idempotent et safe meme si signIn n'a jamais ete appele dans ce process
 * (ex : session restauree depuis AsyncStorage). Ne propage pas l'erreur.
 */
export async function signOutGoogle(): Promise<void> {
  // configureGoogleSignIn() garantit que le SDK connait le webClientId
  // avant qu'on tente le signOut. Sans ca, un signOut sur une session
  // Google sticky restauree au boot ne nettoie rien.
  if (!configureGoogleSignIn()) return;

  try {
    await GoogleSignin.signOut();
  } catch (err) {
    console.warn('[auth/google] signOut non bloquant :', err);
  }
}
