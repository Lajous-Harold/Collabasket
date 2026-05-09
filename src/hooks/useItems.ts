import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database, StorageLocation } from '../types/database';

type ItemRow = Database['public']['Tables']['items']['Row'];
type ItemUpdate = Database['public']['Tables']['items']['Update'];

export function useListItems(listId: string) {
  return useQuery({
    queryKey: ['items', listId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('items')
        .select('*')
        .eq('list_id', listId)
        .order('is_checked', { ascending: true })
        .order('created_at', { ascending: false });

      if (error) throw error;
      return data as ItemRow[];
    },
    enabled: !!listId,
  });
}

export function useAddItem() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async ({
      listId,
      name,
      quantity,
      unit,
      category_id,
      storage_location,
      notes,
      price,
    }: {
      listId: string;
      name: string;
      quantity?: number;
      unit?: string | null;
      category_id?: string | null;
      storage_location?: StorageLocation | null;
      notes?: string | null;
      price?: number | null;
    }) => {
      const { data, error } = await supabase
        .from('items')
        .insert({
          list_id: listId,
          name,
          quantity: quantity ?? 1,
          unit: unit ?? null,
          category_id: category_id ?? null,
          added_by: user!.id,
          storage_location: storage_location ?? null,
          notes: notes ?? null,
          price: price ?? null,
        })
        .select()
        .single();

      if (error) throw error;
      return data as ItemRow;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['items', variables.listId] });
    },
  });
}

export function useToggleItem() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async ({
      item,
    }: {
      item: ItemRow;
    }) => {
      const newChecked = !item.is_checked;
      const { error } = await supabase
        .from('items')
        .update({
          is_checked: newChecked,
          checked_by: newChecked ? user!.id : null,
        })
        .eq('id', item.id);

      if (error) throw error;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['items', variables.item.list_id],
      });
    },
  });
}

export interface UpdateItemPatch {
  name?: string;
  quantity?: number;
  unit?: string | null;
  category_id?: string | null;
  storage_location?: StorageLocation | null;
  notes?: string | null;
  price?: number | null;
}

export function useUpdateItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      itemId,
      listId: _listId,
      ...patch
    }: { itemId: string; listId: string } & UpdateItemPatch) => {
      const update: ItemUpdate = {};
      if (patch.name !== undefined) update.name = patch.name;
      if (patch.quantity !== undefined) update.quantity = patch.quantity;
      if (patch.unit !== undefined) update.unit = patch.unit;
      if (patch.category_id !== undefined) update.category_id = patch.category_id;
      if (patch.storage_location !== undefined)
        update.storage_location = patch.storage_location;
      if (patch.notes !== undefined) update.notes = patch.notes;
      if (patch.price !== undefined) update.price = patch.price;

      const { data, error } = await supabase
        .from('items')
        .update(update)
        .eq('id', itemId)
        .select()
        .single();

      if (error) throw error;
      return data as ItemRow;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['items', variables.listId] });
    },
  });
}

export function useDeleteItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ itemId, listId }: { itemId: string; listId: string }) => {
      const { error } = await supabase.from('items').delete().eq('id', itemId);
      if (error) throw error;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['items', variables.listId],
      });
    },
  });
}

export function useClearCheckedItems() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ listId }: { listId: string }) => {
      const { error } = await supabase
        .from('items')
        .delete()
        .eq('list_id', listId)
        .eq('is_checked', true);
      if (error) throw error;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['items', variables.listId] });
    },
  });
}
