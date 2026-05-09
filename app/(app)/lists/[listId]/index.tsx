import { Stack, useLocalSearchParams } from 'expo-router';
import { ListDetailView } from '../../../../src/components/features/ListDetailView';

export default function PersonalListDetailScreen() {
  const { listId, listName } = useLocalSearchParams<{
    listId: string;
    listName: string;
  }>();

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: listName || 'Liste',
          headerTintColor: '#0d9488',
        }}
      />
      <ListDetailView listId={listId} listName={listName} />
    </>
  );
}
