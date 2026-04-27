import { View, Text, TextInput, type TextInputProps } from 'react-native';

interface Props extends TextInputProps {
  label?: string;
  error?: string;
}

export function Input({ label, error, className = '', ...rest }: Props) {
  const borderColor = error ? 'border-danger-400' : 'border-gray-300 focus:border-primary-500';

  return (
    <View>
      {label && (
        <Text className="text-sm font-medium text-gray-700 mb-1">{label}</Text>
      )}
      <TextInput
        className={`border ${borderColor} rounded-xl px-4 py-3 text-base text-gray-900 bg-gray-50 ${className}`}
        placeholderTextColor="#9ca3af"
        {...rest}
      />
      {error && (
        <Text className="text-xs text-danger-600 mt-1">{error}</Text>
      )}
    </View>
  );
}
