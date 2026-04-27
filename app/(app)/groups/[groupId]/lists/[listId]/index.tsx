import { useState, useEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import {
  useListItems,
  useAddItem,
  useToggleItem,
  useDeleteItem,
  useUpdateItem,
} from '../../../../../../src/hooks/useItems';
import { useItemHistory } from '../../../../../../src/hooks/useItemHistory';
import { useListViews } from '../../../../../../src/hooks/useListViews';
import { Button } from '../../../../../../src/components/ui/Button';
import { Input } from '../../../../../../src/components/ui/Input';
import { EmptyState } from '../../../../../../src/components/ui/EmptyState';
import { LoadingState } from '../../../../../../src/components/ui/LoadingState';
import {
  ItemFormModal,
  type ItemFormValues,
} from '../../../../../../src/components/ui/ItemFormModal';
import { useRealtimeItems } from '../../../../../../src/hooks/useRealtimeItems';
import { confirm, notifyError } from '../../../../../../src/utils/confirm';
import type { Database } from '../../../../../../src/types/database';

type ItemRow = Database['public']['Tables']['items']['Row'];

type ModalMode =
  | { kind: 'closed' }
  | { kind: 'create' }
  | { kind: 'edit'; item: ItemRow };

export default function GroupListDetailScreen() {
  const { listId, listName, groupName } = useLocalSearchParams<{
    groupId: string;
    listId: string;
    listName: string;
    groupName: string;
  }>();
  const { data: items, isLoading } = useListItems(listId);
  const addItem = useAddItem();
  const toggleItem = useToggleItem();
  const deleteItem = useDeleteItem();
  const updateItem = useUpdateItem();
  const { markAsViewed } = useListViews();
  const { data: suggestions } = useItemHistory();

  useRealtimeItems(listId);

  // Marquer la liste comme vue a l'ouverture
  useEffect(() => {
    if (listId) markAsViewed(listId);
  }, [listId, markAsViewed]);

  const [newItemName, setNewItemName] = useState('');
  const [modal, setModal] = useState<ModalMode>({ kind: 'closed' });

  const handleQuickAdd = async () => {
    const name = newItemName.trim();
    if (!name) return;

    try {
      await addItem.mutateAsync({ listId, name });
      setNewItemName('');
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleDetailedAdd = async (values: ItemFormValues) => {
    try {
      await addItem.mutateAsync({
        listId,
        name: values.name,
        quantity: values.quantity,
        unit: values.unit.length > 0 ? values.unit : undefined,
        category: values.category.length > 0 ? values.category : undefined,
      });
      setModal({ kind: 'closed' });
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleEdit = async (values: ItemFormValues) => {
    if (modal.kind !== 'edit') return;
    try {
      await updateItem.mutateAsync({
        itemId: modal.item.id,
        listId,
        name: values.name,
        quantity: values.quantity,
        unit: values.unit.length > 0 ? values.unit : null,
        category: values.category.length > 0 ? values.category : null,
        storage_location: values.storage_location,
        notes: values.notes.length > 0 ? values.notes : null,
        price: values.price,
      });
      setModal({ kind: 'closed' });
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleDelete = async (item: ItemRow) => {
    const ok = await confirm({
      title: 'Supprimer',
      message: `Retirer "${item.name}" de la liste ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteItem.mutateAsync({ itemId: item.id, listId });
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const initialEditValues: Partial<ItemFormValues> | undefined =
    modal.kind === 'edit'
      ? {
          name: modal.item.name,
          quantity: modal.item.quantity,
          unit: modal.item.unit ?? '',
          category: modal.item.category ?? '',
          storage_location: modal.item.storage_location,
          notes: modal.item.notes ?? '',
          price: modal.item.price,
        }
      : undefined;

  const unchecked = items?.filter((i) => !i.is_checked) ?? [];
  const checked = items?.filter((i) => i.is_checked) ?? [];

  const headerTitle = groupName
    ? `${groupName} / ${listName || 'Liste'}`
    : listName || 'Liste';

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: headerTitle,
          headerTintColor: '#0d9488',
        }}
      />

      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        className="flex-1 bg-gray-50"
      >
        {isLoading ? (
          <LoadingState />
        ) : (
          <FlatList
            data={[...unchecked, ...checked]}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ padding: 16, gap: 8 }}
            ListEmptyComponent={
              <EmptyState
                icon="📋"
                title="Aucun article"
                subtitle="Ajoutez-en un avec la barre ci-dessous !"
              />
            }
            ListHeaderComponent={
              unchecked.length > 0 ? (
                <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide mb-1">
                  A acheter ({unchecked.length})
                </Text>
              ) : null
            }
            renderItem={({ item, index }) => {
              const meta: string[] = [];
              if (item.quantity > 1 || item.unit) {
                meta.push(`${item.quantity}${item.unit ? ` ${item.unit}` : ''}`);
              }
              if (item.storage_location) {
                const labels: Record<string, string> = {
                  pantry: 'Placard',
                  fridge: 'Frigo',
                  freezer: 'Congelateur',
                };
                meta.push(labels[item.storage_location] ?? item.storage_location);
              }

              return (
                <>
                  {item.is_checked && index === unchecked.length && checked.length > 0 && (
                    <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide mt-4 mb-1">
                      Fait ({checked.length})
                    </Text>
                  )}
                  <View
                    className={`flex-row items-center bg-white rounded-xl px-3 py-2 border ${
                      item.is_checked ? 'border-gray-100 opacity-60' : 'border-gray-100'
                    }`}
                  >
                    {/* Edit button */}
                    <TouchableOpacity
                      onPress={() => setModal({ kind: 'edit', item })}
                      className="w-9 h-9 items-center justify-center mr-1"
                      activeOpacity={0.6}
                    >
                      <Text className="text-base text-gray-500">✎</Text>
                    </TouchableOpacity>

                    {/* Toggle area */}
                    <TouchableOpacity
                      className="flex-1 flex-row items-center py-1"
                      onPress={() => toggleItem.mutate({ item })}
                      onLongPress={() => handleDelete(item)}
                      activeOpacity={0.7}
                    >
                      {/* Checkbox */}
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

                      {/* Item info */}
                      <View className="flex-1">
                        <Text
                          className={`text-base ${
                            item.is_checked
                              ? 'text-gray-400 line-through'
                              : 'text-gray-800'
                          }`}
                        >
                          {item.name}
                        </Text>
                        {meta.length > 0 && (
                          <Text className="text-xs text-gray-400 mt-0.5">
                            {meta.join(' · ')}
                          </Text>
                        )}
                        {item.notes && item.notes.length > 0 && (
                          <Text
                            className="text-xs text-gray-400 mt-0.5"
                            numberOfLines={1}
                          >
                            {item.notes}
                          </Text>
                        )}
                      </View>
                    </TouchableOpacity>
                  </View>
                </>
              );
            }}
          />
        )}

        {/* Barre d'ajout rapide */}
        <View className="flex-row items-center gap-2 px-4 pb-6 pt-2 bg-white border-t border-gray-100">
          <View className="flex-1">
            <Input
              placeholder="Ajouter un article..."
              value={newItemName}
              onChangeText={setNewItemName}
              returnKeyType="done"
              onSubmitEditing={handleQuickAdd}
            />
          </View>
          <Button
            title="Ajouter"
            size="md"
            onPress={handleQuickAdd}
            loading={addItem.isPending}
            disabled={!newItemName.trim()}
          />
          <Button
            title="+ Detail"
            variant="outline"
            size="md"
            onPress={() => setModal({ kind: 'create' })}
          />
        </View>
      </KeyboardAvoidingView>

      <ItemFormModal
        visible={modal.kind !== 'closed'}
        title={modal.kind === 'edit' ? 'Modifier l\'article' : 'Nouvel article'}
        initialValues={initialEditValues}
        suggestions={
          modal.kind === 'create'
            ? (suggestions ?? []).map((s) => ({
                name: s.name,
                category: s.category ?? undefined,
                unit: s.unit ?? undefined,
                default_quantity: s.default_quantity ?? undefined,
              }))
            : []
        }
        loading={addItem.isPending || updateItem.isPending}
        onSubmit={modal.kind === 'edit' ? handleEdit : handleDetailedAdd}
        onCancel={() => setModal({ kind: 'closed' })}
      />
    </>
  );
}
