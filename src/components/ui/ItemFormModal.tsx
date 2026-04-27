import { useEffect, useMemo, useState } from 'react';
import {
  View,
  Text,
  Modal,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { Button } from './Button';
import { Input } from './Input';

export type StorageLocation = 'pantry' | 'fridge' | 'freezer' | null;

export interface ItemFormValues {
  name: string;
  quantity: number;
  unit: string;
  category: string;
  storage_location: StorageLocation;
  notes: string;
  price: number | null;
}

export interface ItemSuggestion {
  name: string;
  category?: string;
  unit?: string;
  default_quantity?: number;
}

interface Props {
  visible: boolean;
  title: string;
  initialValues?: Partial<ItemFormValues>;
  loading?: boolean;
  onSubmit: (values: ItemFormValues) => void;
  onCancel: () => void;
  suggestions?: ItemSuggestion[];
}

const DEFAULT_VALUES: ItemFormValues = {
  name: '',
  quantity: 1,
  unit: '',
  category: '',
  storage_location: null,
  notes: '',
  price: null,
};

const STORAGE_OPTIONS: Array<{ value: Exclude<StorageLocation, null>; label: string }> = [
  { value: 'pantry', label: 'Placard' },
  { value: 'fridge', label: 'Frigo' },
  { value: 'freezer', label: 'Congelateur' },
];

interface StorageButtonProps {
  label: string;
  selected: boolean;
  onPress: () => void;
  disabled?: boolean;
}

function StorageButton({ label, selected, onPress, disabled }: StorageButtonProps) {
  const baseStyles = 'items-center justify-center px-4 py-2 rounded-lg border';
  const selectedStyles = 'bg-primary-600 border-primary-600 active:bg-primary-700';
  const unselectedStyles = 'bg-transparent border-primary-600 active:bg-primary-50';
  const textStyles = selected ? 'text-white' : 'text-primary-600';

  return (
    <TouchableOpacity
      className={`${baseStyles} ${selected ? selectedStyles : unselectedStyles} ${disabled ? 'opacity-50' : ''}`}
      onPress={onPress}
      disabled={disabled}
      activeOpacity={0.8}
    >
      <Text className={`text-sm font-semibold ${textStyles}`}>{label}</Text>
    </TouchableOpacity>
  );
}

function parseNumber(text: string): number {
  const normalized = text.replace(',', '.');
  const parsed = Number.parseFloat(normalized);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseOptionalNumber(text: string): number | null {
  if (text.trim().length === 0) return null;
  const normalized = text.replace(',', '.');
  const parsed = Number.parseFloat(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function numberToText(value: number): string {
  if (!Number.isFinite(value)) return '';
  return String(value);
}

function optionalNumberToText(value: number | null): string {
  if (value === null || !Number.isFinite(value)) return '';
  return String(value);
}

export function ItemFormModal({
  visible,
  title,
  initialValues,
  loading = false,
  onSubmit,
  onCancel,
  suggestions = [],
}: Props) {
  const [name, setName] = useState<string>('');
  const [quantityText, setQuantityText] = useState<string>('1');
  const [unit, setUnit] = useState<string>('');
  const [category, setCategory] = useState<string>('');
  const [storageLocation, setStorageLocation] = useState<StorageLocation>(null);
  const [notes, setNotes] = useState<string>('');
  const [priceText, setPriceText] = useState<string>('');
  const [nameError, setNameError] = useState<string | undefined>(undefined);
  const [showSuggestions, setShowSuggestions] = useState<boolean>(false);

  useEffect(() => {
    if (visible) {
      const merged: ItemFormValues = { ...DEFAULT_VALUES, ...initialValues };
      setName(merged.name);
      setQuantityText(numberToText(merged.quantity));
      setUnit(merged.unit);
      setCategory(merged.category);
      setStorageLocation(merged.storage_location);
      setNotes(merged.notes);
      setPriceText(optionalNumberToText(merged.price));
      setNameError(undefined);
      setShowSuggestions(false);
    }
  }, [visible, initialValues]);

  const filteredSuggestions = useMemo(() => {
    const query = name.trim().toLowerCase();
    if (query.length === 0) return [];
    return suggestions
      .filter((s) => s.name.toLowerCase().includes(query))
      .slice(0, 5);
  }, [name, suggestions]);

  const handleSelectSuggestion = (suggestion: ItemSuggestion) => {
    setName(suggestion.name);
    if (suggestion.category !== undefined) setCategory(suggestion.category);
    if (suggestion.unit !== undefined) setUnit(suggestion.unit);
    if (suggestion.default_quantity !== undefined) {
      setQuantityText(numberToText(suggestion.default_quantity));
    }
    setShowSuggestions(false);
  };

  const handleNameChange = (text: string) => {
    setName(text);
    if (nameError) setNameError(undefined);
    setShowSuggestions(true);
  };

  const handleSubmit = () => {
    const trimmedName = name.trim();
    if (trimmedName.length === 0) {
      setNameError('Le nom est obligatoire');
      return;
    }

    const quantity = parseNumber(quantityText);
    const safeQuantity = quantity > 0 ? quantity : 1;
    const price = parseOptionalNumber(priceText);

    const values: ItemFormValues = {
      name: trimmedName,
      quantity: safeQuantity,
      unit: unit.trim(),
      category: category.trim(),
      storage_location: storageLocation,
      notes: notes.trim(),
      price,
    };

    onSubmit(values);
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1 justify-center items-center bg-black/50"
      >
        <View className="bg-white w-full h-full md:h-auto md:max-h-[90%] md:max-w-[500px] md:rounded-2xl md:my-8 overflow-hidden">
          <View className="px-6 pt-6 pb-4 border-b border-gray-100">
            <Text className="text-lg font-semibold text-gray-800">{title}</Text>
          </View>

          <ScrollView
            className="flex-1"
            contentContainerClassName="px-6 py-4"
            keyboardShouldPersistTaps="handled"
          >
            {/* Section Nom + autocomplete */}
            <View>
              <Input
                label="Nom *"
                placeholder="Pommes, Lait, Pain..."
                value={name}
                onChangeText={handleNameChange}
                error={nameError}
                autoFocus
                returnKeyType="next"
              />
              {showSuggestions && filteredSuggestions.length > 0 && (
                <View className="mt-2 border border-gray-200 rounded-xl bg-gray-50 overflow-hidden">
                  {filteredSuggestions.map((suggestion, index) => (
                    <TouchableOpacity
                      key={`${suggestion.name}-${index}`}
                      onPress={() => handleSelectSuggestion(suggestion)}
                      activeOpacity={0.6}
                      className={`px-4 py-3 ${index < filteredSuggestions.length - 1 ? 'border-b border-gray-200' : ''}`}
                    >
                      <Text className="text-sm font-medium text-gray-800">
                        {suggestion.name}
                      </Text>
                      {(suggestion.category || suggestion.unit) && (
                        <Text className="text-xs text-gray-500 mt-0.5">
                          {[suggestion.category, suggestion.unit]
                            .filter(Boolean)
                            .join(' - ')}
                        </Text>
                      )}
                    </TouchableOpacity>
                  ))}
                </View>
              )}
            </View>

            {/* Section Quantite + Unite */}
            <View className="flex-row gap-3 mt-4">
              <View className="flex-1">
                <Input
                  label="Quantite"
                  placeholder="1"
                  value={quantityText}
                  onChangeText={setQuantityText}
                  keyboardType="decimal-pad"
                  returnKeyType="next"
                />
              </View>
              <View className="flex-1">
                <Input
                  label="Unite"
                  placeholder="kg, L, pcs"
                  value={unit}
                  onChangeText={setUnit}
                  returnKeyType="next"
                />
              </View>
            </View>

            {/* Categorie */}
            <View className="mt-4">
              <Input
                label="Categorie"
                placeholder="Fruits, Boulangerie..."
                value={category}
                onChangeText={setCategory}
                returnKeyType="next"
              />
            </View>

            {/* Storage location */}
            <View className="mt-4">
              <Text className="text-sm font-medium text-gray-700 mb-2">
                Lieu de stockage
              </Text>
              <View className="flex-row flex-wrap gap-2">
                {STORAGE_OPTIONS.map((option) => (
                  <StorageButton
                    key={option.value}
                    label={option.label}
                    selected={storageLocation === option.value}
                    onPress={() => setStorageLocation(option.value)}
                    disabled={loading}
                  />
                ))}
                <StorageButton
                  label="Aucun"
                  selected={storageLocation === null}
                  onPress={() => setStorageLocation(null)}
                  disabled={loading}
                />
              </View>
            </View>

            {/* Notes */}
            <View className="mt-4">
              <Input
                label="Notes"
                placeholder="Bio, marque preferee..."
                value={notes}
                onChangeText={setNotes}
                multiline
                numberOfLines={3}
                className="min-h-[80px] py-3"
                textAlignVertical="top"
              />
            </View>

            {/* Prix */}
            <View className="mt-4">
              <Input
                label="Prix unitaire (optionnel)"
                placeholder="0.00"
                value={priceText}
                onChangeText={setPriceText}
                keyboardType="decimal-pad"
                returnKeyType="done"
                onSubmitEditing={handleSubmit}
              />
            </View>
          </ScrollView>

          {/* Boutons en bas */}
          <View className="px-6 py-4 border-t border-gray-100 flex-row gap-3">
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
