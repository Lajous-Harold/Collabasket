import { useEffect } from 'react';
import { useAuth } from './useAuth';
import {
  requestAndRegisterPushToken,
  setupNotificationListeners,
} from '../lib/notifications';

/**
 * Hook à brancher dans le layout authentifié.
 * - Enregistre le token push au mount
 * - Installe les listeners de notifications
 *
 * L'envoi de pushes est entièrement server-side (triggers DB +
 * push_outbox + pg_cron + edge function send-push-notification).
 * Aucun mécanisme client-side n'invoque la function : le hook
 * useSendPushNotification a été supprimé pour fermer la surface
 * d'attaque (cf. revue Wave 1 Point C, option C2).
 */
export function useNotifications(): void {
  const { user } = useAuth();

  useEffect(() => {
    if (!user?.id) return;
    requestAndRegisterPushToken(user.id).catch((err: unknown) => {
      console.error('[useNotifications] Erreur enregistrement token:', err);
    });
  }, [user?.id]);

  useEffect(() => {
    const cleanup = setupNotificationListeners();
    return cleanup;
  }, []);
}
