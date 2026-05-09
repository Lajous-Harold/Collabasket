import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import {
  signInWithGoogle as nativeSignInWithGoogle,
  signInWithApple as nativeSignInWithApple,
} from '../lib/auth';

/**
 * Hook d'auth.
 *
 * Selecteur du store + raccourcis pour les methodes de sign-in.
 * Aucune subscription Supabase ici : c'est authStore qui la tient en
 * singleton (cf. authStore.init).
 *
 * Methodes exposees :
 *   - signInWithOtp(email)            -> { error }
 *   - verifyOtp(email, token)         -> { data, error }
 *   - signInWithGoogle()              -> { cancelled, error }
 *   - signInWithApple()               -> { cancelled, error } (stub : error)
 *   - signOut()                       -> Promise<void>, throw on error
 *
 * Le shape { cancelled, error } pour OAuth distingue l'annulation
 * utilisateur (modale fermee) d'une vraie erreur.
 */

interface OAuthResult {
  cancelled: boolean;
  error: Error | null;
}

export function useAuth() {
  const session = useAuthStore((s) => s.session);
  const user = useAuthStore((s) => s.user);
  const isLoading = useAuthStore((s) => s.isLoading);
  const signOut = useAuthStore((s) => s.signOut);

  const signInWithOtp = async (email: string) => {
    const { error } = await supabase.auth.signInWithOtp({ email });
    return { error };
  };

  const verifyOtp = async (email: string, token: string) => {
    const { data, error } = await supabase.auth.verifyOtp({
      email,
      token,
      type: 'email',
    });
    return { data, error };
  };

  const signInWithGoogle = async (): Promise<OAuthResult> => {
    try {
      const native = await nativeSignInWithGoogle();
      if (!native) {
        return { cancelled: true, error: null };
      }
      const { error } = await supabase.auth.signInWithIdToken({
        provider: 'google',
        token: native.idToken,
      });
      return { cancelled: false, error: error as Error | null };
    } catch (err) {
      return { cancelled: false, error: err as Error };
    }
  };

  const signInWithApple = async (): Promise<OAuthResult> => {
    try {
      const native = await nativeSignInWithApple();
      if (!native) {
        return { cancelled: true, error: null };
      }
      const { error } = await supabase.auth.signInWithIdToken({
        provider: 'apple',
        token: native.idToken,
      });
      return { cancelled: false, error: error as Error | null };
    } catch (err) {
      return { cancelled: false, error: err as Error };
    }
  };

  return {
    session,
    user,
    isLoading,
    signInWithOtp,
    verifyOtp,
    signInWithGoogle,
    signInWithApple,
    signOut,
  };
}
