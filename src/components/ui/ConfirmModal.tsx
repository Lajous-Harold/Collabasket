import { View, Text, Modal, KeyboardAvoidingView, Platform } from 'react-native';
import { Button } from './Button';

interface Props {
  visible: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmModal({
  visible,
  title,
  message,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  destructive = false,
  loading = false,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1 justify-center items-center bg-black/40 px-8"
      >
        <View className="bg-white dark:bg-gray-900 rounded-2xl p-6 w-full max-w-sm">
          <Text className="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-2">
            {title}
          </Text>
          <Text className="text-sm text-gray-600 dark:text-gray-300 mb-6 leading-5">
            {message}
          </Text>
          <View className="flex-row gap-3">
            <View className="flex-1">
              <Button
                title={cancelLabel}
                variant="outline"
                onPress={onCancel}
                disabled={loading}
              />
            </View>
            <View className="flex-1">
              <Button
                title={confirmLabel}
                variant={destructive ? 'danger' : 'primary'}
                onPress={onConfirm}
                loading={loading}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
