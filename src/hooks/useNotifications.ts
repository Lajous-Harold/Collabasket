import { useEffect } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useAuth } from './useAuth';
import {
  requestAndRegisterPushToken,
  setupNotificationListeners,
} from '../lib/notifications';
import { supabase } from '../lib/supabase';

/**
 * Hook à brancher dans le layout authentifié.
 * - Enregistre le token push au mount
 * - Installe les listeners de notifications
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

interface SendPushPayload {
  userIds: string[];
  title: string;
  body: string;
  data?: Record<string, unknown>;
}

interface SendPushResult {
  sent: number;
  errors: unknown[];
}

/**
 * Mutation pour envoyer une notification push via la Edge Function.
 * Usage : const { mutate } = useSendPushNotification();
 */
export function useSendPushNotification() {
  return useMutation<SendPushResult, Error, SendPushPayload>({
    mutationFn: async ({ userIds, title, body, data }: SendPushPayload) => {
      const { data: result, error } = await supabase.functions.invoke<SendPushResult>(
        'send-push-notification',
        { body: { userIds, title, body, data } }
      );
      if (error) throw new Error(error.message);
      return result ?? { sent: 0, errors: [] };
    },
  });
}
