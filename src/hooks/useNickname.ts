import { useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import type { Database } from '../types/database';

type MembershipRow = Database['public']['Tables']['memberships']['Row'];

/**
 * Met a jour le surnom (nickname) du user dans un groupe via la RPC
 * update_my_nickname. Si nickname est null ou vide, on transmet ''
 * (la RPC fait nullif(trim(p_nickname), '')).
 */
export function useUpdateNickname() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      groupId,
      nickname,
    }: {
      groupId: string;
      nickname: string | null;
    }) => {
      const { data, error } = await supabase.rpc('update_my_nickname', {
        p_group_id: groupId,
        p_nickname: nickname ?? '',
      });

      if (error) throw error;
      return data as MembershipRow;
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['group-members', variables.groupId],
      });
    },
  });
}
