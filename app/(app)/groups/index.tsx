import { useState } from 'react';
import { View, Text, FlatList } from 'react-native';
import { useRouter } from 'expo-router';
import { useMyGroups, useCreateGroup, useDeleteGroup, useLeaveGroup } from '../../../src/hooks/useGroups';
import { Button } from '../../../src/components/ui/Button';
import { Card } from '../../../src/components/ui/Card';
import { EmptyState } from '../../../src/components/ui/EmptyState';
import { LoadingState } from '../../../src/components/ui/LoadingState';
import { FormModal } from '../../../src/components/ui/FormModal';
import { confirm, notifyError } from '../../../src/utils/confirm';

const ROLE_LABELS: Record<string, string> = {
  owner: 'Propriétaire',
  admin: 'Admin',
  member: 'Membre',
};

export default function GroupsScreen() {
  const { data: groups, isLoading } = useMyGroups();
  const createGroup = useCreateGroup();
  const deleteGroup = useDeleteGroup();
  const leaveGroup = useLeaveGroup();
  const router = useRouter();

  const [showModal, setShowModal] = useState(false);
  const [newGroupName, setNewGroupName] = useState('');

  const handleCreate = async () => {
    const name = newGroupName.trim();
    if (!name) return;

    try {
      await createGroup.mutateAsync({ name });
      setNewGroupName('');
      setShowModal(false);
    } catch (e: any) {
      notifyError(e.message);
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
    } catch (e: any) {
      notifyError(e.message);
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
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  return (
    <View className="flex-1 bg-gray-50">
      {isLoading ? (
        <LoadingState />
      ) : !groups?.length ? (
        <EmptyState
          icon="👥"
          title="Aucun groupe"
          subtitle="Créez un groupe pour partager des listes avec vos proches."
        >
          <Button title="+ Créer un groupe" onPress={() => setShowModal(true)} />
        </EmptyState>
      ) : (
        <>
          <FlatList
            data={groups}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ padding: 16, gap: 12 }}
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
                    <Text className="text-base font-semibold text-gray-800">
                      {item.name}
                    </Text>
                    <Text className="text-xs text-primary-600 mt-1">
                      {ROLE_LABELS[item.role] ?? item.role}
                    </Text>
                  </View>
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
              </Card>
            )}
          />
          <View className="px-4 pb-6">
            <Button
              title="+ Créer un groupe"
              onPress={() => setShowModal(true)}
            />
          </View>
        </>
      )}

      <FormModal
        visible={showModal}
        title="Nouveau groupe"
        label="Nom du groupe"
        placeholder="Ex : Famille, Coloc..."
        value={newGroupName}
        onChangeText={setNewGroupName}
        onSubmit={handleCreate}
        onCancel={() => {
          setShowModal(false);
          setNewGroupName('');
        }}
        loading={createGroup.isPending}
      />
    </View>
  );
}
