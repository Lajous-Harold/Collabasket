import { View, TouchableOpacity } from 'react-native';
import type { ReactNode } from 'react';

interface Props {
  children: ReactNode;
  onPress?: () => void;
  className?: string;
}

export function Card({ children, onPress, className = '' }: Props) {
  const baseStyles = `bg-white dark:bg-gray-900 rounded-2xl p-4 shadow-sm border border-gray-100 dark:border-gray-800 ${className}`;

  if (onPress) {
    return (
      <TouchableOpacity
        className={baseStyles}
        onPress={onPress}
        activeOpacity={0.7}
      >
        {children}
      </TouchableOpacity>
    );
  }

  return <View className={baseStyles}>{children}</View>;
}
