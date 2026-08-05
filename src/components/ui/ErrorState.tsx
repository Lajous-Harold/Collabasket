import { View, Text } from 'react-native';
import { Button } from './Button';

interface Props {
  title?: string;
  subtitle?: string;
  onRetry?: () => void;
}

/**
 * État d'erreur de chargement : distinct d'un EmptyState pour qu'une
 * query en échec ne soit pas confondue avec « aucune donnée ».
 */
export function ErrorState({
  title = 'Impossible de charger les données',
  subtitle = 'Vérifiez votre connexion puis réessayez.',
  onRetry,
}: Props) {
  return (
    <View className="flex-1 items-center justify-center px-8">
      <Text className="text-4xl mb-3">⚠️</Text>
      <Text className="text-base font-semibold text-gray-800 dark:text-gray-100 text-center">
        {title}
      </Text>
      <Text className="text-sm text-gray-500 dark:text-gray-400 text-center mt-1 mb-4">
        {subtitle}
      </Text>
      {onRetry && (
        <Button title="Réessayer" variant="outline" onPress={onRetry} />
      )}
    </View>
  );
}
