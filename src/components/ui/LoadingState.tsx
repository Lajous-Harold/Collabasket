import { View, ActivityIndicator, Text } from 'react-native';

interface Props {
  message?: string;
}

export function LoadingState({ message = 'Chargement...' }: Props) {
  return (
    <View className="flex-1 items-center justify-center">
      <ActivityIndicator size="large" color="#0d9488" />
      <Text className="text-gray-400 mt-3">{message}</Text>
    </View>
  );
}
