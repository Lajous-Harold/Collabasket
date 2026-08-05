import AsyncStorage from '@react-native-async-storage/async-storage';

// Persiste le token d'invitation quand un invité non connecté ouvre un
// deep-link : le flow d'auth (email OTP ou OAuth natif) peut traverser
// plusieurs écrans voire un retour d'app externe, AsyncStorage survit
// à tout ça contrairement à un state en mémoire.
const KEY = 'collabasket_pending_invite_token';

export async function setPendingInviteToken(token: string): Promise<void> {
  await AsyncStorage.setItem(KEY, token);
}

export async function consumePendingInviteToken(): Promise<string | null> {
  const token = await AsyncStorage.getItem(KEY);
  if (token) await AsyncStorage.removeItem(KEY);
  return token;
}
