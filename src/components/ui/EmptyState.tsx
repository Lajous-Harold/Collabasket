import { View, Text } from 'react-native';
import type { ReactNode } from 'react';

interface Props {
  icon: string;
  title: string;
  subtitle?: string;
  children?: ReactNode;
}

export function EmptyState({ icon, title, subtitle, children }: Props) {
  return (
    <View className="flex-1 items-center justify-center px-8">
      <Text className="text-6xl mb-4">{icon}</Text>
      <Text className="text-xl font-semibold text-gray-700 dark:text-gray-200 text-center">
        {title}
      </Text>
      {subtitle && (
        <Text className="text-sm text-gray-400 text-center mt-2">
          {subtitle}
        </Text>
      )}
      {children && <View className="mt-6">{children}</View>}
    </View>
  );
}
