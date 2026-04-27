import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Share } from 'react-native';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import * as Linking from 'expo-linking';

export function useCreateInvitation() {
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async (groupId: string) => {
      // Le token est genere cote serveur par le default
      // encode(gen_random_bytes(32), 'hex') du schema.
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + 7);

      const { data, error } = await supabase
        .from('invitations')
        .insert({
          group_id: groupId,
          invited_by: user!.id,
          contact_identifier: '',
          status: 'pending',
          expires_at: expiresAt.toISOString(),
        })
        .select()
        .single();

      if (error) throw error;
      return data;
    },
  });
}

export function useShareInvitation() {
  const createInvitation = useCreateInvitation();

  const shareInvitation = async (groupId: string, groupName: string) => {
    const invitation = await createInvitation.mutateAsync(groupId);
    const link = Linking.createURL(`/invite/${invitation.token}`);

    await Share.share({
      message: `Rejoins le groupe "${groupName}" sur Collabasket !\n${link}`,
    });

    return invitation;
  };

  return { shareInvitation, isPending: createInvitation.isPending };
}

export function useAcceptInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (token: string) => {
      // Toute la logique est dans la fonction RPC accept_invitation
      // (security definer, bypass RLS pour permettre l'INSERT membership
      //  et l'UPDATE invitation). Cf. migration 002_fix_rls_policies.sql
      const { data, error } = await supabase.rpc('accept_invitation', {
        p_token: token,
      });

      if (error) throw new Error(error.message);

      const result = Array.isArray(data) ? data[0] : data;
      if (!result) throw new Error('Invitation invalide.');

      return result as { group_id: string; group_name: string };
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}
