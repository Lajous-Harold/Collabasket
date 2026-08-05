import type { QueryClient } from '@tanstack/react-query';
import { supabase } from './supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database, StorageLocation } from '../types/database';

type ItemRow = Database['public']['Tables']['items']['Row'];
type ItemUpdate = Database['public']['Tables']['items']['Update'];

/**
 * Les mutations d'articles sont définies en `setMutationDefaults` sur
 * le QueryClient (et non inline dans les hooks) pour une raison
 * précise : les mutations mises en pause hors ligne sont persistées
 * par le persister, et à la restauration (cold start) React Query ne
 * peut les rejouer que si leur mutationFn est retrouvable via la
 * mutationKey. C'est le mécanisme documenté de file offline de
 * TanStack Query.
 *
 * Chaque mutation applique un update optimiste sur ['items', listId] :
 * l'UI répond instantanément, y compris sans réseau (cas d'usage
 * principal : cocher des articles en magasin).
 */

export const itemMutationKeys = {
  add: ['items', 'add'] as const,
  toggle: ['items', 'toggle'] as const,
  update: ['items', 'update'] as const,
  remove: ['items', 'remove'] as const,
  clearChecked: ['items', 'clearChecked'] as const,
};

export interface AddItemVars {
  /** UUID généré côté client (voir useAddItem) : l'article optimiste
   * garde le même id que la ligne serveur, il reste donc manipulable
   * (cocher, supprimer) même ajouté hors ligne. */
  id: string;
  listId: string;
  name: string;
  quantity?: number;
  unit?: string | null;
  category_id?: string | null;
  storage_location?: StorageLocation | null;
  notes?: string | null;
  price?: number | null;
}

