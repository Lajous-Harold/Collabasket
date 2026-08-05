import { useState } from 'react';
import { View, Text, FlatList, RefreshControl } from 'react-native';
import { useRouter } from 'expo-router';
import {
  useMyGroups,
  useCreateGroup,
  useDeleteGroup,
  useLeaveGroup,
  useRenameGroup,
} from '../../../src/hooks/useGroups';
import { Button } from '../../../src/components/ui/Button';
import { Card } from '../../../src/components/ui/Card';
import { EmptyState } from '../../../src/components/ui/EmptyState';
import { LoadingState } from '../../../src/components/ui/LoadingState';
import { ErrorState } from '../../../src/components/ui/ErrorState';
import { FormModal } from '../../../src/components/ui/FormModal';
import { confirm, notifyError } from '../../../src/utils/confirm';

const ROLE_LABELS: Record<string, string> = {
  owner: 'Propriétaire',
  admin: 'Admin',
  member: 'Membre',
};

type ModalState =
  | { kind: 'closed' }
  | { kind: 'create' }
  | { kind: 'rename'; groupId: string };

export default function GroupsScreen() {
  const { data: groups, isLoading, isError, refetch, isRefetching } =
    useMyGroups();
  const createGroup = useCreateGroup();
  const deleteGroup = useDeleteGroup();
  const leaveGroup = useLeaveGroup();
  const renameGroup = useRenameGroup();
  const router = useRouter();

  const [modal, setModal] = useState<ModalState>({ kind: 'closed' });
  const [nameInput, setNameInput] = useState('');

  const closeModal = () => {
    setModal({ kind: 'closed' });
    setNameInput('');
  };

  const handleSubmit = async () => {
    const name = nameInput.trim();
    if (!name || modal.kind === 'closed') return;

    try {
      if (modal.kind === 'create') {
        await createGroup.mutateAsync({ name });
      } else {
        await renameGroup.mutateAsync({ groupId: modal.groupId, name });
      }
      closeModal();
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur');
    }
  };

  const handleDelete = async (groupId: string, groupName: string) => {
    const ok = await confirm({
      title: 'Supprimer le groupe',
      message: `Supprimer "${groupName}" et toutes ses listes ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteGroup.mutateAsync(groupId);
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur');
    }
  };

  const handleLeave = async (membershipId: string, groupName: string) => {
    const ok = await confirm({
      title: 'Quitter le groupe',
      message: `Quitter "${groupName}" ?`,
      confirmLabel: 'Quitter',
      destructive: true,
    });
    if (!ok) return;
    try {
      await leaveGroup.mutateAsync(membershipId);
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur');
    }
  };

  return (
    <View className="flex-1 bg-gray-50 dark:bg-gray-950">
      {isLoading ? (
        <LoadingState />
      ) : isError && !groups ? (
        <ErrorState onRetry={() => refetch()} />
      ) : !groups?.length ? (
        <EmptyState
          icon="👥"
          title="Aucun groupe"
          subtitle="Créez un groupe pour partager des listes avec vos proches."
        >
          <Button
            title="+ Créer un groupe"
            onPress={() => setModal({ kind: 'create' })}
          />
        </EmptyState>
      ) : (
        <>
          <FlatList
            data={groups}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ padding: 16, gap: 12 }}
            refreshControl={
              <RefreshControl
                refreshing={isRefetching}
                onRefresh={() => refetch()}
              />
            }
            renderItem={({ item }) => (
              <Card
                onPress={() =>
                  router.push({
                    pathname: '/(app)/groups/[groupId]',
                    params: { groupId: item.id, groupName: item.name },
                  })
                }
              >
                <View className="flex-row items-center justify-between">
                  <View className="flex-1">
                    <Text className="text-base font-semibold text-gray-800 dark:text-gray-100">
                      {item.name}
                    </Text>
                    <Text className="text-xs text-primary-600 dark:text-primary-400 mt-1">
                      {ROLE_LABELS[item.role] ?? item.role}
                    </Text>
                  </View>
                  <View className="flex-row gap-2">
                    {(item.role === 'owner' || item.role === 'admin') && (
                      <Button
                        title="✎"
                        variant="outline"
                        size="sm"
                        onPress={() => {
                          setNameInput(item.name);
                          setModal({ kind: 'rename', groupId: item.id });
                        }}
                      />
                    )}
                    {item.role === 'owner' ? (
                      <Button
                        title="Suppr."
                        variant="danger"
                        size="sm"
                        onPress={() => handleDelete(item.id, item.name)}
                      />
                    ) : (
                      <Button
                        title="Quitter"
                        variant="outline"
                        size="sm"
                        onPress={() => handleLeave(item.membershipId, item.name)}
                      />
                    )}
                  </View>
                </View>
              </Card>
            )}
          />
          <View className="px-4 pb-6">
            <Button
              title="+ Créer un groupe"
              onPress={() => setModal({ kind: 'create' })}
            />
          </View>
        </>
      )}

      <FormModal
        visible={modal.kind !== 'closed'}
        title={modal.kind === 'rename' ? 'Renommer le groupe' : 'Nouveau groupe'}
        label="Nom du groupe"
        placeholder="Ex : Famille, Coloc..."
        value={nameInput}
        onChangeText={setNameInput}
        onSubmit={handleSubmit}
        onCancel={closeModal}
        submitLabel={modal.kind === 'rename' ? 'Renommer' : 'Créer'}
        loading={createGroup.isPending || renameGroup.isPending}
      />
    </View>
  );
}
