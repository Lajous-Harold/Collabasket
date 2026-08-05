import { View, Text } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useIsOnline } from '../../lib/offline';

/**
 * Bandeau global affiché quand l'appareil est hors ligne.
 * Les écritures restent possibles (updates optimistes + file de
 * mutations rejouée au retour du réseau) — le bandeau l'explique.
 */
export function OfflineBanner() {
  const isOnline = useIsOnline();
  const insets = useSafeAreaInsets();

  if (isOnline) return null;

  return (
    <View style={{ paddingTop: insets.top }} className="bg-amber-500">
      <Text className="text-white text-xs font-medium text-center py-1.5 px-4">
        Hors ligne — vos modifications seront synchronisées au retour du
        réseau
      </Text>
    </View>
  );
}
