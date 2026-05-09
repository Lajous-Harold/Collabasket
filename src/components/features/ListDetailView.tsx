import { useState, useEffect, useMemo } from 'react';
import {
  View,
  Text,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  TouchableOpacity,
} from 'react-native';
import {
  useListItems,
  useAddItem,
  useToggleItem,
  useDeleteItem,
  useUpdateItem,
  useClearCheckedItems,
} from '../../hooks/useItems';
import { useCategories, useCreateCategory } from '../../hooks/useCategories';
import { useItemHistory } from '../../hooks/useItemHistory';
import { useListViews } from '../../hooks/useListViews';
import { useRealtimeItems } from '../../hooks/useRealtimeItems';
import { useAuthStore } from '../../stores/authStore';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { EmptyState } from '../ui/EmptyState';
import { LoadingState } from '../ui/LoadingState';
import { ItemCard } from '../ui/ItemCard';
import {
  ItemFormModal,
  type ItemFormValues,
  type StorageLocation,
} from '../ui/ItemFormModal';
import { confirm, notifyError } from '../../utils/confirm';
import type { Database } from '../../types/database';
import type { CategoryRow } from '../../hooks/useCategories';

type ItemRow = Database['public']['Tables']['items']['Row'];

type ModalMode =
  | { kind: 'closed' }
  | { kind: 'create' }
  | { kind: 'edit'; item: ItemRow };

type SortBy = 'recent' | 'alpha';

const SORT_LABELS: Record<SortBy, string> = {
  recent: 'Récent',
  alpha: 'A→Z',
};

const SORT_ORDER: SortBy[] = ['recent', 'alpha'];

const LOCATION_FILTERS: Array<{ value: StorageLocation; label: string }> = [
  { value: null, label: 'Tous' },
  { value: 'pantry', label: 'Placard' },
  { value: 'fridge', label: 'Frigo' },
  { value: 'freezer', label: 'Congél.' },
];

// Union type pour le rendu unifié (vue plate et vue groupée)
type ListEntry =
  | { kind: 'unchecked-header'; count: number }
  | { kind: 'checked-header'; count: number }
  | { kind: 'cat-header'; name: string; categoryId: string | null }
  | { kind: 'item'; data: ItemRow };

interface Props {
  listId: string;
  listName?: string;
  groupId?: string;
}

