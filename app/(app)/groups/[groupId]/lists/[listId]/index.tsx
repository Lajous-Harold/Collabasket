import { Stack, useLocalSearchParams } from 'expo-router';
import { ListDetailView } from '../../../../../../src/components/features/ListDetailView';

export default function GroupListDetailScreen() {
  const { listId, listName, groupName, groupId } = useLocalSearchParams<{
    groupId: string;
    listId: string;
    listName: string;
    groupName: string;
  }>();

  const headerTitle = groupName
    ? `${groupName} / ${listName || 'Liste'}`
    : listName || 'Liste';

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: headerTitle,
          headerTintColor: '#0d9488',
        }}
      />
      <ListDetailView listId={listId} listName={listName} groupId={groupId} />
    </>
  );
}
