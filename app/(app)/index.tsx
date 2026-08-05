import { useState } from 'react';
import { View, Text, FlatList, RefreshControl } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/hooks/useAuth';
import {
  useMyLists,
  useCreateList,
  useDeleteList,
  useRenameList,
} from '../../src/hooks/useLists';
import { useListViews } from '../../src/hooks/useListViews';
import { Button } from '../../src/components/ui/Button';
import { Card } from '../../src/components/ui/Card';
import { Badge } from '../../src/components/ui/Badge';
import { EmptyState } from '../../src/components/ui/EmptyState';
import { LoadingState } from '../../src/components/ui/LoadingState';
import { ErrorState } from '../../src/components/ui/ErrorState';
import { FormModal } from '../../src/components/ui/FormModal';
import { confirm, notifyError } from '../../src/utils/confirm';

type ModalState =
  | { kind: 'closed' }
  | { kind: 'create' }
  | { kind: 'rename'; listId: string };

export default function HomeScreen() {
  const { user } = useAuth();
  const { data: lists, isLoading, isError, refetch, isRefetching } =
    useMyLists();
  const createList = useCreateList();
  const deleteList = useDeleteList();
  const renameList = useRenameList();
  const { hasNewChanges } = useListViews();
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
        await createList.mutateAsync(name);
      } else {
        await renameList.mutateAsync({ listId: modal.listId, name });
      }
      closeModal();
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur');
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
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur');
    }
  };

  const displayName = user?.user_metadata?.display_name
    || user?.email?.split('@')[0]
    || '';

  return (
    <View className="flex-1 bg-gray-50 dark:bg-gray-950">
      <View className="px-4 pt-4 pb-2">
        <Text className="text-lg text-gray-500 dark:text-gray-400">
          Bonjour{displayName ? `, ${displayName}` : ''} !
        </Text>
      </View>

      {isLoading ? (
        <LoadingState />
      ) : isError && !lists ? (
        <ErrorState onRetry={() => refetch()} />
      ) : !lists?.length ? (
        <EmptyState
          icon="🛒"
          title="Aucune liste pour le moment"
          subtitle="Créez votre première liste de courses pour commencer."
        >
          <Button
            title="+ Nouvelle liste"
            onPress={() => setModal({ kind: 'create' })}
          />
        </EmptyState>
      ) : (
        <>
          <FlatList
            data={lists}
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
                    pathname: '/(app)/lists/[listId]',
                    params: { listId: item.id, listName: item.name },
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
                        setNameInput(item.name);
                        setModal({ kind: 'rename', listId: item.id });
                      }}
                    />
                    <Button
                      title="Suppr."
                      variant="danger"
                      size="sm"
                      onPress={() => handleDelete(item.id, item.name)}
                    />
                  </View>
                </View>
              </Card>
            )}
          />
          <View className="px-4 pb-6">
            <Button
              title="+ Nouvelle liste"
              onPress={() => setModal({ kind: 'create' })}
            />
          </View>
        </>
      )}

      <FormModal
        visible={modal.kind !== 'closed'}
        title={modal.kind === 'rename' ? 'Renommer la liste' : 'Nouvelle liste'}
        label="Nom de la liste"
        placeholder="Ex : Courses semaine"
        value={nameInput}
        onChangeText={setNameInput}
        onSubmit={handleSubmit}
        onCancel={closeModal}
        submitLabel={modal.kind === 'rename' ? 'Renommer' : 'Créer'}
        loading={createList.isPending || renameList.isPending}
      />
    </View>
  );
}
