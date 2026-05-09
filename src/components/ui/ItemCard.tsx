import { View, Text, TouchableOpacity } from 'react-native';
import type { Database } from '../../types/database';

type ItemRow = Database['public']['Tables']['items']['Row'];

const STORAGE_LABELS: Record<string, string> = {
  pantry: 'Placard',
  fridge: 'Frigo',
  freezer: 'Congélateur',
};

interface Props {
  item: ItemRow;
  onToggle: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

export function ItemCard({ item, onToggle, onEdit, onDelete }: Props) {
  const meta: string[] = [];
  if (item.quantity > 1 || item.unit) {
    meta.push(`${item.quantity}${item.unit ? ` ${item.unit}` : ''}`);
  }
  if (item.storage_location) {
    meta.push(STORAGE_LABELS[item.storage_location] ?? item.storage_location);
  }
  if (item.price !== null && item.price !== undefined) {
    meta.push(
      item.price.toLocaleString('fr-FR', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }) + ' €'
    );
  }

  return (
    <View
      className={`flex-row items-center bg-white rounded-xl px-3 py-2 border ${
        item.is_checked ? 'border-gray-100 opacity-60' : 'border-gray-100'
      }`}
    >
      <TouchableOpacity
        onPress={onEdit}
        className="w-9 h-9 items-center justify-center mr-1"
        activeOpacity={0.6}
      >
        <Text className="text-base text-gray-500">✎</Text>
      </TouchableOpacity>

      <TouchableOpacity
        className="flex-1 flex-row items-center py-1"
        onPress={onToggle}
        onLongPress={onDelete}
        activeOpacity={0.7}
      >
        <View
          className={`w-6 h-6 rounded-full border-2 items-center justify-center mr-3 ${
            item.is_checked
              ? 'bg-success-500 border-success-500'
              : 'border-gray-300'
          }`}
        >
          {item.is_checked && (
            <Text className="text-white text-xs font-bold">✓</Text>
          )}
        </View>

        <View className="flex-1">
          <Text
            className={`text-base ${
              item.is_checked ? 'text-gray-400 line-through' : 'text-gray-800'
            }`}
          >
            {item.name}
          </Text>
          {meta.length > 0 && (
            <Text className="text-xs text-gray-400 mt-0.5">
              {meta.join(' · ')}
            </Text>
          )}
          {!!item.notes && (
            <Text className="text-xs text-gray-400 mt-0.5" numberOfLines={1}>
              {item.notes}
            </Text>
          )}
        </View>
      </TouchableOpacity>
    </View>
  );
}
