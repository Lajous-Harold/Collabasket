import { QueryClient } from '@tanstack/react-query';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';

/**
 * Durée de rétention du cache persisté. Doit couvrir plusieurs jours
 * sans réseau : une liste de courses consultée en magasin doit
 * s'afficher même si l'app n'a pas été ouverte depuis une semaine.
 * gcTime doit être >= maxAge du persister, sinon les entrées sont
 * garbage-collectées avant d'être restaurées.
 */
export const CACHE_MAX_AGE = 1000 * 60 * 60 * 24 * 7; // 7 jours

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      gcTime: CACHE_MAX_AGE,
      retry: 2,
    },
    mutations: {
      // 'always' = échec rapide et explicite hors ligne pour les
      // mutations non critiques (création de groupe, invitation…).
      // Les mutations d'articles surchargent en 'online' (mise en
      // file + rejeu au retour du réseau) dans itemMutations.ts.
      networkMode: 'always',
      gcTime: CACHE_MAX_AGE,
    },
  },
});

/**
 * Persister AsyncStorage : sauvegarde le cache de queries ET les
 * mutations en pause (file offline), restaurés au démarrage par
 * PersistQueryClientProvider (voir app/_layout.tsx).
 */
export const asyncStoragePersister = createAsyncStoragePersister({
  storage: AsyncStorage,
  key: 'collabasket-query-cache',
  throttleTime: 1000,
});
