import { View } from 'react-native';

interface Props {
  visible: boolean;
}

export function Badge({ visible }: Props) {
  if (!visible) return null;

  return (
    <View className="w-2.5 h-2.5 rounded-full bg-accent-500" />
  );
}
