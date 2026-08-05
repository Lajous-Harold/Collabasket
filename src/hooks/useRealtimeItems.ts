import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';

export function useRealtimeItems(listId: string) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!listId) return;

    const channel = supabase
      .channel(`items:${listId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'items',
          filter: `list_id=eq.${listId}`,
        },
        () => {
          // Ne pas refetch pendant qu'une mutation d'items est en vol
          // (ou en file hors ligne) : le refetch écraserait l'état
          // optimiste. L'invalidation finale est faite par onSettled
          // (voir settleItems dans itemMutations.ts).
          if (queryClient.isMutating({ mutationKey: ['items'] }) > 0) return;
          queryClient.invalidateQueries({ queryKey: ['items', listId] });
        },
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [listId, queryClient]);
}
