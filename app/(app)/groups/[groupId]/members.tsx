import { View, Text, FlatList } from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { confirm, notifyError } from '../../../../src/utils/confirm';
import {
  useGroupMembers,
  useUpdateMembership,
  useRemoveMember,
  type GroupMember,
} from '../../../../src/hooks/useGroups';
import { useAuthStore } from '../../../../src/stores/authStore';
import { Button } from '../../../../src/components/ui/Button';
import { LoadingState } from '../../../../src/components/ui/LoadingState';
import { EmptyState } from '../../../../src/components/ui/EmptyState';
import type { MembershipRole } from '../../../../src/types/database';
import { useNavColors } from '../../../../src/lib/theme';

interface MemberItemProps {
  member: GroupMember;
  currentUserId: string;
  currentUserRole: MembershipRole | undefined;
  onPromote: (membershipId: string) => void;
  onDemote: (membershipId: string) => void;
  onRemove: (membershipId: string, displayName: string) => void;
}

function roleBadge(role: MembershipRole): { label: string; bg: string; text: string } {
  switch (role) {
    case 'owner':
      return { label: 'Propriétaire', bg: 'bg-primary-100 dark:bg-primary-900', text: 'text-primary-700 dark:text-primary-300' };
    case 'admin':
      return { label: 'Admin', bg: 'bg-amber-100', text: 'text-amber-700' };
    case 'member':
      return { label: 'Membre', bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-300' };
  }
}

function MemberItem({
  member,
  currentUserId,
  currentUserRole,
  onPromote,
  onDemote,
  onRemove,
}: MemberItemProps) {
  const isMe = member.user_id === currentUserId;
  const canManage =
    !isMe && (currentUserRole === 'owner' || currentUserRole === 'admin');
  const badge = roleBadge(member.role);

  return (
    <View className="bg-white dark:bg-gray-900 rounded-xl px-4 py-3 mb-2 mx-4 flex-row items-center justify-between">
      <View className="flex-1 flex-row items-center gap-3">
        <View className="flex-1">
          <Text className="text-sm font-semibold text-gray-800 dark:text-gray-100">
            {member.display_name}
            {isMe ? ' (vous)' : ''}
          </Text>
          <View
            className={`self-start mt-1 rounded-full px-2 py-0.5 ${badge.bg}`}
          >
            <Text className={`text-xs font-medium ${badge.text}`}>
              {badge.label}
            </Text>
          </View>
        </View>
      </View>

      {canManage && member.role !== 'owner' && (
        <View className="flex-row gap-2 ml-2">
          {member.role === 'member' && (
            <Button
              title="Promouvoir"
              variant="outline"
              size="sm"
              onPress={() => onPromote(member.id)}
            />
          )}
          {member.role === 'admin' && currentUserRole === 'owner' && (
            <Button
              title="Rétrograder"
              variant="outline"
              size="sm"
              onPress={() => onDemote(member.id)}
            />
          )}
          <Button
            title="Retirer"
            variant="danger"
            size="sm"
            onPress={() => onRemove(member.id, member.display_name)}
          />
        </View>
      )}
    </View>
  );
}

export default function MembersScreen() {
  const nav = useNavColors();
  const { groupId } = useLocalSearchParams<{
    groupId: string;
    groupName: string;
  }>();
  const { data: members, isLoading } = useGroupMembers(groupId);
  const currentUser = useAuthStore((s) => s.user);
  const updateMembership = useUpdateMembership(groupId);
  const removeMember = useRemoveMember(groupId);

  const currentUserRole = members?.find(
    (m) => m.user_id === currentUser?.id,
  )?.role;

  const handlePromote = async (membershipId: string) => {
    try {
      await updateMembership.mutateAsync({ membershipId, role: 'admin' });
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur lors de la promotion');
    }
  };

  const handleDemote = async (membershipId: string) => {
    try {
      await updateMembership.mutateAsync({ membershipId, role: 'member' });
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur lors de la rétrogradation');
    }
  };

  const handleRemove = async (membershipId: string, displayName: string) => {
    const ok = await confirm({
      title: 'Retirer le membre',
      message: `Voulez-vous retirer ${displayName} du groupe ?`,
      confirmLabel: 'Retirer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await removeMember.mutateAsync(membershipId);
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur lors du retrait');
    }
  };

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: 'Membres',
          headerTintColor: nav.tint,
          headerStyle: { backgroundColor: nav.background },
          headerTitleStyle: { color: nav.text },
        }}
      />

      <View className="flex-1 bg-gray-50 dark:bg-gray-950 pt-4">
        {isLoading ? (
          <LoadingState />
        ) : !members?.length ? (
          <EmptyState
            icon="👥"
            title="Aucun membre"
            subtitle="Ce groupe ne contient aucun membre."
          />
        ) : (
          <FlatList
            data={members}
            keyExtractor={(item) => item.id}
            renderItem={({ item }) => (
              <MemberItem
                member={item}
                currentUserId={currentUser?.id ?? ''}
                currentUserRole={currentUserRole}
                onPromote={handlePromote}
                onDemote={handleDemote}
                onRemove={handleRemove}
              />
            )}
            contentContainerStyle={{ paddingBottom: 32 }}
          />
        )}
      </View>
    </>
  );
}
