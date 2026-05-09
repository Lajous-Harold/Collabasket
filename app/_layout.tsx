import '../global.css';
import { useEffect } from 'react';
import { Slot, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { QueryClientProvider } from '@tanstack/react-query';
import { View, ActivityIndicator } from 'react-native';
import { queryClient } from '../src/lib/queryClient';
import { useAuthStore } from '../src/stores/authStore';

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

    if (!session && !inAuthGroup) {
      router.replace('/(auth)/login');
    } else if (session && inAuthGroup) {
      router.replace('/(app)');
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
    <QueryClientProvider client={queryClient}>
      <RootLayoutNav />
    </QueryClientProvider>
  );
}
