import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database } from '../types/database';

type ProfileRow = Database['public']['Tables']['profiles']['Row'];
type ProfileUpdate = Database['public']['Tables']['profiles']['Update'];

/**
 * Fetch le profile du user actuellement connecte (auth.uid()).
 */
export function useMyProfile() {
  const user = useAuthStore((s) => s.user);

  return useQuery({
    queryKey: ['profile', user?.id],
    queryFn: async (): Promise<ProfileRow> => {
      const { data, error } = await supabase
        .from('profiles')
        .select('*')
        .eq('id', user!.id)
        .single();

      if (error) throw error;
      return data as ProfileRow;
    },
    enabled: !!user,
  });
}

export interface UpdateProfilePatch {
  display_name?: string;
  phone_number?: string | null;
  photo_url?: string | null;
}

/**
 * Met a jour le profile du user connecte.
 * Invalide ['profile'] et ['groups'] (les listes de membres affichent
 * display_name).
 */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async (patch: UpdateProfilePatch) => {
      const update: ProfileUpdate = {};
      if (patch.display_name !== undefined) update.display_name = patch.display_name;
      if (patch.phone_number !== undefined) update.phone_number = patch.phone_number;
      if (patch.photo_url !== undefined) update.photo_url = patch.photo_url;

      const { data, error } = await supabase
        .from('profiles')
        .update(update)
        .eq('id', user!.id)
        .select()
        .single();

      if (error) throw error;
      return data as ProfileRow;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
      queryClient.invalidateQueries({ queryKey: ['group-members'] });
    },
  });
}
