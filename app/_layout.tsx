import '../global.css';
import { useEffect } from 'react';
import { Slot, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { View, ActivityIndicator } from 'react-native';
import {
  queryClient,
  asyncStoragePersister,
  CACHE_MAX_AGE,
} from '../src/lib/queryClient';
import { setupOnlineManager } from '../src/lib/offline';
import { setupItemMutationDefaults } from '../src/lib/itemMutations';
import { useAuthStore } from '../src/stores/authStore';
import { consumePendingInviteToken } from '../src/stores/pendingInvite';

// Connectivité (NetInfo -> onlineManager) et defaults des mutations
// offline : à installer avant le premier rendu et avant la
// restauration du cache persisté.
setupOnlineManager();
setupItemMutationDefaults(queryClient);

// Bootstrap auth (getSession + subscription onAuthStateChange) au
// chargement du module. Idempotent : initPromise dans authStore
// deduplique les appels concurrents et est cleanup-safe en hot reload.
//
// On ne propage pas l'erreur (le UX correct est de presenter l'ecran
// auth si la session ne peut pas etre chargee, ce qui se produit
// automatiquement avec session=null).
useAuthStore.getState().init().catch((err) => {
  // TODO: Sentry.captureException(err) quand le hook Sentry sera en place
  console.error('[root] auth init failed:', err);
});

function RootLayoutNav() {
  // Selecteurs store directs : on ne consomme que session+isLoading
  // ici, pas besoin de la couche useAuth qui ajouterait juste de
  // l'indirection au root layout.
  const session = useAuthStore((s) => s.session);
  const isLoading = useAuthStore((s) => s.isLoading);
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;

    const inAuthGroup = segments[0] === '(auth)';
    // La route d'invitation doit rester accessible sans session : c'est
    // le point d'entree des nouveaux utilisateurs via deep-link.
    const inInviteRoute = segments[0] === 'invite';

    if (!session && !inAuthGroup && !inInviteRoute) {
      router.replace('/(auth)/login');
    } else if (session && inAuthGroup) {
      // Si un invite non connecte avait ouvert un lien d'invitation,
      // on rejoue l'invitation apres l'authentification.
      consumePendingInviteToken().then((token) => {
        if (token) {
          router.replace(`/invite/${token}`);
        } else {
          router.replace('/(app)');
        }
      });
    }
  }, [session, isLoading, segments, router]);

  if (isLoading) {
    return (
      <View className="flex-1 items-center justify-center bg-white">
        <ActivityIndicator size="large" color="#0d9488" />
      </View>
    );
  }

  return (
    <>
      <StatusBar style="auto" />
      <Slot />
    </>
  );
}

export default function RootLayout() {
  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={{
        persister: asyncStoragePersister,
        maxAge: CACHE_MAX_AGE,
        // À incrémenter lors d'un changement incompatible de shape de
        // cache (invalide le cache persisté des anciennes versions).
        buster: 'v1',
      }}
      onSuccess={() => {
        // Cache restauré : rejoue les mutations restées en pause
        // (écritures faites hors ligne avant la fermeture de l'app),
        // puis rafraîchit les données.
        queryClient.resumePausedMutations().then(() => {
          queryClient.invalidateQueries();
        });
      }}
    >
      <RootLayoutNav />
    </PersistQueryClientProvider>
  );
}
