import { useState } from 'react';
import {
  View,
  Text,
  Modal,
  TouchableOpacity,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Input } from './Input';
import { Button } from './Button';
import type { CategoryRow } from '../../hooks/useCategories';

interface Props {
  label?: string;
  selectedId: string | null;
  categories: CategoryRow[];
  onSelect: (id: string | null) => void;
  onCreateCategory: (name: string) => Promise<CategoryRow>;
  disabled?: boolean;
}

export function CategoryPicker({
  label = 'Catégorie',
  selectedId,
  categories,
  onSelect,
  onCreateCategory,
  disabled = false,
}: Props) {
  const [open, setOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [creating, setCreating] = useState(false);
  const [nameError, setNameError] = useState<string | undefined>();

  const selectedCategory = categories.find((c) => c.id === selectedId);

  const handleSelect = (id: string | null) => {
    onSelect(id);
    setOpen(false);
  };

  const handleCreate = async () => {
    const name = newName.trim();
    if (!name) {
      setNameError('Le nom est obligatoire');
      return;
    }
    const duplicate = categories.some(
      (c) => c.name.toLowerCase() === name.toLowerCase()
    );
    if (duplicate) {
      setNameError('Cette catégorie existe déjà');
      return;
    }

    setCreating(true);
    try {
      const created = await onCreateCategory(name);
      setNewName('');
      setNameError(undefined);
      onSelect(created.id);
      setOpen(false);
    } finally {
      setCreating(false);
    }
  };

  return (
    <>
      <View>
        {label ? (
          <Text className="text-sm font-medium text-gray-700 dark:text-gray-200 mb-1">{label}</Text>
        ) : null}
        <TouchableOpacity
          onPress={() => setOpen(true)}
          disabled={disabled}
          className={`flex-row items-center justify-between px-4 py-3 bg-gray-50 dark:bg-gray-950 border border-gray-200 dark:border-gray-700 rounded-xl ${
            disabled ? 'opacity-50' : ''
          }`}
          activeOpacity={0.7}
        >
          <View className="flex-row items-center gap-2">
            {selectedCategory?.color ? (
              <View
                style={{ backgroundColor: selectedCategory.color }}
                className="w-3 h-3 rounded-full"
              />
            ) : null}
            <Text
              className={
                selectedId ? 'text-base text-gray-800 dark:text-gray-100' : 'text-base text-gray-400'
              }
            >
              {selectedCategory?.name ?? 'Sans catégorie'}
            </Text>
          </View>
          <Text className="text-gray-400 text-sm">▾</Text>
        </TouchableOpacity>
      </View>

      <Modal
        visible={open}
        transparent
        animationType="fade"
        onRequestClose={() => setOpen(false)}
      >
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          className="flex-1 justify-end bg-black/40"
        >
          <View className="bg-white dark:bg-gray-900 rounded-t-2xl max-h-[70%]">
            <View className="px-6 pt-5 pb-3 border-b border-gray-100 dark:border-gray-800 flex-row items-center justify-between">
              <Text className="text-lg font-semibold text-gray-800 dark:text-gray-100">
                Catégorie
              </Text>
              <TouchableOpacity onPress={() => setOpen(false)} className="p-1">
                <Text className="text-gray-400 text-base">✕</Text>
              </TouchableOpacity>
            </View>

            <ScrollView
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={{ paddingBottom: 16 }}
            >
              {/* Sans catégorie */}
              <TouchableOpacity
                onPress={() => handleSelect(null)}
                className="flex-row items-center justify-between px-6 py-3.5 border-b border-gray-50"
                activeOpacity={0.6}
              >
                <Text className="text-base text-gray-500 dark:text-gray-400 italic">
                  Sans catégorie
                </Text>
                {selectedId === null && (
                  <Text className="text-primary-600 dark:text-primary-400 font-bold">✓</Text>
                )}
              </TouchableOpacity>

              {/* Catégories existantes */}
              {categories.map((cat) => (
                <TouchableOpacity
                  key={cat.id}
                  onPress={() => handleSelect(cat.id)}
                  className="flex-row items-center justify-between px-6 py-3.5 border-b border-gray-50"
                  activeOpacity={0.6}
                >
                  <View className="flex-row items-center gap-3">
                    {cat.color ? (
                      <View
                        style={{ backgroundColor: cat.color }}
                        className="w-3 h-3 rounded-full"
                      />
                    ) : (
                      <View className="w-3 h-3 rounded-full bg-gray-200 dark:bg-gray-700" />
                    )}
                    <Text className="text-base text-gray-800 dark:text-gray-100">{cat.name}</Text>
                  </View>
                  {selectedId === cat.id && (
                    <Text className="text-primary-600 dark:text-primary-400 font-bold">✓</Text>
                  )}
                </TouchableOpacity>
              ))}

              {/* Créer une nouvelle catégorie */}
              <View className="px-6 pt-4">
                <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide mb-3">
                  Nouvelle catégorie
                </Text>
                <Input
                  placeholder="Fruits, Épicerie, Boucherie..."
                  value={newName}
                  onChangeText={(t) => {
                    setNewName(t);
                    if (nameError) setNameError(undefined);
                  }}
                  error={nameError}
                  returnKeyType="done"
                  onSubmitEditing={handleCreate}
                  editable={!creating}
                />
                <View className="mt-3">
                  <Button
                    title="Créer et sélectionner"
                    onPress={handleCreate}
                    loading={creating}
                    disabled={!newName.trim()}
                    size="md"
                  />
                </View>
              </View>
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </>
  );
}