export function ListDetailView({ listId, groupId }: Props) {
  const user = useAuthStore((s) => s.user);
  const ownerId = groupId ? undefined : user?.id;

  const { data: items, isLoading } = useListItems(listId);
  const { data: categories } = useCategories({ userId: ownerId, groupId });
  const createCategory = useCreateCategory({ userId: ownerId, groupId });

  const addItem = useAddItem();
  const toggleItem = useToggleItem();
  const deleteItem = useDeleteItem();
  const updateItem = useUpdateItem();
  const clearChecked = useClearCheckedItems();
  const { markAsViewed } = useListViews();
  const { data: suggestions } = useItemHistory();

  useRealtimeItems(listId);

  useEffect(() => {
    if (listId) markAsViewed(listId);
  }, [listId, markAsViewed]);

  const [newItemName, setNewItemName] = useState('');
  const [modal, setModal] = useState<ModalMode>({ kind: 'closed' });
  const [showFilters, setShowFilters] = useState(false);
  const [groupedView, setGroupedView] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortBy>('recent');
  const [filterLocation, setFilterLocation] = useState<StorageLocation>(null);

  const filtersActive = searchQuery.trim().length > 0 || filterLocation !== null;

  const cycleSortBy = () => {
    const idx = SORT_ORDER.indexOf(sortBy);
    setSortBy(SORT_ORDER[(idx + 1) % SORT_ORDER.length]);
  };

  // Items filtrés et triés (avant groupement)
  const filteredItems = useMemo(() => {
    let result = items ?? [];

    if (searchQuery.trim().length > 0) {
      const q = searchQuery.trim().toLowerCase();
      result = result.filter(
        (item) =>
          item.name.toLowerCase().includes(q) ||
          (categories
            ?.find((c) => c.id === item.category_id)
            ?.name.toLowerCase()
            .includes(q) ?? false)
      );
    }

    if (filterLocation !== null) {
      result = result.filter((item) => item.storage_location === filterLocation);
    }

    return [...result].sort((a, b) => {
      if (a.is_checked !== b.is_checked) return a.is_checked ? 1 : -1;
      if (sortBy === 'alpha') return a.name.localeCompare(b.name);
      return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
    });
  }, [items, categories, searchQuery, filterLocation, sortBy]);

  const unchecked = filteredItems.filter((i) => !i.is_checked);
  const checked = filteredItems.filter((i) => i.is_checked);

  // Construction du tableau plat unifié pour FlatList
  const listData = useMemo((): ListEntry[] => {
    if (!groupedView) {
      const entries: ListEntry[] = [];
      if (unchecked.length > 0) {
        entries.push({ kind: 'unchecked-header', count: unchecked.length });
        for (const item of unchecked) entries.push({ kind: 'item', data: item });
      }
      if (checked.length > 0) {
        entries.push({ kind: 'checked-header', count: checked.length });
        for (const item of checked) entries.push({ kind: 'item', data: item });
      }
      return entries;
    }

    // Vue groupée par catégorie
    const byCategory = new Map<string | null, ItemRow[]>();
    for (const cat of categories ?? []) byCategory.set(cat.id, []);
    byCategory.set(null, []);

    for (const item of filteredItems) {
      const key = item.category_id ?? null;
      if (!byCategory.has(key)) byCategory.set(key, []);
      byCategory.get(key)!.push(item);
    }

    const catName = (id: string | null) =>
      id ? (categories?.find((c) => c.id === id)?.name ?? '') : '';

    const sortedEntries = Array.from(byCategory.entries())
      .filter(([, catItems]) => catItems.length > 0)
      .sort(([aId], [bId]) => {
        if (aId === null) return 1;
        if (bId === null) return -1;
        return catName(aId).localeCompare(catName(bId));
      });

    const entries: ListEntry[] = [];
    for (const [catId, catItems] of sortedEntries) {
      entries.push({
        kind: 'cat-header',
        categoryId: catId,
        name: catId ? (catName(catId) || 'Catégorie') : 'Sans catégorie',
      });
      for (const item of catItems) entries.push({ kind: 'item', data: item });
    }
    return entries;
  }, [filteredItems, unchecked, checked, categories, groupedView]);

  // ── Handlers ──────────────────────────────────────────────

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
        unit: values.unit.length > 0 ? values.unit : null,
        category_id: values.category_id,
        storage_location: values.storage_location,
        notes: values.notes.length > 0 ? values.notes : null,
        price: values.price,
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
        category_id: values.category_id,
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
    setModal({ kind: 'closed' });
    try {
      await deleteItem.mutateAsync({ itemId: item.id, listId });
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleClearChecked = async () => {
    const ok = await confirm({
      title: 'Vider les articles faits',
      message: `Supprimer les ${checked.length} article(s) cochés ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await clearChecked.mutateAsync({ listId });
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleCreateCategory = async (name: string): Promise<CategoryRow> => {
    return createCategory.mutateAsync({ name });
  };

  const initialEditValues: Partial<ItemFormValues> | undefined =
    modal.kind === 'edit'
      ? {
          name: modal.item.name,
          quantity: modal.item.quantity,
          unit: modal.item.unit ?? '',
          category_id: modal.item.category_id,
          storage_location: modal.item.storage_location,
          notes: modal.item.notes ?? '',
          price: modal.item.price,
        }
      : undefined;

  // ── Rendu ─────────────────────────────────────────────────

  const renderEntry = ({ item: entry }: { item: ListEntry }) => {
    if (entry.kind === 'unchecked-header') {
      return (
        <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide mb-1 mt-1">
          À acheter ({entry.count})
        </Text>
      );
    }

    if (entry.kind === 'checked-header') {
      return (
        <View className="flex-row items-center justify-between mt-4 mb-1">
          <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide">
            Fait ({entry.count})
          </Text>
          <TouchableOpacity
            onPress={handleClearChecked}
            disabled={clearChecked.isPending}
            className="px-2 py-1"
          >
            <Text className="text-xs text-danger-600 font-medium">Tout vider</Text>
          </TouchableOpacity>
        </View>
      );
    }

    if (entry.kind === 'cat-header') {
      return (
        <Text className="text-xs font-semibold text-primary-700 uppercase tracking-wide mt-4 mb-1 px-1">
          {entry.name}
        </Text>
      );
    }

    // entry.kind === 'item'
    return (
      <ItemCard
        item={entry.data}
        onToggle={() => toggleItem.mutate({ item: entry.data })}
        onEdit={() => setModal({ kind: 'edit', item: entry.data })}
        onDelete={() => handleDelete(entry.data)}
      />
    );
  };

  return (
    <>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        className="flex-1 bg-gray-50"
      >
        {isLoading ? (
          <LoadingState />
        ) : (
          <FlatList
            data={listData}
            keyExtractor={(entry, idx) =>
              entry.kind === 'item'
                ? entry.data.id
                : `${entry.kind}-${idx}`
            }
            contentContainerStyle={
              listData.length === 0
                ? { flex: 1, padding: 16 }
                : { padding: 16, gap: 8 }
            }
            ListEmptyComponent={
              <EmptyState
                icon="📋"
                title="Aucun article"
                subtitle={
                  filtersActive
                    ? 'Aucun résultat pour ces filtres.'
                    : 'Ajoutez-en un avec la barre ci-dessous !'
                }
              />
            }
            renderItem={renderEntry}
          />
        )}

        {/* Barre du bas */}
        <View className="bg-white border-t border-gray-100">
          {showFilters && (
            <View className="px-4 pt-3 pb-2 gap-2">
              <Input
                placeholder="Rechercher un article..."
                value={searchQuery}
                onChangeText={setSearchQuery}
              />
              <View className="flex-row items-center gap-2 flex-wrap">
                <TouchableOpacity
                  onPress={cycleSortBy}
                  className="px-3 py-1.5 rounded-lg bg-primary-50 border border-primary-200"
                >
                  <Text className="text-xs text-primary-700 font-medium">
                    Tri : {SORT_LABELS[sortBy]}
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={() => setGroupedView((v) => !v)}
                  className={`px-3 py-1.5 rounded-lg border ${
                    groupedView
                      ? 'bg-primary-600 border-primary-600'
                      : 'bg-transparent border-gray-300'
                  }`}
                >
                  <Text
                    className={`text-xs font-medium ${
                      groupedView ? 'text-white' : 'text-gray-600'
                    }`}
                  >
                    Par catégorie
                  </Text>
                </TouchableOpacity>
                {LOCATION_FILTERS.map((opt) => (
                  <TouchableOpacity
                    key={String(opt.value)}
                    onPress={() => setFilterLocation(opt.value)}
                    className={`px-3 py-1.5 rounded-lg border ${
                      filterLocation === opt.value
                        ? 'bg-primary-600 border-primary-600'
                        : 'bg-transparent border-gray-300'
                    }`}
                  >
                    <Text
                      className={`text-xs font-medium ${
                        filterLocation === opt.value ? 'text-white' : 'text-gray-600'
                      }`}
                    >
                      {opt.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          )}

          <View className="flex-row items-center gap-2 px-4 pb-6 pt-2">
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
              title="+ Détail"
              variant="outline"
              size="md"
              onPress={() => setModal({ kind: 'create' })}
            />
            <TouchableOpacity
              onPress={() => setShowFilters((v) => !v)}
              className={`w-10 h-10 items-center justify-center rounded-xl border ${
                showFilters || filtersActive || groupedView
                  ? 'border-primary-600 bg-primary-50'
                  : 'border-gray-200'
              }`}
            >
              <Text className="text-base">⊞</Text>
            </TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>

      <ItemFormModal
        visible={modal.kind !== 'closed'}
        title={modal.kind === 'edit' ? "Modifier l'article" : 'Nouvel article'}
        initialValues={initialEditValues}
        categories={categories ?? []}
        onCreateCategory={handleCreateCategory}
        suggestions={
          modal.kind === 'create'
            ? (suggestions ?? []).map((s) => ({
                name: s.name,
                unit: s.unit ?? undefined,
                default_quantity: s.default_quantity ?? undefined,
              }))
            : []
        }
        loading={addItem.isPending || updateItem.isPending}
        onSubmit={modal.kind === 'edit' ? handleEdit : handleDetailedAdd}
        onCancel={() => setModal({ kind: 'closed' })}
        onDelete={
          modal.kind === 'edit' ? () => handleDelete(modal.item) : undefined
        }
      />
    </>
  );
}
