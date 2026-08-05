import { useCallback } from 'react';
import {
  useQuery,
  useMutation,
  type MutateOptions,
} from '@tanstack/react-query';
import * as Crypto from 'expo-crypto';
import { supabase } from '../lib/supabase';
import {
  itemMutationKeys,
  type AddItemVars,
  type ToggleItemVars,
  type UpdateItemVars,
  type DeleteItemVars,
  type ClearCheckedVars,
  type ItemsMutationContext,
} from '../lib/itemMutations';
import type { Database } from '../types/database';

export type { UpdateItemPatch } from '../lib/itemMutations';

type ItemRow = Database['public']['Tables']['items']['Row'];

// Les mutationFn + updates optimistes vivent dans
// src/lib/itemMutations.ts (setMutationDefaults) pour que les
// mutations en pause hors ligne soient rejouables après un cold
// start. Les hooks ci-dessous ne font que référencer la mutationKey.

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
  const mutation = useMutation<ItemRow, Error, AddItemVars, ItemsMutationContext>(
    { mutationKey: itemMutationKeys.add },
  );

  // Génère l'UUID côté client au moment du mutate : la ligne optimiste
  // et la ligne serveur partagent le même id, donc un article ajouté
  // hors ligne peut être coché/supprimé avant même d'être synchronisé.
  const { mutate, mutateAsync } = mutation;

  type AddOptions = MutateOptions<ItemRow, Error, AddItemVars, ItemsMutationContext>;

  const mutateWithId = useCallback(
    (vars: Omit<AddItemVars, 'id'>, options?: AddOptions) =>
      mutate({ ...vars, id: Crypto.randomUUID() }, options),
    [mutate],
  );

  const mutateAsyncWithId = useCallback(
    (vars: Omit<AddItemVars, 'id'>, options?: AddOptions) =>
      mutateAsync({ ...vars, id: Crypto.randomUUID() }, options),
    [mutateAsync],
  );

  return { ...mutation, mutate: mutateWithId, mutateAsync: mutateAsyncWithId };
}

export function useToggleItem() {
  return useMutation<void, Error, ToggleItemVars, ItemsMutationContext>({
    mutationKey: itemMutationKeys.toggle,
  });
}

export function useUpdateItem() {
  return useMutation<void, Error, UpdateItemVars, ItemsMutationContext>({
    mutationKey: itemMutationKeys.update,
  });
}

export function useDeleteItem() {
  return useMutation<void, Error, DeleteItemVars, ItemsMutationContext>({
    mutationKey: itemMutationKeys.remove,
  });
}

export function useClearCheckedItems() {
  return useMutation<void, Error, ClearCheckedVars, ItemsMutationContext>({
    mutationKey: itemMutationKeys.clearChecked,
  });
}
