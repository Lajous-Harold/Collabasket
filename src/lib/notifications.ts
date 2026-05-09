import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import { supabase } from './supabase';

// Configure le comportement des notifications en avant-plan
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

/**
 * Demande la permission, récupère le token Expo Push, upsert dans devices.
 * Retourne le token ou null si impossible (simulateur, permission refusée).
 */
export async function requestAndRegisterPushToken(userId: string): Promise<string | null> {
  if (!Device.isDevice) {
    console.warn('[Notifications] Push notifications non disponibles sur simulateur.');
    return null;
  }

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'default',
      importance: Notifications.AndroidImportance.MAX,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#0d9488',
    });
  }

  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;

  if (existingStatus !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }

  if (finalStatus !== 'granted') {
    console.warn('[Notifications] Permission refusée.');
    return null;
  }

  const projectId =
    Constants?.expoConfig?.extra?.eas?.projectId ??
    Constants?.easConfig?.projectId;

  if (!projectId) {
    console.warn('[Notifications] EAS projectId non configuré — exécute `npx eas init` d\'abord.');
    return null;
  }

  const token = await Notifications.getExpoPushTokenAsync({ projectId });

  const platform: 'ios' | 'android' = Platform.OS === 'ios' ? 'ios' : 'android';

  const { error } = await supabase.from('devices').upsert(
    {
      user_id: userId,
      push_token: token.data,
      platform,
      updated_at: new Date().toISOString(),
    },
    { onConflict: 'user_id,push_token' }
  );

  if (error) {
    console.error('[Notifications] Erreur upsert device:', error.message);
  }

  return token.data;
}

/**
 * Installe les listeners de notifications.
 * Retourne une fonction de nettoyage à appeler au unmount.
 */
export function setupNotificationListeners(): () => void {
  // Notification reçue en avant-plan
  const receivedSubscription = Notifications.addNotificationReceivedListener((notification) => {
    console.log('[Notifications] Reçue en avant-plan:', notification.request.content);
  });

  // Tap sur une notification (avant-plan ou arrière-plan)
  const responseSubscription = Notifications.addNotificationResponseReceivedListener((response) => {
    const data = response.notification.request.content.data as Record<string, unknown>;
    console.log('[Notifications] Tap notification, data:', data);
    // Navigation future : utiliser expo-router router.push() avec data.url ou data.screen
  });

  return () => {
    receivedSubscription.remove();
    responseSubscription.remove();
  };
}
