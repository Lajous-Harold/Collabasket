import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import type { Database } from '../types/database';

type ListRow = Database['public']['Tables']['lists']['Row'];

export function useGroupLists(groupId: string) {
  return useQuery({
    queryKey: ['lists', 'group', groupId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('lists')
        .select('*')
        .eq('type', 'group')
        .eq('owner_group_id', groupId)
        .eq('is_archived', false)
        .order('updated_at', { ascending: false });

      if (error) throw error;
      return data as ListRow[];
    },
    enabled: !!groupId,
  });
}

export function useRenameGroupList() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      listId,
      name,
    }: {
      listId: string;
      name: string;
      groupId: string;
    }) => {
      const { error } = await supabase
        .from('lists')
        .update({ name })
        .eq('id', listId);
      if (error) throw error;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['lists', 'group', variables.groupId],
      });
    },
  });
}

export function useDeleteGroupList() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      listId,
    }: {
      listId: string;
      groupId: string;
    }) => {
      const { error } = await supabase.from('lists').delete().eq('id', listId);
      if (error) throw error;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['lists', 'group', variables.groupId],
      });
    },
  });
}

export function useCreateGroupList() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ name, groupId }: { name: string; groupId: string }) => {
      const { data, error } = await supabase
        .from('lists')
        .insert({
          name,
          type: 'group',
          owner_group_id: groupId,
        })
        .select()
        .single();

      if (error) throw error;
      return data as ListRow;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['lists', 'group', variables.groupId],
      });
    },
  });
}
