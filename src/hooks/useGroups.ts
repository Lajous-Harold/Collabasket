import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database, MembershipRole } from '../types/database';

type GroupRow = Database['public']['Tables']['groups']['Row'];
type MembershipRow = Database['public']['Tables']['memberships']['Row'];
type ProfileRow = Database['public']['Tables']['profiles']['Row'];

export type GroupWithRole = GroupRow & { role: MembershipRow['role']; membershipId: string };

export type GroupMember = MembershipRow & {
  profile: ProfileRow | null;
  /**
   * Nom d'affichage calcule cote client :
   *   nickname > profile.display_name > 'Membre'
   */
  display_name: string;
};

export function displayNameFor(member: {
  nickname?: string | null;
  profile?: { display_name?: string | null } | null;
}): string {
  return (
    member.nickname?.trim() ||
    member.profile?.display_name?.trim() ||
    'Membre'
  );
}

export function useMyGroups() {
  const user = useAuthStore((s) => s.user);

  return useQuery({
    queryKey: ['groups', user?.id],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('memberships')
        .select('id, role, group:groups(*)')
        .eq('user_id', user!.id)
        .order('joined_at', { ascending: false });

      if (error) throw error;

      return (data ?? []).map((m) => ({
        ...(m.group as unknown as GroupRow),
        role: m.role,
        membershipId: m.id as string,
      })) as GroupWithRole[];
    },
    enabled: !!user,
  });
}

export function useCreateGroup() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async ({ name, description }: { name: string; description?: string }) => {
      const { data: group, error } = await supabase
        .from('groups')
        .insert({
          name,
          description: description ?? null,
          created_by: user!.id,
        })
        .select()
        .single();

      if (error) throw error;
      return group as GroupRow;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}

export function useRenameGroup() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ groupId, name }: { groupId: string; name: string }) => {
      const { error } = await supabase
        .from('groups')
        .update({ name })
        .eq('id', groupId);
      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}

export function useDeleteGroup() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (groupId: string) => {
      const { error } = await supabase.from('groups').delete().eq('id', groupId);
      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}

export function useLeaveGroup() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (membershipId: string) => {
      const { error } = await supabase
        .from('memberships')
        .delete()
        .eq('id', membershipId);
      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
}

export function useUpdateMembership(groupId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      membershipId,
      role,
    }: {
      membershipId: string;
      role: MembershipRole;
    }) => {
      const { data, error } = await supabase
        .from('memberships')
        .update({ role })
        .eq('id', membershipId)
        .select()
        .single();
      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId] });
    },
  });
}

export function useRemoveMember(groupId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (membershipId: string) => {
      const { error } = await supabase
        .from('memberships')
        .delete()
        .eq('id', membershipId);
      if (error) throw error;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId] });
    },
  });
}

export function useGroupMembers(groupId: string) {
  return useQuery({
    queryKey: ['group-members', groupId],
    queryFn: async (): Promise<GroupMember[]> => {
      const { data, error } = await supabase
        .from('memberships')
        .select('*, profile:profiles(*)')
        .eq('group_id', groupId)
        .order('joined_at', { ascending: true });

      if (error) throw error;

      const rows = (data ?? []) as unknown as Array<
        MembershipRow & { profile: ProfileRow | null }
      >;

      return rows.map((row) => ({
        ...row,
        display_name: displayNameFor(row),
      }));
    },
    enabled: !!groupId,
  });
}
