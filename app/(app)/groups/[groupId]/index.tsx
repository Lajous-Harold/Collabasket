import { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
} from 'react-native';
import { useLocalSearchParams, useRouter, Stack } from 'expo-router';
import { confirm, notifyError } from '../../../../src/utils/confirm';
import {
  useGroupMembers,
  useDeleteGroup,
  useLeaveGroup,
  type GroupMember,
} from '../../../../src/hooks/useGroups';
import {
  useGroupLists,
  useCreateGroupList,
  useRenameGroupList,
  useDeleteGroupList,
} from '../../../../src/hooks/useGroupLists';
import { useListViews } from '../../../../src/hooks/useListViews';
import { useShareInvitation } from '../../../../src/hooks/useInvitations';
import { useUpdateNickname } from '../../../../src/hooks/useNickname';
import { useAuthStore } from '../../../../src/stores/authStore';
import { Button } from '../../../../src/components/ui/Button';
import { Card } from '../../../../src/components/ui/Card';
import { Badge } from '../../../../src/components/ui/Badge';
import { EmptyState } from '../../../../src/components/ui/EmptyState';
import { LoadingState } from '../../../../src/components/ui/LoadingState';
import { FormModal } from '../../../../src/components/ui/FormModal';
import { NicknameModal } from '../../../../src/components/ui/NicknameModal';
import { useNavColors } from '../../../../src/lib/theme';

