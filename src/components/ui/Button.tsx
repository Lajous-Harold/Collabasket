import { TouchableOpacity, Text, ActivityIndicator } from 'react-native';

const variantStyles = {
  primary: 'bg-primary-600 active:bg-primary-700',
  secondary: 'bg-accent-500 active:bg-accent-600',
  danger: 'bg-danger-600 active:bg-danger-700',
  outline: 'border border-primary-600 bg-transparent active:bg-primary-50',
} as const;

const variantTextStyles = {
  primary: 'text-white',
  secondary: 'text-white',
  danger: 'text-white',
  outline: 'text-primary-600 dark:text-primary-400',
} as const;

const sizeStyles = {
  sm: 'px-4 py-2 rounded-lg',
  md: 'px-6 py-3 rounded-xl',
  lg: 'px-8 py-4 rounded-xl',
} as const;

const sizeTextStyles = {
  sm: 'text-sm',
  md: 'text-base',
  lg: 'text-lg',
} as const;

interface Props {
  title: string;
  onPress: () => void;
  variant?: keyof typeof variantStyles;
  size?: keyof typeof sizeStyles;
  loading?: boolean;
  disabled?: boolean;
  className?: string;
}

export function Button({
  title,
  onPress,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  className = '',
}: Props) {
  const isDisabled = disabled || loading;

  return (
    <TouchableOpacity
      className={`items-center justify-center ${sizeStyles[size]} ${variantStyles[variant]} ${isDisabled ? 'opacity-50' : ''} ${className}`}
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.8}
    >
      {loading ? (
        <ActivityIndicator
          size="small"
          color={variant === 'outline' ? '#0d9488' : '#ffffff'}
        />
      ) : (
        <Text
          className={`font-semibold ${sizeTextStyles[size]} ${variantTextStyles[variant]}`}
        >
          {title}
        </Text>
      )}
    </TouchableOpacity>
  );
}