export interface ToggleItemVars {
  item: ItemRow;
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

export type UpdateItemVars = { itemId: string; listId: string } & UpdateItemPatch;

export interface DeleteItemVars {
  itemId: string;
  listId: string;
}

export interface ClearCheckedVars {
  listId: string;
}

export interface ItemsMutationContext {
  previous: ItemRow[] | undefined;
}

function itemsKey(listId: string) {
  return ['items', listId] as const;
}

async function snapshotItems(
  qc: QueryClient,
  listId: string,
): Promise<ItemsMutationContext> {
  // Annule les refetch en vol pour qu'ils n'écrasent pas l'optimiste
  await qc.cancelQueries({ queryKey: itemsKey(listId) });
  return { previous: qc.getQueryData<ItemRow[]>(itemsKey(listId)) };
}

function rollbackItems(
  qc: QueryClient,
  listId: string,
  ctx: ItemsMutationContext | undefined,
): void {
  if (ctx?.previous !== undefined) {
    qc.setQueryData(itemsKey(listId), ctx.previous);
  }
}

function settleItems(qc: QueryClient, listId: string): void {
  // N'invalide qu'une fois la DERNIÈRE mutation d'items terminée :
  // un refetch pendant qu'une autre mutation optimiste est en vol
  // ferait "sauter" visuellement son effet.
  if (qc.isMutating({ mutationKey: ['items'] }) === 1) {
    qc.invalidateQueries({ queryKey: itemsKey(listId) });
  }
}

export function setupItemMutationDefaults(qc: QueryClient): void {
  qc.setMutationDefaults(itemMutationKeys.add, {
    networkMode: 'online',
    mutationFn: async (vars: AddItemVars): Promise<ItemRow> => {
      const user = useAuthStore.getState().user;
      if (!user) throw new Error('Utilisateur non connecté');
      const { data, error } = await supabase
        .from('items')
        .insert({
          id: vars.id,
          list_id: vars.listId,
          name: vars.name,
          quantity: vars.quantity ?? 1,
          unit: vars.unit ?? null,
          category_id: vars.category_id ?? null,
          added_by: user.id,
          storage_location: vars.storage_location ?? null,
          notes: vars.notes ?? null,
          price: vars.price ?? null,
        })
        .select()
        .single();
      if (error) throw error;
      return data as ItemRow;
    },
    onMutate: async (vars: AddItemVars): Promise<ItemsMutationContext> => {
      const ctx = await snapshotItems(qc, vars.listId);
      const user = useAuthStore.getState().user;
      const now = new Date().toISOString();
      const optimistic: ItemRow = {
        id: vars.id,
        list_id: vars.listId,
        name: vars.name,
        quantity: vars.quantity ?? 1,
        unit: vars.unit ?? null,
        category_id: vars.category_id ?? null,
        added_by: user?.id ?? '',
        checked_by: null,
        is_checked: false,
        storage_location: vars.storage_location ?? null,
        notes: vars.notes ?? null,
        price: vars.price ?? null,
        created_at: now,
        updated_at: now,
      };
      qc.setQueryData<ItemRow[]>(itemsKey(vars.listId), (old) => [
        optimistic,
        ...(old ?? []),
      ]);
      return ctx;
    },
    onError: (_err, vars: AddItemVars, ctx) =>
      rollbackItems(qc, vars.listId, ctx as ItemsMutationContext | undefined),
    onSettled: (_data, _err, vars: AddItemVars) => settleItems(qc, vars.listId),
  });

  qc.setMutationDefaults(itemMutationKeys.toggle, {
    networkMode: 'online',
    mutationFn: async ({ item }: ToggleItemVars): Promise<void> => {
      const user = useAuthStore.getState().user;
      const newChecked = !item.is_checked;
      const { error } = await supabase
        .from('items')
        .update({
          is_checked: newChecked,
          checked_by: newChecked ? (user?.id ?? null) : null,
        })
        .eq('id', item.id);
      if (error) throw error;
    },
    onMutate: async ({ item }: ToggleItemVars): Promise<ItemsMutationContext> => {
      const ctx = await snapshotItems(qc, item.list_id);
      const user = useAuthStore.getState().user;
      const newChecked = !item.is_checked;
      qc.setQueryData<ItemRow[]>(itemsKey(item.list_id), (old) =>
        old?.map((i) =>
          i.id === item.id
            ? {
                ...i,
                is_checked: newChecked,
                checked_by: newChecked ? (user?.id ?? null) : null,
              }
            : i,
        ),
      );
      return ctx;
    },
    onError: (_err, { item }: ToggleItemVars, ctx) =>
      rollbackItems(qc, item.list_id, ctx as ItemsMutationContext | undefined),
    onSettled: (_data, _err, { item }: ToggleItemVars) =>
      settleItems(qc, item.list_id),
  });

  qc.setMutationDefaults(itemMutationKeys.update, {
    networkMode: 'online',
    mutationFn: async (vars: UpdateItemVars): Promise<void> => {
      const { itemId, listId: _listId, ...patch } = vars;
      const update: ItemUpdate = {};
      if (patch.name !== undefined) update.name = patch.name;
      if (patch.quantity !== undefined) update.quantity = patch.quantity;
      if (patch.unit !== undefined) update.unit = patch.unit;
      if (patch.category_id !== undefined) update.category_id = patch.category_id;
      if (patch.storage_location !== undefined)
        update.storage_location = patch.storage_location;
      if (patch.notes !== undefined) update.notes = patch.notes;
      if (patch.price !== undefined) update.price = patch.price;

      const { error } = await supabase
        .from('items')
        .update(update)
        .eq('id', itemId);
      if (error) throw error;
    },
    onMutate: async (vars: UpdateItemVars): Promise<ItemsMutationContext> => {
      const ctx = await snapshotItems(qc, vars.listId);
      const { itemId, listId: _listId, ...patch } = vars;
      qc.setQueryData<ItemRow[]>(itemsKey(vars.listId), (old) =>
        old?.map((i) => (i.id === itemId ? { ...i, ...patch } : i)),
      );
      return ctx;
    },
    onError: (_err, vars: UpdateItemVars, ctx) =>
      rollbackItems(qc, vars.listId, ctx as ItemsMutationContext | undefined),
    onSettled: (_data, _err, vars: UpdateItemVars) =>
      settleItems(qc, vars.listId),
  });

  qc.setMutationDefaults(itemMutationKeys.remove, {
    networkMode: 'online',
    mutationFn: async ({ itemId }: DeleteItemVars): Promise<void> => {
      const { error } = await supabase.from('items').delete().eq('id', itemId);
      if (error) throw error;
    },
    onMutate: async (vars: DeleteItemVars): Promise<ItemsMutationContext> => {
      const ctx = await snapshotItems(qc, vars.listId);
      qc.setQueryData<ItemRow[]>(itemsKey(vars.listId), (old) =>
        old?.filter((i) => i.id !== vars.itemId),
      );
      return ctx;
    },
    onError: (_err, vars: DeleteItemVars, ctx) =>
      rollbackItems(qc, vars.listId, ctx as ItemsMutationContext | undefined),
    onSettled: (_data, _err, vars: DeleteItemVars) =>
      settleItems(qc, vars.listId),
  });

  qc.setMutationDefaults(itemMutationKeys.clearChecked, {
    networkMode: 'online',
    mutationFn: async ({ listId }: ClearCheckedVars): Promise<void> => {
      const { error } = await supabase
        .from('items')
        .delete()
        .eq('list_id', listId)
        .eq('is_checked', true);
      if (error) throw error;
    },
    onMutate: async (vars: ClearCheckedVars): Promise<ItemsMutationContext> => {
      const ctx = await snapshotItems(qc, vars.listId);
      qc.setQueryData<ItemRow[]>(itemsKey(vars.listId), (old) =>
        old?.filter((i) => !i.is_checked),
      );
      return ctx;
    },
    onError: (_err, vars: ClearCheckedVars, ctx) =>
      rollbackItems(qc, vars.listId, ctx as ItemsMutationContext | undefined),
    onSettled: (_data, _err, vars: ClearCheckedVars) =>
      settleItems(qc, vars.listId),
  });
}