export default function GroupDetailScreen() {
  const nav = useNavColors();
  const { groupId, groupName } = useLocalSearchParams<{
    groupId: string;
    groupName: string;
  }>();
  const {
    data: lists,
    isLoading: listsLoading,
    refetch: refetchLists,
    isRefetching: listsRefetching,
  } = useGroupLists(groupId);
  const { data: members } = useGroupMembers(groupId);
  const createList = useCreateGroupList();
  const renameList = useRenameGroupList();
  const deleteList = useDeleteGroupList();
  const deleteGroup = useDeleteGroup();
  const leaveGroup = useLeaveGroup();
  const { shareInvitation, isPending: isSharing } = useShareInvitation();
  const { hasNewChanges } = useListViews();
  const updateNickname = useUpdateNickname();
  const currentUser = useAuthStore((s) => s.user);
  const router = useRouter();

  const myMembership = members?.find((m) => m.user_id === currentUser?.id);
  const myRole = myMembership?.role;
  const isAdminOrOwner = myRole === 'owner' || myRole === 'admin';

  const [listModal, setListModal] = useState<
    { kind: 'closed' } | { kind: 'create' } | { kind: 'rename'; listId: string }
  >({ kind: 'closed' });
  const [newListName, setNewListName] = useState('');
  const [nicknameMember, setNicknameMember] = useState<GroupMember | null>(null);

  const closeListModal = () => {
    setListModal({ kind: 'closed' });
    setNewListName('');
  };

  const handleListSubmit = async () => {
    const name = newListName.trim();
    if (!name || listModal.kind === 'closed') return;

    try {
      if (listModal.kind === 'create') {
        await createList.mutateAsync({ name, groupId });
      } else {
        await renameList.mutateAsync({
          listId: listModal.listId,
          name,
          groupId,
        });
      }
      closeListModal();
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleDeleteList = async (listId: string, listName: string) => {
    const ok = await confirm({
      title: 'Supprimer la liste',
      message: `Supprimer "${listName}" et tous ses articles ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteList.mutateAsync({ listId, groupId });
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleDeleteGroup = async () => {
    const ok = await confirm({
      title: 'Supprimer le groupe',
      message: `Supprimer "${groupName}" et toutes ses listes ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteGroup.mutateAsync(groupId);
      router.back();
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleLeaveGroup = async () => {
    if (!myMembership) return;
    const ok = await confirm({
      title: 'Quitter le groupe',
      message: `Quitter "${groupName}" ?`,
      confirmLabel: 'Quitter',
      destructive: true,
    });
    if (!ok) return;
    try {
      await leaveGroup.mutateAsync(myMembership.id);
      router.back();
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleNicknameSubmit = async (nickname: string | null) => {
    if (!nicknameMember) return;
    try {
      await updateNickname.mutateAsync({ groupId, nickname });
      setNicknameMember(null);
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: groupName || 'Groupe',
          headerTintColor: nav.tint,
          headerStyle: { backgroundColor: nav.background },
          headerTitleStyle: { color: nav.text },
        }}
      />

      <View className="flex-1 bg-gray-50 dark:bg-gray-950">
        {/* Bandeau membres */}
        <View className="bg-white dark:bg-gray-900 px-4 py-3 border-b border-gray-100 dark:border-gray-800">
          <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide mb-2">
            Membres ({members?.length ?? 0})
          </Text>
          <View className="flex-row flex-wrap items-center gap-2">
            {members?.map((m) => {
              const isMe = currentUser?.id === m.user_id;
              return (
                <TouchableOpacity
                  key={m.id}
                  className={`rounded-full px-3 py-1 ${
                    isMe
                      ? 'bg-primary-100 dark:bg-primary-900 border border-primary-200 dark:border-primary-800'
                      : 'bg-primary-50 dark:bg-primary-950'
                  }`}
                  activeOpacity={isMe ? 0.6 : 1}
                  onPress={() => {
                    if (isMe) setNicknameMember(m);
                  }}
                  disabled={!isMe}
                >
                  <Text className="text-xs text-primary-700 dark:text-primary-300">
                    {m.display_name}
                    {isMe ? ' (vous)' : ''}
                  </Text>
                </TouchableOpacity>
              );
            })}
            <Button
              title="+ Inviter"
              variant="outline"
              size="sm"
              loading={isSharing}
              onPress={async () => {
                try {
                  await shareInvitation(groupId, groupName || 'Groupe');
                } catch (e: any) {
                  if (e.message !== 'User did not share') {
                    notifyError(e.message);
                  }
                }
              }}
            />
            {isAdminOrOwner && (
              <Button
                title="Gérer"
                variant="outline"
                size="sm"
                onPress={() =>
                  router.push({
                    pathname: '/(app)/groups/[groupId]/members',
                    params: { groupId, groupName: groupName ?? '' },
                  })
                }
              />
            )}
            <Button
              title="💶 Dépenses"
              variant="outline"
              size="sm"
              onPress={() =>
                router.push({
                  pathname: '/(app)/groups/[groupId]/expenses',
                  params: { groupId },
                })
              }
            />
          </View>
        </View>

        {/* Listes du groupe */}
        <View className="flex-1">
          {listsLoading ? (
            <LoadingState />
          ) : !lists?.length ? (
            <EmptyState
              icon="📋"
              title="Aucune liste"
              subtitle="Ajoutez une liste partagée pour ce groupe."
            >
              <Button
                title="+ Nouvelle liste"
                onPress={() => setListModal({ kind: 'create' })}
              />
            </EmptyState>
          ) : (
            <FlatList
              data={lists}
              keyExtractor={(item) => item.id}
              contentContainerStyle={{ padding: 16, gap: 12 }}
              refreshControl={
                <RefreshControl
                  refreshing={listsRefetching}
                  onRefresh={() => refetchLists()}
                />
              }
              renderItem={({ item }) => (
                <Card
                  onPress={() =>
                    router.push({
                      pathname: '/(app)/groups/[groupId]/lists/[listId]',
                      params: {
                        groupId,
                        listId: item.id,
                        listName: item.name,
                        groupName: groupName ?? '',
                      },
                    })
                  }
                >
                  <View className="flex-row items-center justify-between">
                    <View className="flex-1 flex-row items-center gap-2">
                      <Badge visible={hasNewChanges(item.id, item.updated_at)} />
                      <View>
                        <Text className="text-base font-semibold text-gray-800 dark:text-gray-100">
                          {item.name}
                        </Text>
                        <Text className="text-xs text-gray-400 mt-1">
                          {new Date(item.updated_at).toLocaleDateString('fr-FR', {
                            day: 'numeric',
                            month: 'short',
                          })}
                        </Text>
                      </View>
                    </View>
                    <View className="flex-row gap-2">
                      <Button
                        title="✎"
                        variant="outline"
                        size="sm"
                        onPress={() => {
                          setNewListName(item.name);
                          setListModal({ kind: 'rename', listId: item.id });
                        }}
                      />
                      {isAdminOrOwner && (
                        <Button
                          title="Suppr."
                          variant="danger"
                          size="sm"
                          onPress={() => handleDeleteList(item.id, item.name)}
                        />
                      )}
                    </View>
                  </View>
                </Card>
              )}
              ListFooterComponent={
                <View className="pt-2 pb-2">
                  <Button
                    title="+ Nouvelle liste"
                    onPress={() => setListModal({ kind: 'create' })}
                  />
                </View>
              }
            />
          )}
        </View>

        {/* Actions groupe */}
        <View className="px-4 pb-6 pt-3 border-t border-gray-100 dark:border-gray-800">
          {myRole === 'owner' ? (
            <Button
              title="Supprimer le groupe"
              variant="danger"
              size="sm"
              loading={deleteGroup.isPending}
              onPress={handleDeleteGroup}
            />
          ) : myRole ? (
            <Button
              title="Quitter le groupe"
              variant="danger"
              size="sm"
              loading={leaveGroup.isPending}
              onPress={handleLeaveGroup}
            />
          ) : null}
        </View>
      </View>

      <FormModal
        visible={listModal.kind !== 'closed'}
        title={
          listModal.kind === 'rename'
            ? 'Renommer la liste'
            : 'Nouvelle liste de groupe'
        }
        label="Nom de la liste"
        placeholder="Ex : Courses hebdo"
        value={newListName}
        onChangeText={setNewListName}
        onSubmit={handleListSubmit}
        onCancel={closeListModal}
        submitLabel={listModal.kind === 'rename' ? 'Renommer' : 'Créer'}
        loading={createList.isPending || renameList.isPending}
      />

      <NicknameModal
        visible={nicknameMember !== null}
        currentNickname={nicknameMember?.nickname ?? null}
        defaultDisplayName={
          nicknameMember?.profile?.display_name ?? 'Utilisateur'
        }
        loading={updateNickname.isPending}
        onSubmit={handleNicknameSubmit}
        onCancel={() => setNicknameMember(null)}
      />
    </>
  );
}
