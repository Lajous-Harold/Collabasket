import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database } from '../types/database';

type ListRow = Database['public']['Tables']['lists']['Row'];

export function useMyLists() {
  const user = useAuthStore((s) => s.user);

  return useQuery({
    queryKey: ['lists', 'personal', user?.id],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('lists')
        .select('*')
        .eq('type', 'personal')
        .eq('owner_user_id', user!.id)
        .eq('is_archived', false)
        .order('updated_at', { ascending: false });

      if (error) throw error;
      return data as ListRow[];
    },
    enabled: !!user,
  });
}

export function useCreateList() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async (name: string) => {
      const { data, error } = await supabase
        .from('lists')
        .insert({
          name,
          type: 'personal',
          owner_user_id: user!.id,
        })
        .select()
        .single();

      if (error) throw error;
      return data as ListRow;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lists', 'personal'] });
    },
  });
}

export function useDeleteList() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (listId: string) => {
      const { error } = await supabase.from('lists').delete().eq('id', listId);
      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lists', 'personal'] });
    },
  });
}

export function useRenameList() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ listId, name }: { listId: string; name: string }) => {
      const { error } = await supabase
        .from('lists')
        .update({ name })
        .eq('id', listId);

      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lists', 'personal'] });
    },
  });
}
