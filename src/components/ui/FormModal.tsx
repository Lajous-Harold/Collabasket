import { View, Text, Modal, KeyboardAvoidingView, Platform } from 'react-native';
import { Button } from './Button';
import { Input } from './Input';

interface Props {
  visible: boolean;
  title: string;
  label: string;
  placeholder: string;
  value: string;
  onChangeText: (text: string) => void;
  onSubmit: () => void;
  onCancel: () => void;
  submitLabel?: string;
  loading?: boolean;
}

export function FormModal({
  visible,
  title,
  label,
  placeholder,
  value,
  onChangeText,
  onSubmit,
  onCancel,
  submitLabel = 'Créer',
  loading = false,
}: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade">
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1 justify-center items-center bg-black/50 px-8"
      >
        <View className="bg-white rounded-2xl p-6 w-full max-w-sm">
          <Text className="text-lg font-semibold text-gray-800 mb-4">
            {title}
          </Text>
          <Input
            label={label}
            placeholder={placeholder}
            value={value}
            onChangeText={onChangeText}
            autoFocus
            returnKeyType="done"
            onSubmitEditing={onSubmit}
          />
          <View className="flex-row gap-3 mt-6">
            <View className="flex-1">
              <Button title="Annuler" variant="outline" onPress={onCancel} />
            </View>
            <View className="flex-1">
              <Button
                title={submitLabel}
                onPress={onSubmit}
                loading={loading}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
