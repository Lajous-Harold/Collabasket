import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database } from '../types/database';

export type CategoryRow = Database['public']['Tables']['categories']['Row'];

interface UseCategoriesOptions {
  userId?: string;
  groupId?: string;
}

export function useCategories({ userId, groupId }: UseCategoriesOptions) {
  return useQuery({
    queryKey: ['categories', userId ?? null, groupId ?? null],
    queryFn: async (): Promise<CategoryRow[]> => {
      let query = supabase.from('categories').select('*').order('name');

      if (groupId) {
        query = query.eq('owner_group_id', groupId);
      } else if (userId) {
        query = query.eq('owner_user_id', userId);
      } else {
        return [];
      }

      const { data, error } = await query;
      if (error) throw error;
      return data as CategoryRow[];
    },
    enabled: !!(userId || groupId),
  });
}

export function useCreateCategory({ userId, groupId }: UseCategoriesOptions) {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async ({ name, color }: { name: string; color?: string | null }) => {
      const { data, error } = await supabase
        .from('categories')
        .insert({
          name: name.trim(),
          color: color ?? null,
          created_by: user!.id,
          owner_user_id: groupId ? null : user!.id,
          owner_group_id: groupId ?? null,
        })
        .select()
        .single();

      if (error) throw error;
      return data as CategoryRow;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['categories', userId ?? null, groupId ?? null],
      });
    },
  });
}

export function useDeleteCategory({ userId, groupId }: UseCategoriesOptions) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ categoryId }: { categoryId: string }) => {
      const { error } = await supabase
        .from('categories')
        .delete()
        .eq('id', categoryId);
      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['categories', userId ?? null, groupId ?? null],
      });
    },
  });
}
