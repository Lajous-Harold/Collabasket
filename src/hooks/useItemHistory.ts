import { useQuery } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database } from '../types/database';

type ItemHistoryRow = Database['public']['Tables']['item_history']['Row'];

/**
 * Suggestion d'item compatible avec ItemFormModal.
 * default_quantity peut etre absent (cas legacy) → optionnel.
 */
export interface ItemSuggestion {
  name: string;
  category?: string | null;
  unit?: string | null;
  default_quantity?: number | null;
}

/**
 * Liste les items deja utilises par le user, optionnellement filtres
 * par `query` (ILIKE sur name), tries par last_used_at desc, limit 20.
 *
 * Renvoie le shape compatible avec ItemSuggestion de ItemFormModal.
 */
export function useItemHistory(query?: string) {
  const user = useAuthStore((s) => s.user);
  const trimmed = query?.trim() ?? '';

  return useQuery({
    queryKey: ['item-history', user?.id, trimmed],
    queryFn: async (): Promise<ItemSuggestion[]> => {
      let req = supabase
        .from('item_history')
        .select('name, category, unit, default_quantity')
        .eq('user_id', user!.id)
        .order('last_used_at', { ascending: false })
        .limit(20);

      if (trimmed.length > 0) {
        // Echappe les wildcards SQL dans la requete utilisateur
        const safe = trimmed.replace(/[%_]/g, (m) => `\\${m}`);
        req = req.ilike('name', `%${safe}%`);
      }

      const { data, error } = await req;
      if (error) throw error;

      return (data ?? []) as Pick<
        ItemHistoryRow,
        'name' | 'category' | 'unit' | 'default_quantity'
      >[];
    },
    enabled: !!user,
  });
}
