import { useEffect, useState } from 'react';
import { View, Text, Modal, KeyboardAvoidingView, Platform } from 'react-native';
import { Button } from './Button';
import { Input } from './Input';

interface Props {
  visible: boolean;
  currentNickname: string | null;
  defaultDisplayName: string;
  loading?: boolean;
  onSubmit: (nickname: string | null) => void;
  onCancel: () => void;
}

export function NicknameModal({
  visible,
  currentNickname,
  defaultDisplayName,
  loading = false,
  onSubmit,
  onCancel,
}: Props) {
  const [value, setValue] = useState<string>(currentNickname ?? '');

  useEffect(() => {
    if (visible) {
      setValue(currentNickname ?? '');
    }
  }, [visible, currentNickname]);

  const handleSubmit = () => {
    const trimmed = value.trim();
    onSubmit(trimmed.length === 0 ? null : trimmed);
  };

  const handleClear = () => {
    setValue('');
    onSubmit(null);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1 justify-center items-center bg-black/50 px-8"
      >
        <View className="bg-white rounded-2xl p-6 w-full max-w-sm">
          <Text className="text-lg font-semibold text-gray-800 mb-4">
            Modifier le surnom
          </Text>
          <Input
            label="Surnom"
            placeholder={defaultDisplayName}
            value={value}
            onChangeText={setValue}
            autoFocus
            returnKeyType="done"
            onSubmitEditing={handleSubmit}
          />
          <Text className="text-xs text-gray-500 mt-2">
            Laissez vide pour utiliser &laquo;&nbsp;{defaultDisplayName}&nbsp;&raquo;.
          </Text>

          {currentNickname !== null && currentNickname.length > 0 && (
            <View className="mt-4">
              <Button
                title="Effacer le surnom"
                variant="outline"
                size="sm"
                onPress={handleClear}
                disabled={loading}
              />
            </View>
          )}

          <View className="flex-row gap-3 mt-6">
            <View className="flex-1">
              <Button
                title="Annuler"
                variant="outline"
                onPress={onCancel}
                disabled={loading}
              />
            </View>
            <View className="flex-1">
              <Button
                title="Enregistrer"
                onPress={handleSubmit}
                loading={loading}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
