import { create } from 'zustand';
import type { Session, User } from '@supabase/supabase-js';
import { supabase } from '../lib/supabase';
import { signOutGoogle } from '../lib/auth';

/**
 * Store d'auth.
 *
 * Tient une subscription unique a supabase.auth.onAuthStateChange pour
 * la duree de vie de l'app, plutot qu'une subscription par composant
 * qui appelle useAuth() (cf. revue Wave 1 Point D).
 *
 * Le bootstrap (getSession + subscribe) est lance par init(), idempotent
 * et concurrent-safe : plusieurs callers peuvent l'await en parallele,
 * un seul travail est effectue.
 *
 * signOut() centralise le cleanup multi-provider (Google natif + Supabase).
 * Apple sera branche ici quand la wave Apple sera active.
 */

interface AuthState {
  session: Session | null;
  user: User | null;
  /** true tant que la session initiale n'a pas ete chargee depuis AsyncStorage */
  isLoading: boolean;
  init: () => Promise<void>;
  signOut: () => Promise<void>;
}

// Variables module-level : referencent la subscription et la promesse
// d'init. Survivent aux re-renders mais sont reset au hot reload module
// (le cleanup de l'ancienne subscription dans init() couvre ce cas).
let authSubscription: { unsubscribe: () => void } | null = null;
let initPromise: Promise<void> | null = null;

export const useAuthStore = create<AuthState>((set) => ({
  session: null,
  user: null,
  isLoading: true,

  init: async () => {
    if (initPromise) return initPromise;

    initPromise = (async () => {
      // 1. Charger la session existante (refresh token AsyncStorage)
      const {
        data: { session },
      } = await supabase.auth.getSession();
      set({
        session,
        user: session?.user ?? null,
        isLoading: false,
      });

      // 2. Cleanup d'une eventuelle subscription residuelle (hot reload)
      if (authSubscription) {
        authSubscription.unsubscribe();
        authSubscription = null;
      }

      // 3. Subscription unique pour la vie de l'app
      const {
        data: { subscription },
      } = supabase.auth.onAuthStateChange((_event, newSession) => {
        set({
          session: newSession,
          user: newSession?.user ?? null,
        });
      });
      authSubscription = subscription;
    })();

    return initPromise;
  },

  signOut: async () => {
    // 1. Best-effort cleanup des SDK natifs (idempotent si non utilise).
    //    Apple sera ajoute ici quand la wave dediee sera active.
    try {
      await signOutGoogle();
    } catch (err) {
      console.warn('[authStore] signOutGoogle non bloquant :', err);
    }

    // 2. Supabase signOut (primary). L'event SIGNED_OUT qui suit met a
    //    jour session/user via la subscription onAuthStateChange.
    const { error } = await supabase.auth.signOut();
    if (error) {
      console.error('[authStore] supabase signOut error:', error.message);
      throw error;
    }
  },
}));
