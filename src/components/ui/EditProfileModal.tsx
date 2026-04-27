import { useEffect, useState } from 'react';
import { View, Text, Modal, KeyboardAvoidingView, Platform } from 'react-native';
import { Button } from './Button';
import { Input } from './Input';

interface Props {
  visible: boolean;
  currentDisplayName: string;
  loading?: boolean;
  onSubmit: (displayName: string) => void;
  onCancel: () => void;
}

export function EditProfileModal({
  visible,
  currentDisplayName,
  loading = false,
  onSubmit,
  onCancel,
}: Props) {
  const [value, setValue] = useState<string>(currentDisplayName);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (visible) {
      setValue(currentDisplayName);
      setError(undefined);
    }
  }, [visible, currentDisplayName]);

  const handleSubmit = () => {
    const trimmed = value.trim();
    if (trimmed.length === 0) {
      setError('Le nom ne peut pas etre vide');
      return;
    }
    setError(undefined);
    onSubmit(trimmed);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1 justify-center items-center bg-black/50 px-8"
      >
        <View className="bg-white rounded-2xl p-6 w-full max-w-sm">
          <Text className="text-lg font-semibold text-gray-800 mb-4">
            Modifier mon profil
          </Text>
          <Input
            label="Nom d'affichage"
            placeholder="Votre nom"
            value={value}
            onChangeText={(text) => {
              setValue(text);
              if (error) setError(undefined);
            }}
            error={error}
            autoFocus
            returnKeyType="done"
            onSubmitEditing={handleSubmit}
          />
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
