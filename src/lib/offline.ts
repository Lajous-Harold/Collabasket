import { useSyncExternalStore } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { onlineManager } from '@tanstack/react-query';

/**
 * Branche le onlineManager de React Query sur NetInfo.
 * Sans cela, React Query considère l'app toujours en ligne : les
 * mutations échoueraient au lieu de se mettre en pause, et le
 * refetchOnReconnect ne se déclencherait jamais.
 *
 * À appeler une seule fois au démarrage (module racine).
 */
export function setupOnlineManager(): void {
  onlineManager.setEventListener((setOnline) => {
    const unsubscribe = NetInfo.addEventListener((state) => {
      // isConnected === null au premier event sur certains devices :
      // on considère "en ligne" tant que la déconnexion n'est pas avérée.
      setOnline(state.isConnected !== false);
    });
    return unsubscribe;
  });
}

/** État de connectivité réactif, aligné sur ce que voit React Query. */
export function useIsOnline(): boolean {
  return useSyncExternalStore(
    (onStoreChange) => onlineManager.subscribe(onStoreChange),
    () => onlineManager.isOnline(),
  );
}
