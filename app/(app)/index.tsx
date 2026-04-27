import { useState } from 'react';
import { View, Text, FlatList } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/hooks/useAuth';
import { useMyLists, useCreateList, useDeleteList } from '../../src/hooks/useLists';
import { useListViews } from '../../src/hooks/useListViews';
import { Button } from '../../src/components/ui/Button';
import { Card } from '../../src/components/ui/Card';
import { Badge } from '../../src/components/ui/Badge';
import { EmptyState } from '../../src/components/ui/EmptyState';
import { LoadingState } from '../../src/components/ui/LoadingState';
import { FormModal } from '../../src/components/ui/FormModal';
import { confirm, notifyError } from '../../src/utils/confirm';

export default function HomeScreen() {
  const { user } = useAuth();
  const { data: lists, isLoading } = useMyLists();
  const createList = useCreateList();
  const deleteList = useDeleteList();
  const { hasNewChanges } = useListViews();
  const router = useRouter();

  const [showModal, setShowModal] = useState(false);
  const [newListName, setNewListName] = useState('');

  const handleCreate = async () => {
    const name = newListName.trim();
    if (!name) return;

    try {
      await createList.mutateAsync(name);
      setNewListName('');
      setShowModal(false);
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const handleDelete = async (listId: string, listName: string) => {
    const ok = await confirm({
      title: 'Supprimer la liste',
      message: `Supprimer "${listName}" et tous ses articles ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteList.mutateAsync(listId);
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const displayName = user?.user_metadata?.display_name
    || user?.email?.split('@')[0]
    || '';

  return (
    <View className="flex-1 bg-gray-50">
      <View className="px-4 pt-4 pb-2">
        <Text className="text-lg text-gray-500">
          Bonjour{displayName ? `, ${displayName}` : ''} !
        </Text>
      </View>

      {isLoading ? (
        <LoadingState />
      ) : !lists?.length ? (
        <EmptyState
          icon="🛒"
          title="Aucune liste pour le moment"
          subtitle="Créez votre première liste de courses pour commencer."
        >
          <Button title="+ Nouvelle liste" onPress={() => setShowModal(true)} />
        </EmptyState>
      ) : (
        <>
          <FlatList
            data={lists}
            keyExtractor={(item) => item.id}
            contentContainerStyle={{ padding: 16, gap: 12 }}
            renderItem={({ item }) => (
              <Card
                onPress={() =>
                  router.push({
                    pathname: '/(app)/lists/[listId]',
                    params: { listId: item.id, listName: item.name },
                  })
                }
              >
                <View className="flex-row items-center justify-between">
                  <View className="flex-1 flex-row items-center gap-2">
                    <Badge visible={hasNewChanges(item.id, item.updated_at)} />
                    <View>
                      <Text className="text-base font-semibold text-gray-800">
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
                  <Button
                    title="Suppr."
                    variant="danger"
                    size="sm"
                    onPress={() => handleDelete(item.id, item.name)}
                  />
                </View>
              </Card>
            )}
          />
          <View className="px-4 pb-6">
            <Button
              title="+ Nouvelle liste"
              onPress={() => setShowModal(true)}
            />
          </View>
        </>
      )}

      <FormModal
        visible={showModal}
        title="Nouvelle liste"
        label="Nom de la liste"
        placeholder="Ex : Courses semaine"
        value={newListName}
        onChangeText={setNewListName}
        onSubmit={handleCreate}
        onCancel={() => {
          setShowModal(false);
          setNewListName('');
        }}
        loading={createList.isPending}
      />
    </View>
  );
}
